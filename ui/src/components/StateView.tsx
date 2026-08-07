import styles from './StateView.module.css';

interface StateViewProps {
  kind: 'loading' | 'empty' | 'error';
  message: string;
  onRetry?: () => void;
}

export function StateView({ kind, message, onRetry }: StateViewProps) {
  return (
    <div className={styles.state} role={kind === 'error' ? 'alert' : 'status'}>
      <p className={kind === 'loading' ? styles.loading : styles.message}>{message}</p>
      {kind === 'error' && onRetry ? (
        <button type="button" className={styles.retry} onClick={onRetry}>
          Tentar de novo
        </button>
      ) : null}
    </div>
  );
}
