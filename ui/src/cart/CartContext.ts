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
