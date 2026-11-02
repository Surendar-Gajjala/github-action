import type { ReactNode } from 'react';
import { errorMessage } from '../api/client';

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-10 text-slate-500">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-indigo-600" />
      {label}
    </div>
  );
}

export function EmptyState({ title, hint, action }: { title: string; hint?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-10 text-center">
      <div className="text-2xl text-slate-300">◇</div>
      <p className="font-medium text-slate-700">{title}</p>
      {hint && <p className="max-w-md text-slate-500">{hint}</p>}
      {action}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div role="alert" className="m-4 rounded-md border border-rose-200 bg-rose-50 p-4 text-rose-800">
      <p className="font-medium">Something went wrong</p>
      <p className="mt-1 whitespace-pre-line">{errorMessage(error)}</p>
      {onRetry && (
        <button className="btn-secondary btn-sm mt-3" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

export function InlineError({ error }: { error: unknown }) {
  if (!error) return null;
  return <p role="alert" className="whitespace-pre-line rounded-md bg-rose-50 px-3 py-2 text-rose-700">{errorMessage(error)}</p>;
}

export function Field({ label, error, children, hint, htmlFor }: {
  label: string;
  error?: string;
  hint?: ReactNode;
  htmlFor?: string;
  children: ReactNode;
}) {
  return (
    <div>
      <label className="label" htmlFor={htmlFor}>{label}</label>
      {children}
      {hint && !error && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
    </div>
  );
}
