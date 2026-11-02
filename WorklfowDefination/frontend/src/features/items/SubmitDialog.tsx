import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { definitionsApi, itemsApi } from '../../api/endpoints';
import type { ItemDetail } from '../../api/types';
import { Modal } from '../../components/Overlay';
import { InlineError, LoadingState } from '../../components/States';
import { useToast } from '../../components/Toast';
import { ChangeDiffView } from '../workflow/ChangeDiffView';

export function SubmitDialog({ item, open, onClose }: { item: ItemDetail; open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [workflowKey, setWorkflowKey] = useState('');
  const definitions = useQuery({ queryKey: ['definitions'], queryFn: definitionsApi.list, enabled: open });
  const diff = useQuery({ queryKey: ['item', item.id, 'changes'], queryFn: () => itemsApi.changes(item.id), enabled: open });
  const usable = (definitions.data ?? []).filter((d) => d.active && d.publishedVersion);

  const submit = useMutation({
    mutationFn: () => itemsApi.submit(item.id, { workflowKey: workflowKey || undefined, version: item.version }),
    onSuccess: (instance) => {
      toast.success(`${item.itemNumber} submitted — ${instance.definitionKey} v${instance.definitionVersion} started` +
        (instance.managerApprovalRequired ? ' (manager approval required)' : ''));
      void qc.invalidateQueries({ queryKey: ['items'] });
      void qc.invalidateQueries({ queryKey: ['item', item.id] });
      void qc.invalidateQueries({ queryKey: ['instances'] });
      void qc.invalidateQueries({ queryKey: ['tasks'] });
      onClose();
    },
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title={`Submit ${item.itemNumber} for approval`}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancel</button>
          <button className="btn-primary" disabled={submit.isPending || usable.length === 0} onClick={() => submit.mutate()}>
            {submit.isPending ? 'Submitting…' : 'Submit for approval'}
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="text-slate-600">
          Submitting starts the lifecycle workflow for{' '}
          <b>{item.currentChangeRequest?.number ?? 'the open change request'}</b> (revision {item.revision}). The item becomes{' '}
          <b>IN REVIEW</b> and can no longer be edited until the workflow completes.
        </p>
        <div>
          <label className="label" htmlFor="workflowKey">Workflow</label>
          <select id="workflowKey" className="input" value={workflowKey} onChange={(e) => setWorkflowKey(e.target.value)}>
            <option value="">Automatic — {item.approvedRevision ? 'part change approval' : 'item lifecycle approval'}</option>
            {usable.map((d) => (
              <option key={d.key} value={d.key}>
                {d.name} ({d.key} v{d.publishedVersion})
              </option>
            ))}
          </select>
          {definitions.data && usable.length === 0 && (
            <p className="mt-2 rounded-md bg-amber-50 px-3 py-2 text-amber-800">
              No workflow is published and active. An administrator must publish one in the Workflow tab first.
            </p>
          )}
        </div>
        <div>
          <p className="label">Changes to be reviewed</p>
          {diff.isLoading ? <LoadingState /> : diff.data && <ChangeDiffView diff={diff.data} />}
        </div>
        <InlineError error={submit.error} />
      </div>
    </Modal>
  );
}
