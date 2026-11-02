import type { StepResponse, StepStatus } from '../api/types';
import { formatDateTime } from '../lib/format';
import { StatusBadge, statusTone } from './StatusBadge';

const DOT: Record<string, string> = {
  success: 'bg-emerald-500 text-white',
  failed: 'bg-red-500 text-white',
  progress: 'bg-blue-500 text-white animate-pulse',
  waiting: 'bg-amber-400 text-white',
  neutral: 'bg-slate-200 text-slate-500',
};

function icon(status: StepStatus, order: number): string {
  switch (statusTone(status)) {
    case 'success':
      return '✓';
    case 'failed':
      return '!';
    default:
      return String(order);
  }
}

export function StepTracker({ steps }: { steps: StepResponse[] }) {
  return (
    <ol className="space-y-0" aria-label="Workflow steps">
      {steps.map((step, index) => (
        <li key={step.name} className="relative flex gap-4 pb-6 last:pb-0" data-testid={`step-${step.name}`}>
          {index < steps.length - 1 && (
            <span className="absolute left-4 top-8 -ml-px h-[calc(100%-2rem)] w-0.5 bg-slate-200" aria-hidden="true" />
          )}
          <span
            className={`relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold ${DOT[statusTone(step.status)]}`}
            aria-hidden="true"
          >
            {icon(step.status, step.order)}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <p className="font-medium text-slate-900">{step.label}</p>
              <StatusBadge status={step.status} />
              {step.attempts > 1 && <span className="text-xs text-slate-500">{step.attempts} attempts</span>}
            </div>
            <p className="mt-0.5 text-xs text-slate-500">
              {step.startedAt ? `Started ${formatDateTime(step.startedAt)}` : 'Not started'}
              {step.completedAt && ` · Finished ${formatDateTime(step.completedAt)}`}
              {step.externalReference && ` · Ref ${step.externalReference}`}
            </p>
            {step.errorMessage && (
              <p
                className={`mt-1 text-sm ${statusTone(step.status) === 'failed' ? 'text-red-700' : 'text-slate-600'}`}
                role={statusTone(step.status) === 'failed' ? 'alert' : undefined}
              >
                {step.errorMessage}
              </p>
            )}
          </div>
        </li>
      ))}
    </ol>
  );
}
