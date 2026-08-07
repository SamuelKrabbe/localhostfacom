import { formatCurrency } from '../lib/format';
import styles from './Money.module.css';

interface MoneyProps {
  value: number;
  /** 'auto' colors by sign — used for the net balance, which can be a real deficit. */
  tone?: 'default' | 'auto' | 'muted';
  size?: 'sm' | 'md' | 'lg';
}

export function Money({ value, tone = 'default', size = 'md' }: MoneyProps) {
  const toneClass =
    tone === 'muted'
      ? styles.muted
      : tone === 'auto'
        ? value < 0
          ? styles.negative
          : styles.positive
        : undefined;

  return (
    <span className={[styles.money, styles[size], toneClass].filter(Boolean).join(' ')}>
      {formatCurrency(value)}
    </span>
  );
}
