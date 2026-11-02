import type { ArtifactStatus, StepStatus, WorkflowStatus } from '../api/types';

type AnyStatus = StepStatus | WorkflowStatus | ArtifactStatus;

const LABELS: Record<AnyStatus, string> = {
  PENDING: 'Pending',
  IN_PROGRESS: 'In Progress',
  SUCCESS: 'Success',
  FAILED: 'Failed',
  FAILED_UNCERTAIN: 'Failed (uncertain)',
  AWAITING_APPROVAL: 'Awaiting Approval',
  SKIPPED: 'Skipped',
  COMPLETED: 'Completed',
  DRAFT: 'Draft',
  APPROVED: 'Approved',
  SENDING: 'Sending',
  SENT: 'Sent',
  SEND_FAILED: 'Send Failed',
  CREATED: 'Created',
};

const STYLES: Record<string, string> = {
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  failed: 'bg-red-50 text-red-700 ring-red-600/20',
  progress: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  waiting: 'bg-amber-50 text-amber-800 ring-amber-600/20',
  neutral: 'bg-slate-100 text-slate-600 ring-slate-500/20',
};

export function statusTone(status: AnyStatus): keyof typeof STYLES {
  switch (status) {
    case 'SUCCESS':
    case 'COMPLETED':
    case 'SENT':
    case 'CREATED':
    case 'APPROVED':
      return 'success';
    case 'FAILED':
    case 'FAILED_UNCERTAIN':
    case 'SEND_FAILED':
      return 'failed';
    case 'IN_PROGRESS':
    case 'SENDING':
      return 'progress';
    case 'AWAITING_APPROVAL':
    case 'DRAFT':
      return 'waiting';
    default:
      return 'neutral';
  }
}

export function StatusBadge({ status }: { status?: AnyStatus | null }) {
  if (!status) {
    return <span className="text-sm text-slate-400">—</span>;
  }
  return (
    <span
      className={`inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${STYLES[statusTone(status)]}`}
    >
      {LABELS[status] ?? status}
    </span>
  );
}
