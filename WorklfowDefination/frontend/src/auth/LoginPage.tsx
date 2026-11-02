import { useState, type FormEvent } from 'react';
import { errorMessage } from '../api/client';
import { useAuth } from './AuthContext';

const DEMO_USERS = [
  { username: 'author', role: 'Author', hint: 'Creates and edits items/parts, submits changes' },
  { username: 'engineer', role: 'Engineering approver', hint: 'Completes ENGINEERING approval tasks' },
  { username: 'manager', role: 'Manager approver', hint: 'Completes MANAGER approval tasks' },
  { username: 'admin', role: 'Workflow admin + author', hint: 'Designs, publishes and activates workflows' },
];

export function LoginPage() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function signIn(u: string, p: string) {
    setBusy(true);
    setError(null);
    try {
      await login(u, p);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void signIn(username, password);
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-100 to-indigo-50 p-4">
      <div className="card w-full max-w-lg p-8">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-600 font-bold text-white">S</div>
          <div>
            <h1 className="text-lg font-semibold text-slate-900">SCM Lifecycle Workflow</h1>
            <p className="text-slate-500">Item &amp; Part lifecycle approval</p>
          </div>
        </div>
        <form onSubmit={onSubmit} className="space-y-3">
          <div>
            <label className="label" htmlFor="username">Username</label>
            <input id="username" className="input" value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
          </div>
          <div>
            <label className="label" htmlFor="password">Password</label>
            <input id="password" type="password" className="input" value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-rose-700">{error}</p>}
          <button className="btn-primary w-full" disabled={busy || !username}>Sign in</button>
        </form>
        <div className="mt-6">
          <p className="label">Local demo users (password = username)</p>
          <div className="grid gap-2 sm:grid-cols-2">
            {DEMO_USERS.map((u) => (
              <button
                key={u.username}
                type="button"
                disabled={busy}
                onClick={() => void signIn(u.username, u.username)}
                className="rounded-md border border-slate-200 p-3 text-left hover:border-indigo-400 hover:bg-indigo-50"
              >
                <div className="font-medium text-slate-900">{u.username}</div>
                <div className="text-xs font-medium text-indigo-700">{u.role}</div>
                <div className="mt-1 text-xs text-slate-500">{u.hint}</div>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
