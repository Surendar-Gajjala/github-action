import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { clearCredentials, hasCredentials, onUnauthorized, setCredentials } from '../api/client';
import { workflowApi } from '../api/endpoints';
import type { Me } from '../api/types';

interface AuthState {
  user: Me | null;
  initializing: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  hasRole: (...roles: string[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [initializing, setInitializing] = useState(hasCredentials());
  const queryClient = useQueryClient();

  const logout = useCallback(() => {
    clearCredentials();
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    onUnauthorized(logout);
    if (hasCredentials()) {
      workflowApi
        .me()
        .then(setUser)
        .catch(() => clearCredentials())
        .finally(() => setInitializing(false));
    }
  }, [logout]);

  const login = useCallback(
    async (username: string, password: string) => {
      setCredentials(username, password);
      try {
        const me = await workflowApi.me();
        queryClient.clear();
        setUser(me);
      } catch (e) {
        clearCredentials();
        throw e;
      }
    },
    [queryClient],
  );

  const value = useMemo<AuthState>(
    () => ({
      user,
      initializing,
      login,
      logout,
      hasRole: (...roles) => !!user && roles.some((r) => user.roles.includes(r)),
    }),
    [user, initializing, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
