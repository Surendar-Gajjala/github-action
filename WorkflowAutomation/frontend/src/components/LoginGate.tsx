import { useState, type FormEvent, type ReactNode } from 'react';
import { ApiError, clearCredentials, setCredentials } from '../api/client';
import { employeesApi } from '../api/employees';
import { Button } from './ui';

/**
 * Asks for HR or admin credentials before showing the app. Credentials stay in memory for this tab only;
 * reloading the page signs the user out.
 */
export function LoginGate({ children }: { children: (user: string, signOut: () => void) => ReactNode }) {
  const [user, setUser] = useState<string | null>(null);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setCredentials(username, password);
    try {
      await employeesApi.ping();
      setUser(username);
      setPassword('');
    } catch (e) {
      clearCredentials();
      setError(e instanceof ApiError && e.status === 401 ? 'Invalid username or password.' : 'Cannot reach the server.');
    } finally {
      setBusy(false);
    }
  }

  function signOut() {
    clearCredentials();
    setUser(null);
  }

  if (user) return <>{children(user, signOut)}</>;

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <form onSubmit={onSubmit} className="w-full max-w-sm space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <div>
          <h1 className="text-lg font-semibold text-slate-900">Employee Onboarding Automation</h1>
          <p className="text-sm text-slate-500">Sign in with your HR account.</p>
        </div>
        <label className="block text-sm font-medium text-slate-700">
          Username
          <input
            className="input mt-1"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Password
          <input
            className="input mt-1"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {error && (
          <p role="alert" className="text-sm text-red-700">
            {error}
          </p>
        )}
        <Button type="submit" className="w-full" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </div>
  );
}
