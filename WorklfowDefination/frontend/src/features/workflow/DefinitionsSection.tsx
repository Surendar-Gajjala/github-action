import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { definitionsApi } from '../../api/endpoints';
import { WORKFLOW_TYPES, type DefinitionSummary, type WorkflowType } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { DataTable, type Column } from '../../components/DataTable';
import { fmtDateTime, humanize } from '../../components/format';
import { ConfirmDialog, Modal } from '../../components/Overlay';
import { Field, InlineError, LoadingState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useToast } from '../../components/Toast';

export function DefinitionsSection() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const qc = useQueryClient();
  const toast = useToast();
  const list = useQuery({ queryKey: ['definitions'], queryFn: definitionsApi.list });
  const [creating, setCreating] = useState(false);
  const [historyKey, setHistoryKey] = useState<string | null>(null);
  const [publishKey, setPublishKey] = useState<string | null>(null);

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['definitions'] });
    void qc.invalidateQueries({ queryKey: ['definition'] });
  };
  const publish = useMutation({
    mutationFn: (key: string) => definitionsApi.publish(key),
    onSuccess: (d) => {
      refresh();
      setPublishKey(null);
      toast.success(`${d.definition.key} v${d.definition.publishedVersion} published and deployed to the workflow engine`);
    },
  });
  const toggle = useMutation({
    mutationFn: (d: DefinitionSummary) => (d.active ? definitionsApi.deactivate(d.key) : definitionsApi.activate(d.key)),
    onSuccess: (d) => { refresh(); toast.success(`${d.definition.key} ${d.definition.active ? 'activated' : 'deactivated'}`); },
    onError: (e) => toast.error(String((e as Error).message)),
  });

  const columns: Column<DefinitionSummary>[] = [
    { key: 'name', header: 'Workflow Name', render: (d) => <div><div className="font-medium text-slate-900">{d.name}</div>{d.description && <div className="max-w-sm truncate text-xs text-slate-500">{d.description}</div>}</div> },
    { key: 'key', header: 'Workflow Key', render: (d) => <code className="text-xs">{d.key}</code> },
    {
      key: 'version', header: 'Version',
      render: (d) => (
        <div className="text-xs">
          <div>Published: <b>{d.publishedVersion ? `v${d.publishedVersion}` : '—'}</b></div>
          {d.hasDraft && <div className="text-amber-700">Draft: v{d.latestVersion}</div>}
        </div>
      ),
    },
    { key: 'type', header: 'Workflow Type', render: (d) => humanize(d.type) },
    {
      key: 'status', header: 'Status',
      render: (d) => (
        <div className="flex flex-wrap gap-1">
          <StatusBadge status={d.active ? 'ACTIVE' : 'INACTIVE'} />
          <StatusBadge status={d.publishedVersion ? 'PUBLISHED' : 'DRAFT'} label={d.publishedVersion ? 'Published' : 'Not published'} />
        </div>
      ),
    },
    { key: 'updated', header: 'Last Updated', render: (d) => <div className="text-xs">{fmtDateTime(d.updatedAt)}<div className="text-slate-500">{d.updatedBy}</div></div> },
    {
      key: 'actions', header: 'Actions',
      render: (d) => (
        <div className="flex flex-wrap gap-1">
          {isAdmin && <button className="btn-ghost btn-sm" onClick={() => navigate(`/workflow/designer/${d.key}`)}>Edit</button>}
          <button className="btn-ghost btn-sm" onClick={() => navigate(`/workflow/designer/${d.key}`)}>View</button>
          {isAdmin && (
            <button className="btn-ghost btn-sm text-indigo-700" disabled={!d.hasDraft} onClick={() => setPublishKey(d.key)}>Publish</button>
          )}
          {isAdmin && (
            <button className="btn-ghost btn-sm" disabled={toggle.isPending} onClick={() => toggle.mutate(d)}>
              {d.active ? 'Deactivate' : 'Activate'}
            </button>
          )}
          <button className="btn-ghost btn-sm" onClick={() => navigate(`/workflow/instances?definitionKey=${d.key}`)}>Instances</button>
          <button className="btn-ghost btn-sm" onClick={() => setHistoryKey(d.key)}>Versions</button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-slate-500">
          Definitions are BPMN 2.0 processes. Drafts are edited in the Designer; publishing validates and deploys a new immutable version.
        </p>
        {isAdmin && <button className="btn-primary" onClick={() => setCreating(true)}>+ Create Workflow</button>}
      </div>
      <div className="card">
        <DataTable
          columns={columns}
          page={list.data && { content: list.data, page: 0, size: list.data.length, totalElements: list.data.length, totalPages: 1 }}
          loading={list.isLoading}
          error={list.error}
          onRetry={() => list.refetch()}
          rowKey={(d) => d.key}
          emptyTitle="No workflow definitions"
        />
      </div>
      <CreateDefinitionDialog open={creating} onClose={() => setCreating(false)} onCreated={(key) => navigate(`/workflow/designer/${key}`)} />
      {historyKey && <VersionHistoryDialog workflowKey={historyKey} onClose={() => setHistoryKey(null)} />}
      <ConfirmDialog
        open={!!publishKey}
        title={`Publish ${publishKey}?`}
        message={
          <div className="space-y-2">
            <p>The draft is validated and deployed to the workflow engine as a new version. New submissions use it; running instances keep their version.</p>
            <InlineError error={publish.error} />
          </div>
        }
        confirmLabel="Publish"
        busy={publish.isPending}
        onConfirm={() => publishKey && publish.mutate(publishKey)}
        onClose={() => { setPublishKey(null); publish.reset(); }}
      />
    </div>
  );
}

const createSchema = z.object({
  key: z.string().regex(/^[A-Z][A-Z0-9_]*$/, 'UPPER_SNAKE_CASE, e.g. ITEM_FAST_TRACK_APPROVAL').max(100),
  name: z.string().trim().min(1, 'Name is required').max(200),
  type: z.enum(WORKFLOW_TYPES as [WorkflowType, ...WorkflowType[]]),
  description: z.string().max(2000).optional(),
});
type CreateValues = z.infer<typeof createSchema>;

function CreateDefinitionDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (key: string) => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { key: '', name: '', type: 'ITEM_LIFECYCLE', description: '' },
  });
  const create = useMutation({
    mutationFn: (v: CreateValues) => definitionsApi.create(v),
    onSuccess: (d) => {
      void qc.invalidateQueries({ queryKey: ['definitions'] });
      toast.success(`${d.definition.key} created from the default template`);
      reset();
      onClose();
      onCreated(d.definition.key);
    },
    onError: (e) => e instanceof ApiError && Object.entries(e.fieldErrors).forEach(([f, m]) => setError(f as keyof CreateValues, { message: m })),
  });
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Create Workflow"
      footer={<>
        <button className="btn-secondary" onClick={onClose}>Cancel</button>
        <button className="btn-primary" disabled={create.isPending} onClick={handleSubmit((v) => create.mutate(v))}>Create and open designer</button>
      </>}
    >
      <form className="space-y-4" onSubmit={handleSubmit((v) => create.mutate(v))}>
        <Field label="Workflow key" error={errors.key?.message} hint="Also the BPMN process id. Cannot be changed later." htmlFor="wf-key">
          <input id="wf-key" className="input font-mono" {...register('key')} />
        </Field>
        <Field label="Name" error={errors.name?.message} htmlFor="wf-name">
          <input id="wf-name" className="input" {...register('name')} />
        </Field>
        <Field label="Workflow type" error={errors.type?.message} htmlFor="wf-type"
          hint="Automatic selection: first approvals use Item lifecycle; changes to approved items prefer Item part change.">
          <select id="wf-type" className="input" {...register('type')}>
            {WORKFLOW_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </select>
        </Field>
        <Field label="Description" htmlFor="wf-desc">
          <textarea id="wf-desc" rows={2} className="input" {...register('description')} />
        </Field>
        <InlineError error={create.error} />
      </form>
    </Modal>
  );
}

function VersionHistoryDialog({ workflowKey, onClose }: { workflowKey: string; onClose: () => void }) {
  const navigate = useNavigate();
  const versions = useQuery({ queryKey: ['definition', workflowKey, 'versions'], queryFn: () => definitionsApi.versions(workflowKey) });
  return (
    <Modal open onClose={onClose} title={`${workflowKey} — version history`} size="lg">
      {versions.isLoading ? <LoadingState /> : (
        <table className="min-w-full divide-y divide-slate-200">
          <thead><tr><th className="th">Version</th><th className="th">Status</th><th className="th">Engine</th><th className="th">Published</th><th className="th">Created</th><th className="th" /></tr></thead>
          <tbody className="divide-y divide-slate-100">
            {(versions.data ?? []).map((v) => (
              <tr key={v.id}>
                <td className="td font-medium">v{v.versionNumber}</td>
                <td className="td"><StatusBadge status={v.status} /></td>
                <td className="td text-xs">{v.engine ? `${v.engine} · key ${v.engineProcessDefinitionKey} · v${v.engineVersion}` : '—'}</td>
                <td className="td text-xs">{v.publishedAt ? `${fmtDateTime(v.publishedAt)} by ${v.publishedBy}` : '—'}</td>
                <td className="td text-xs">{fmtDateTime(v.createdAt)} {v.createdBy && `by ${v.createdBy}`}</td>
                <td className="td">
                  <button className="btn-ghost btn-sm" onClick={() => { onClose(); navigate(`/workflow/designer/${workflowKey}?version=${v.versionNumber}`); }}>
                    View
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Modal>
  );
}
