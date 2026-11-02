import { AlertTriangle, RotateCw } from "lucide-react";
import { normaliseError } from "@/services/httpClient";

export function ErrorState({ error, onRetry, compact }: { error: unknown; onRetry?: () => void; compact?: boolean }) {
  const e = normaliseError(error);
  return (
    <div role="alert" className={`rounded-md border border-red-200 bg-red-50 text-sm text-red-800 ${compact ? "p-3" : "m-4 p-4"}`}>
      <div className="flex items-start gap-2">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <div className="min-w-0 flex-1">
          <p className="font-medium">
            {e.status ? `HTTP ${e.status} · ` : ""}
            {e.message}
          </p>
          {e.hint && <p className="mt-1 text-red-700">{e.hint}</p>}
          {e.retryAfterSeconds ? <p className="mt-1 text-red-700">Retry after {e.retryAfterSeconds}s.</p> : null}
        </div>
        {onRetry && (
          <button type="button" className="btn-secondary shrink-0" onClick={onRetry}>
            <RotateCw className="h-3.5 w-3.5" aria-hidden /> Retry
          </button>
        )}
      </div>
    </div>
  );
}
