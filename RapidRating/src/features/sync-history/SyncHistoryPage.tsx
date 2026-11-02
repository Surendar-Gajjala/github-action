import { useState } from "react";
import { Info, Play, RotateCw, Trash2 } from "lucide-react";
import { clearHistory, runSync, syncStore, type RunStatus, type SyncRun } from "@/services/syncService";
import { RESOURCES } from "@/schemas/sourceSchemaRegistry";
import { useStore } from "@/utils/store";
import { DemoBanner, PageHeader } from "@/components/common/PageHeader";
import { StatusBadge, type Tone } from "@/components/common/StatusBadge";
import { EmptyState } from "@/components/common/EmptyState";
import { DataTable } from "@/components/tables/DataTable";
import { fmtDateTime } from "@/utils/format";
import type { ResourceKey } from "@/types/schema";

const STATUS_TONE: Record<RunStatus, Tone> = { running: "blue", succeeded: "green", partial: "amber", failed: "red" };
const STATUS_LABEL: Record<RunStatus, string> = { running: "Running", succeeded: "Fetch succeeded", partial: "Partial fetch", failed: "Failed" };

export function SyncHistoryPage() {
  const { runs, inFlight } = useStore(syncStore);
  const [error, setError] = useState<string>();

  const start = (resource: ResourceKey, retryOf?: string) => {
    setError(undefined);
    runSync(resource, retryOf).catch((e: Error) => setError(e.message));
  };

  return (
    <>
      <PageHeader title="Sync History" description="Manual retrieval from RapidRatings with transient processing through the current schema mapping." />
      <DemoBanner />

      <div className="mb-4 flex gap-2 rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900">
        <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <div>
          <p>
            <strong>Temporary demo history.</strong> Runs are stored in this browser only: they're not durable, not shared between users, and contain metadata only (counts, times, errors). No provider data is saved.
          </p>
          <p className="mt-1">
            <strong>"Fetch succeeded" is not an A1 sync.</strong> Records are retrieved, mapped and validated in memory, then discarded. No A1 write target is configured, so A1 synchronization is never performed here.
          </p>
        </div>
      </div>

      <div className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        {RESOURCES.map((r) => {
          const p = inFlight[r.key];
          const pct = p?.total ? Math.round((p.done / p.total) * 100) : undefined;
          return (
            <div key={r.key} className="card p-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-sm font-medium text-slate-800">{r.label}</span>
                <button type="button" className="btn-primary px-2 py-1 text-xs" disabled={!!p} onClick={() => start(r.key)} aria-label={`Sync ${r.label} now`}>
                  <Play className="h-3.5 w-3.5" aria-hidden /> {p ? "Running" : "Sync now"}
                </button>
              </div>
              {p ? (
                <div aria-live="polite">
                  <div className="h-1.5 overflow-hidden rounded bg-slate-100">
                    <div className="h-full bg-blue-600 transition-all" style={{ width: `${pct ?? 5}%` }} />
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    {p.label}: {p.done.toLocaleString()}
                    {p.total !== undefined ? ` / ${p.total.toLocaleString()}` : ""}
                  </p>
                </div>
              ) : (
                <p className="text-xs text-slate-500">{r.key === "periods" || r.key === "ratings" ? "Fetches every company's history (1 request per page per company)." : r.endpoint}</p>
              )}
            </div>
          );
        })}
      </div>
      {error && <p className="mb-4 text-sm text-red-700">{error}</p>}

      <div className="card-flush">
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-4 sm:px-8">
          <h2 className="text-sm font-semibold text-slate-800">Runs ({runs.length})</h2>
          <button type="button" className="btn-ghost text-xs" onClick={clearHistory} disabled={!runs.length}>
            <Trash2 className="h-3.5 w-3.5" aria-hidden /> Clear history
          </button>
        </div>
        {runs.length === 0 ? (
          <EmptyState title="No sync runs yet" description="Use “Sync now” on a resource to retrieve its data and record the result here." />
        ) : (
          <DataTable<SyncRun>
            columns={[
              { key: "resource", header: "Resource", render: (r) => <span className="font-medium capitalize">{r.resource}</span> },
              { key: "startedAt", header: "Start", render: (r) => fmtDateTime(r.startedAt) },
              { key: "endedAt", header: "End", render: (r) => fmtDateTime(r.endedAt) },
              { key: "status", header: "Status", render: (r) => <StatusBadge tone={STATUS_TONE[r.status]}>{STATUS_LABEL[r.status]}</StatusBadge> },
              { key: "recordsRetrieved", header: "Retrieved", align: "right" },
              { key: "recordsProcessed", header: "Processed", align: "right", render: (r) => <span title={`${r.recordsRejected} rejected by mapping validation`}>{r.recordsProcessed.toLocaleString()}</span> },
              { key: "a1Sync", header: "A1 sync", render: () => <StatusBadge tone="slate">Not performed</StatusBadge> },
              { key: "mode", header: "Mode", render: (r) => <StatusBadge tone={r.mode === "mock" ? "violet" : "slate"}>{r.mode === "mock" ? "demo" : r.mode}</StatusBadge> },
              { key: "errorSummary", header: "Error summary", className: "max-w-md whitespace-normal text-xs", render: (r) => r.errorSummary ?? "—" },
              {
                key: "retry",
                header: <span className="sr-only">Retry</span>,
                render: (r) =>
                  r.status === "failed" || r.status === "partial" ? (
                    <button type="button" className="btn-ghost px-2 py-1 text-xs" disabled={!!inFlight[r.resource]} onClick={() => start(r.resource, r.id)}>
                      <RotateCw className="h-3.5 w-3.5" aria-hidden /> Retry
                    </button>
                  ) : null,
              },
            ]}
            rows={runs}
            rowKey={(r) => r.id}
          />
        )}
      </div>
    </>
  );
}
