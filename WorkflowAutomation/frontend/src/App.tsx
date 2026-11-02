import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { employeesApi, queryKeys } from './api/employees';
import { LoginGate } from './components/LoginGate';
import { StatusBadge } from './components/StatusBadge';
import { Tabs, type TabDefinition } from './components/Tabs';
import { Button } from './components/ui';
import { EmailPreviewTab } from './features/email/EmailPreviewTab';
import { EmployeeDetailsTab } from './features/employee/EmployeeDetailsTab';
import { GitHubOnboardingTab } from './features/github/GitHubOnboardingTab';
import { EmployeeHistoryTab } from './features/history/EmployeeHistoryTab';
import { WorkflowStatusTab } from './features/workflow/WorkflowStatusTab';
import { useWorkflow } from './features/workflow/useWorkflow';

type TabKey = 'details' | 'workflow' | 'email' | 'github' | 'history';

const TABS: TabDefinition<TabKey>[] = [
  { key: 'details', label: 'Employee Details' },
  { key: 'workflow', label: 'Workflow Status' },
  { key: 'email', label: 'Email Preview' },
  { key: 'github', label: 'GitHub Onboarding' },
  { key: 'history', label: 'Employee History' },
];

export default function App() {
  return <LoginGate>{(user, signOut) => <Dashboard user={user} onSignOut={signOut} />}</LoginGate>;
}

/** Single page: every section is a tab sharing the currently selected employee. */
function Dashboard({ user, onSignOut }: { user: string; onSignOut: () => void }) {
  const [tab, setTab] = useState<TabKey>('details');
  const [employeeId, setEmployeeId] = useState<number | null>(null);

  function openEmployee(id: number, target: TabKey = 'workflow') {
    setEmployeeId(id);
    setTab(target);
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Employee Onboarding Automation</h1>
          <p className="text-sm text-slate-500">
            Submit once — the welcome email and GitHub onboarding issue are created automatically.
          </p>
        </div>
        <div className="flex items-center gap-3 text-sm text-slate-600">
          <span>
            Signed in as <strong>{user}</strong>
          </span>
          <Button variant="secondary" onClick={onSignOut}>
            Sign out
          </Button>
        </div>
      </header>

      <SelectedEmployeeBar employeeId={employeeId} onClear={() => setEmployeeId(null)} />

      <Tabs tabs={TABS} active={tab} onChange={setTab} />

      <main id={`panel-${tab}`} role="tabpanel" aria-labelledby={`tab-${tab}`} className="pt-6">
        {tab === 'details' && <EmployeeDetailsTab onSubmitted={(created) => openEmployee(created.employeeId)} />}
        {tab === 'workflow' && <WorkflowStatusTab employeeId={employeeId} />}
        {tab === 'email' && <EmailPreviewTab employeeId={employeeId} />}
        {tab === 'github' && <GitHubOnboardingTab employeeId={employeeId} />}
        {tab === 'history' && <EmployeeHistoryTab onOpen={(id) => openEmployee(id)} />}
      </main>
    </div>
  );
}

function SelectedEmployeeBar({ employeeId, onClear }: { employeeId: number | null; onClear: () => void }) {
  const employee = useQuery({
    queryKey: queryKeys.employee(employeeId ?? 0),
    queryFn: () => employeesApi.get(employeeId!),
    enabled: employeeId != null,
  });
  const workflow = useWorkflow(employeeId);

  if (employeeId == null || !employee.data) return null;
  const e = employee.data.employee;
  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-indigo-100 bg-indigo-50 px-4 py-2 text-sm">
      <span className="text-indigo-900">
        <span className="font-mono text-xs">{e.employeeNumber}</span> · <strong>{e.fullName}</strong> · {e.jobTitle},{' '}
        {e.department}
      </span>
      <span className="flex items-center gap-3">
        <StatusBadge status={workflow.data?.overallStatus ?? employee.data.workflow.overallStatus} />
        <button type="button" className="text-xs font-medium text-indigo-700 hover:underline" onClick={onClear}>
          Clear selection
        </button>
      </span>
    </div>
  );
}
