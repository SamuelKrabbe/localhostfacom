import { useState } from 'react';
import type { FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { messageFor } from '../../lib/errors';
import { wordmarkSegments } from '../../lib/wordmark';
import admin from './admin.module.css';
import styles from './AdminLogin.module.css';

export function AdminLogin() {
  const auth = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (auth.admin) {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from ?? '/admin/pedidos'} replace />;
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      await auth.signIn(email, password);
    } catch (cause: unknown) {
      setError(messageFor(cause));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className={styles.screen}>
      <form className={styles.card} onSubmit={submit}>
        <div>
          <p className={styles.wordmark}>
            {wordmarkSegments('/admin').map((segment) => (
              <span
                key={segment.kind}
                className={[
                  segment.kind === 'port' || segment.kind === 'path'
                    ? styles.accent
                    : styles.muted,
                  segment.kind === 'prompt' ? styles.promptLine : '',
                ].join(' ')}
              >
                {segment.text}
              </span>
            ))}
          </p>
          <p className={styles.hint}>Acesso restrito à administração da sala.</p>
        </div>

        <div className={admin.field}>
          <label className={admin.label} htmlFor="email">
            E-mail
          </label>
          <input
            id="email"
            className={admin.input}
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>

        <div className={admin.field}>
          <label className={admin.label} htmlFor="password">
            Senha
          </label>
          <input
            id="password"
            className={admin.input}
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>

        {error ? (
          <p className={admin.error} role="alert">
            {error}
          </p>
        ) : null}

        <button
          type="submit"
          className={`${admin.button} ${admin.primary}`}
          disabled={isSubmitting}
        >
          {isSubmitting ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
    </div>
  );
}
