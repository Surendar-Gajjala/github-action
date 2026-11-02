import { useMemo, useRef, useState } from "react";
import { AlertTriangle, Download, Save, Upload } from "lucide-react";
import { RESOURCES, sourceFieldsFor, schemaStore } from "@/schemas/sourceSchemaRegistry";
import { targetSchemaRegistry as registry } from "@/schemas/targetSchemaRegistry";
import { loadMappingConfiguration, parseMappingImport, saveMappingConfiguration } from "@/schemas/mappingSchema";
import { missingRequiredTargets, unmappedTargets, validateMapping } from "@/utils/mappingValidator";
import { primaryType } from "@/utils/fieldTypeDetector";
import { useStore } from "@/utils/store";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge, type Tone } from "@/components/common/StatusBadge";
import { cx } from "@/utils/format";
import type { FieldMapping, MappingConfiguration, MappingStatus, ValidationLevel } from "@/types/mapping";
import type { ResourceKey, SchemaField } from "@/types/schema";

const LEVEL_TONE: Record<ValidationLevel, Tone> = { ok: "green", warning: "amber", error: "red", none: "slate" };
const STATUS_TONE: Record<MappingStatus, Tone> = { mapped: "green", unmapped: "slate", ignored: "violet" };

export function SchemaMappingPage() {
  useStore(schemaStore); // re-render when a live discovery updates the source fields
  const [resource, setResource] = useState<ResourceKey>("companies");
  const [config, setConfig] = useState<MappingConfiguration>(loadMappingConfiguration);
  const [dirty, setDirty] = useState(false);
  const [message, setMessage] = useState<{ tone: Tone; text: string }>();
  const [showOnly, setShowOnly] = useState<"all" | "unmapped" | "issues">("all");
  const fileRef = useRef<HTMLInputElement>(null);

  const source = sourceFieldsFor(resource);
  // Container fields (objects/arrays) are listed but leaf fields are the usual mapping targets
  const fields = source.fields;

  const mappingFor = (f: SchemaField): FieldMapping =>
    config.mappings.find((m) => m.sourceResource === resource && m.sourcePath === f.path) ?? { sourceResource: resource, sourcePath: f.path, sourceType: primaryType(f), status: "unmapped" };

  const update = (f: SchemaField, patch: Partial<FieldMapping>) => {
    setConfig((c) => {
      const others = c.mappings.filter((m) => !(m.sourceResource === resource && m.sourcePath === f.path));
      const next = { ...mappingFor(f), sourceType: primaryType(f), ...patch };
      if (patch.targetEntity !== undefined || patch.targetProperty !== undefined) next.status = next.targetEntity && next.targetProperty ? "mapped" : "unmapped";
      const keep = next.status !== "unmapped" || next.targetEntity || next.note;
      return { ...c, mappings: keep ? [...others, next] : others };
    });
    setDirty(true);
  };

  const rows = fields.map((f) => {
    const m = mappingFor(f);
    return { f, m, v: validateMapping(m, registry, f.nullable) };
  });
  const visible = rows.filter(({ m, v }) => showOnly === "all" || (showOnly === "unmapped" ? m.status === "unmapped" : v.level === "error" || v.level === "warning"));

  const resourceMappings = config.mappings.filter((m) => m.sourceResource === resource);
  const usedEntities = [...new Set(resourceMappings.filter((m) => m.status === "mapped" && m.targetEntity).map((m) => m.targetEntity!))];
  const missing = missingRequiredTargets(config.mappings, registry, usedEntities);
  const unmappedSource = rows.filter((r) => r.m.status === "unmapped");
  const unmappedTarget = useMemo(() => unmappedTargets(config.mappings, registry), [config.mappings]);
  const stale = resourceMappings.filter((m) => !fields.some((f) => f.path === m.sourcePath));

  const save = () => {
    saveMappingConfiguration(config);
    setDirty(false);
    setMessage({ tone: "green", text: "Mapping saved in this browser (demo storage)." });
  };

  const exportJson = () => {
    const blob = new Blob([JSON.stringify({ ...config, updatedAt: new Date().toISOString() }, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `rapidratings-a1-mapping-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const importJson = async (file?: File) => {
    if (!file) return;
    const result = parseMappingImport(await file.text());
    if (!result.ok) return setMessage({ tone: "red", text: `Import rejected: ${result.error}` });
    setConfig(result.config);
    setDirty(true);
    setMessage({ tone: "amber", text: `Imported ${result.config.mappings.length} mapping(s). Review, then Save to keep them.` });
  };

  return (
    <>
      <PageHeader
        title="Schema Mapping"
        description="Map RapidRatings source fields to A1 target properties. Source schemas, target schemas and mapping configuration are kept separate."
        actions={
          <>
            <button type="button" className="btn-primary" onClick={save} disabled={!dirty}>
              <Save className="h-4 w-4" aria-hidden /> Save{dirty ? " *" : ""}
            </button>
            <button type="button" className="btn-secondary" onClick={exportJson}>
              <Download className="h-4 w-4" aria-hidden /> Export JSON
            </button>
            <button type="button" className="btn-secondary" onClick={() => fileRef.current?.click()}>
              <Upload className="h-4 w-4" aria-hidden /> Import JSON
            </button>
            <input ref={fileRef} type="file" accept="application/json,.json" className="hidden" onChange={(e) => (importJson(e.target.files?.[0]), (e.target.value = ""))} />
          </>
        }
      />

      {registry.isExample && (
        <div className="mb-4 flex gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <span>
            <strong>Example A1 target schema</strong> ({registry.version}). No A1 documentation was provided, so target entities and properties are illustrative. Replace <code>src/schemas/targetSchemaRegistry.ts</code> with the real A1 metadata.
          </span>
        </div>
      )}
      {message && (
        <div className="mb-4">
          <StatusBadge tone={message.tone}>{message.text}</StatusBadge>
        </div>
      )}

      <div className="card mb-4 flex flex-wrap items-end gap-3 p-3">
        <div className="w-52">
          <label htmlFor="map-resource" className="label">
            Source resource
          </label>
          <select id="map-resource" className="input" value={resource} onChange={(e) => setResource(e.target.value as ResourceKey)}>
            {RESOURCES.map((r) => (
              <option key={r.key} value={r.key}>
                {r.label}
              </option>
            ))}
          </select>
        </div>
        <div className="w-44">
          <label htmlFor="map-show" className="label">
            Show
          </label>
          <select id="map-show" className="input" value={showOnly} onChange={(e) => setShowOnly(e.target.value as typeof showOnly)}>
            <option value="all">All fields</option>
            <option value="unmapped">Unmapped only</option>
            <option value="issues">Warnings & errors</option>
          </select>
        </div>
        <p className="text-xs text-slate-500">
          Source: <StatusBadge tone={source.source === "declared" ? "blue" : "amber"}>{source.source}</StatusBadge> {source.origin}
        </p>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1fr_20rem]">
        <div className="card overflow-x-auto">
          <table className="min-w-full border-separate border-spacing-0">
            <thead>
              <tr>
                {["Source resource", "Source field", "Source data type", "Target A1 entity", "Target A1 property", "Target data type", "Mapping status", "Validation result"].map((h) => (
                  <th key={h} scope="col" className="th">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visible.map(({ f, m, v }) => {
                const entity = registry.entities.find((e) => e.name === m.targetEntity);
                const prop = entity?.properties.find((p) => p.name === m.targetProperty);
                return (
                  <tr key={f.path} className={cx(m.status === "ignored" && "opacity-60")}>
                    <td className="td text-xs text-slate-500">{resource}</td>
                    <td className="td">
                      <code className="text-xs">{f.path}</code>
                    </td>
                    <td className="td font-mono text-xs">
                      {f.types.join(" | ")}
                      {f.nullable && <span className="text-slate-400"> (nullable)</span>}
                    </td>
                    <td className="td">
                      <select
                        aria-label={`Target entity for ${f.path}`}
                        className="input py-1 text-xs"
                        value={m.targetEntity ?? ""}
                        onChange={(e) => update(f, { targetEntity: e.target.value || undefined, targetProperty: undefined })}
                      >
                        <option value="">—</option>
                        {registry.entities.map((e) => (
                          <option key={e.name}>{e.name}</option>
                        ))}
                      </select>
                    </td>
                    <td className="td">
                      <select
                        aria-label={`Target property for ${f.path}`}
                        className="input py-1 text-xs"
                        value={m.targetProperty ?? ""}
                        disabled={!entity}
                        onChange={(e) => update(f, { targetProperty: e.target.value || undefined })}
                      >
                        <option value="">—</option>
                        {entity?.properties.map((p) => (
                          <option key={p.name} value={p.name}>
                            {p.name}
                            {p.required ? " *" : ""}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="td font-mono text-xs">{prop ? `${prop.type}${prop.required ? " (required)" : ""}` : "—"}</td>
                    <td className="td">
                      <select
                        aria-label={`Mapping status for ${f.path}`}
                        className="input py-1 text-xs"
                        value={m.status}
                        onChange={(e) => update(f, { status: e.target.value as MappingStatus })}
                      >
                        <option value="unmapped" disabled={!!(m.targetEntity && m.targetProperty)}>
                          unmapped
                        </option>
                        <option value="mapped" disabled={!(m.targetEntity && m.targetProperty)}>
                          mapped
                        </option>
                        <option value="ignored">ignored</option>
                      </select>
                    </td>
                    <td className="td max-w-xs whitespace-normal">
                      {v.level === "none" ? (
                        <StatusBadge tone={STATUS_TONE[m.status]}>{v.message}</StatusBadge>
                      ) : (
                        <>
                          <StatusBadge tone={LEVEL_TONE[v.level]}>{v.level}</StatusBadge>
                          <span className="ml-1 text-xs text-slate-600">{v.message}</span>
                        </>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {!visible.length && <p className="p-6 text-center text-sm text-slate-500">No fields match this view.</p>}
        </div>

        <aside className="space-y-4">
          <Panel title="Summary">
            <ul className="space-y-1 text-sm">
              <li>Source fields: {rows.length}</li>
              <li>Mapped: {rows.filter((r) => r.m.status === "mapped").length}</li>
              <li>Ignored: {rows.filter((r) => r.m.status === "ignored").length}</li>
              <li>Unmapped: {unmappedSource.length}</li>
              <li className="text-red-700">Errors: {rows.filter((r) => r.v.level === "error").length}</li>
              <li className="text-amber-700">Warnings: {rows.filter((r) => r.v.level === "warning").length}</li>
            </ul>
          </Panel>
          <Panel title={`Missing required target properties (${missing.length})`}>
            {usedEntities.length === 0 ? (
              <p className="text-xs text-slate-500">Map a field to an entity to check its required properties.</p>
            ) : missing.length ? (
              <ul className="space-y-0.5 text-xs text-red-700">
                {missing.map((m) => (
                  <li key={`${m.entity}.${m.property}`}>
                    {m.entity}.{m.property} ({m.type})
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-xs text-emerald-700">All required properties of {usedEntities.join(", ")} are mapped.</p>
            )}
          </Panel>
          {stale.length > 0 && (
            <Panel title={`Stale mappings (${stale.length})`}>
              <p className="mb-1 text-xs text-slate-500">Saved mappings whose source field is not in the current source schema:</p>
              <ul className="text-xs text-amber-800">
                {stale.map((m) => (
                  <li key={m.sourcePath}>
                    <code>{m.sourcePath}</code>
                  </li>
                ))}
              </ul>
            </Panel>
          )}
          <Panel title={`Unmapped source fields (${unmappedSource.length})`}>
            <ul className="max-h-48 overflow-y-auto text-xs text-slate-600">
              {unmappedSource.map(({ f }) => (
                <li key={f.path}>
                  <code>{f.path}</code>
                </li>
              ))}
            </ul>
          </Panel>
          <Panel title={`Target properties without mappings (${unmappedTarget.length})`}>
            <ul className="max-h-48 overflow-y-auto text-xs text-slate-600">
              {unmappedTarget.map((t) => (
                <li key={`${t.entity}.${t.name}`}>
                  {t.entity}.{t.name}
                  {t.required && <span className="text-red-600"> *</span>}
                </li>
              ))}
            </ul>
          </Panel>
        </aside>
      </div>
    </>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="card p-3">
      <h3 className="mb-2 text-sm font-semibold text-slate-800">{title}</h3>
      {children}
    </section>
  );
}
