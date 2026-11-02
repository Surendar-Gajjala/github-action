import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { workflowApi } from '../../api/endpoints';
import type { WorkflowEvent } from '../../api/types';
import { DataTable, SearchInput, toSortParam, useDebounced, type Column, type SortState } from '../../components/DataTable';
import { fmtDateTime, humanize, shortId } from '../../components/format';
import { StatusBadge } from '../../components/StatusBadge';

const ACTIONS = [
  'ITEM_SUBMITTED', 'WORKFLOW_STARTED', 'PARTS_ADDED', 'PARTS_REMOVED', 'APPROVAL_REQUESTED', 'APPROVAL_COMPLETED',
  'REQUEST_REJECTED', 'REVISION_RELEASED', 'WORKFLOW_FAILED', 'WORKFLOW_RETRIED', 'WORKFLOW_COMPLETED',
  'LIFECYCLE_TRANSITION', 'CHANGE_REQUEST_CREATED', 'ITEM_CREATED', 'ITEM_UPDATED', 'VALIDATION_COMPLETED',
  'DEFINITION_PUBLISHED', 'DEFINITION_SAVED', 'DEFINITION_STATUS_CHANGED', 'LIFECYCLE_CONFIG_CHANGED',
];

export function HistorySection() {
  const [search, setSearch] = useState('');
  const [action, setAction] = useState('');
  const [sort, setSort] = useState<SortState>({ property: 'timestamp', direction: 'desc' });
  const [page, setPage] = useState(0);
  const q = useDebounced(search);
  const events = useQuery({
    queryKey: ['history', { q, action, sort, page }],
    queryFn: () => workflowApi.history({ q, action: action || undefined, sort: toSortParam(sort), page, size: 25 }),
    placeholderData: keepPreviousData,
    refetchInterval: 5000,
  });

  const columns: Column<WorkflowEvent>[] = [
    { key: 'timestamp', header: 'Timestamp', sortKey: 'timestamp', render: (e) => <span className="whitespace-nowrap">{fmtDateTime(e.timestamp)}</span> },
    { key: 'action', header: 'Action', sortKey: 'action', render: (e) => <span className="font-medium">{humanize(e.action)}</span> },
    { key: 'actor', header: 'Actor', sortKey: 'actor', render: (e) => e.actor },
    { key: 'item', header: 'Item', render: (e) => (e.itemId ? <Link className="text-indigo-700 hover:underline" to={`/items/${e.itemId}`}>{e.itemNumber}</Link> : '—') },
    { key: 'transition', header: 'Status', render: (e) => (e.toStatus ? <span className="flex items-center gap-1">{e.fromStatus && <><StatusBadge status={e.fromStatus} />→</>}<StatusBadge status={e.toStatus} /></span> : '') },
    { key: 'key', header: 'Business Key / Instance', render: (e) => (
      <div className="text-xs">
        {e.businessKey && <code>{e.businessKey}</code>}
        {e.workflowInstanceId && <div><Link className="text-indigo-700 hover:underline" to={`/workflow/instances/${e.workflowInstanceId}`}>{shortId(e.workflowInstanceId)}</Link></div>}
        {!e.businessKey && e.workflowDefinitionKey && <code>{e.workflowDefinitionKey}</code>}
      </div>
    ) },
    { key: 'summary', header: 'Change summary', render: (e) => <span className="text-slate-600">{e.summary}</span>, className: 'max-w-md' },
  ];

  return (
    <div className="card">
      <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-3">
        <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(0); }} placeholder="Search item, actor, key, text…" />
        <select aria-label="Action" className="input w-64" value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }}>
          <option value="">All actions</option>
          {ACTIONS.map((a) => <option key={a} value={a}>{humanize(a)}</option>)}
        </select>
      </div>
      <DataTable
        columns={columns}
        page={events.data}
        loading={events.isFetching && !events.data}
        error={events.error}
        onRetry={() => events.refetch()}
        sort={sort}
        onSortChange={setSort}
        onPageChange={setPage}
        rowKey={(e) => e.id}
        emptyTitle="No workflow history"
      />
    </div>
  );
}
