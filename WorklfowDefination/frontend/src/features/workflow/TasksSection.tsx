import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { instancesApi, tasksApi } from '../../api/endpoints';
import type { ApprovalTask, TaskStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { DataTable, SearchInput, toSortParam, useDebounced, type Column, type SortState } from '../../components/DataTable';
import { fmtDate, fmtDateTime, shortId } from '../../components/format';
import { HistoryTimeline } from '../../components/HistoryTimeline';
import { Modal } from '../../components/Overlay';
import { Field, InlineError, LoadingState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useToast } from '../../components/Toast';
import { ChangeDiffView } from './ChangeDiffView';

type Dialog =
  | { kind: 'approve' | 'reject'; task: ApprovalTask }
  | { kind: 'item' | 'parts'; task: ApprovalTask }
  | { kind: 'history'; task: ApprovalTask };

export function TasksSection() {
  const { user, hasRole } = useAuth();
  const isApprover = hasRole('ENGINEERING', 'MANAGER');
  const [status, setStatus] = useState<TaskStatus>('OPEN');
  const [mine, setMine] = useState(true);
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState<SortState>({ property: 'createdAt', direction: 'desc' });
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const q = useDebounced(search);

  const tasks = useQuery({
    queryKey: ['tasks', { status, mine, q, sort, page }],
    queryFn: () => tasksApi.list({ status, mine, q, sort: toSortParam(sort), page, size: 20 }),
    placeholderData: keepPreviousData,
    refetchInterval: 3000,
  });

  const canDecide = (t: ApprovalTask) => t.status === 'OPEN' && !!user?.roles.includes(t.candidateGroup);

  const columns: Column<ApprovalTask>[] = [
    { key: 'name', header: 'Task Name', sortKey: 'name', render: (t) => <span className="font-medium text-slate-900">{t.name}</span> },
    { key: 'itemNumber', header: 'Item Number', sortKey: 'itemNumber', render: (t) => <Link className="text-indigo-700 hover:underline" to={`/items/${t.itemId}`}>{t.itemNumber}</Link> },
    { key: 'itemName', header: 'Item Name', render: (t) => t.itemName },
    { key: 'instance', header: 'Workflow Instance ID', render: (t) => <Link className="font-mono text-xs text-indigo-700 hover:underline" to={`/workflow/instances/${t.workflowInstanceId}`} title={t.workflowInstanceId}>{shortId(t.workflowInstanceId)}</Link> },
    { key: 'group', header: 'Assigned Role', sortKey: 'candidateGroup', render: (t) => <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium">{t.candidateGroup}</span> },
    { key: 'stage', header: 'Current Approval Stage', render: (t) => <div className="text-xs">{t.currentStage ?? '—'}<div className="text-slate-400">{t.definitionKey} v{t.definitionVersion}</div></div> },
    { key: 'createdAt', header: 'Created At', sortKey: 'createdAt', render: (t) => fmtDateTime(t.createdAt) },
    { key: 'due', header: 'Due Date', sortKey: 'dueDate', render: (t) => <span className={t.overdue ? 'font-medium text-rose-700' : ''}>{fmtDate(t.dueDate)}{t.overdue && ' (overdue)'}</span> },
    {
      key: 'actions', header: 'Actions',
      render: (t) => (
        <div className="flex flex-wrap gap-1">
          {t.status === 'OPEN' ? (
            <>
              <button className="btn-success btn-sm" disabled={!canDecide(t)} title={canDecide(t) ? '' : `Requires role ${t.candidateGroup}`}
                onClick={() => setDialog({ kind: 'approve', task: t })}>Approve</button>
              <button className="btn-danger btn-sm" disabled={!canDecide(t)} title={canDecide(t) ? '' : `Requires role ${t.candidateGroup}`}
                onClick={() => setDialog({ kind: 'reject', task: t })}>Reject</button>
            </>
          ) : (
            <span className="flex items-center gap-1 text-xs"><StatusBadge status={t.status} /> by {t.decisionBy}</span>
          )}
          <button className="btn-ghost btn-sm" onClick={() => setDialog({ kind: 'item', task: t })}>Item changes</button>
          <button className="btn-ghost btn-sm" onClick={() => setDialog({ kind: 'parts', task: t })}>Parts changes</button>
          <button className="btn-ghost btn-sm" onClick={() => setDialog({ kind: 'history', task: t })}>History</button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-3">
      {!isApprover && (
        <p className="rounded-md bg-sky-50 px-3 py-2 text-sky-800">
          You are signed in as <b>{user?.username}</b> without an approver role. Sign in as <b>engineer</b> or <b>manager</b> to decide tasks.
        </p>
      )}
      <div className="card">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-3">
          <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(0); }} placeholder="Search item or task…" />
          <select aria-label="Task status" className="input w-40" value={status} onChange={(e) => { setStatus(e.target.value as TaskStatus); setPage(0); }}>
            <option value="OPEN">Open</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
          </select>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={mine} onChange={(e) => { setMine(e.target.checked); setPage(0); }} />
            Only tasks for my roles ({user?.roles.join(', ')})
          </label>
        </div>
        <DataTable
          columns={columns}
          page={tasks.data}
          loading={tasks.isFetching && !tasks.data}
          error={tasks.error}
          onRetry={() => tasks.refetch()}
          sort={sort}
          onSortChange={setSort}
          onPageChange={setPage}
          rowKey={(t) => t.id}
          emptyTitle={status === 'OPEN' ? 'No open approval tasks' : 'No tasks'}
          emptyHint={status === 'OPEN' ? 'Tasks appear here when a submitted item reaches an approval stage.' : undefined}
        />
      </div>
      {dialog && (dialog.kind === 'approve' || dialog.kind === 'reject') && (
        <DecisionDialog kind={dialog.kind} task={dialog.task} onClose={() => setDialog(null)} />
      )}
      {dialog && (dialog.kind === 'item' || dialog.kind === 'parts') && (
        <ChangesDialog task={dialog.task} section={dialog.kind} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'history' && <InstanceHistoryDialog task={dialog.task} onClose={() => setDialog(null)} />}
    </div>
  );
}

export const rejectSchema = z.object({
  reason: z.string().trim().min(5, 'Please give a rejection reason (at least 5 characters)').max(2000),
});
const approveSchema = z.object({ comment: z.string().max(2000).optional() });

export function DecisionDialog({ kind, task, onClose }: { kind: 'approve' | 'reject'; task: ApprovalTask; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const reject = kind === 'reject';
  const form = useForm<{ reason?: string; comment?: string }>({
    resolver: zodResolver(reject ? rejectSchema : approveSchema),
    defaultValues: { reason: '', comment: '' },
  });
  const decide = useMutation({
    mutationFn: (v: { reason?: string; comment?: string }) =>
      reject ? tasksApi.reject(task.id, v.reason!.trim()) : tasksApi.approve(task.id, v.comment || undefined),
    onSuccess: () => {
      toast.success(`${task.name} for ${task.itemNumber} ${reject ? 'rejected' : 'approved'}`);
      void qc.invalidateQueries({ queryKey: ['tasks'] });
      void qc.invalidateQueries({ queryKey: ['instances'] });
      void qc.invalidateQueries({ queryKey: ['items'] });
      void qc.invalidateQueries({ queryKey: ['item', task.itemId] });
      onClose();
    },
  });
  const submit = form.handleSubmit((v) => decide.mutate(v));
  return (
    <Modal
      open
      onClose={onClose}
      title={`${reject ? 'Reject' : 'Approve'}: ${task.name}`}
      footer={<>
        <button className="btn-secondary" onClick={onClose}>Cancel</button>
        <button className={reject ? 'btn-danger' : 'btn-success'} disabled={decide.isPending} onClick={submit}>
          {reject ? 'Reject' : 'Approve'}
        </button>
      </>}
    >
      <form className="space-y-3" onSubmit={submit}>
        <p className="text-slate-600">
          {task.itemNumber} · {task.itemName} — {task.definitionKey} v{task.definitionVersion}
        </p>
        {reject ? (
          <Field label="Rejection reason (required)" error={form.formState.errors.reason?.message} htmlFor="reason">
            <textarea id="reason" rows={4} className="input" {...form.register('reason')} />
          </Field>
        ) : (
          <Field label="Comment (optional)" htmlFor="comment">
            <textarea id="comment" rows={3} className="input" {...form.register('comment')} />
          </Field>
        )}
        <InlineError error={decide.error} />
      </form>
    </Modal>
  );
}

function ChangesDialog({ task, section, onClose }: { task: ApprovalTask; section: 'item' | 'parts'; onClose: () => void }) {
  const diff = useQuery({ queryKey: ['task', task.id, 'changes'], queryFn: () => tasksApi.changes(task.id) });
  return (
    <Modal open onClose={onClose} size="lg" title={`${section === 'item' ? 'Item' : 'Parts'} changes — ${task.itemNumber}`}>
      {diff.isLoading ? <LoadingState /> : diff.error ? <InlineError error={diff.error} /> : diff.data && <ChangeDiffView diff={diff.data} section={section} />}
    </Modal>
  );
}

function InstanceHistoryDialog({ task, onClose }: { task: ApprovalTask; onClose: () => void }) {
  const history = useQuery({ queryKey: ['instance', task.workflowInstanceId, 'history'], queryFn: () => instancesApi.history(task.workflowInstanceId) });
  return (
    <Modal open onClose={onClose} size="lg" title={`Workflow history — ${task.itemNumber}`}>
      {history.isLoading ? <LoadingState /> : <HistoryTimeline events={history.data ?? []} />}
    </Modal>
  );
}
