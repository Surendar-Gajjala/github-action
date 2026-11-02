import { NavLink, Outlet } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { workflowApi } from '../api/endpoints';

const TABS = [
  { to: '/items', label: 'Items' },
  { to: '/parts', label: 'Parts' },
  { to: '/workflow', label: 'Workflow' },
];

function EngineBadge() {
  const { data } = useQuery({ queryKey: ['engine'], queryFn: workflowApi.engine, refetchInterval: 30_000 });
  if (!data) return null;
  const style = !data.realCamunda
    ? 'bg-amber-50 text-amber-800 ring-amber-300'
    : data.connected
      ? 'bg-emerald-50 text-emerald-800 ring-emerald-300'
      : 'bg-rose-50 text-rose-800 ring-rose-300';
  const label = !data.realCamunda ? 'Dev workflow adapter — not Camunda' : data.connected ? 'Camunda 8 · connected' : 'Camunda 8 · unreachable';
  return (
    <span title={data.details} className={`hidden rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset md:inline-flex ${style}`}>
      Engine: {label}
    </span>
  );
}

export function AppShell() {
  const { user, logout } = useAuth();
  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-14 max-w-[1400px] items-center gap-6 px-4">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-indigo-600 font-bold text-white">S</div>
            <span className="hidden font-semibold text-slate-900 sm:inline">SCM Lifecycle</span>
          </div>
          <nav aria-label="Main" className="flex h-full items-stretch gap-1">
            {TABS.map((t) => (
              <NavLink
                key={t.to}
                to={t.to}
                className={({ isActive }) =>
                  `flex items-center border-b-2 px-3 text-sm font-medium ${
                    isActive ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-slate-600 hover:text-slate-900'
                  }`
                }
              >
                {t.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <EngineBadge />
            {user && (
              <div className="flex items-center gap-2">
                <div className="text-right leading-tight">
                  <div className="font-medium text-slate-900">{user.username}</div>
                  <div className="text-xs text-slate-500">{user.roles.join(', ')}</div>
                </div>
                <button className="btn-secondary btn-sm" onClick={logout}>Switch user</button>
              </div>
            )}
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-[1400px] flex-1 px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
