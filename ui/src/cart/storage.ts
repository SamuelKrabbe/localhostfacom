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
