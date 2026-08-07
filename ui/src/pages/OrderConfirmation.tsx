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
      .then(setStatus)
      .catch((cause: unknown) => setError(messageFor(cause)));
  }, [orderId]);

  // Clearing the cart is a side effect of a value that already changed, not a fetch of
  // its own — kept out of the fetch effect above to keep each effect's job singular.
  useEffect(() => {
    if (status?.status === 'PAID') {
      clear();
    }
  }, [status, clear]);

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
