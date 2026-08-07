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
