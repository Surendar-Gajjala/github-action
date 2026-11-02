import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { itemsApi } from '../../api/endpoints';
import type { ItemDetail } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { fmtDateTime, shortId } from '../../components/format';
import { HistoryTimeline } from '../../components/HistoryTimeline';
import { MultiSelect } from '../../components/MultiSelect';
import { Drawer } from '../../components/Overlay';
import { ErrorState, InlineError, LoadingState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useToast } from '../../components/Toast';
import { usePartOptions } from '../options';
import { ChangeDiffView } from '../workflow/ChangeDiffView';

export type ItemDrawerTab = 'overview' | 'parts' | 'changes' | 'approved' | 'history';

interface Props {
  itemId: number;
  initialTab?: ItemDrawerTab;
  onClose: () => void;
  onEdit: (item: ItemDetail) => void;
  onSubmit: (item: ItemDetail) => void;
}

export function ItemDetailsDrawer({ itemId, initialTab = 'overview', onClose, onEdit, onSubmit }: Props) {
  const [tab, setTab] = useState<ItemDrawerTab>(initialTab);
  const { hasRole } = useAuth();
  const canEdit = hasRole('AUTHOR', 'ADMIN');
  useEffect(() => setTab(initialTab), [initialTab, itemId]);

  const item = useQuery({
    queryKey: ['item', itemId],
    queryFn: () => itemsApi.get(itemId),
    // While the workflow runs, poll so engine progress (approvals, release) shows up.
    refetchInterval: (q) => (q.state.data?.lifecycleStatus === 'IN_REVIEW' ? 2000 : false),
  });
  const d = item.data;

  const tabs: [ItemDrawerTab, string][] = [
    ['overview', 'Overview'],
    ['parts', `Parts${d ? ` (${d.parts.length})` : ''}`],
    ['changes', 'Pending changes'],
    ['approved', 'Approved revision'],
    ['history', 'Workflow history'],
  ];

  return (
    <Drawer
      open
      onClose={onClose}
      title={d ? `${d.itemNumber} · ${d.name}` : 'Item'}
      subtitle={d && (
        <span className="flex flex-wrap items-center gap-2">
          <StatusBadge status={d.lifecycleStatus} />
          <span>Working rev <b>{d.revision}</b></span>
          <span>· Approved rev <b>{d.approvedRevision ?? '—'}</b></span>
        </span>
      )}
      actions={d && canEdit && (
        <>
          <button className="btn-secondary btn-sm" disabled={!d.editable} onClick={() => onEdit(d)}>Edit</button>
          <button className="btn-primary btn-sm" disabled={!d.submittable} onClick={() => onSubmit(d)}>Submit for approval</button>
        </>
      )}
    >
      {item.isLoading && <LoadingState />}
      {item.error && <ErrorState error={item.error} onRetry={() => item.refetch()} />}
      {d && (
        <>
          <div className="mb-4 flex gap-1 border-b border-slate-200" role="tablist">
            {tabs.map(([key, label]) => (
              <button
                key={key}
                role="tab"
                aria-selected={tab === key}
                onClick={() => setTab(key)}
                className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${tab === key ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-slate-500 hover:text-slate-800'}`}
              >
                {label}
              </button>
            ))}
          </div>
          {tab === 'overview' && <Overview item={d} />}
          {tab === 'parts' && <PartsPanel item={d} canEdit={canEdit} />}
          {tab === 'changes' && <ChangesPanel item={d} />}
          {tab === 'approved' && <ApprovedPanel item={d} />}
          {tab === 'history' && <HistoryPanel itemId={d.id} />}
        </>
      )}
    </Drawer>
  );
}

function Overview({ item }: { item: ItemDetail }) {
  const cr = item.currentChangeRequest;
  return (
    <div className="space-y-5">
      {item.lifecycleStatus === 'CHANGE_PENDING' && (
        <div className="rounded-md border border-amber-300 bg-amber-50 p-3 text-amber-900">
          Revision <b>{item.revision}</b> has unapproved changes. The released revision is still <b>{item.approvedRevision}</b>.
          Submit the change for approval to release it.
        </div>
      )}
      {item.lifecycleStatus === 'IN_REVIEW' && (
        <div className="rounded-md border border-sky-300 bg-sky-50 p-3 text-sky-900">
          In review — current stage: <b>{item.latestWorkflow?.currentStage ?? 'starting'}</b>. Editing is locked until the workflow completes.
        </div>
      )}
      <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
        <Detail label="Item number" value={item.itemNumber} />
        <Detail label="Name" value={item.name} />
        <Detail label="Description" value={item.description ?? '—'} wide />
        <Detail label="Created" value={fmtDateTime(item.createdAt)} />
        <Detail label="Updated" value={fmtDateTime(item.updatedAt)} />
      </dl>
      {cr && (
        <div className="card p-4">
          <div className="mb-2 flex items-center justify-between">
            <span className="font-semibold text-slate-900">Change request {cr.number}</span>
            <StatusBadge status={cr.status} />
          </div>
          <p className="text-slate-600">
            Revision {cr.baseRevision ? `${cr.baseRevision} → ` : ''}{cr.targetRevision} · opened by {cr.createdBy} {fmtDateTime(cr.createdAt)}
            {cr.submittedBy && <> · submitted by {cr.submittedBy}</>}
          </p>
          {cr.rejectionReason && <p className="mt-2 rounded bg-rose-50 px-2 py-1 text-rose-800">Rejected: {cr.rejectionReason}</p>}
          {cr.changeLog && <pre className="mt-2 whitespace-pre-wrap rounded bg-slate-50 p-2 font-sans text-xs text-slate-600">{cr.changeLog}</pre>}
        </div>
      )}
      {item.latestWorkflow && (
        <div className="card flex items-center justify-between p-4">
          <div>
            <div className="font-semibold text-slate-900">Latest workflow</div>
            <div className="text-slate-600">
              {item.latestWorkflow.definitionKey} v{item.latestWorkflow.definitionVersion} · {item.latestWorkflow.currentStage ?? '—'} · instance {shortId(item.latestWorkflow.id)}
            </div>
          </div>
          <div className="flex items-center gap-2">
            <StatusBadge status={item.latestWorkflow.status} />
            <Link className="btn-secondary btn-sm" to={`/workflow/instances/${item.latestWorkflow.id}`}>Open</Link>
          </div>
        </div>
      )}
    </div>
  );
}

function Detail({ label, value, wide }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={wide ? 'col-span-2' : ''}>
      <dt className="label">{label}</dt>
      <dd className="text-slate-800">{value}</dd>
    </div>
  );
}

function PartsPanel({ item, canEdit }: { item: ItemDetail; canEdit: boolean }) {
  const [editing, setEditing] = useState(false);
  const [selection, setSelection] = useState<number[]>([]);
  const { options: partOptions, loading } = usePartOptions(editing);
  const options = partOptions.length
    ? partOptions
    : item.parts.map((p) => ({ value: p.id, label: `${p.partNumber} · ${p.name}`, description: p.partType }));
  const qc = useQueryClient();
  const toast = useToast();
  const save = useMutation({
    mutationFn: () => itemsApi.replaceParts(item.id, selection, item.version),
    onSuccess: (saved) => {
      qc.setQueryData(['item', item.id], saved);
      void qc.invalidateQueries({ queryKey: ['items'] });
      void qc.invalidateQueries({ queryKey: ['parts'] });
      toast.success(saved.lifecycleStatus === 'CHANGE_PENDING' && item.lifecycleStatus === 'APPROVED'
        ? `Parts updated — ${saved.itemNumber} is now CHANGE PENDING (rev ${saved.revision})`
        : 'Parts updated');
      setEditing(false);
    },
  });

  return (
    <div className="space-y-3">
      {canEdit && !editing && (
        <button className="btn-secondary btn-sm" disabled={!item.editable}
          onClick={() => { setSelection(item.parts.map((p) => p.id)); setEditing(true); }}>
          Add / remove parts
        </button>
      )}
      {editing && (
        <div className="card space-y-3 p-3">
          <MultiSelect ariaLabel="Parts" options={options} loading={loading} value={selection} onChange={setSelection} placeholder="Search parts…" />
          {item.lifecycleStatus === 'APPROVED' && (
            <p className="text-xs text-amber-700">Changing parts of an approved item opens a change request (CHANGE PENDING).</p>
          )}
          <InlineError error={save.error} />
          <div className="flex justify-end gap-2">
            <button className="btn-secondary btn-sm" onClick={() => setEditing(false)}>Cancel</button>
            <button className="btn-primary btn-sm" disabled={save.isPending} onClick={() => save.mutate()}>Save parts</button>
          </div>
        </div>
      )}
      {item.parts.length === 0 ? (
        <p className="text-slate-500">No parts associated.</p>
      ) : (
        <table className="min-w-full divide-y divide-slate-200">
          <thead><tr><th className="th">Part number</th><th className="th">Name</th><th className="th">Type</th><th className="th">Qty</th></tr></thead>
          <tbody className="divide-y divide-slate-100">
            {item.parts.map((p) => (
              <tr key={p.id}>
                <td className="td font-medium"><Link className="text-indigo-700 hover:underline" to={`/parts/${p.id}`}>{p.partNumber}</Link></td>
                <td className="td">{p.name}</td>
                <td className="td">{p.partType ?? '—'}</td>
                <td className="td">{p.quantity}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function ChangesPanel({ item }: { item: ItemDetail }) {
  const diff = useQuery({ queryKey: ['item', item.id, 'changes', item.version], queryFn: () => itemsApi.changes(item.id) });
  if (diff.isLoading) return <LoadingState />;
  if (diff.error) return <ErrorState error={diff.error} />;
  return diff.data ? <ChangeDiffView diff={diff.data} /> : null;
}

function ApprovedPanel({ item }: { item: ItemDetail }) {
  const revisions = useQuery({ queryKey: ['item', item.id, 'revisions'], queryFn: () => itemsApi.revisions(item.id) });
  if (revisions.isLoading) return <LoadingState />;
  if (revisions.error) return <ErrorState error={revisions.error} />;
  const list = revisions.data ?? [];
  if (!list.length) return <p className="text-slate-500">This item has never been approved.</p>;
  return (
    <div className="space-y-3">
      {list.map((r) => (
        <div key={r.id} className={`card p-4 ${r.revision === item.approvedRevision ? 'ring-1 ring-emerald-300' : ''}`}>
          <div className="mb-2 flex items-center justify-between">
            <span className="font-semibold text-slate-900">Revision {r.revision} · {r.name}</span>
            {r.revision === item.approvedRevision ? <StatusBadge status="APPROVED" label="Current approved" /> : <StatusBadge status="SUPERSEDED" />}
          </div>
          <p className="text-xs text-slate-500">Approved {fmtDateTime(r.approvedAt)} · last approver {r.approvedBy}</p>
          <ul className="mt-2 flex flex-wrap gap-1.5">
            {r.parts.map((p) => (
              <li key={p.partNumber} className="rounded bg-slate-100 px-2 py-0.5 text-xs">{p.partNumber} · {p.name}</li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

function HistoryPanel({ itemId }: { itemId: number }) {
  const history = useQuery({ queryKey: ['item', itemId, 'history'], queryFn: () => itemsApi.history(itemId), refetchInterval: 4000 });
  if (history.isLoading) return <LoadingState />;
  if (history.error) return <ErrorState error={history.error} />;
  return <HistoryTimeline events={history.data ?? []} />;
}
