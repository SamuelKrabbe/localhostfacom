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
