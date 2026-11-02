import { useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Camera, Radar } from "lucide-react";
import { RESOURCES, compareWithPrevious, declaredSchema, saveSnapshot, schemaStore, sourceFieldsFor } from "@/schemas/sourceSchemaRegistry";
import { discoverSchema } from "./discoverSchema";
import { useStore } from "@/utils/store";
import { DemoBanner, PageHeader } from "@/components/common/PageHeader";
import { ErrorState } from "@/components/common/ErrorState";
import { EmptyState } from "@/components/common/EmptyState";
import { JsonViewer } from "@/components/common/JsonViewer";
import { StatusBadge } from "@/components/common/StatusBadge";
import { DataTable } from "@/components/tables/DataTable";
import { cx } from "@/utils/format";
import type { ResourceKey, SchemaField } from "@/types/schema";

export function SchemaExplorerPage() {
  const [resource, setResource] = useState<ResourceKey>("companies");
  const [rrid, setRrid] = useState("");
  const state = useStore(schemaStore);
  const discover = useMutation({ mutationFn: () => discoverSchema(resource, rrid.trim() || undefined) });

  const meta = RESOURCES.find((r) => r.key === resource)!;
  const declared = declaredSchema(resource);
  const live = state.discovered[resource];
  const current = live ?? sourceFieldsFor(resource);
  const sample = state.samples[resource];
  const comparison = useMemo(() => (live ? compareWithPrevious(resource) : null), [live, resource, state.snapshots]);

  // For declared resources, flag live fields that the official schema does not declare
  const declaredPaths = new Set(declared?.fields.map((f) => f.path));

  return (
    <>
      <PageHeader title="Schema Explorer" description="Inspect RapidRatings response structures: fields declared by the official spec vs fields inferred from real responses." />
      <DemoBanner />

      <div className="grid gap-4 lg:grid-cols-[14rem_1fr]">
        <nav className="card h-fit p-2" aria-label="Resources">
          {RESOURCES.map((r) => (
            <button
              key={r.key}
              type="button"
              onClick={() => (setResource(r.key), discover.reset())}
              className={cx("flex w-full items-center justify-between rounded-md px-2.5 py-2 text-left text-sm", resource === r.key ? "bg-blue-50 font-medium text-blue-700" : "text-slate-600 hover:bg-slate-50")}
            >
              {r.label}
              <StatusBadge tone={r.key === "outreaches" ? "blue" : "amber"}>{r.key === "outreaches" ? "declared" : "inferred"}</StatusBadge>
            </button>
          ))}
        </nav>

        <div className="space-y-4">
          <div className="card p-4">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-slate-900">{meta.label}</h2>
                <p className="font-mono text-xs text-slate-500">{meta.endpoint}</p>
                <p className="mt-1 text-xs text-slate-500">
                  {declared ? `Official schema: ${declared.origin}` : "No official schema is published for this host (rridata /openapi.json returns 403). Fields are inferred from real responses."}
                </p>
              </div>
              <div className="flex flex-wrap items-end gap-2">
                {(resource === "periods" || resource === "ratings") && (
                  <div className="w-36">
                    <label htmlFor="se-rrid" className="label">
                      Sample RRID (optional)
                    </label>
                    <input id="se-rrid" className="input font-mono" placeholder="first company" value={rrid} onChange={(e) => setRrid(e.target.value)} />
                  </div>
                )}
                <button type="button" className="btn-primary" onClick={() => discover.mutate()} disabled={discover.isPending}>
                  <Radar className="h-4 w-4" aria-hidden /> {discover.isPending ? "Discovering…" : "Discover from live API"}
                </button>
                <button type="button" className="btn-secondary" disabled={!live} onClick={() => saveSnapshot(resource)} title="Save current field list (names and types only) as the comparison baseline">
                  <Camera className="h-4 w-4" aria-hidden /> Save snapshot
                </button>
              </div>
            </div>
            {discover.error && (
              <div className="mt-3">
                <ErrorState error={discover.error} compact />
              </div>
            )}
            {discover.data && discover.data.records === 0 && (
              <p className="mt-3 rounded-md bg-amber-50 p-2 text-sm text-amber-800">The API returned 0 records, so no fields could be inferred. {declared ? "The declared schema below still applies." : ""}</p>
            )}
          </div>

          {comparison && (
            <div className="card p-4">
              <h3 className="text-sm font-semibold text-slate-800">Changes vs previous</h3>
              <p className="mb-2 text-xs text-slate-500">Compared against: {comparison.against}</p>
              {comparison.diff.added.length + comparison.diff.removed.length + comparison.diff.typeChanged.length === 0 ? (
                <p className="text-sm text-emerald-700">No added, removed or type-changed fields.</p>
              ) : (
                <ul className="space-y-1 text-sm">
                  {comparison.diff.added.map((f) => (
                    <li key={`a-${f.path}`}>
                      <StatusBadge tone="green">added</StatusBadge> <code>{f.path}</code> ({f.types.join(" | ")})
                    </li>
                  ))}
                  {comparison.diff.removed.map((f) => (
                    <li key={`r-${f.path}`}>
                      <StatusBadge tone="red">removed</StatusBadge> <code>{f.path}</code>
                    </li>
                  ))}
                  {comparison.diff.typeChanged.map((t) => (
                    <li key={`t-${t.path}`}>
                      <StatusBadge tone="amber">type changed</StatusBadge> <code>{t.path}</code>: {t.before.join("|")} → {t.after.join("|")}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {declared && (
            <FieldTable title="Declared fields (official OpenAPI schema)" subtitle={declared.origin} fields={declared.fields} />
          )}

          {(!declared || live) && (
            <FieldTable
              title={live ? "Inferred fields (live discovery)" : "Inferred fields (baseline)"}
              subtitle={`${current.origin} · ${new Date(current.discoveredAt).toLocaleString()}`}
              fields={live ? live.fields : current.fields}
              flag={declared ? (f) => (!declaredPaths.has(f.path) ? "not in official schema" : undefined) : undefined}
            />
          )}

          <div className="card p-4">
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Sample response</h3>
            {sample !== undefined ? (
              <>
                <p className="mb-2 text-xs text-slate-500">First records of the last discovery. Held in memory only, never saved.</p>
                <JsonViewer data={sample} />
              </>
            ) : (
              <EmptyState title="No sample retrieved yet" description="Run “Discover from live API” to fetch and inspect a real response." />
            )}
          </div>
        </div>
      </div>
    </>
  );
}

function FieldTable({ title, subtitle, fields, flag }: { title: string; subtitle: string; fields: SchemaField[]; flag?: (f: SchemaField) => string | undefined }) {
  const [filter, setFilter] = useState("");
  const rows = fields.filter((f) => !filter || f.path.toLowerCase().includes(filter.toLowerCase()));
  return (
    <div className="card">
      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-slate-200 p-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-800">
            {title} <span className="font-normal text-slate-400">({fields.length})</span>
          </h3>
          <p className="text-xs text-slate-500">{subtitle}</p>
        </div>
        <input className="input w-56" placeholder="Filter fields" aria-label={`Filter ${title}`} value={filter} onChange={(e) => setFilter(e.target.value)} />
      </div>
      <DataTable
        columns={[
          {
            key: "path",
            header: "Field path",
            render: (f) => {
              const depth = (f.path.match(/\.|\[\]/g) ?? []).length;
              return (
                <code className="text-xs" style={{ paddingLeft: depth * 12 }}>
                  {f.path}
                </code>
              );
            },
          },
          { key: "types", header: "Type", render: (f) => <span className="font-mono text-xs">{f.types.join(" | ")}</span> },
          { key: "nullable", header: "Nullable", render: (f) => (f.nullable ? "yes" : "no") },
          { key: "required", header: "Required", render: (f) => (f.required === undefined ? <span className="text-slate-400" title="Not declared by an official schema">unknown</span> : f.required ? "required" : "optional") },
          { key: "source", header: "Source", render: (f) => <StatusBadge tone={f.source === "declared" ? "blue" : "amber"}>{f.source}</StatusBadge> },
          { key: "seen", header: "Seen in", render: (f) => (f.seenIn !== undefined ? `${f.seenIn}/${f.sampleSize}` : "—") },
          { key: "note", header: "Notes", render: (f) => <span className="text-xs text-slate-500">{[flag?.(f), f.description].filter(Boolean).join(" · ") || "—"}</span> },
        ]}
        rows={rows}
        rowKey={(f) => f.path}
      />
    </div>
  );
}
