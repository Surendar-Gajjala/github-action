import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { partsApi } from '../../api/endpoints';
import type { PartDetail } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { fmtDateTime } from '../../components/format';
import { MultiSelect } from '../../components/MultiSelect';
import { Drawer } from '../../components/Overlay';
import { ErrorState, InlineError, LoadingState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useToast } from '../../components/Toast';
import { useItemOptions } from '../options';

export function PartDetailsDrawer({ partId, onClose, onEdit }: { partId: number; onClose: () => void; onEdit: (p: PartDetail) => void }) {
  const { hasRole } = useAuth();
  const canEdit = hasRole('AUTHOR', 'ADMIN');
  const part = useQuery({ queryKey: ['part', partId], queryFn: () => partsApi.get(partId) });
  const p = part.data;
  return (
    <Drawer
      open
      onClose={onClose}
      title={p ? `${p.partNumber} · ${p.name}` : 'Part'}
      subtitle={p && <span>{p.partType ?? 'No type'} · used in {p.items.length} item(s)</span>}
      actions={p && canEdit && <button className="btn-secondary btn-sm" onClick={() => onEdit(p)}>Edit</button>}
    >
      {part.isLoading && <LoadingState />}
      {part.error && <ErrorState error={part.error} onRetry={() => part.refetch()} />}
      {p && (
        <div className="space-y-6">
          <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
            <div><dt className="label">Part number</dt><dd>{p.partNumber}</dd></div>
            <div><dt className="label">Type</dt><dd>{p.partType ?? '—'}</dd></div>
            <div className="col-span-2"><dt className="label">Description</dt><dd>{p.description ?? '—'}</dd></div>
            <div><dt className="label">Created</dt><dd>{fmtDateTime(p.createdAt)}</dd></div>
            <div><dt className="label">Updated</dt><dd>{fmtDateTime(p.updatedAt)}</dd></div>
          </dl>
          <AssociatedItems part={p} canEdit={canEdit} />
        </div>
      )}
    </Drawer>
  );
}

function AssociatedItems({ part, canEdit }: { part: PartDetail; canEdit: boolean }) {
  const [editing, setEditing] = useState(false);
  const [selection, setSelection] = useState<number[]>([]);
  const { options: itemOptions, loading, items } = useItemOptions(editing);
  const options = itemOptions.length
    ? itemOptions
    : part.items.map((i) => ({ value: i.id, label: `${i.itemNumber} · ${i.name}` }));
  const qc = useQueryClient();
  const toast = useToast();
  const save = useMutation({
    mutationFn: () => partsApi.replaceItems(part.id, selection),
    onSuccess: (r) => {
      qc.setQueryData(['part', part.id], r.part);
      void qc.invalidateQueries({ queryKey: ['parts'] });
      void qc.invalidateQueries({ queryKey: ['items'] });
      void qc.invalidateQueries({ queryKey: ['item'] });
      const flagged = r.affectedItems.filter((i) => i.lifecycleStatus === 'CHANGE_PENDING' && i.approvedRevision);
      toast.success(
        `Associations updated for ${r.affectedItems.length} item(s).` +
          (flagged.length ? `\nChange requests opened: ${flagged.map((i) => i.itemNumber).join(', ')}` : ''),
      );
      setEditing(false);
    },
  });
  const current = new Set(part.items.map((i) => i.id));
  const changedApproved = items.filter(
    (i) => i.lifecycleStatus === 'APPROVED' && current.has(i.id) !== selection.includes(i.id),
  );
  const inReview = items.filter((i) => i.lifecycleStatus === 'IN_REVIEW' && current.has(i.id) !== selection.includes(i.id));

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold text-slate-900">Associated items</h3>
        {canEdit && !editing && (
          <button className="btn-secondary btn-sm" onClick={() => { setSelection(part.items.map((i) => i.id)); setEditing(true); }}>
            Manage associated items
          </button>
        )}
      </div>
      {editing && (
        <div className="card space-y-3 p-3">
          <MultiSelect ariaLabel="Items" options={options} loading={loading} value={selection} onChange={setSelection} placeholder="Search items…" />
          {changedApproved.length > 0 && (
            <p className="rounded bg-amber-50 px-2 py-1 text-xs text-amber-800">
              Change requests will be opened for approved items: {changedApproved.map((i) => i.itemNumber).join(', ')}. Other items are not affected.
            </p>
          )}
          {inReview.length > 0 && (
            <p className="rounded bg-rose-50 px-2 py-1 text-xs text-rose-800">
              {inReview.map((i) => i.itemNumber).join(', ')} {inReview.length === 1 ? 'is' : 'are'} in review and cannot be changed now.
            </p>
          )}
          <InlineError error={save.error} />
          <div className="flex justify-end gap-2">
            <button className="btn-secondary btn-sm" onClick={() => setEditing(false)}>Cancel</button>
            <button className="btn-primary btn-sm" disabled={save.isPending} onClick={() => save.mutate()}>Save associations</button>
          </div>
        </div>
      )}
      {part.items.length === 0 ? (
        <p className="text-slate-500">This part is not used by any item.</p>
      ) : (
        <table className="min-w-full divide-y divide-slate-200">
          <thead><tr><th className="th">Item number</th><th className="th">Name</th><th className="th">Revision</th><th className="th">Status</th></tr></thead>
          <tbody className="divide-y divide-slate-100">
            {part.items.map((i) => (
              <tr key={i.id}>
                <td className="td font-medium"><Link className="text-indigo-700 hover:underline" to={`/items/${i.id}`}>{i.itemNumber}</Link></td>
                <td className="td">{i.name}</td>
                <td className="td">{i.revision}{i.approvedRevision && i.approvedRevision !== i.revision && <span className="text-xs text-slate-500"> (approved {i.approvedRevision})</span>}</td>
                <td className="td"><StatusBadge status={i.lifecycleStatus} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
