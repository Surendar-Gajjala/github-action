import { useQuery } from '@tanstack/react-query';
import { employeesApi, queryKeys } from '../../api/employees';
import { StatusBadge } from '../../components/StatusBadge';
import { Card, EmptyState, ErrorMessage, Field, SelectEmployeeHint } from '../../components/ui';
import { POLL_INTERVAL_MS, useWorkflowActive } from '../workflow/useWorkflow';

function isSafeGitHubUrl(url?: string): url is string {
  return !!url && /^https:\/\/github\.com\//.test(url);
}

export function GitHubOnboardingTab({ employeeId }: { employeeId: number | null }) {
  const active = useWorkflowActive(employeeId);
  const issue = useQuery({
    queryKey: queryKeys.github(employeeId ?? 0),
    queryFn: () => employeesApi.githubIssue(employeeId!),
    enabled: employeeId != null,
    refetchInterval: active ? POLL_INTERVAL_MS : false,
  });

  if (employeeId == null) return <SelectEmployeeHint />;
  if (issue.isPending) return <Card>Loading GitHub issue…</Card>;
  if (issue.isError) return <ErrorMessage error={issue.error} />;

  const data = issue.data;
  if (!data.available) {
    return <EmptyState>The GitHub onboarding issue has not been prepared yet.</EmptyState>;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        <Card title="GitHub onboarding issue" actions={<StatusBadge status={data.stepStatus} />}>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Field label="GitHub repository" value={data.repository} />
            <Field label="Requested access repository" value={data.requestedRepository} />
            <Field label="Issue title" value={data.issueTitle} />
            <Field label="Issue number" value={data.issueNumber ? `#${data.issueNumber}` : 'Not created yet'} />
            <Field label="Creation status" value={<StatusBadge status={data.status} />} />
            <Field
              label="GitHub issue URL"
              value={
                isSafeGitHubUrl(data.issueUrl) ? (
                  <a href={data.issueUrl} target="_blank" rel="noopener noreferrer" className="font-medium text-indigo-600 hover:underline">
                    {data.issueUrl}
                  </a>
                ) : (
                  '—'
                )
              }
            />
          </dl>
          {data.errorMessage && data.stepStatus !== 'SUCCESS' && (
            <p role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
              {data.errorMessage}
            </p>
          )}
          {isSafeGitHubUrl(data.issueUrl) && (
            <a
              href={data.issueUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-5 inline-flex items-center rounded-md bg-slate-900 px-3.5 py-2 text-sm font-semibold text-white hover:bg-slate-700"
            >
              Open GitHub issue ↗
            </a>
          )}
        </Card>

        <Card title="Issue description">
          {/* Shown as plain text (markdown source); never rendered as HTML. */}
          <pre className="max-h-[32rem] overflow-auto whitespace-pre-wrap rounded-md bg-slate-50 p-4 text-xs leading-relaxed text-slate-800">
            {data.issueBody}
          </pre>
        </Card>
      </div>

      <Card title="Onboarding checklist">
        <ul className="space-y-2 text-sm text-slate-700">
          {data.checklist.map((item) => (
            <li key={item} className="flex gap-2">
              <span aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 rounded border border-slate-300" />
              {item}
            </li>
          ))}
        </ul>
        <p className="mt-4 text-xs text-slate-500">
          Creating this issue does not grant repository access. Access must be provisioned separately with explicit
          authorization.
        </p>
      </Card>
    </div>
  );
}
