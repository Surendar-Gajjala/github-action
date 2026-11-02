import { humanize } from './format';

const STYLES: Record<string, string> = {
  // lifecycle
  DRAFT: 'bg-slate-100 text-slate-700 ring-slate-300',
  IN_REVIEW: 'bg-sky-50 text-sky-700 ring-sky-300',
  APPROVED: 'bg-emerald-50 text-emerald-700 ring-emerald-300',
  REJECTED: 'bg-rose-50 text-rose-700 ring-rose-300',
  CHANGE_PENDING: 'bg-amber-50 text-amber-800 ring-amber-300',
  // workflow instances
  STARTING: 'bg-slate-100 text-slate-700 ring-slate-300',
  RUNNING: 'bg-sky-50 text-sky-700 ring-sky-300',
  COMPLETED: 'bg-emerald-50 text-emerald-700 ring-emerald-300',
  INCIDENT: 'bg-rose-100 text-rose-800 ring-rose-400',
  // tasks / change requests / definitions
  OPEN: 'bg-indigo-50 text-indigo-700 ring-indigo-300',
  SUBMITTED: 'bg-sky-50 text-sky-700 ring-sky-300',
  PUBLISHED: 'bg-emerald-50 text-emerald-700 ring-emerald-300',
  SUPERSEDED: 'bg-slate-100 text-slate-500 ring-slate-300',
  ACTIVE: 'bg-emerald-50 text-emerald-700 ring-emerald-300',
  INACTIVE: 'bg-slate-100 text-slate-500 ring-slate-300',
};

export function StatusBadge({ status, label }: { status: string; label?: string }) {
  const style = STYLES[status] ?? 'bg-slate-100 text-slate-700 ring-slate-300';
  return (
    <span
      data-testid="status-badge"
      className={`inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${style}`}
    >
      {label ?? humanize(status)}
    </span>
  );
}
