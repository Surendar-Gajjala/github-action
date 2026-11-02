import { companyService } from "./rapidRatingsCompanyService";
import { portfolioService } from "./rapidRatingsPortfolioService";
import { periodService } from "./rapidRatingsPeriodService";
import { ratingService } from "./rapidRatingsRatingService";
import { outreachService } from "./rapidRatingsOutreachService";
import { getAuthMode } from "./config";
import { normaliseError } from "./httpClient";
import { loadMappingConfiguration } from "@/schemas/mappingSchema";
import { targetSchemaRegistry } from "@/schemas/targetSchemaRegistry";
import { transformRecord } from "@/utils/transform";
import { createStore } from "@/utils/store";
import { storage } from "@/utils/storage";
import type { AuthMode } from "@/types/api";
import type { ResourceKey } from "@/types/schema";

/**
 * Manual retrieval ("Sync Now"). All operations are read-only GETs, so retries are safe.
 * Records are processed transiently (mapping + validation in memory) and then discarded.
 * Only run METADATA (counts, times, error summaries) is kept, in browser storage, as temporary demo history.
 * There is no A1 write target, so "A1 sync" is always reported as not performed.
 */
export type RunStatus = "running" | "succeeded" | "partial" | "failed";

export interface SyncRun {
  id: string;
  resource: ResourceKey;
  mode: AuthMode;
  startedAt: string;
  endedAt?: string;
  status: RunStatus;
  recordsRetrieved: number;
  recordsProcessed: number;
  /** Records that failed mapping validation (transient processing) */
  recordsRejected: number;
  failedRequests: number;
  errorSummary?: string;
  scope: string;
  a1Sync: "not-performed";
  retryOf?: string;
}

export interface Progress {
  done: number;
  total?: number;
  label: string;
}

const HISTORY_KEY = "rr.syncHistory";
const MAX_RUNS = 100;
const CONCURRENCY = 4;

export const syncStore = createStore<{ runs: SyncRun[]; inFlight: Partial<Record<ResourceKey, Progress>> }>({
  runs: storage.get<SyncRun[]>(HISTORY_KEY, []).map((r) => (r.status === "running" ? { ...r, status: "failed", errorSummary: "Interrupted (page was closed or reloaded)." } : r)),
  inFlight: {},
});

const persist = () => storage.set(HISTORY_KEY, syncStore.get().runs.slice(0, MAX_RUNS));

function upsertRun(run: SyncRun) {
  syncStore.set((s) => ({ ...s, runs: [run, ...s.runs.filter((r) => r.id !== run.id)].slice(0, MAX_RUNS) }));
  persist();
}

function setProgress(resource: ResourceKey, p: Progress | undefined) {
  syncStore.set((s) => {
    const inFlight = { ...s.inFlight };
    if (p) inFlight[resource] = p;
    else delete inFlight[resource];
    return { ...s, inFlight };
  });
}

export function isRunning(resource: ResourceKey) {
  return !!syncStore.get().inFlight[resource];
}

export function clearHistory() {
  syncStore.set((s) => ({ ...s, runs: s.runs.filter((r) => r.status === "running") }));
  persist();
}

export async function runSync(resource: ResourceKey, retryOf?: string): Promise<SyncRun> {
  if (isRunning(resource)) throw new Error(`A ${resource} sync is already in progress.`);
  setProgress(resource, { done: 0, label: "Starting…" });

  const run: SyncRun = {
    id: `${resource}-${Date.now()}`,
    resource,
    mode: getAuthMode(),
    startedAt: new Date().toISOString(),
    status: "running",
    recordsRetrieved: 0,
    recordsProcessed: 0,
    recordsRejected: 0,
    failedRequests: 0,
    scope: "",
    a1Sync: "not-performed",
    retryOf,
  };
  upsertRun(run);

  const mappings = loadMappingConfiguration().mappings.filter((m) => m.sourceResource === resource);
  const errors: string[] = [];
  const process = (records: unknown[]) => {
    run.recordsRetrieved += records.length;
    if (!mappings.some((m) => m.status === "mapped")) return; // nothing to process without mappings
    for (const r of records) {
      const t = transformRecord(r, mappings, targetSchemaRegistry);
      if (t.ok) run.recordsProcessed++;
      else run.recordsRejected++;
    }
  };

  try {
    const onProgress = (label: string) => (done: number, total: number) => setProgress(resource, { done, total, label });
    switch (resource) {
      case "companies":
        run.scope = "All companies (following provider pages)";
        process(await companyService.listAll({}, onProgress("Companies")));
        break;
      case "portfolios":
        run.scope = "All portfolios";
        process(await portfolioService.listAll(onProgress("Portfolios")));
        break;
      case "outreaches":
        run.scope = "All outreach requests (cursor pages of 100)";
        process(await outreachService.listAll(onProgress("Outreach requests")));
        break;
      case "periods":
      case "ratings": {
        setProgress(resource, { done: 0, label: "Listing companies…" });
        const companies = await companyService.listAll();
        run.scope = `${resource} for ${companies.length} companies`;
        const svc = resource === "periods" ? periodService : ratingService;
        let done = 0;
        const queue = [...companies];
        const worker = async () => {
          for (let c = queue.shift(); c; c = queue.shift()) {
            try {
              process(await svc.listAll(c.id));
            } catch (e) {
              run.failedRequests++;
              if (errors.length < 5) errors.push(`${c.id}: ${normaliseError(e).message}`);
            }
            setProgress(resource, { done: ++done, total: companies.length, label: "Companies processed" });
          }
        };
        await Promise.all(Array.from({ length: CONCURRENCY }, worker));
        break;
      }
    }
    run.status = run.failedRequests > 0 ? (run.recordsRetrieved > 0 ? "partial" : "failed") : "succeeded";
    const notes = [...errors];
    if (!mappings.some((m) => m.status === "mapped")) notes.push("No mappings configured for this resource; records were retrieved but not processed.");
    if (run.recordsRejected) notes.push(`${run.recordsRejected} record(s) failed mapping validation.`);
    run.errorSummary = notes.join(" · ") || undefined;
  } catch (e) {
    const err = normaliseError(e);
    run.status = "failed";
    run.failedRequests++;
    run.errorSummary = [err.status ? `HTTP ${err.status}` : err.kind, err.message, err.hint].filter(Boolean).join(": ");
  } finally {
    run.endedAt = new Date().toISOString();
    upsertRun({ ...run });
    setProgress(resource, undefined);
  }
  return run;
}

export function lastRun(resource: ResourceKey, status: RunStatus[] = ["succeeded", "partial"]) {
  return syncStore.get().runs.find((r) => r.resource === resource && status.includes(r.status));
}
