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
