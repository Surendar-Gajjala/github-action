import { lazy, Suspense } from 'react';
import { NavLink, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { tasksApi } from '../../api/endpoints';
import { LoadingState } from '../../components/States';
import { DefinitionsSection } from './DefinitionsSection';
import { HistorySection } from './HistorySection';
import { InstancesSection } from './InstancesSection';
import { LifecycleSection } from './LifecycleSection';
import { TasksSection } from './TasksSection';

// bpmn-js is large; load the designer only when it is opened.
const DesignerSection = lazy(() => import('./DesignerSection').then((m) => ({ default: m.DesignerSection })));

const SECTIONS = [
  { key: 'definitions', label: 'Definitions' },
  { key: 'designer', label: 'Designer' },
  { key: 'lifecycle', label: 'Lifecycle States' },
  { key: 'tasks', label: 'Approval Tasks' },
  { key: 'instances', label: 'Running Instances' },
  { key: 'history', label: 'History' },
] as const;

export function WorkflowPage() {
  const { section = 'definitions', key } = useParams();
  const openTasks = useQuery({
    queryKey: ['tasks', 'count'],
    queryFn: () => tasksApi.list({ status: 'OPEN', mine: true, size: 1 }),
    refetchInterval: 5000,
  });
  const count = openTasks.data?.totalElements ?? 0;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">Workflow</h1>
        <p className="text-slate-500">Workflow definitions, lifecycle configuration, approvals and execution.</p>
      </div>
      <nav aria-label="Workflow sections" className="flex flex-wrap gap-1 rounded-lg bg-slate-100 p-1">
        {SECTIONS.map((s) => (
          <NavLink
            key={s.key}
            to={`/workflow/${s.key}`}
            className={() =>
              `rounded-md px-3 py-1.5 text-sm font-medium ${section === s.key ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`
            }
          >
            {s.label}
            {s.key === 'tasks' && count > 0 && (
              <span className="ml-1.5 rounded-full bg-indigo-600 px-1.5 py-0.5 text-[10px] font-semibold text-white">{count}</span>
            )}
          </NavLink>
        ))}
      </nav>
      {section === 'definitions' && <DefinitionsSection />}
      {section === 'designer' && (
        <Suspense fallback={<LoadingState label="Loading designer…" />}>
          <DesignerSection workflowKey={key} />
        </Suspense>
      )}
      {section === 'lifecycle' && <LifecycleSection />}
      {section === 'tasks' && <TasksSection />}
      {section === 'instances' && <InstancesSection instanceId={key} />}
      {section === 'history' && <HistorySection />}
    </div>
  );
}
