import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { me } from '../api/admin';
import { login, logout } from '../api/auth';
import { tokenStorage } from '../api/client';
import type { AdminUser } from '../types';
import { AuthContext } from './AuthContext';
import type { AuthValue } from './AuthContext';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<AdminUser | null>(null);
  const [isResolving, setIsResolving] = useState(() => tokenStorage.get() !== null);

  useEffect(() => {
    if (!isResolving) {
      return;
    }
    let cancelled = false;
    me()
      .then((resolved) => {
        if (!cancelled) {
          setAdmin(resolved);
        }
      })
      // request() already drops a rejected token; there is nothing left to recover.
      .catch(() => tokenStorage.clear())
      .finally(() => {
        if (!cancelled) {
          setIsResolving(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [isResolving]);

  const signIn = useCallback(async (email: string, password: string) => {
    await login(email, password);
    setAdmin(await me());
  }, []);

  const signOut = useCallback(() => {
    logout();
    setAdmin(null);
  }, []);

  const value = useMemo<AuthValue>(
    () => ({ admin, isResolving, signIn, signOut }),
    [admin, isResolving, signIn, signOut],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}
