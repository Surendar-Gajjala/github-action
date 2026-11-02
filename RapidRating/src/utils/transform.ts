import type { FieldMapping, TargetSchemaRegistry } from "@/types/mapping";

/** Reads a dot path; for "[]" segments the first array item is used (documented selection rule). */
export function getPath(record: unknown, path: string): unknown {
  let cur: unknown = record;
  for (const seg of path.split(".")) {
    if (cur === null || cur === undefined) return undefined;
    const isArr = seg.endsWith("[]");
    const key = isArr ? seg.slice(0, -2) : seg;
    cur = (cur as Record<string, unknown>)[key];
    if (isArr) cur = Array.isArray(cur) ? cur[0] : undefined;
  }
  return cur;
}

export interface TransformResult {
  ok: boolean;
  errors: string[];
}

/**
 * Applies the active mappings to one record IN MEMORY and checks required target properties.
 * The result is discarded: no A1 write target is configured, so nothing is persisted.
 */
export function transformRecord(record: unknown, mappings: FieldMapping[], registry: TargetSchemaRegistry): TransformResult {
  const active = mappings.filter((m) => m.status === "mapped" && m.targetEntity && m.targetProperty);
  if (!active.length) return { ok: false, errors: ["No active mappings for this resource"] };
  const out: Record<string, Record<string, unknown>> = {};
  for (const m of active) (out[m.targetEntity!] ??= {})[m.targetProperty!] = getPath(record, m.sourcePath);

  const errors: string[] = [];
  for (const [entity, values] of Object.entries(out)) {
    const def = registry.entities.find((e) => e.name === entity);
    for (const p of def?.properties ?? []) {
      if (p.required && (values[p.name] === undefined || values[p.name] === null || values[p.name] === "")) errors.push(`${entity}.${p.name} missing`);
    }
  }
  return { ok: errors.length === 0, errors };
}
