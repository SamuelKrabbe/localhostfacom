# Public frontend — design

Date: 2026-08-07
Scope: the customer-facing side of `ui/` — design tokens, shared primitives, catalog and
cart, PIX payment, order confirmation, and the public transparency dashboard.
Out of scope this pass: admin login, the auth guard, and the four admin screens. Those
are a second spec and a second plan, written after this one ships.

## Context

The API is complete through Task 18 of
`docs/superpowers/plans/2026-08-06-backend-and-api-client.md`: 97 tests passing, and
`ui/src/api/` holds a typed client for every endpoint the screens need.

`ui/` currently contains four screens — `PublicDashboard`, `PixPayment`,
`OrderConfirmation`, `AdminLayout` — plus `ProductCard` and `CartBar`. They share three
problems:

1. **They are written entirely in Tailwind utility classes, and Tailwind is not
   installed.** No dependency, no config, no import; `src/index.css` is unrelated
   boilerplate. Every screen renders unstyled today.
2. **They are prop-driven mockups.** `PixPayment` and `OrderConfirmation` receive
   `pixCode`, `items`, `totalValue` from a parent that does not exist. `AdminLayout`
   hardcodes an admin email.
3. **Where they do fetch, they bypass the client and use wrong URLs.** `PixPayment`
   polls `/api/orders/{id}/status`; the real route is `/api/public/orders/{id}/status`.
   `PublicDashboard` calls `fetch` directly instead of `getDashboard`.

`App.tsx` has every route except `/transparencia` commented out. There is no cart state,
no router wiring beyond that one route, and no catalog screen at all.

Given all four screens need their styling rewritten from scratch anyway, they are treated
as visual reference rather than as code to preserve.

## Decisions taken

| Decision | Choice | Reason |
|---|---|---|
| Styling | Plain CSS, no Tailwind | Chosen over installing Tailwind. No new dependency; the project already has zero styling deps. |
| CSS organization | CSS Modules + a token file | Vite supports `.module.css` natively with no config. Scoped names, styles colocated with their component, no growing global stylesheet. |
| Design tokens | CSS custom properties in `:root` | Standard, no build step, readable in devtools. |
| Visual direction | Clean light base, monospace numerals, purple accent | Leans into the `localhost:facom` port-number joke without making money figures hard to read. |
| Cart state | React context, mirrored to `localStorage` | The PIX flow requires leaving the tab for a bank app. Losing the cart on return is a real failure, not an edge case. |
| Order identity | Order id in the URL | Refresh- and share-safe without putting charge data in state. |
| Scope split | Public flow now, admin panel next | Nine screens with a full restyle is too large for one reviewable plan. |

### On the accent color

The accent is purple (`#7C3AED`). The university athletic association's primary color is
also purple; this project has no affiliation with them, and the choice is not meant to
imply one. It is simply a locally familiar color that reads as more distinctive than the
terminal-green cliché, and it matches the `--accent: #aa3bff` already sitting unused in
the boilerplate `index.css`.

## Visual direction

`localhost:facom` is a pun on a port number, made by computing students. The design
carries that identity in the typography rather than in a costume:

- **Monospace for every numeral** — prices, totals, KPI figures, the PIX code, dates,
  order sequence numbers. Prose stays in a system sans.
- **A block cursor after the wordmark**, `localhost:facom █`, static rather than blinking
  (a blinking element next to a payment total is a distraction, and it violates
  `prefers-reduced-motion` for no benefit).
- **Restrained everywhere else.** Money screens should look trustworthy. The joke lives
  in the header, the type, and the empty states.

Tokens:

```
--color-bg          #FAFAF9   page background, warm off-white
--color-surface     #FFFFFF   cards, rows
--color-border      #E7E5E4
--color-text        #1C1917
--color-text-muted  #57534E
--color-accent      #7C3AED
--color-accent-weak #F5F3FF
--color-positive    #15803D   paid, revenue
--color-negative    #B91C1C   expired, deficit
--space-1 … --space-8         4px scale
--radius-sm/md/lg   4/8/12px
--font-sans         system-ui stack
--font-mono         ui-monospace, SFMono-Regular, Menlo, monospace
```

Every screen is designed phone-first: customers arrive by scanning a QR code taped to the
room, so a phone is the primary device and the desktop layout is the adaptation.

## Architecture

```
src/
  styles/
    tokens.css              :root custom properties
    global.css              reset, base type, focus rings
  lib/
    format.ts               formatCurrency, formatDate, formatDateTime
    errors.ts               ApiError slug -> Portuguese message
  cart/
    CartContext.tsx         provider + useCart hook
    storage.ts              localStorage read/write, order snapshots
  components/
    ProductCard.tsx         + .module.css
    CartBar.tsx             + .module.css
    Money.tsx               monospace currency figure
    StateView.tsx           loading / empty / error, one component
  pages/
    Catalog.tsx
    PixPayment.tsx
    OrderConfirmation.tsx
    PublicDashboard.tsx
  api/                      unchanged, already built
  App.tsx                   routes
```

Each unit has one job and a stated dependency direction: `pages` compose `components`,
`components` read from `cart` and `lib`, nothing imports upward, and only `pages` call
`api`.

## Screens

### `/` — Catalog

Lists active products from `listProducts()`. Each row is a `ProductCard` with a quantity
stepper. A sticky `CartBar` shows item count and total, and is the only way to check out.

Checkout calls `createOrder(items)` with product ids and quantities only — never prices,
which the API recomputes from its own catalog — then navigates to
`/pagamento/:orderId`.

States: loading, empty catalog (`nenhum produto disponível no momento`), and load failure
with a retry.

### `/pagamento/:orderId` — PIX payment

Shows the QR image, a copy-to-clipboard PIX code, the total, and a countdown to
`expiresAt`.

Charge data arrives from the `createOrder` response via router state on the normal path.
On a cold load — refresh, or reopening the tab after the bank app — it calls
`createCharge(orderId)`, which is idempotent server-side and returns the original charge
rather than creating a second payable one.

Payment confirmation polls `getOrderStatus(orderId, signal)` every 3 seconds with an
`AbortController`, stopping on `PAID` (navigate to confirmation) or `EXPIRED` (show a
terminal state with a link back to the catalog). The countdown is display-only: expiry is
whatever the API says it is, never a client-side `setTimeout`. The existing mockup's
10-minute timer can fire while a payment is genuinely in flight and is dropped.

### `/confirmacao/:orderId` — Confirmation

Confirms payment and shows the itemized receipt, then clears the cart.

The item breakdown comes from a snapshot written to `localStorage` at order creation,
keyed by order id. No public endpoint returns order items — `getOrderStatus` returns
status and `paidAt` only — and adding one just to render a receipt is not worth widening
the public API surface. If the snapshot is missing (different device, cleared storage),
the page still confirms the payment and simply omits the itemized list. Snapshots are
pruned on read once older than 24 hours.

### `/transparencia` — Public dashboard

Rebuilds the existing dashboard against `getDashboard(page, size)`: KPI tiles, a
seven-day revenue bar chart, the funding goal with its progress bar, and a paginated
transaction feed.

Two correctness details the current mockup gets wrong:

- `netBalance` and `goal.current` can legitimately be negative when stock was bought
  before it sold. The figure renders as-is, in the negative color; only the progress bar
  is clamped to `0…100%`.
- `topProduct` is `null` until something sells, and `crowdfundingUrl` may be `null`. Both
  render a neutral placeholder rather than an empty link.

## Error handling

`ApiError` carries `status`, `slug`, `detail` and optional `fieldErrors`. `lib/errors.ts`
maps known slugs to Portuguese user-facing messages, falling back to a generic message
for anything unmapped — an unrecognized slug must never render a raw English backend
string to a customer.

Every screen renders one of four states explicitly: loading, empty, error with retry, or
content. `StateView` exists so this is uniform rather than reinvented per screen. The
current mockups have none of these states.

Failures that matter and how they surface:

| Failure | Behavior |
|---|---|
| Catalog fetch fails | Error state, retry button |
| `createOrder` fails validation | Message near the cart bar, cart preserved |
| Charge fetch fails on payment page | Error state with retry; order is not abandoned |
| Status poll fails transiently | Silent, keeps polling — a dropped poll is not a payment failure |
| Order expires | Terminal state, link back to catalog, cart preserved so the customer can retry |

## Testing

`ui/` has no test runner, and adding one is out of scope for this pass — the same call
the API-client task made.

Verification per task:

1. `npm run build` (`tsc -b` type-checks every file) and `npm run lint` must pass.
   `src/pages/admin/AdminLayout.tsx` has two pre-existing
   `react-hooks/static-components` lint errors that are untouched by this pass and
   expected to remain.
2. The affected screen driven in a real browser against a running API, using the
   `playwright-firefox` skill — this machine has Firefox only.

The full customer path is exercised end to end at least once: catalog, add items, create
order, PIX screen, mark the order paid out of band, confirm the poll advances to the
confirmation screen, and confirm the sale appears on `/transparencia`. The `fake` payment
provider makes this runnable with no Mercado Pago credentials.

## Risks

**The `fake` provider's QR image is not a real PIX code.** The payment screen can only be
verified visually and structurally in development. The Mercado Pago path stays untested
end to end until real credentials exist, which per the API spec is owned by other people
on the team.

**localStorage is not available in every context** (private browsing in some browsers,
storage disabled). Cart and snapshot access is wrapped so a storage failure degrades to
in-memory state for the session instead of throwing.

**Restyling from scratch means the screens will look different from the mockups.** That is
intended — they are unstyled today — but it is a visible change rather than a refinement.
