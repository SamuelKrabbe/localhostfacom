# Public frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the customer-facing side of `ui/` — design tokens, shared primitives, catalog and cart, PIX payment, order confirmation, and the public transparency dashboard — in plain CSS against the already-complete typed API client.

**Architecture:** CSS Modules colocated with each component, driven by one `:root` token file. Cart state lives in a React context mirrored to `localStorage` so it survives the trip to a bank app. Order identity lives in the URL. Only `pages/` call `api/`; `components/` read from `cart/` and `lib/` and never import upward.

**Tech Stack:** React 19, React Router 7, Vite 8, TypeScript 6, plain CSS with CSS Modules, Recharts 3 (already a dependency, used only on the dashboard), lucide-react for icons.

Spec: `docs/superpowers/specs/2026-08-07-public-frontend-design.md`.

## Global Constraints

- **Portuguese for anything a customer reads; English for everything else** — code, comments, commit messages, file names, CSS class names. This is a standing project rule.
- **No new dependencies.** Everything below uses the standard library, React, React Router, and the packages already in `ui/package.json`.
- **No Tailwind.** Any Tailwind utility class encountered in an existing file is dead markup and must be replaced, not preserved.
- **`erasableSyntaxOnly` is on.** No TypeScript constructor parameter properties, no `enum`, no `namespace`. This is what forced `ApiError` in `src/api/client.ts` to use explicit field declarations — follow that precedent.
- **`verbatimModuleSyntax` is on.** Type-only imports must be written `import type { X } from '...'`.
- **`noUnusedLocals` and `noUnusedParameters` are on.** An unused import fails the build, not just the lint.
- **ESLint runs `react-hooks/set-state-in-effect`.** Calling a `setState` function synchronously at the top of an effect body — e.g. `setError(null); setData(null);` before starting a fetch — is flagged even for an ordinary "reset then load" pattern. Fix: keep the effect body to the fetch's `.then`/`.catch` only, and drive re-fetching from a separate state value (a `reloadToken` counter bumped in the retry handler) rather than resetting state synchronously inside the effect. Task 4's `Catalog.tsx` establishes this pattern; Tasks 5, 6 and 7 need it too for their own loading effects.
- **ESLint runs `react-refresh/only-export-components`.** A module that exports a React component must not also export anything else. This is why cart state is split across three files in Task 3 — do not consolidate them.
- **ESLint runs `react-hooks/static-components`.** Never define a component inside another component's body. `src/pages/admin/AdminLayout.tsx` violates this today with its inner `NavLinks`; that file is out of scope for this plan and its two existing errors are expected to remain.
- **Money is a `number` in BRL** end to end. Format for display only, never round for storage.
- **Colors, spacing, radii and fonts come from tokens.** No hard-coded hex value or pixel spacing in any `.module.css` outside `tokens.css`.

## File Structure

| File | Responsibility |
|---|---|
| `src/styles/tokens.css` | Every design token as a `:root` custom property. The only file with literal colors. |
| `src/styles/global.css` | Reset, base typography, focus rings, body background. |
| `src/lib/format.ts` | `formatCurrency`, `formatDate`, `formatDateTime`, `formatCountdown`. |
| `src/lib/errors.ts` | `messageFor(error)` — maps an `ApiError` slug to a Portuguese message. |
| `src/components/Money.tsx` | A currency figure in monospace, with a sign-aware tone. |
| `src/components/StateView.tsx` | Loading, empty and error presentation, one component. |
| `src/components/AppHeader.tsx` | Wordmark, block cursor, and the two public nav links. |
| `src/components/ProductCard.tsx` | One catalog row with a quantity stepper. |
| `src/components/CartBar.tsx` | Sticky cart summary and checkout button. |
| `src/cart/storage.ts` | Safe `localStorage` access, cart persistence, order snapshots. |
| `src/cart/CartContext.ts` | The context object and its types. No component, so it may export freely. |
| `src/cart/CartProvider.tsx` | The provider component. Exports one component and nothing else. |
| `src/cart/useCart.ts` | The consumer hook. |
| `src/pages/Catalog.tsx` | `/cardapio` |
| `src/pages/PixPayment.tsx` | `/pagamento/:orderId` |
| `src/pages/OrderConfirmation.tsx` | `/confirmacao/:orderId` |
| `src/pages/PublicDashboard.tsx` | `/` |
| `src/App.tsx` | Routes and the provider tree. |

Each of the above gets a sibling `.module.css` where it renders anything.

## A note on testing

`ui/` has no test runner and adding one is out of scope, so these tasks cannot follow a literal red-green TDD cycle. Every task instead ends with the same three-part gate, and a task is not done until all three pass:

1. `npm run build` — `tsc -b` type-checks every file in the project, not just the changed one.
2. `npm run lint` — must report only the two known pre-existing `AdminLayout.tsx` errors.
3. The screen driven in a real browser via the `playwright-firefox` skill, with a stated thing to look for.

Do not skip step 3 by reasoning about the code. The whole point of this pass is visual.

## Running the stack

Every task from Task 4 onward needs the API running. In three terminals:

```bash
podman compose up -d                                   # postgres + minio
cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd ui && npm run dev                                   # http://localhost:5173
```

The Vite dev server proxies `/api` to `localhost:8080`, so no CORS setup is needed.

---

## Task 1: Design tokens, global stylesheet and app shell

**Files:**
- Create: `ui/src/styles/tokens.css`, `ui/src/styles/global.css`
- Create: `ui/src/components/AppHeader.tsx`, `ui/src/components/AppHeader.module.css`
- Modify: `ui/src/main.tsx`, `ui/src/App.tsx`, `ui/tsconfig.app.json`
- Delete: `ui/src/index.css`, `ui/src/App.css`

**Interfaces:**
- Produces: every token name used by every later task; `AppHeader` (no props).
- Consumes: nothing.

- [ ] **Step 1: Turn on TypeScript strict mode**

The spec's null handling (`topProduct: string | null`, `crowdfundingUrl: string | null`) only means something if the compiler enforces it. `strict` is currently off. Verified: turning it on produces zero errors in the existing code, so this is free.

In `ui/tsconfig.app.json`, add `"strict": true` directly above `"noUnusedLocals": true`:

```json
    /* Linting */
    "strict": true,
    "noUnusedLocals": true,
```

- [ ] **Step 2: Verify strict mode is clean before writing anything else**

Run: `cd ui && npm run build`
Expected: PASS. If it fails, stop — something changed since this plan was written, and the failures need fixing before continuing.

- [ ] **Step 3: Write the token file**

`ui/src/styles/tokens.css`:

```css
:root {
  /* Surfaces. A warm off-white rather than pure white — this is a room, not a bank. */
  --color-bg: #fafaf9;
  --color-surface: #ffffff;
  --color-surface-sunken: #f5f5f4;
  --color-border: #e7e5e4;

  --color-text: #1c1917;
  --color-text-muted: #57534e;

  --color-accent: #7c3aed;
  --color-accent-hover: #6d28d9;
  --color-accent-weak: #f5f3ff;

  --color-positive: #15803d;
  --color-negative: #b91c1c;

  /* 4px scale. */
  --space-1: 0.25rem;
  --space-2: 0.5rem;
  --space-3: 0.75rem;
  --space-4: 1rem;
  --space-5: 1.5rem;
  --space-6: 2rem;
  --space-7: 3rem;
  --space-8: 4rem;

  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;

  --font-sans: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  --font-mono: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;

  --text-xs: 0.75rem;
  --text-sm: 0.875rem;
  --text-base: 1rem;
  --text-lg: 1.125rem;
  --text-xl: 1.375rem;
  --text-2xl: 1.75rem;

  --shadow-sm: 0 1px 2px rgba(28, 25, 23, 0.06);
  --shadow-up: 0 -2px 8px rgba(28, 25, 23, 0.08);

  /* Phone-first content column; the dashboard opts into the wider one. */
  --width-content: 34rem;
  --width-wide: 64rem;
}
```

- [ ] **Step 4: Write the global stylesheet**

`ui/src/styles/global.css`:

```css
@import "./tokens.css";

*,
*::before,
*::after {
  box-sizing: border-box;
}

html {
  -webkit-text-size-adjust: 100%;
}

body {
  margin: 0;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: var(--font-sans);
  font-size: var(--text-base);
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}

h1,
h2,
h3,
p,
figure {
  margin: 0;
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

img {
  display: block;
  max-width: 100%;
}

button {
  font: inherit;
  color: inherit;
  cursor: pointer;
}

a {
  color: var(--color-accent);
}

/* Keyboard focus stays visible; mouse focus does not draw a ring. */
:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}
```

- [ ] **Step 5: Write the header**

`ui/src/components/AppHeader.module.css`:

```css
.header {
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.inner {
  max-width: var(--width-wide);
  margin: 0 auto;
  padding: var(--space-4);
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-4);
}

.wordmark {
  font-family: var(--font-mono);
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--color-text);
  text-decoration: none;
}

.host {
  color: var(--color-text-muted);
}

.port {
  color: var(--color-accent);
}

/* Static, not blinking: a flashing element next to a payment total is a distraction. */
.cursor {
  color: var(--color-accent);
}

.nav {
  display: flex;
  gap: var(--space-4);
  font-size: var(--text-sm);
}

.link {
  color: var(--color-text-muted);
  text-decoration: none;
}

.link:hover {
  color: var(--color-accent);
}

.linkActive {
  color: var(--color-accent);
  font-weight: 500;
}
```

`ui/src/components/AppHeader.tsx`:

```tsx
import { Link, NavLink } from 'react-router-dom';
import styles from './AppHeader.module.css';

export function AppHeader() {
  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link to="/" className={styles.wordmark}>
          <span className={styles.host}>localhost</span>
          <span className={styles.port}>:facom</span>
          <span className={styles.cursor}> █</span>
        </Link>
        <nav className={styles.nav}>
          <NavLink
            to="/"
            end
            className={({ isActive }) => (isActive ? styles.linkActive : styles.link)}
          >
            Transparência
          </NavLink>
          <NavLink
            to="/cardapio"
            className={({ isActive }) => (isActive ? styles.linkActive : styles.link)}
          >
            Cardápio
          </NavLink>
        </nav>
      </div>
    </header>
  );
}
```

- [ ] **Step 6: Point the entry file at the new stylesheet**

`ui/src/main.tsx` — change the CSS import only:

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/global.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

- [ ] **Step 7: Wire the shell into the router**

Replace `ui/src/App.tsx` in full. `PublicDashboard` moves to `/` now and gets rebuilt in Task 7; it will look bare until then, because its Tailwind classes have never done anything.

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppHeader } from './components/AppHeader';
import { PublicDashboard } from './pages/PublicDashboard';

function App() {
  return (
    <BrowserRouter>
      <AppHeader />
      <Routes>
        <Route path="/" element={<PublicDashboard />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
```

- [ ] **Step 8: Delete the dead stylesheets**

```bash
cd ui && rm src/index.css src/App.css
```

`index.css` was Vite template boilerplate that nothing in the design uses; `App.css` was never imported at all.

- [ ] **Step 9: Verify**

```bash
cd ui && npm run build && npm run lint
```
Expected: build passes; lint reports only the two known `AdminLayout.tsx` errors.

Then start the dev server and open `http://localhost:5173/` with the `playwright-firefox` skill. Look for: the `localhost:facom █` wordmark in monospace with a purple `:facom` and cursor, a white header bar on a warm off-white page, and two working nav links. The dashboard below it will be unstyled — that is expected at this stage.

- [ ] **Step 10: Commit**

```bash
git add ui/src ui/tsconfig.app.json
git commit -m "feat(ui): add design tokens, global stylesheet and app header"
```

---

## Task 2: Shared utilities and primitives

**Files:**
- Create: `ui/src/lib/format.ts`, `ui/src/lib/errors.ts`
- Create: `ui/src/components/Money.tsx`, `ui/src/components/Money.module.css`
- Create: `ui/src/components/StateView.tsx`, `ui/src/components/StateView.module.css`

**Interfaces:**
- Consumes: `ApiError` from `src/api/client.ts`; tokens from Task 1.
- Produces:
  - `formatCurrency(value: number): string`
  - `formatDate(iso: string): string`
  - `formatDateTime(iso: string): string`
  - `formatCountdown(msRemaining: number): string`
  - `messageFor(error: unknown): string`
  - `<Money value={number} tone?: 'default' | 'auto' | 'muted' size?: 'sm' | 'md' | 'lg' />`
  - `<StateView kind="loading" | "empty" | "error" message={string} onRetry?={() => void} />`

Every later task uses these. No page re-implements currency formatting or an error message.

- [ ] **Step 1: Write the formatters**

`ui/src/lib/format.ts`:

```ts
const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const date = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit' });
const dateTime = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatCurrency(value: number): string {
  return currency.format(value);
}

export function formatDate(iso: string): string {
  return date.format(new Date(iso));
}

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** Renders a countdown as mm:ss, clamped at zero so it never shows a negative timer. */
export function formatCountdown(msRemaining: number): string {
  const totalSeconds = Math.max(0, Math.floor(msRemaining / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}
```

- [ ] **Step 2: Write the error mapper**

The slugs below are the complete set the public flow can produce, read off the API's `ApiException` call sites. Anything unmapped falls back to a generic message — a raw English backend string must never reach a customer.

`ui/src/lib/errors.ts`:

```ts
import { ApiError } from '../api/client';

const MESSAGES: Record<string, string> = {
  'empty-cart': 'Seu carrinho está vazio.',
  'product-not-found': 'Um dos produtos não está mais disponível.',
  'product-inactive': 'Um dos produtos saiu do cardápio. Revise seu pedido.',
  'order-not-found': 'Pedido não encontrado.',
  'order-not-pending': 'Este pedido já foi finalizado.',
  'order-has-no-charge': 'A cobrança deste pedido ainda não foi gerada.',
  'payment-provider-error': 'O sistema de pagamento está indisponível. Tente de novo em instantes.',
  'rate-limited': 'Muitas tentativas seguidas. Aguarde alguns segundos.',
  'validation-failed': 'Confira os dados do pedido e tente de novo.',
  'internal-error': 'Algo deu errado do nosso lado. Tente de novo.',
};

const FALLBACK = 'Não foi possível completar a operação. Tente de novo.';
const OFFLINE = 'Sem conexão com o servidor. Verifique sua internet.';

export function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    return MESSAGES[error.slug] ?? FALLBACK;
  }
  // fetch rejects with a TypeError when the network itself is unreachable.
  if (error instanceof TypeError) {
    return OFFLINE;
  }
  return FALLBACK;
}
```

- [ ] **Step 3: Write the Money primitive**

`ui/src/components/Money.module.css`:

```css
.money {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.sm {
  font-size: var(--text-sm);
}

.md {
  font-size: var(--text-base);
}

.lg {
  font-size: var(--text-2xl);
  font-weight: 600;
}

.muted {
  color: var(--color-text-muted);
}

.positive {
  color: var(--color-positive);
}

.negative {
  color: var(--color-negative);
}
```

`ui/src/components/Money.tsx`:

```tsx
import { formatCurrency } from '../lib/format';
import styles from './Money.module.css';

interface MoneyProps {
  value: number;
  /** 'auto' colors by sign — used for the net balance, which can be a real deficit. */
  tone?: 'default' | 'auto' | 'muted';
  size?: 'sm' | 'md' | 'lg';
}

export function Money({ value, tone = 'default', size = 'md' }: MoneyProps) {
  const toneClass =
    tone === 'muted'
      ? styles.muted
      : tone === 'auto'
        ? value < 0
          ? styles.negative
          : styles.positive
        : undefined;

  return (
    <span className={[styles.money, styles[size], toneClass].filter(Boolean).join(' ')}>
      {formatCurrency(value)}
    </span>
  );
}
```

- [ ] **Step 4: Write the StateView primitive**

`ui/src/components/StateView.module.css`:

```css
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-7) var(--space-4);
  text-align: center;
  color: var(--color-text-muted);
}

.message {
  font-size: var(--text-sm);
  max-width: 28rem;
}

/* The loading label is monospace so it reads as machine output. */
.loading {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
}

.retry {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-4);
  font-size: var(--text-sm);
}

.retry:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
```

`ui/src/components/StateView.tsx`:

```tsx
import styles from './StateView.module.css';

interface StateViewProps {
  kind: 'loading' | 'empty' | 'error';
  message: string;
  onRetry?: () => void;
}

export function StateView({ kind, message, onRetry }: StateViewProps) {
  return (
    <div className={styles.state} role={kind === 'error' ? 'alert' : 'status'}>
      <p className={kind === 'loading' ? styles.loading : styles.message}>{message}</p>
      {kind === 'error' && onRetry ? (
        <button type="button" className={styles.retry} onClick={onRetry}>
          Tentar de novo
        </button>
      ) : null}
    </div>
  );
}
```

- [ ] **Step 5: Verify**

```bash
cd ui && npm run build && npm run lint
```
Expected: build passes; lint reports only the two known `AdminLayout.tsx` errors.

No browser check this task — nothing renders these yet. Task 4 is the first visual confirmation.

- [ ] **Step 6: Commit**

```bash
git add ui/src/lib ui/src/components
git commit -m "feat(ui): add formatting, error messages and shared primitives"
```

---

## Task 3: Cart state

**Files:**
- Create: `ui/src/cart/storage.ts`, `ui/src/cart/CartContext.ts`, `ui/src/cart/CartProvider.tsx`, `ui/src/cart/useCart.ts`
- Modify: `ui/src/App.tsx`

**Interfaces:**
- Consumes: `Product`, `CartItem` from `src/types.ts`.
- Produces:
  - `useCart(): CartValue` where `CartValue` is `{ items: CartItem[]; totalItems: number; totalPrice: number; quantityOf(productId: string): number; setQuantity(product: Product, quantity: number): void; clear(): void }`
  - `saveOrderSnapshot(orderId: string, items: CartItem[]): void`
  - `readOrderSnapshot(orderId: string): CartItem[] | null`

**The three-file split is deliberate.** `react-refresh/only-export-components` fails the lint if a module exports both a component and a hook or a context object. Do not merge these files.

- [ ] **Step 1: Write the storage layer**

Every access is wrapped: private browsing and disabled-storage modes make `localStorage` throw rather than return null, and losing a cart must never crash the checkout screen.

`ui/src/cart/storage.ts`:

```ts
import type { CartItem } from '../types';

const CART_KEY = 'localhostfacom.cart';
const SNAPSHOT_PREFIX = 'localhostfacom.order.';
const SNAPSHOT_TTL_MS = 24 * 60 * 60 * 1000;

interface Snapshot {
  savedAt: number;
  items: CartItem[];
}

/** localStorage throws instead of returning null when storage is disabled. */
function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage unavailable or full. State stays in memory for this session.
  }
}

function remove(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch {
    // Nothing to do — the entry is unreachable either way.
  }
}

export function loadCart(): CartItem[] {
  const raw = read(CART_KEY);
  if (!raw) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as CartItem[]) : [];
  } catch {
    // Corrupted entry from an older shape; start clean rather than crash.
    return [];
  }
}

export function saveCart(items: CartItem[]): void {
  write(CART_KEY, JSON.stringify(items));
}

export function saveOrderSnapshot(orderId: string, items: CartItem[]): void {
  const snapshot: Snapshot = { savedAt: Date.now(), items };
  write(SNAPSHOT_PREFIX + orderId, JSON.stringify(snapshot));
}

/** Returns null when the snapshot is absent, unreadable or older than a day. */
export function readOrderSnapshot(orderId: string): CartItem[] | null {
  const key = SNAPSHOT_PREFIX + orderId;
  const raw = read(key);
  if (!raw) {
    return null;
  }
  try {
    const snapshot: Snapshot = JSON.parse(raw);
    if (Date.now() - snapshot.savedAt > SNAPSHOT_TTL_MS) {
      remove(key);
      return null;
    }
    return snapshot.items;
  } catch {
    remove(key);
    return null;
  }
}
```

- [ ] **Step 2: Write the context object**

This file exports no component, so it is free to export the context and its types.

`ui/src/cart/CartContext.ts`:

```ts
import { createContext } from 'react';
import type { CartItem, Product } from '../types';

export interface CartValue {
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
  quantityOf: (productId: string) => number;
  setQuantity: (product: Product, quantity: number) => void;
  clear: () => void;
}

export const CartContext = createContext<CartValue | null>(null);
```

- [ ] **Step 3: Write the provider**

`ui/src/cart/CartProvider.tsx`:

```tsx
import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { CartItem, Product } from '../types';
import { CartContext } from './CartContext';
import type { CartValue } from './CartContext';
import { loadCart, saveCart } from './storage';

/** Quantity ceiling per line, matching the API's @Max(99) on order items. */
const MAX_QUANTITY = 99;

export function CartProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<CartItem[]>(() => loadCart());

  useEffect(() => {
    saveCart(items);
  }, [items]);

  const setQuantity = useCallback((product: Product, quantity: number) => {
    const clamped = Math.min(Math.max(quantity, 0), MAX_QUANTITY);

    setItems((current) => {
      if (clamped === 0) {
        return current.filter((item) => item.id !== product.id);
      }
      const existing = current.find((item) => item.id === product.id);
      if (!existing) {
        return [...current, { ...product, quantity: clamped }];
      }
      // Reuses the freshest product data, so a price change is picked up on re-add.
      return current.map((item) =>
        item.id === product.id ? { ...product, quantity: clamped } : item,
      );
    });
  }, []);

  const clear = useCallback(() => setItems([]), []);

  const value = useMemo<CartValue>(() => {
    return {
      items,
      totalItems: items.reduce((sum, item) => sum + item.quantity, 0),
      totalPrice: items.reduce((sum, item) => sum + item.price * item.quantity, 0),
      quantityOf: (productId) => items.find((item) => item.id === productId)?.quantity ?? 0,
      setQuantity,
      clear,
    };
  }, [items, setQuantity, clear]);

  return <CartContext value={value}>{children}</CartContext>;
}
```

Note: React 19 allows rendering a context object directly as a provider — `<CartContext value={...}>` rather than `<CartContext.Provider value={...}>`. Both work; this is the current form.

- [ ] **Step 4: Write the hook**

`ui/src/cart/useCart.ts`:

```ts
import { use } from 'react';
import { CartContext } from './CartContext';
import type { CartValue } from './CartContext';

export function useCart(): CartValue {
  const value = use(CartContext);
  if (!value) {
    throw new Error('useCart must be used inside CartProvider');
  }
  return value;
}
```

- [ ] **Step 5: Wrap the app in the provider**

Replace `ui/src/App.tsx` in full:

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppHeader } from './components/AppHeader';
import { CartProvider } from './cart/CartProvider';
import { PublicDashboard } from './pages/PublicDashboard';

function App() {
  return (
    <CartProvider>
      <BrowserRouter>
        <AppHeader />
        <Routes>
          <Route path="/" element={<PublicDashboard />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </CartProvider>
  );
}

export default App;
```

- [ ] **Step 6: Verify**

```bash
cd ui && npm run build && npm run lint
```
Expected: build passes; lint reports only the two known `AdminLayout.tsx` errors. In particular there must be no `react-refresh/only-export-components` error — if one appears, a file is exporting a component alongside something else and needs splitting further.

- [ ] **Step 7: Commit**

```bash
git add ui/src/cart ui/src/App.tsx
git commit -m "feat(ui): add cart state persisted to local storage"
```

---

## Task 4: Catalog screen

**Files:**
- Rewrite: `ui/src/components/ProductCard.tsx`, `ui/src/components/CartBar.tsx`
- Create: `ui/src/components/ProductCard.module.css`, `ui/src/components/CartBar.module.css`
- Create: `ui/src/pages/Catalog.tsx`, `ui/src/pages/Catalog.module.css`
- Modify: `ui/src/App.tsx`

**Interfaces:**
- Consumes: `listProducts`, `createOrder` from `src/api/public.ts`; `useCart`; `saveOrderSnapshot`; `Money`, `StateView`; `messageFor`.
- Produces: the `/cardapio` route. Navigates to `/pagamento/:orderId` with the `OrderChargeResponse` in router state under the key `charge`. Task 5 reads that key.

Both existing components are rewritten rather than edited: their entire markup is Tailwind classes, and their props change to read from the cart context.

- [ ] **Step 1: Write the product card**

`ui/src/components/ProductCard.module.css`:

```css
.card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.thumb,
.thumbEmpty {
  width: 3.5rem;
  height: 3.5rem;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  object-fit: cover;
  background: var(--color-surface-sunken);
}

.thumbEmpty {
  display: grid;
  place-items: center;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: var(--text-base);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stepper {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
  padding: var(--space-1);
  flex-shrink: 0;
}

.step {
  width: 2rem;
  height: 2rem;
  display: grid;
  place-items: center;
  border: none;
  background: none;
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
}

.step:hover:not(:disabled) {
  background: var(--color-surface);
  color: var(--color-accent);
}

.step:disabled {
  opacity: 0.35;
  cursor: default;
}

.quantity {
  min-width: 1.5rem;
  text-align: center;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}
```

`ui/src/components/ProductCard.tsx`:

```tsx
import { Minus, Plus } from 'lucide-react';
import type { Product } from '../types';
import { Money } from './Money';
import styles from './ProductCard.module.css';

interface ProductCardProps {
  product: Product;
  quantity: number;
  onChange: (product: Product, quantity: number) => void;
}

export function ProductCard({ product, quantity, onChange }: ProductCardProps) {
  return (
    <li className={styles.card}>
      {product.imageUrl ? (
        <img src={product.imageUrl} alt="" className={styles.thumb} />
      ) : (
        <div className={styles.thumbEmpty} aria-hidden="true">
          sem foto
        </div>
      )}

      <div className={styles.info}>
        <h2 className={styles.name}>{product.name}</h2>
        <Money value={product.price} size="sm" tone="muted" />
      </div>

      <div className={styles.stepper}>
        <button
          type="button"
          className={styles.step}
          onClick={() => onChange(product, quantity - 1)}
          disabled={quantity === 0}
          aria-label={`Remover um ${product.name}`}
        >
          <Minus size={16} />
        </button>
        <span className={styles.quantity}>{quantity}</span>
        <button
          type="button"
          className={styles.step}
          onClick={() => onChange(product, quantity + 1)}
          aria-label={`Adicionar um ${product.name}`}
        >
          <Plus size={16} />
        </button>
      </div>
    </li>
  );
}
```

The image `alt` is intentionally empty: the product name sits right beside it, and a screen reader announcing the name twice is noise.

- [ ] **Step 2: Write the cart bar**

`ui/src/components/CartBar.module.css`:

```css
.bar {
  position: fixed;
  inset: auto 0 0 0;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  box-shadow: var(--shadow-up);
  padding: var(--space-3) var(--space-4);
  padding-bottom: calc(var(--space-3) + env(safe-area-inset-bottom));
}

.inner {
  max-width: var(--width-content);
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.count {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.checkout {
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-accent);
  color: #fff;
  font-weight: 500;
  padding: var(--space-3) var(--space-5);
}

.checkout:hover:not(:disabled) {
  background: var(--color-accent-hover);
}

.checkout:disabled {
  opacity: 0.6;
  cursor: default;
}

.error {
  max-width: var(--width-content);
  margin: 0 auto var(--space-2);
  color: var(--color-negative);
  font-size: var(--text-sm);
  text-align: center;
}
```

`ui/src/components/CartBar.tsx`:

```tsx
import { Money } from './Money';
import styles from './CartBar.module.css';

interface CartBarProps {
  totalItems: number;
  totalPrice: number;
  isSubmitting: boolean;
  error: string | null;
  onCheckout: () => void;
}

export function CartBar({ totalItems, totalPrice, isSubmitting, error, onCheckout }: CartBarProps) {
  if (totalItems === 0) {
    return null;
  }

  return (
    <div className={styles.bar}>
      {error ? (
        <p className={styles.error} role="alert">
          {error}
        </p>
      ) : null}
      <div className={styles.inner}>
        <div>
          <p className={styles.count}>
            {totalItems} {totalItems === 1 ? 'item' : 'itens'}
          </p>
          <Money value={totalPrice} size="lg" />
        </div>
        <button
          type="button"
          className={styles.checkout}
          onClick={onCheckout}
          disabled={isSubmitting}
        >
          {isSubmitting ? 'Gerando cobrança...' : 'Pagar com PIX'}
        </button>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Write the catalog page**

`ui/src/pages/Catalog.module.css`:

```css
.page {
  max-width: var(--width-content);
  margin: 0 auto;
  padding: var(--space-5) var(--space-4);
  /* Room for the fixed cart bar so the last product is never trapped underneath. */
  padding-bottom: var(--space-8);
}

.intro {
  margin-bottom: var(--space-5);
}

.title {
  font-size: var(--text-xl);
  font-weight: 600;
}

.subtitle {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
```

`ui/src/pages/Catalog.tsx`:

```tsx
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createOrder, listProducts } from '../api/public';
import { CartBar } from '../components/CartBar';
import { ProductCard } from '../components/ProductCard';
import { StateView } from '../components/StateView';
import { saveOrderSnapshot } from '../cart/storage';
import { useCart } from '../cart/useCart';
import { messageFor } from '../lib/errors';
import type { Product } from '../types';
import styles from './Catalog.module.css';

export function Catalog() {
  const [products, setProducts] = useState<Product[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const cart = useCart();
  const navigate = useNavigate();

  const load = useCallback(() => {
    setLoadError(null);
    setProducts(null);
    listProducts()
      .then(setProducts)
      .catch((error: unknown) => setLoadError(messageFor(error)));
  }, []);

  useEffect(load, [load]);

  const checkout = async () => {
    setIsSubmitting(true);
    setCheckoutError(null);
    try {
      const items = cart.items.map((item) => ({ productId: item.id, quantity: item.quantity }));
      const charge = await createOrder(items);
      // Written before navigating: the receipt screen has no other source for line items.
      saveOrderSnapshot(charge.orderId, cart.items);
      navigate(`/pagamento/${charge.orderId}`, { state: { charge } });
    } catch (error: unknown) {
      setCheckoutError(messageFor(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loadError) {
    return <StateView kind="error" message={loadError} onRetry={load} />;
  }

  if (!products) {
    return <StateView kind="loading" message="carregando cardápio..." />;
  }

  if (products.length === 0) {
    return <StateView kind="empty" message="Nenhum produto disponível no momento." />;
  }

  return (
    <>
      <div className={styles.page}>
        <div className={styles.intro}>
          <h1 className={styles.title}>Cardápio</h1>
          <p className={styles.subtitle}>
            Monte seu pedido e pague com PIX. Sem cadastro, sem login.
          </p>
        </div>

        <ul className={styles.list}>
          {products.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              quantity={cart.quantityOf(product.id)}
              onChange={cart.setQuantity}
            />
          ))}
        </ul>
      </div>

      <CartBar
        totalItems={cart.totalItems}
        totalPrice={cart.totalPrice}
        isSubmitting={isSubmitting}
        error={checkoutError}
        onCheckout={checkout}
      />
    </>
  );
}
```

- [ ] **Step 4: Add the route**

In `ui/src/App.tsx`, add the import and the route:

```tsx
import { Catalog } from './pages/Catalog';
```

```tsx
        <Route path="/cardapio" element={<Catalog />} />
```

Place it directly above the `path="*"` fallback route.

- [ ] **Step 5: Verify**

```bash
cd ui && npm run build && npm run lint
```
Expected: build passes; lint reports only the two known `AdminLayout.tsx` errors.

Then, with the API running, seed two products so the catalog has something to show:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@localhost.facom","password":"troque-esta-senha"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

curl -s -X POST localhost:8080/api/admin/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Café","price":3.00}'

curl -s -X POST localhost:8080/api/admin/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Bolo de cenoura","price":5.50}'
```

That login only works if the API was started with a bootstrap admin. If `$TOKEN` comes back empty, restart the API with:

```bash
cd api && APP_BOOTSTRAP_ADMIN_EMAIL=admin@localhost.facom \
  APP_BOOTSTRAP_ADMIN_PASSWORD=troque-esta-senha \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Open `http://localhost:5173/cardapio` with the `playwright-firefox` skill. Look for: both products in bordered cards with monospace prices, steppers that increment and decrement, the cart bar appearing only once something is added, and the total updating. Reload the page with items in the cart and confirm they are still there — that is the localStorage path working.

- [ ] **Step 6: Commit**

```bash
git add ui/src/components ui/src/pages/Catalog.tsx ui/src/pages/Catalog.module.css ui/src/App.tsx
git commit -m "feat(ui): add catalog screen with cart"
```

---

## Task 5: PIX payment screen

**Files:**
- Rewrite: `ui/src/pages/PixPayment.tsx`
- Create: `ui/src/pages/PixPayment.module.css`
- Modify: `ui/src/App.tsx`

**Interfaces:**
- Consumes: `createCharge`, `getOrderStatus` from `src/api/public.ts`; the `charge` key in router state set by Task 4; `Money`, `StateView`, `formatCountdown`, `messageFor`.
- Produces: the `/pagamento/:orderId` route. Navigates to `/confirmacao/:orderId` on `PAID`, which Task 6 implements.

- [ ] **Step 1: Write the stylesheet**

`ui/src/pages/PixPayment.module.css`:

```css
.page {
  max-width: var(--width-content);
  margin: 0 auto;
  padding: var(--space-5) var(--space-4);
}

.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
  text-align: center;
}

.title {
  font-size: var(--text-xl);
  font-weight: 600;
}

.hint {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.qr {
  width: 15rem;
  height: 15rem;
  padding: var(--space-3);
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.copy {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: 1px solid var(--color-border);
  background: var(--color-surface-sunken);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  font-size: var(--text-sm);
}

.copy:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.waiting {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: var(--text-sm);
}

.spinner {
  animation: spin 1s linear infinite;
  color: var(--color-accent);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation: none;
  }
}

.countdown {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.expiring {
  color: var(--color-negative);
}

.back {
  color: var(--color-accent);
  font-size: var(--text-sm);
}
```

- [ ] **Step 2: Write the page**

Three things this replaces from the old mockup: the wrong URL (`/api/orders/...`), the raw `fetch`, and the client-side 10-minute `setTimeout` that could fire mid-payment.

`ui/src/pages/PixPayment.tsx`:

```tsx
import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Check, Copy, Loader2 } from 'lucide-react';
import { createCharge, getOrderStatus } from '../api/public';
import { Money } from '../components/Money';
import { StateView } from '../components/StateView';
import { formatCountdown } from '../lib/format';
import { messageFor } from '../lib/errors';
import type { OrderChargeResponse } from '../types';
import styles from './PixPayment.module.css';

const POLL_INTERVAL_MS = 3000;

export function PixPayment() {
  const { orderId = '' } = useParams();
  const location = useLocation();
  const navigate = useNavigate();

  // Present on the normal path from checkout; absent after a refresh or a cold open.
  const initialCharge = (location.state as { charge?: OrderChargeResponse } | null)?.charge ?? null;

  const [charge, setCharge] = useState<OrderChargeResponse | null>(initialCharge);
  const [error, setError] = useState<string | null>(null);
  const [isExpired, setIsExpired] = useState(false);
  const [copied, setCopied] = useState(false);
  const [msRemaining, setMsRemaining] = useState(0);

  const loadCharge = useCallback(() => {
    setError(null);
    // Idempotent server-side: returns the original charge, never a second payable one.
    createCharge(orderId)
      .then(setCharge)
      .catch((cause: unknown) => setError(messageFor(cause)));
  }, [orderId]);

  useEffect(() => {
    if (!charge) {
      loadCharge();
    }
  }, [charge, loadCharge]);

  // Polls until the order leaves PENDING. A failed poll is not a failed payment.
  useEffect(() => {
    if (!charge || isExpired) {
      return;
    }

    const controller = new AbortController();

    const check = async () => {
      try {
        const result = await getOrderStatus(orderId, controller.signal);
        if (result.status === 'PAID') {
          navigate(`/confirmacao/${orderId}`, { replace: true });
        } else if (result.status === 'EXPIRED' || result.status === 'CANCELED') {
          setIsExpired(true);
        }
      } catch {
        // Transient network or server blip. Keep polling.
      }
    };

    const interval = setInterval(check, POLL_INTERVAL_MS);
    return () => {
      clearInterval(interval);
      controller.abort();
    };
  }, [charge, isExpired, navigate, orderId]);

  // Display only. Expiry itself is whatever the API reports, never this timer.
  useEffect(() => {
    if (!charge) {
      return;
    }
    const expiresAt = new Date(charge.expiresAt).getTime();
    const tick = () => setMsRemaining(expiresAt - Date.now());
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [charge]);

  const copy = async () => {
    if (!charge) {
      return;
    }
    try {
      await navigator.clipboard.writeText(charge.payload);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError('Não foi possível copiar. Selecione o código manualmente.');
    }
  };

  if (isExpired) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <h1 className={styles.title}>Pedido expirado</h1>
          <p className={styles.hint}>
            O prazo para pagamento acabou. Seu carrinho foi mantido — é só refazer o pedido.
          </p>
          <Link to="/cardapio" className={styles.back}>
            Voltar ao cardápio
          </Link>
        </div>
      </div>
    );
  }

  if (error && !charge) {
    return <StateView kind="error" message={error} onRetry={loadCharge} />;
  }

  if (!charge) {
    return <StateView kind="loading" message="gerando cobrança..." />;
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <h1 className={styles.title}>Pague com PIX</h1>
        <p className={styles.hint}>Escaneie o QR Code no app do seu banco ou copie o código.</p>

        <img
          src={`data:image/png;base64,${charge.qrImageBase64}`}
          alt="QR Code do PIX"
          className={styles.qr}
        />

        <Money value={charge.total} size="lg" />

        <button type="button" className={styles.copy} onClick={copy}>
          {copied ? <Check size={18} /> : <Copy size={18} />}
          {copied ? 'Código copiado' : 'Copiar código PIX'}
        </button>

        {error ? (
          <p className={styles.hint} role="alert">
            {error}
          </p>
        ) : null}

        <div className={styles.waiting}>
          <Loader2 size={18} className={styles.spinner} />
          aguardando pagamento...
        </div>

        <p className={`${styles.countdown} ${msRemaining < 60_000 ? styles.expiring : ''}`}>
          expira em {formatCountdown(msRemaining)}
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Add the route**

In `ui/src/App.tsx`, add the import and the route above the `path="*"` fallback:

```tsx
import { PixPayment } from './pages/PixPayment';
```

```tsx
        <Route path="/pagamento/:orderId" element={<PixPayment />} />
```

- [ ] **Step 4: Verify**

```bash
cd ui && npm run build && npm run lint
```
Expected: build passes; lint reports only the two known `AdminLayout.tsx` errors.

Then, in the browser: add a product on `/cardapio`, press "Pagar com PIX", and confirm the payment screen shows a scannable QR image, the total in monospace, a working copy button, and a countdown ticking down from `10:00`. Reload the page and confirm the QR is still there — that is `createCharge` recovering after a cold load.

Wait roughly ten seconds without touching anything. The fake provider approves the charge, the poll picks it up, and the app navigates to `/confirmacao/:orderId`, which is a blank route until Task 6. Seeing the URL change is the confirmation that polling works.

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/PixPayment.tsx ui/src/pages/PixPayment.module.css ui/src/App.tsx
git commit -m "feat(ui): add PIX payment screen with status polling"
```

---

## Task 6: Confirmation screen

**Files:**
- Rewrite: `ui/src/pages/OrderConfirmation.tsx`
- Create: `ui/src/pages/OrderConfirmation.module.css`
- Modify: `ui/src/App.tsx`

**Interfaces:**
- Consumes: `getOrderStatus`; `readOrderSnapshot`; `useCart().clear`; `Money`, `StateView`, `formatDateTime`, `messageFor`.
- Produces: the `/confirmacao/:orderId` route.

The receipt's line items come from the localStorage snapshot Task 4 wrote. No public endpoint returns order items, and adding one to render a receipt is not worth widening the public API surface — so a missing snapshot degrades to confirming the payment without the itemized list.

- [ ] **Step 1: Write the stylesheet**

`ui/src/pages/OrderConfirmation.module.css`:

```css
.page {
  max-width: var(--width-content);
  margin: 0 auto;
  padding: var(--space-5) var(--space-4);
}

.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  text-align: center;
}

.badge {
  display: inline-grid;
  place-items: center;
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 50%;
  background: var(--color-accent-weak);
  color: var(--color-positive);
  margin-bottom: var(--space-3);
}

.title {
  font-size: var(--text-xl);
  font-weight: 600;
}

.thanks {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  margin-top: var(--space-2);
}

.paidAt {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  margin-top: var(--space-2);
}

.receipt {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
  text-align: left;
}

.receiptTitle {
  font-size: var(--text-sm);
  font-weight: 500;
  margin-bottom: var(--space-3);
}

.line {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  padding: var(--space-1) 0;
}

.total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border);
  font-weight: 600;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

.primary {
  display: block;
  border: none;
  border-radius: var(--radius-md);
  background: var(--color-accent);
  color: #fff;
  font-weight: 500;
  padding: var(--space-3) var(--space-5);
  text-align: center;
  text-decoration: none;
}

.primary:hover {
  background: var(--color-accent-hover);
}

.secondary {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  text-decoration: none;
}

.secondary:hover {
  color: var(--color-accent);
}
```

- [ ] **Step 2: Write the page**

`ui/src/pages/OrderConfirmation.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { getOrderStatus } from '../api/public';
import { Money } from '../components/Money';
import { StateView } from '../components/StateView';
import { readOrderSnapshot } from '../cart/storage';
import { useCart } from '../cart/useCart';
import { formatDateTime } from '../lib/format';
import { messageFor } from '../lib/errors';
import type { CartItem, OrderStatusResponse } from '../types';
import styles from './OrderConfirmation.module.css';

export function OrderConfirmation() {
  const { orderId = '' } = useParams();
  const { clear } = useCart();

  const [status, setStatus] = useState<OrderStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [items] = useState<CartItem[] | null>(() => readOrderSnapshot(orderId));

  useEffect(() => {
    getOrderStatus(orderId)
      .then((result) => {
        setStatus(result);
        if (result.status === 'PAID') {
          clear();
        }
      })
      .catch((cause: unknown) => setError(messageFor(cause)));
  }, [orderId, clear]);

  if (error) {
    return <StateView kind="error" message={error} />;
  }

  if (!status) {
    return <StateView kind="loading" message="confirmando pagamento..." />;
  }

  if (status.status !== 'PAID') {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <h1 className={styles.title}>Pagamento não confirmado</h1>
          <p className={styles.thanks}>Este pedido ainda não consta como pago.</p>
          <div className={styles.actions}>
            <Link to="/cardapio" className={styles.primary}>
              Voltar ao cardápio
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const total = items?.reduce((sum, item) => sum + item.price * item.quantity, 0) ?? null;

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.badge}>
          <CheckCircle2 size={32} />
        </div>
        <h1 className={styles.title}>Pagamento confirmado</h1>
        <p className={styles.thanks}>
          Obrigado! Todo o valor arrecadado vai para a manutenção e melhoria da sala de estudos.
        </p>
        {status.paidAt ? <p className={styles.paidAt}>{formatDateTime(status.paidAt)}</p> : null}

        {items && items.length > 0 ? (
          <div className={styles.receipt}>
            <h2 className={styles.receiptTitle}>Resumo do pedido</h2>
            <ul>
              {items.map((item) => (
                <li key={item.id} className={styles.line}>
                  <span>
                    {item.quantity}x {item.name}
                  </span>
                  <Money value={item.price * item.quantity} size="sm" tone="muted" />
                </li>
              ))}
            </ul>
            {total !== null ? (
              <div className={styles.total}>
                <span>Total</span>
                <Money value={total} />
              </div>
            ) : null}
          </div>
        ) : null}

        <div className={styles.actions}>
          <Link to="/cardapio" className={styles.primary}>
            Fazer novo pedido
          </Link>
          <Link to="/" className={styles.secondary}>
            Ver para onde o dinheiro vai
          </Link>
        </div>
      </div>
    </div>
  );
}
```

The itemized block is skipped entirely when the snapshot is missing, and the page still confirms the payment. That is the designed degradation, not a bug to fix later.

- [ ] **Step 3: Add the route**

In `ui/src/App.tsx`, add the import and the route above the `path="*"` fallback:

```tsx
import { OrderConfirmation } from './pages/OrderConfirmation';
```

```tsx
        <Route path="/confirmacao/:orderId" element={<OrderConfirmation />} />
```

- [ ] **Step 4: Verify**

```bash
cd ui && npm run build && npm run lint
```
Expected: build passes; lint reports only the two known `AdminLayout.tsx` errors.

Then run the whole flow in the browser: `/cardapio`, add two different products, pay, wait about ten seconds. Confirm the receipt lists both line items with the right quantities and total, that the cart bar is gone afterward (the cart was cleared), and that `/cardapio` is empty on return.

Then open the same `/confirmacao/:orderId` URL in a private window. Confirm it still says the payment is confirmed but omits the itemized list — that is the missing-snapshot path.

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/OrderConfirmation.tsx ui/src/pages/OrderConfirmation.module.css ui/src/App.tsx
git commit -m "feat(ui): add order confirmation screen"
```

---

## Task 7: Transparency dashboard

**Files:**
- Rewrite: `ui/src/pages/PublicDashboard.tsx`
- Create: `ui/src/pages/PublicDashboard.module.css`

**Interfaces:**
- Consumes: `getDashboard` from `src/api/public.ts`; `Money`, `StateView`, `formatDateTime`, `messageFor`; Recharts.
- Produces: nothing new. This is the last screen; the route already exists from Task 1.

Three things the current file gets wrong and this rewrite fixes: it calls `fetch` directly instead of `getDashboard`, it renders `topProduct` and `crowdfundingUrl` without handling `null`, and it has no loading, empty or error states.

- [ ] **Step 1: Write the stylesheet**

`ui/src/pages/PublicDashboard.module.css`:

```css
.page {
  max-width: var(--width-wide);
  margin: 0 auto;
  padding: var(--space-5) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.title {
  font-size: var(--text-xl);
  font-weight: 600;
}

.subtitle {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
}

.goalHead {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--space-4);
  margin-bottom: var(--space-3);
}

.goalLabel {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.track {
  height: 0.5rem;
  border-radius: var(--radius-sm);
  background: var(--color-surface-sunken);
  overflow: hidden;
}

.fill {
  height: 100%;
  background: var(--color-accent);
}

.goalFoot {
  margin-top: var(--space-3);
  font-size: var(--text-sm);
}

.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
  gap: var(--space-3);
}

.kpi {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.kpiLabel {
  font-size: var(--text-xs);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
  margin-bottom: var(--space-1);
}

.kpiValue {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: var(--text-lg);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-5);
}

@media (min-width: 60rem) {
  .grid {
    grid-template-columns: 1fr 1fr;
  }
}

.panelTitle {
  font-size: var(--text-base);
  font-weight: 600;
  margin-bottom: var(--space-4);
}

.chart {
  height: 16rem;
}

.transaction {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--space-4);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-border);
}

.transaction:last-child {
  border-bottom: none;
}

.transactionItems {
  font-size: var(--text-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transactionMeta {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.more {
  display: block;
  width: 100%;
  margin-top: var(--space-4);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.more:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
```

- [ ] **Step 2: Write the page**

`ui/src/pages/PublicDashboard.tsx`:

```tsx
import { useCallback, useEffect, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getDashboard } from '../api/public';
import { Money } from '../components/Money';
import { StateView } from '../components/StateView';
import { formatCurrency, formatDateTime } from '../lib/format';
import { messageFor } from '../lib/errors';
import type { DashboardResponse, Transaction } from '../types';
import styles from './PublicDashboard.module.css';

const PAGE_SIZE = 20;

export function PublicDashboard() {
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback((targetPage: number) => {
    setError(null);
    getDashboard(targetPage, PAGE_SIZE)
      .then((result) => {
        setData(result);
        setTransactions((current) =>
          targetPage === 0
            ? result.transactions.content
            : [...current, ...result.transactions.content],
        );
      })
      .catch((cause: unknown) => setError(messageFor(cause)));
  }, []);

  useEffect(() => load(page), [load, page]);

  if (error) {
    return <StateView kind="error" message={error} onRetry={() => load(page)} />;
  }

  if (!data) {
    return <StateView kind="loading" message="carregando dados..." />;
  }

  const { kpis, goal, chartData } = data;
  // The balance can be a real deficit; only the bar is clamped, never the figure.
  const progress = Math.min(100, Math.max(0, (goal.current / goal.target) * 100));
  const hasMore = page < data.transactions.totalPages - 1;

  return (
    <div className={styles.page}>
      <div>
        <h1 className={styles.title}>Portal de transparência</h1>
        <p className={styles.subtitle}>
          Cada centavo arrecadado e cada despesa da sala de estudos, em tempo real.
        </p>
      </div>

      <section className={styles.panel}>
        <div className={styles.goalHead}>
          <div>
            <p className={styles.goalLabel}>Caixa atual</p>
            <Money value={goal.current} tone="auto" size="lg" />
          </div>
          <div>
            <p className={styles.goalLabel}>Meta</p>
            <Money value={goal.target} tone="muted" />
          </div>
        </div>

        <div
          className={styles.track}
          role="progressbar"
          aria-valuenow={Math.round(progress)}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <div className={styles.fill} style={{ width: `${progress}%` }} />
        </div>

        {goal.crowdfundingUrl ? (
          <p className={styles.goalFoot}>
            <a href={goal.crowdfundingUrl} target="_blank" rel="noopener noreferrer">
              Contribuir pela vaquinha
            </a>
          </p>
        ) : null}
      </section>

      <section className={styles.kpis}>
        <div className={styles.kpi}>
          <p className={styles.kpiLabel}>Arrecadado</p>
          <p className={styles.kpiValue}>{formatCurrency(kpis.totalRaised)}</p>
        </div>
        <div className={styles.kpi}>
          <p className={styles.kpiLabel}>Despesas</p>
          <p className={styles.kpiValue}>{formatCurrency(kpis.totalExpenses)}</p>
        </div>
        <div className={styles.kpi}>
          <p className={styles.kpiLabel}>Pedidos pagos</p>
          <p className={styles.kpiValue}>{kpis.totalOrders}</p>
        </div>
        <div className={styles.kpi}>
          <p className={styles.kpiLabel}>Ticket médio</p>
          <p className={styles.kpiValue}>{formatCurrency(kpis.averageTicket)}</p>
        </div>
        <div className={styles.kpi}>
          <p className={styles.kpiLabel}>Mais vendido</p>
          {/* Null until something sells — an em dash, never an empty box. */}
          <p className={styles.kpiValue} title={kpis.topProduct ?? undefined}>
            {kpis.topProduct ?? '—'}
          </p>
        </div>
      </section>

      <div className={styles.grid}>
        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>Últimos 7 dias</h2>
          <div className={styles.chart}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e7e5e4" />
                <XAxis
                  dataKey="date"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fontSize: 12, fill: '#57534e' }}
                  dy={8}
                />
                <YAxis
                  axisLine={false}
                  tickLine={false}
                  tick={{ fontSize: 12, fill: '#57534e' }}
                  tickFormatter={(value) => `R$${value}`}
                />
                <Tooltip
                  cursor={{ fill: '#f5f5f4' }}
                  contentStyle={{ borderRadius: '8px', border: '1px solid #e7e5e4' }}
                  formatter={(value) => [formatCurrency(Number(value)), 'Arrecadado']}
                />
                <Bar dataKey="amount" fill="#7c3aed" radius={[4, 4, 0, 0]} maxBarSize={40} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>

        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>Transações</h2>
          {transactions.length === 0 ? (
            <StateView kind="empty" message="Nenhuma venda registrada ainda." />
          ) : (
            <>
              <ul>
                {transactions.map((transaction) => (
                  <li key={transaction.id} className={styles.transaction}>
                    <div>
                      <p className={styles.transactionItems}>{transaction.productNames}</p>
                      <p className={styles.transactionMeta}>
                        #{transaction.id} · {formatDateTime(transaction.timestamp)}
                      </p>
                    </div>
                    <Money value={transaction.amount} size="sm" />
                  </li>
                ))}
              </ul>
              {hasMore ? (
                <button type="button" className={styles.more} onClick={() => setPage(page + 1)}>
                  Carregar mais
                </button>
              ) : null}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
```

The three Recharts colors are literal hex values because Recharts sets them as SVG attributes rather than CSS properties and cannot read a custom property. They are copies of `--color-border`, `--color-text-muted`, `--color-surface-sunken` and `--color-accent`; if a token changes, change these too.

- [ ] **Step 3: Verify**

```bash
cd ui && npm run build && npm run lint
```
Expected: build passes; lint reports only the two known `AdminLayout.tsx` errors.

In the browser at `/`: confirm the KPI row, the purple progress bar, a seven-column chart (zero-filled days included), and the sale from Task 6 in the transaction list with a `#<sequence>` id rather than a UUID. Confirm "Mais vendido" shows a product name now that something has sold.

To check the negative-balance path, add an expense larger than the revenue and reload:

```bash
curl -s -X POST localhost:8080/api/admin/expenses \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"description":"Estoque inicial","amount":500.00}'
```

Confirm the caixa figure goes red and negative, and the progress bar sits empty rather than inverting or overflowing.

- [ ] **Step 4: Commit**

```bash
git add ui/src/pages/PublicDashboard.tsx ui/src/pages/PublicDashboard.module.css
git commit -m "feat(ui): rebuild transparency dashboard in plain CSS"
```

---

## Task 8: End-to-end smoke check and README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: every screen from Tasks 1 through 7.
- Produces: nothing in code. This task proves the customer path works and fixes the setup docs.

- [ ] **Step 1: Reset to a clean state**

```bash
podman compose down -v && podman compose up -d
cd api && APP_BOOTSTRAP_ADMIN_EMAIL=admin@localhost.facom \
  APP_BOOTSTRAP_ADMIN_PASSWORD=troque-esta-senha \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

In another terminal, seed a catalog and a goal:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@localhost.facom","password":"troque-esta-senha"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

curl -s -X POST localhost:8080/api/admin/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Café","price":3.00}'

curl -s -X POST localhost:8080/api/admin/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Bolo de cenoura","price":5.50}'

curl -s -X PUT localhost:8080/api/admin/settings \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"goalTarget":2000.00,"crowdfundingUrl":"https://vakinha.example/sala"}'
```

- [ ] **Step 2: Walk the full customer path in the browser**

Using the `playwright-firefox` skill, in one session:

1. Open `/cardapio`. Both products listed.
2. Add 2× Café and 1× Bolo. Cart bar reads `3 itens` and `R$ 11,50`.
3. Reload the page. The cart still reads `3 itens` — localStorage persistence.
4. Press "Pagar com PIX". Lands on `/pagamento/:orderId` with a QR image and a countdown.
5. Reload that page. QR still renders — `createCharge` idempotency.
6. Wait ~10s without interacting. The app moves itself to `/confirmacao/:orderId`.
7. Receipt lists `2x Café` and `1x Bolo de cenoura`, total `R$ 11,50`.
8. Open `/`. The sale appears in Transações with a `#` sequence id; "Arrecadado" reads `R$ 11,50`.
9. Open `/cardapio` again. Cart is empty.

Every one of the nine must pass. If any step fails, fix it before continuing — this is the gate for the whole plan.

- [ ] **Step 3: Confirm the production build is clean**

```bash
cd ui && npm run build && npm run lint
```
Expected: build passes; lint reports only the two known `AdminLayout.tsx` errors and nothing else.

- [ ] **Step 4: Fix the README**

`README.md` currently documents directories that do not exist (`backend/`, `frontend/`) and a config file that was never created (`application-local.yml.example`). Replace the "Rodando o projeto localmente" and "Estrutura do repositório" sections with:

````markdown
## Rodando o projeto localmente

### Pré-requisitos

- Java 25+
- Node.js 24+
- Podman ou Docker (Postgres e MinIO sobem via compose)

### Infraestrutura

```bash
podman compose up -d
```

### Backend

```bash
cd api
APP_BOOTSTRAP_ADMIN_EMAIL=admin@localhost.facom \
APP_BOOTSTRAP_ADMIN_PASSWORD=troque-esta-senha \
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

O perfil `dev` usa o provedor de pagamento `fake`, que confirma qualquer cobrança
após 10 segundos — dá para rodar o fluxo inteiro sem credenciais do Mercado Pago.

### Frontend

```bash
cd ui
npm install
npm run dev
```

O Vite faz proxy de `/api` para `localhost:8080`, então não é preciso configurar
`VITE_API_URL` em desenvolvimento.

### Rotas

| Rota | Tela |
|---|---|
| `/` | Portal de transparência |
| `/cardapio` | Cardápio e carrinho |
| `/pagamento/:orderId` | Pagamento PIX |
| `/confirmacao/:orderId` | Confirmação do pedido |

> O QR Code fixo da sala precisa apontar para `/cardapio`, e não para a raiz —
> a raiz é o portal de transparência.

## Estrutura do repositório

```
.
├── api/     # API Spring Boot
├── ui/      # Aplicação React
├── docs/    # Especificações e planos de implementação
└── README.md
```
````

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: fix local setup instructions and document public routes"
```

---

## Self-review

**Spec coverage.** Every section of the spec maps to a task: tokens and visual direction to Task 1; `format.ts`, `errors.ts`, `Money`, `StateView` to Task 2; cart context and `localStorage` to Task 3; the four screens to Tasks 4 through 7; the end-to-end path and the QR-code note to Task 8. The error-handling table's five rows are implemented across Tasks 4, 5 and 7. The two dashboard correctness details — negative balance and null `topProduct`/`crowdfundingUrl` — are in Task 7 with an explicit verification step for the negative case.

**Additions beyond the spec, deliberate.** `AppHeader` is not in the spec's file listing, but the spec says the joke lives in the header, so it needs a home. `strict: true` is not in the spec either; it is added in Task 1 because the spec's null handling is unenforceable without it, and it was verified to produce zero errors today.

**Naming consistency.** `setQuantity(product, quantity)` is defined in Task 3 and used with that signature in Task 4. `saveOrderSnapshot`/`readOrderSnapshot` are defined in Task 3 and used in Tasks 4 and 6. `messageFor` is defined in Task 2 and used in Tasks 4 through 7. `StateView` takes `kind`/`message`/`onRetry` everywhere. The router state key is `charge` in both Task 4 and Task 5.
