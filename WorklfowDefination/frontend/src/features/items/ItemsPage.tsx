import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { itemsApi } from '../../api/endpoints';
import { LIFECYCLE_STATUSES, type ItemDetail, type ItemSummary, type LifecycleStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { DataTable, SearchInput, toSortParam, useDebounced, type Column, type SortState } from '../../components/DataTable';
import { fmtDateTime, humanize } from '../../components/format';
import { StatusBadge } from '../../components/StatusBadge';
import { ItemDetailsDrawer, type ItemDrawerTab } from './ItemDetailsDrawer';
import { ItemFormDialog } from './ItemFormDialog';
import { SubmitDialog } from './SubmitDialog';

export function ItemsPage() {
  const { itemId } = useParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canEdit = hasRole('AUTHOR', 'ADMIN');

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<LifecycleStatus | ''>('');
  const [sort, setSort] = useState<SortState>({ property: 'updatedAt', direction: 'desc' });
  const [page, setPage] = useState(0);
  const q = useDebounced(search);

  const [formOpen, setFormOpen] = useState(false);
  const [editItem, setEditItem] = useState<ItemDetail | undefined>();
  const [submitItem, setSubmitItem] = useState<ItemDetail | undefined>();
  const [drawerTab, setDrawerTab] = useState<ItemDrawerTab>('overview');

  const list = useQuery({
    queryKey: ['items', { q, status, sort, page }],
    queryFn: () => itemsApi.list({ q, status: status || undefined, sort: toSortParam(sort), page, size: 20 }),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => (query.state.data?.content.some((i) => i.lifecycleStatus === 'IN_REVIEW') ? 3000 : false),
  });

  async function openForEdit(id: number) {
    setEditItem(await itemsApi.get(id));
    setFormOpen(true);
  }

  async function openForSubmit(id: number) {
    setSubmitItem(await itemsApi.get(id));
  }

  const open = (id: number, tab: ItemDrawerTab = 'overview') => {
    setDrawerTab(tab);
    navigate(`/items/${id}`);
  };

  const columns: Column<ItemSummary>[] = [
    { key: 'itemNumber', header: 'Item Number', sortKey: 'itemNumber', render: (i) => <span className="font-medium text-slate-900">{i.itemNumber}</span> },
    { key: 'name', header: 'Item Name', sortKey: 'name', render: (i) => i.name },
    {
      key: 'revision', header: 'Revision', sortKey: 'revision',
      render: (i) => (
        <span>
          {i.revision}
          {i.approvedRevision && i.approvedRevision !== i.revision && (
            <span className="ml-1 text-xs text-slate-500">(approved {i.approvedRevision})</span>
          )}
        </span>
      ),
    },
    { key: 'parts', header: 'Parts', render: (i) => i.partCount, className: 'text-right' },
    { key: 'status', header: 'Lifecycle Status', sortKey: 'lifecycleStatus', render: (i) => <StatusBadge status={i.lifecycleStatus} /> },
    { key: 'updatedAt', header: 'Updated At', sortKey: 'updatedAt', render: (i) => fmtDateTime(i.updatedAt) },
    {
      key: 'actions', header: 'Actions',
      render: (i) => (
        <div className="flex flex-wrap gap-1" onClick={(e) => e.stopPropagation()}>
          <button className="btn-ghost btn-sm" onClick={() => open(i.id)}>View</button>
          {canEdit && (
            <>
              <button className="btn-ghost btn-sm" disabled={i.lifecycleStatus === 'IN_REVIEW'} onClick={() => void openForEdit(i.id)}>Edit</button>
              <button className="btn-ghost btn-sm" disabled={i.lifecycleStatus === 'IN_REVIEW'} onClick={() => open(i.id, 'parts')}>Parts</button>
              <button
                className="btn-ghost btn-sm text-indigo-700"
                disabled={!(i.lifecycleStatus === 'DRAFT' || i.lifecycleStatus === 'CHANGE_PENDING')}
                onClick={() => void openForSubmit(i.id)}
              >
                Submit
              </button>
            </>
          )}
          <button className="btn-ghost btn-sm" onClick={() => open(i.id, 'history')}>History</button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Items</h1>
          <p className="text-slate-500">Items, their parts and lifecycle status. Changes require workflow approval before release.</p>
        </div>
        {canEdit && (
          <button className="btn-primary" onClick={() => { setEditItem(undefined); setFormOpen(true); }}>+ Create Item</button>
        )}
      </div>
      <div className="card">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-3">
          <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(0); }} placeholder="Search number or name…" />
          <select aria-label="Filter by status" className="input w-48" value={status}
            onChange={(e) => { setStatus(e.target.value as LifecycleStatus | ''); setPage(0); }}>
            <option value="">All statuses</option>
            {LIFECYCLE_STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
          </select>
        </div>
        <DataTable
          columns={columns}
          page={list.data}
          loading={list.isFetching && !list.data}
          error={list.error}
          onRetry={() => list.refetch()}
          sort={sort}
          onSortChange={(s) => { setSort(s); setPage(0); }}
          onPageChange={setPage}
          rowKey={(i) => i.id}
          onRowClick={(i) => open(i.id)}
          emptyTitle="No items found"
          emptyHint={search || status ? 'Try a different search or filter.' : canEdit ? 'Create the first item to get started.' : undefined}
        />
      </div>

      {itemId && (
        <ItemDetailsDrawer
          itemId={Number(itemId)}
          initialTab={drawerTab}
          onClose={() => navigate('/items')}
          onEdit={(i) => { setEditItem(i); setFormOpen(true); }}
          onSubmit={setSubmitItem}
        />
      )}
      <ItemFormDialog open={formOpen} item={editItem} onClose={() => setFormOpen(false)}
        onSaved={(saved) => !editItem && navigate(`/items/${saved.id}`)} />
      {submitItem && <SubmitDialog open item={submitItem} onClose={() => setSubmitItem(undefined)} />}
    </div>
  );
}
