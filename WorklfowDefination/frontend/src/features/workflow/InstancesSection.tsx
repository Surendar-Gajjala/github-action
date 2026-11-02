import { lazy, Suspense, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { definitionsApi, instancesApi } from '../../api/endpoints';
import type { InstanceStatus, InstanceSummary } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { DataTable, SearchInput, toSortParam, useDebounced, type Column, type SortState } from '../../components/DataTable';
import { fmtDateTime, shortId } from '../../components/format';
import { HistoryTimeline } from '../../components/HistoryTimeline';
import { Drawer } from '../../components/Overlay';
import { ErrorState, InlineError, LoadingState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useToast } from '../../components/Toast';

const BpmnCanvas = lazy(() => import('./bpmn/BpmnCanvas').then((m) => ({ default: m.BpmnCanvas })));

const STATUSES: InstanceStatus[] = ['RUNNING', 'INCIDENT', 'COMPLETED', 'REJECTED', 'STARTING'];

export function InstancesSection({ instanceId }: { instanceId?: string }) {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const definitionKey = params.get('definitionKey') ?? '';
  const [activeOnly, setActiveOnly] = useState(!definitionKey);
  const [status, setStatus] = useState<InstanceStatus | ''>('');
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState<SortState>({ property: 'startedAt', direction: 'desc' });
  const [page, setPage] = useState(0);
  const q = useDebounced(search);
  const definitions = useQuery({ queryKey: ['definitions'], queryFn: definitionsApi.list });

  const list = useQuery({
    queryKey: ['instances', { activeOnly, status, definitionKey, q, sort, page }],
    queryFn: () => instancesApi.list({ activeOnly, status: status || undefined, definitionKey: definitionKey || undefined, q, sort: toSortParam(sort), page, size: 20 }),
    placeholderData: keepPreviousData,
    refetchInterval: 3000,
  });

  const columns: Column<InstanceSummary>[] = [
    { key: 'id', header: 'Workflow Instance ID', render: (w) => <span className="font-mono text-xs" title={w.id}>{shortId(w.id)}<div className="text-slate-400">engine {w.engineInstanceKey ?? '—'}</div></span> },
    { key: 'def', header: 'Workflow Definition', sortKey: 'definitionKey', render: (w) => <div><div className="font-medium">{w.definitionKey}</div><div className="text-xs text-slate-500">published v{w.definitionVersion} · {w.engine === 'DEV_ADAPTER' ? 'dev adapter' : 'Camunda 8'}</div></div> },
    { key: 'businessKey', header: 'Business Key', sortKey: 'businessKey', render: (w) => <code className="text-xs">{w.businessKey}</code> },
    { key: 'item', header: 'Item Number', sortKey: 'itemNumber', render: (w) => <Link onClick={(e) => e.stopPropagation()} className="text-indigo-700 hover:underline" to={`/items/${w.itemId}`}>{w.itemNumber}</Link> },
    { key: 'stage', header: 'Current Stage', render: (w) => w.currentStage ?? '—' },
    { key: 'status', header: 'Status', sortKey: 'status', render: (w) => <StatusBadge status={w.status} /> },
    { key: 'startedAt', header: 'Started At', sortKey: 'startedAt', render: (w) => fmtDateTime(w.startedAt) },
    { key: 'updatedAt', header: 'Last Updated', sortKey: 'updatedAt', render: (w) => fmtDateTime(w.updatedAt) },
  ];

  return (
    <div className="space-y-3">
      <div className="card">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-3">
          <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(0); }} placeholder="Search key, item, stage…" />
          <select aria-label="Definition" className="input w-64" value={definitionKey}
            onChange={(e) => { setParams(e.target.value ? { definitionKey: e.target.value } : {}); setPage(0); }}>
            <option value="">All definitions</option>
            {(definitions.data ?? []).map((d) => <option key={d.key} value={d.key}>{d.key}</option>)}
          </select>
          <select aria-label="Instance status" className="input w-40" value={status} onChange={(e) => { setStatus(e.target.value as InstanceStatus | ''); setPage(0); }}>
            <option value="">Any status</option>
            {STATUSES.map((s) => <option key={s} value={s}>{s.toLowerCase()}</option>)}
          </select>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={activeOnly} disabled={!!status} onChange={(e) => { setActiveOnly(e.target.checked); setPage(0); }} />
            Active only
          </label>
        </div>
        <DataTable
          columns={columns}
          page={list.data}
          loading={list.isFetching && !list.data}
          error={list.error}
          onRetry={() => list.refetch()}
          sort={sort}
          onSortChange={setSort}
          onPageChange={setPage}
          rowKey={(w) => w.id}
          onRowClick={(w) => navigate(`/workflow/instances/${w.id}${params.toString() ? `?${params}` : ''}`)}
          emptyTitle={activeOnly ? 'No active workflow instances' : 'No workflow instances'}
          emptyHint={activeOnly ? 'Untick "Active only" to see completed and rejected instances.' : undefined}
        />
      </div>
      {instanceId && <InstanceDrawer id={instanceId} onClose={() => navigate(`/workflow/instances${params.toString() ? `?${params}` : ''}`)} />}
    </div>
  );
}

function InstanceDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const detail = useQuery({ queryKey: ['instance', id], queryFn: () => instancesApi.get(id), refetchInterval: 3000 });
  const w = detail.data?.instance;
  const version = useQuery({
    queryKey: ['definition', w?.definitionKey, 'version', w?.definitionVersion],
    queryFn: () => definitionsApi.version(w!.definitionKey, w!.definitionVersion),
    enabled: !!w,
    staleTime: Infinity,
  });
  const elementIds = detail.data?.history.length && w?.status !== 'COMPLETED' && w?.status !== 'REJECTED'
    ? detail.data.tasks.filter((t) => t.status === 'OPEN').map((t) => t.elementId)
    : [];
  const retry = useMutation({
    mutationFn: () => instancesApi.retry(id),
    onSuccess: () => { toast.success('Incident resolved — job retried'); void qc.invalidateQueries({ queryKey: ['instance', id] }); },
  });

  return (
    <Drawer
      open
      onClose={onClose}
      title={w ? `${w.definitionKey} · ${w.itemNumber}` : 'Workflow instance'}
      subtitle={w && <span className="flex flex-wrap items-center gap-2"><StatusBadge status={w.status} /> {w.businessKey} · v{w.definitionVersion}</span>}
    >
      {detail.isLoading && <LoadingState />}
      {detail.error && <ErrorState error={detail.error} />}
      {w && detail.data && (
        <div className="space-y-5">
          {w.status === 'INCIDENT' && (
            <div className="rounded-md border border-rose-300 bg-rose-50 p-3 text-rose-800">
              <p className="font-medium">Incident at “{w.currentStage}”</p>
              <p className="mt-1 whitespace-pre-line text-sm">{w.incidentMessage}</p>
              {hasRole('ADMIN') && (
                <button className="btn-secondary btn-sm mt-2" disabled={retry.isPending} onClick={() => retry.mutate()}>Resolve &amp; retry</button>
              )}
              <InlineError error={retry.error} />
            </div>
          )}
          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
            <div><dt className="label">Instance ID</dt><dd className="font-mono text-xs">{w.id}</dd></div>
            <div><dt className="label">Engine</dt><dd>{w.engine === 'DEV_ADAPTER' ? 'Development adapter (not Camunda)' : 'Camunda 8'} · key {w.engineInstanceKey ?? '—'}</dd></div>
            <div><dt className="label">Current stage</dt><dd>{w.currentStage ?? '—'}</dd></div>
            <div><dt className="label">Manager approval</dt><dd>{w.managerApprovalRequired ? 'Required' : 'Not required'}</dd></div>
            <div><dt className="label">Started</dt><dd>{fmtDateTime(w.startedAt)} by {w.startedBy}</dd></div>
            <div><dt className="label">Ended</dt><dd>{fmtDateTime(w.endedAt)}</dd></div>
          </dl>
          {version.data && (
            <div className="card overflow-hidden">
              <Suspense fallback={<LoadingState label="Loading diagram…" />}>
                <BpmnCanvas xml={version.data.bpmnXml} readOnly highlight={elementIds} height={300} />
              </Suspense>
            </div>
          )}
          <div>
            <h3 className="mb-2 font-semibold text-slate-900">Approval tasks</h3>
            {detail.data.tasks.length === 0 ? <p className="text-slate-500">No approval tasks yet.</p> : (
              <ul className="space-y-1">
                {detail.data.tasks.map((t) => (
                  <li key={t.id} className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2">
                    <span><b>{t.name}</b> · {t.candidateGroup}</span>
                    <span className="flex items-center gap-2 text-xs text-slate-500">
                      {t.decisionBy && <>by {t.decisionBy} {fmtDateTime(t.completedAt)}</>}
                      <StatusBadge status={t.status} />
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div>
            <h3 className="mb-2 font-semibold text-slate-900">Execution history</h3>
            <HistoryTimeline events={detail.data.history} />
          </div>
        </div>
      )}
    </Drawer>
  );
}
