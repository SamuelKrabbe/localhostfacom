import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { createAdmin, deleteAdmin, listAdmins } from '../../api/admin';
import { useAuth } from '../../auth/useAuth';
import { StateView } from '../../components/StateView';
import { messageFor } from '../../lib/errors';
import { formatDateTime } from '../../lib/format';
import type { AdminUser } from '../../types';
import adminStyles from './admin.module.css';

export function AdminUsers() {
  const { admin: currentAdmin } = useAuth();
  const [admins, setAdmins] = useState<AdminUser[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    listAdmins()
      .then(setAdmins)
      .catch((error: unknown) => setLoadError(messageFor(error)));
  }, [reloadToken]);

  const reload = () => {
    setLoadError(null);
    setAdmins(null);
    setReloadToken((token) => token + 1);
  };

  const add = async (event: FormEvent) => {
    event.preventDefault();
    setIsSaving(true);
    setFormError(null);
    try {
      const created = await createAdmin(email, password);
      setAdmins((current) => [...(current ?? []), created]);
      setEmail('');
      setPassword('');
    } catch (error: unknown) {
      setFormError(messageFor(error));
    } finally {
      setIsSaving(false);
    }
  };

  const remove = async (target: AdminUser) => {
    if (!window.confirm(`Desativar o acesso de ${target.email}?`)) {
      return;
    }
    try {
      await deleteAdmin(target.id);
      reload();
    } catch (error: unknown) {
      setFormError(messageFor(error));
    }
  };

  return (
    <div className={adminStyles.page}>
      <div className={adminStyles.head}>
        <div>
          <h1 className={adminStyles.title}>Admins</h1>
          <p className={adminStyles.subtitle}>
            Quem removido é desativado, não apagado: as despesas registradas continuam
            apontando para quem as lançou.
          </p>
        </div>
      </div>

      <section className={adminStyles.panel}>
        <h2 className={adminStyles.panelTitle}>Novo admin</h2>
        <form className={adminStyles.form} onSubmit={add}>
          <div className={adminStyles.fields}>
            <div className={adminStyles.field}>
              <label className={adminStyles.label} htmlFor="new-email">
                E-mail
              </label>
              <input
                id="new-email"
                className={adminStyles.input}
                type="email"
                autoComplete="off"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>
            <div className={adminStyles.field}>
              <label className={adminStyles.label} htmlFor="new-password">
                Senha (mínimo 8 caracteres)
              </label>
              <input
                id="new-password"
                className={adminStyles.input}
                type="password"
                autoComplete="new-password"
                minLength={8}
                maxLength={72}
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>
          </div>

          {formError ? (
            <p className={adminStyles.error} role="alert">
              {formError}
            </p>
          ) : null}

          <div className={adminStyles.actions}>
            <button
              type="submit"
              className={`${adminStyles.button} ${adminStyles.primary}`}
              disabled={isSaving}
            >
              Criar admin
            </button>
          </div>
        </form>
      </section>

      <section className={adminStyles.panel}>
        <h2 className={adminStyles.panelTitle}>Acessos</h2>
        {loadError ? (
          <StateView kind="error" message={loadError} onRetry={reload} />
        ) : !admins ? (
          <StateView kind="loading" message="carregando admins..." />
        ) : (
          <ul className={adminStyles.list}>
            {admins.map((entry) => (
              <li key={entry.id} className={adminStyles.row}>
                <div className={adminStyles.rowInfo}>
                  <p className={adminStyles.rowTitle}>{entry.email}</p>
                  <p className={adminStyles.rowMeta}>desde {formatDateTime(entry.createdAt)}</p>
                </div>
                {entry.active ? null : <span className={adminStyles.badge}>inativo</span>}
                {entry.id === currentAdmin?.id ? (
                  <span className={adminStyles.badge}>você</span>
                ) : (
                  <button
                    type="button"
                    className={`${adminStyles.button} ${adminStyles.danger}`}
                    disabled={!entry.active}
                    onClick={() => remove(entry)}
                  >
                    Desativar
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
