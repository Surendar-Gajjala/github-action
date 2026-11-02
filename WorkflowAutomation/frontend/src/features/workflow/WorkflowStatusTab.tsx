import { useMutation, useQueryClient } from '@tanstack/react-query';
import { employeesApi, queryKeys } from '../../api/employees';
import { StatusBadge } from '../../components/StatusBadge';
import { StepTracker } from '../../components/StepTracker';
import { Button, Card, ErrorMessage, SelectEmployeeHint } from '../../components/ui';
import { formatDateTime } from '../../lib/format';
import { useWorkflow } from './useWorkflow';

export function WorkflowStatusTab({ employeeId }: { employeeId: number | null }) {
  const queryClient = useQueryClient();
  const workflow = useWorkflow(employeeId);

  const retry = useMutation({
    mutationFn: (force: boolean) => employeesApi.retry(employeeId!, force),
    onSuccess: (data) => {
      queryClient.setQueryData(queryKeys.workflow(employeeId!), data);
      queryClient.invalidateQueries({ queryKey: ['employees'] });
    },
  });

  if (employeeId == null) return <SelectEmployeeHint />;
  if (workflow.isPending) return <Card>Loading workflow…</Card>;
  if (workflow.isError) return <ErrorMessage error={workflow.error} />;

  const data = workflow.data;
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="lg:col-span-2">
        <Card
          title={`Workflow #${data.workflowId}`}
          actions={
            <div className="flex items-center gap-2">
              <StatusBadge status={data.overallStatus} />
              {workflow.isFetching && <span className="text-xs text-slate-400">refreshing…</span>}
            </div>
          }
        >
          <StepTracker steps={data.steps} />

          {data.lastError && data.overallStatus !== 'COMPLETED' && (
            <div role="alert" className="mt-5 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
              <strong className="block">Last error</strong>
              {data.lastError}
            </div>
          )}

          {(data.retryable || data.requiresForcedRetry) && (
            <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-4">
              {data.retryable && (
                <Button onClick={() => retry.mutate(false)} disabled={retry.isPending}>
                  {retry.isPending ? 'Retrying…' : 'Retry failed steps'}
                </Button>
              )}
              {data.requiresForcedRetry && (
                <>
                  <Button
                    variant="danger"
                    disabled={retry.isPending}
                    title="Only administrators can force a retry"
                    onClick={() => {
                      if (window.confirm('Email delivery status is unknown. Forcing a retry may send a duplicate email. Continue?')) {
                        retry.mutate(true);
                      }
                    }}
                  >
                    Force retry (admin)
                  </Button>
                  <span className="text-xs text-slate-500">Email delivery is uncertain; an administrator must confirm.</span>
                </>
              )}
            </div>
          )}
          {retry.isError && (
            <div className="mt-3">
              <ErrorMessage error={retry.error} />
            </div>
          )}
        </Card>
      </div>

      <Card title="Execution history">
        <dl className="mb-4 grid grid-cols-2 gap-3 text-sm">
          <div>
            <dt className="text-xs text-slate-500">Started</dt>
            <dd>{formatDateTime(data.startedAt)}</dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Completed</dt>
            <dd>{formatDateTime(data.completedAt)}</dd>
          </div>
          <div className="col-span-2">
            <dt className="text-xs text-slate-500">Email approval</dt>
            <dd>{data.emailApprovalRequired ? 'Required before sending' : 'Automatic sending'}</dd>
          </div>
        </dl>
        <ol className="max-h-96 space-y-3 overflow-y-auto text-sm">
          {[...data.history].reverse().map((entry, index) => (
            <li key={index} className="border-l-2 border-slate-200 pl-3">
              <p className="font-medium text-slate-800">{entry.action.replaceAll('_', ' ').toLowerCase()}</p>
              {entry.details && <p className="text-slate-600">{entry.details}</p>}
              <p className="text-xs text-slate-400">
                {formatDateTime(entry.at)} · {entry.actor}
              </p>
            </li>
          ))}
        </ol>
      </Card>
    </div>
  );
}
