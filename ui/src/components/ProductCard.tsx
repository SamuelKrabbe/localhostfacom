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
