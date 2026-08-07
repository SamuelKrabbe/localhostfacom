import { createContext } from 'react';
import type { AdminUser } from '../types';

export interface AuthValue {
  admin: AdminUser | null;
  /** True until the token found in storage has been checked against the API. */
  isResolving: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => void;
}

export const AuthContext = createContext<AuthValue | null>(null);
