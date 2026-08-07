import { useEffect, useState } from 'react';
import { cancelOrder, listOrders, markOrderPaid, syncOrder } from '../../api/admin';
import { Money } from '../../components/Money';
import { StateView } from '../../components/StateView';
import { messageFor } from '../../lib/errors';
import { formatDateTime } from '../../lib/format';
import type { AdminOrder, OrderStatus } from '../../types';
import admin from './admin.module.css';
import styles from './AdminOrders.module.css';

const PAGE_SIZE = 20;

const FILTERS: { value: OrderStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'PENDING', label: 'Aguardando' },
  { value: 'PAID', label: 'Pagos' },
  { value: 'EXPIRED', label: 'Expirados' },
  { value: 'CANCELED', label: 'Cancelados' },
];

const STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'aguardando',
  PAID: 'pago',
  EXPIRED: 'expirado',
  CANCELED: 'cancelado',
};

function describe(order: AdminOrder): string {
  return order.items.map((item) => `${item.quantity}x ${item.productName}`).join(', ');
}

export function AdminOrders() {
  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [orders, setOrders] = useState<AdminOrder[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    listOrders(filter === 'ALL' ? undefined : filter, page, PAGE_SIZE)
      .then((result) => {
        setTotalPages(result.totalPages);
        setOrders((current) =>
          page === 0 || current === null ? result.content : [...current, ...result.content],
        );
        setLoadError(null);
      })
      .catch((error: unknown) => setLoadError(messageFor(error)));
  }, [filter, page, reloadToken]);

  const changeFilter = (value: OrderStatus | 'ALL') => {
    setOrders(null);
    setPage(0);
    setFilter(value);
  };

  const retry = () => {
    setLoadError(null);
    setOrders(null);
    setPage(0);
    setReloadToken((token) => token + 1);
  };

  const act = async (order: AdminOrder, action: (id: string) => Promise<AdminOrder>) => {
    setBusyId(order.id);
    setActionError(null);
    try {
      const updated = await action(order.id);
      setOrders((current) =>
        (current ?? []).map((candidate) => (candidate.id === order.id ? updated : candidate)),
      );
    } catch (error: unknown) {
      setActionError(messageFor(error));
    } finally {
      setBusyId(null);
    }
  };

  const confirm = (order: AdminOrder) => {
    if (window.confirm(`Confirmar o pagamento do pedido #${order.seq} à mão?`)) {
      void act(order, markOrderPaid);
    }
  };

  const hasMore = page < totalPages - 1;

  return (
    <div className={admin.page}>
      <div className={admin.head}>
        <div>
          <h1 className={admin.title}>Pedidos</h1>
          <p className={admin.subtitle}>
            Um pedido pago nunca volta atrás. Cancelar ou expirar só significa parar de
            esperar — um PIX que chegar depois ainda é creditado.
          </p>
        </div>
      </div>

      <div className={styles.filters}>
        {FILTERS.map((option) => (
          <button
            key={option.value}
            type="button"
            className={
              option.value === filter
                ? `${styles.filter} ${styles.filterActive}`
                : styles.filter
            }
            onClick={() => changeFilter(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>

      {actionError ? (
        <p className={admin.error} role="alert">
          {actionError}
        </p>
      ) : null}

      <section className={admin.panel}>
        {loadError ? (
          <StateView kind="error" message={loadError} onRetry={retry} />
        ) : !orders ? (
          <StateView kind="loading" message="carregando pedidos..." />
        ) : orders.length === 0 ? (
          <StateView kind="empty" message="Nenhum pedido nesse filtro." />
        ) : (
          <>
            <ul className={admin.list}>
              {orders.map((order) => (
                <li key={order.id} className={styles.order}>
                  <span className={styles.seq}>#{order.seq}</span>
                  <div className={styles.info}>
                    <p className={styles.items}>{describe(order)}</p>
                    <p className={styles.meta}>
                      {formatDateTime(order.createdAt)}
                      {order.paidManuallyBy ? ' · confirmado à mão' : ''}
                    </p>
                  </div>
                  <span
                    className={
                      order.status === 'PAID'
                        ? `${admin.badge} ${admin.badgePaid}`
                        : order.status === 'PENDING'
                          ? `${admin.badge} ${admin.badgePending}`
                          : admin.badge
                    }
                  >
                    {STATUS_LABELS[order.status]}
                  </span>
                  <Money value={order.total} size="sm" />
                  {order.status === 'PENDING' ? (
                    <div className={admin.actions}>
                      <button
                        type="button"
                        className={`${admin.button} ${admin.primary}`}
                        disabled={busyId === order.id}
                        onClick={() => confirm(order)}
                      >
                        Confirmar
                      </button>
                      {order.hasCharge ? (
                        <button
                          type="button"
                          className={admin.button}
                          disabled={busyId === order.id}
                          onClick={() => void act(order, syncOrder)}
                        >
                          Sincronizar
                        </button>
                      ) : null}
                      <button
                        type="button"
                        className={`${admin.button} ${admin.danger}`}
                        disabled={busyId === order.id}
                        onClick={() => void act(order, cancelOrder)}
                      >
                        Cancelar
                      </button>
                    </div>
                  ) : null}
                </li>
              ))}
            </ul>
            {hasMore ? (
              <button
                type="button"
                className={styles.more}
                onClick={() => setPage(page + 1)}
              >
                Carregar mais
              </button>
            ) : null}
          </>
        )}
      </section>
    </div>
  );
}
