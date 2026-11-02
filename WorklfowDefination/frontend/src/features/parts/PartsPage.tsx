import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { partsApi } from '../../api/endpoints';
import type { PartDetail, PartSummary } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { DataTable, SearchInput, toSortParam, useDebounced, type Column, type SortState } from '../../components/DataTable';
import { fmtDateTime } from '../../components/format';
import { PartDetailsDrawer } from './PartDetailsDrawer';
import { PartFormDialog } from './PartFormDialog';

export function PartsPage() {
  const { partId } = useParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const canEdit = hasRole('AUTHOR', 'ADMIN');
  const [search, setSearch] = useState('');
  const [partType, setPartType] = useState('');
  const [sort, setSort] = useState<SortState>({ property: 'partNumber', direction: 'asc' });
  const [page, setPage] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [editPart, setEditPart] = useState<PartDetail | undefined>();
  const q = useDebounced(search);

  const list = useQuery({
    queryKey: ['parts', { q, partType, sort, page }],
    queryFn: () => partsApi.list({ q, partType: partType || undefined, sort: toSortParam(sort), page, size: 20 }),
    placeholderData: keepPreviousData,
  });

  async function edit(id: number) {
    setEditPart(await partsApi.get(id));
    setFormOpen(true);
  }

  const columns: Column<PartSummary>[] = [
    { key: 'partNumber', header: 'Part Number', sortKey: 'partNumber', render: (p) => <span className="font-medium text-slate-900">{p.partNumber}</span> },
    { key: 'name', header: 'Part Name', sortKey: 'name', render: (p) => p.name },
    { key: 'type', header: 'Part Type', sortKey: 'partType', render: (p) => p.partType ?? '—' },
    { key: 'items', header: 'Associated Items', render: (p) => p.associatedItemsCount, className: 'text-right' },
    { key: 'createdAt', header: 'Created At', sortKey: 'createdAt', render: (p) => fmtDateTime(p.createdAt) },
    {
      key: 'actions', header: 'Actions',
      render: (p) => (
        <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
          <button className="btn-ghost btn-sm" onClick={() => navigate(`/parts/${p.id}`)}>View</button>
          {canEdit && <button className="btn-ghost btn-sm" onClick={() => void edit(p.id)}>Edit</button>}
          <button className="btn-ghost btn-sm" onClick={() => navigate(`/parts/${p.id}`)}>Items</button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Parts</h1>
          <p className="text-slate-500">Parts master data and the items that use them.</p>
        </div>
        {canEdit && <button className="btn-primary" onClick={() => { setEditPart(undefined); setFormOpen(true); }}>+ Create Part</button>}
      </div>
      <div className="card">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-3">
          <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(0); }} placeholder="Search number or name…" />
          <input aria-label="Filter by part type" className="input w-48" placeholder="Part type (exact)" value={partType}
            onChange={(e) => { setPartType(e.target.value.toUpperCase()); setPage(0); }} />
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
          rowKey={(p) => p.id}
          onRowClick={(p) => navigate(`/parts/${p.id}`)}
          emptyTitle="No parts found"
        />
      </div>
      {partId && <PartDetailsDrawer partId={Number(partId)} onClose={() => navigate('/parts')} onEdit={(p) => { setEditPart(p); setFormOpen(true); }} />}
      <PartFormDialog open={formOpen} part={editPart} onClose={() => setFormOpen(false)} />
    </div>
  );
}
