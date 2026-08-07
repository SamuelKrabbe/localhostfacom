import { useEffect, useState } from 'react';
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
import { useDarkMode } from '../lib/useDarkMode';
import type { DashboardResponse, Transaction } from '../types';
import styles from './PublicDashboard.module.css';

const PAGE_SIZE = 20;

// Recharts sets these as SVG attributes, not CSS properties, so it cannot read the
// custom properties in tokens.css — these are copies, light and dark, of the same
// --color-border / --color-text-muted / --color-surface / --color-accent values.
const CHART_COLORS = {
  light: { grid: '#e7e5e4', tick: '#57534e', cursor: '#f5f5f4', bar: '#7c3aed', tooltipBg: '#ffffff', tooltipText: '#1c1917' },
  dark: { grid: '#44403c', tick: '#a8a29e', cursor: '#1c1917', bar: '#a78bfa', tooltipBg: '#292524', tooltipText: '#fafaf9' },
};

export function PublicDashboard() {
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  // Bumped by retry() to re-run the fetch below without calling setState synchronously
  // inside the effect body itself — that pattern trips react-hooks/set-state-in-effect.
  const [reloadToken, setReloadToken] = useState(0);
  const chartColors = CHART_COLORS[useDarkMode() ? 'dark' : 'light'];

  useEffect(() => {
    getDashboard(page, PAGE_SIZE)
      .then((result) => {
        setData(result);
        setTransactions((current) =>
          page === 0
            ? result.transactions.content
            : [...current, ...result.transactions.content],
        );
        setError(null);
      })
      .catch((cause: unknown) => setError(messageFor(cause)));
  }, [page, reloadToken]);

  const retry = () => setReloadToken((token) => token + 1);

  if (error) {
    return <StateView kind="error" message={error} onRetry={retry} />;
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
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={chartColors.grid} />
                <XAxis
                  dataKey="date"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fontSize: 12, fill: chartColors.tick }}
                  dy={8}
                />
                <YAxis
                  axisLine={false}
                  tickLine={false}
                  tick={{ fontSize: 12, fill: chartColors.tick }}
                  tickFormatter={(value) => `R$${value}`}
                />
                <Tooltip
                  cursor={{ fill: chartColors.cursor }}
                  contentStyle={{
                    borderRadius: '8px',
                    border: `1px solid ${chartColors.grid}`,
                    background: chartColors.tooltipBg,
                    color: chartColors.tooltipText,
                  }}
                  labelStyle={{ color: chartColors.tooltipText }}
                  itemStyle={{ color: chartColors.tooltipText }}
                  formatter={(value) => [formatCurrency(Number(value)), 'Arrecadado']}
                />
                <Bar dataKey="amount" fill={chartColors.bar} radius={[4, 4, 0, 0]} maxBarSize={40} />
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
                    <div className={styles.transactionInfo}>
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
