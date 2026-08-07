import { use } from 'react';
import { AuthContext } from './AuthContext';
import type { AuthValue } from './AuthContext';

export function useAuth(): AuthValue {
  const value = use(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return value;
}
