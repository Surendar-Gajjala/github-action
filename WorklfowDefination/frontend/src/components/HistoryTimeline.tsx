import type { WorkflowEvent } from '../api/types';
import { fmtDateTime, humanize, shortId } from './format';
import { StatusBadge } from './StatusBadge';

const DOT: Record<string, string> = {
  REVISION_RELEASED: 'bg-emerald-500',
  APPROVAL_COMPLETED: 'bg-emerald-400',
  REQUEST_REJECTED: 'bg-rose-500',
  WORKFLOW_FAILED: 'bg-rose-600',
  WORKFLOW_STARTED: 'bg-indigo-500',
  ITEM_SUBMITTED: 'bg-indigo-400',
  APPROVAL_REQUESTED: 'bg-sky-400',
  PARTS_ADDED: 'bg-amber-400',
  PARTS_REMOVED: 'bg-amber-500',
};

export function HistoryTimeline({ events, showItem }: { events: WorkflowEvent[]; showItem?: boolean }) {
  if (!events.length) return <p className="py-4 text-slate-500">No history yet.</p>;
  return (
    <ol className="relative ml-2 border-l border-slate-200">
      {events.map((e) => (
        <li key={e.id} className="mb-4 ml-4">
          <span className={`absolute -left-1.5 mt-1.5 h-3 w-3 rounded-full ring-2 ring-white ${DOT[e.action] ?? 'bg-slate-400'}`} />
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-medium text-slate-900">{humanize(e.action)}</span>
            {e.toStatus && <StatusBadge status={e.toStatus} />}
            <span className="text-xs text-slate-500">
              {fmtDateTime(e.timestamp)} · by <span className="font-medium text-slate-700">{e.actor}</span>
            </span>
          </div>
          {e.summary && <p className="mt-0.5 text-slate-600">{e.summary}</p>}
          <p className="mt-0.5 text-xs text-slate-400">
            {showItem && e.itemNumber && <>Item {e.itemNumber} · </>}
            {e.businessKey && <>Key {e.businessKey} · </>}
            {e.workflowInstanceId && <>Instance {shortId(e.workflowInstanceId)}</>}
            {e.workflowDefinitionKey && !e.workflowInstanceId && <>Definition {e.workflowDefinitionKey}</>}
          </p>
        </li>
      ))}
    </ol>
  );
}
