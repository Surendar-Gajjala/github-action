import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { workflowApi } from '../../api/endpoints';
import { LIFECYCLE_STATUSES, type LifecycleStatus, type LifecycleTransition } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { fmtDateTime, humanize } from '../../components/format';
import { ErrorState, InlineError, LoadingState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useToast } from '../../components/Toast';

export function LifecycleSection() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const qc = useQueryClient();
  const toast = useToast();
  const transitions = useQuery({ queryKey: ['transitions'], queryFn: workflowApi.transitions });
  const [from, setFrom] = useState<LifecycleStatus>('DRAFT');
  const [to, setTo] = useState<LifecycleStatus>('REJECTED');

  const upsert = useMutation({
    mutationFn: (t: { fromStatus: LifecycleStatus; toStatus: LifecycleStatus; enabled: boolean }) => workflowApi.upsertTransition(t),
    onSuccess: (t) => {
      void qc.invalidateQueries({ queryKey: ['transitions'] });
      toast.success(`${humanize(t.fromStatus)} → ${humanize(t.toStatus)} ${t.enabled ? 'enabled' : 'disabled'}`);
    },
  });
  const remove = useMutation({
    mutationFn: (t: LifecycleTransition) => workflowApi.deleteTransition(t.id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['transitions'] }),
  });

  if (transitions.isLoading) return <LoadingState />;
  if (transitions.error) return <ErrorState error={transitions.error} onRetry={() => transitions.refetch()} />;
  const list = transitions.data ?? [];
  const lookup = (f: LifecycleStatus, t: LifecycleStatus) => list.find((x) => x.fromStatus === f && x.toStatus === t);

  return (
    <div className="space-y-4">
      <p className="text-slate-500">
        Permitted item lifecycle transitions. The backend checks every status change against this table — including changes
        made by workflow workers — so a transition that is not enabled here cannot happen, whatever the BPMN process does.
      </p>
      <div className="grid gap-4 xl:grid-cols-[auto_1fr]">
        <div className="card overflow-x-auto p-4">
          <p className="label">Transition matrix (from ↓ / to →)</p>
          <table className="text-xs">
            <thead>
              <tr>
                <th />
                {LIFECYCLE_STATUSES.map((s) => <th key={s} className="px-2 py-1 font-medium text-slate-500">{humanize(s)}</th>)}
              </tr>
            </thead>
            <tbody>
              {LIFECYCLE_STATUSES.map((f) => (
                <tr key={f}>
                  <th className="px-2 py-1 text-right"><StatusBadge status={f} /></th>
                  {LIFECYCLE_STATUSES.map((t) => {
                    const tr = lookup(f, t);
                    return (
                      <td key={t} className="px-2 py-1 text-center">
                        {f === t ? <span className="text-slate-300">—</span> : (
                          <button
                            type="button"
                            disabled={!isAdmin || upsert.isPending}
                            title={tr?.description ?? 'Not configured'}
                            aria-label={`${f} to ${t}`}
                            onClick={() => upsert.mutate({ fromStatus: f, toStatus: t, enabled: !tr?.enabled })}
                            className={`h-7 w-7 rounded-md border text-sm ${tr?.enabled ? 'border-emerald-300 bg-emerald-100 text-emerald-700' : tr ? 'border-slate-300 bg-slate-100 text-slate-400' : 'border-dashed border-slate-200 text-slate-300'} ${isAdmin ? 'hover:border-indigo-400' : ''}`}
                          >
                            {tr?.enabled ? '✓' : tr ? '×' : ''}
                          </button>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
          {isAdmin && <p className="mt-2 text-xs text-slate-500">Click a cell to enable or disable a transition.</p>}
        </div>
        <div className="card">
          <table className="min-w-full divide-y divide-slate-200">
            <thead><tr><th className="th">From</th><th className="th">To</th><th className="th">Description</th><th className="th">Enabled</th><th className="th">Updated</th><th className="th" /></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {list.map((t) => (
                <tr key={t.id}>
                  <td className="td"><StatusBadge status={t.fromStatus} /></td>
                  <td className="td"><StatusBadge status={t.toStatus} /></td>
                  <td className="td text-slate-600">{t.description}{t.system && <span className="ml-1 text-xs text-slate-400">(system)</span>}</td>
                  <td className="td">
                    <input type="checkbox" aria-label={`Enable ${t.fromStatus} to ${t.toStatus}`} disabled={!isAdmin} checked={t.enabled}
                      onChange={(e) => upsert.mutate({ fromStatus: t.fromStatus, toStatus: t.toStatus, enabled: e.target.checked })} />
                  </td>
                  <td className="td text-xs text-slate-500">{t.updatedBy ? `${fmtDateTime(t.updatedAt)} · ${t.updatedBy}` : '—'}</td>
                  <td className="td">
                    {isAdmin && !t.system && <button className="btn-ghost btn-sm text-rose-700" onClick={() => remove.mutate(t)}>Delete</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {isAdmin && (
            <div className="flex flex-wrap items-end gap-2 border-t border-slate-200 p-3">
              <div>
                <label className="label" htmlFor="tr-from">From</label>
                <select id="tr-from" className="input" value={from} onChange={(e) => setFrom(e.target.value as LifecycleStatus)}>
                  {LIFECYCLE_STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
                </select>
              </div>
              <div>
                <label className="label" htmlFor="tr-to">To</label>
                <select id="tr-to" className="input" value={to} onChange={(e) => setTo(e.target.value as LifecycleStatus)}>
                  {LIFECYCLE_STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
                </select>
              </div>
              <button className="btn-secondary" disabled={from === to || upsert.isPending}
                onClick={() => upsert.mutate({ fromStatus: from, toStatus: to, enabled: true })}>
                Add transition
              </button>
            </div>
          )}
          <div className="px-3 pb-3"><InlineError error={upsert.error || remove.error} /></div>
        </div>
      </div>
    </div>
  );
}
