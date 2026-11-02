import type { FieldType } from "@/types/schema";
import type { FieldMapping, TargetSchemaRegistry, TargetType, ValidationResult } from "@/types/mapping";

/** Which source types can be written to which target types without a transform. */
const COMPATIBLE: Record<TargetType, { ok: FieldType[]; warn: FieldType[] }> = {
  string: { ok: ["string", "uri"], warn: ["integer", "number", "boolean", "date", "date-time"] },
  integer: { ok: ["integer"], warn: ["number"] },
  decimal: { ok: ["number", "integer"], warn: [] },
  boolean: { ok: ["boolean"], warn: [] },
  date: { ok: ["date"], warn: ["date-time"] },
  datetime: { ok: ["date-time"], warn: ["date"] },
  json: { ok: ["object", "array"], warn: ["string", "integer", "number", "boolean"] },
};

export function validateMapping(m: FieldMapping, registry: TargetSchemaRegistry, sourceNullable = false): ValidationResult {
  if (m.status === "ignored") return { level: "none", message: "Ignored" };
  if (!m.targetEntity || !m.targetProperty) return { level: "none", message: "Not mapped" };

  const prop = registry.entities.find((e) => e.name === m.targetEntity)?.properties.find((p) => p.name === m.targetProperty);
  if (!prop) return { level: "error", message: `Target ${m.targetEntity}.${m.targetProperty} does not exist in the target registry.` };

  if (m.sourceType === "null") return { level: "warning", message: "Source field was only ever null in sampled data; type cannot be verified." };
  if (m.sourcePath.includes("[]") && prop.type !== "json") {
    return { level: "warning", message: "Source path is inside an array; the target expects a single value. Needs a selection rule (e.g. first item)." };
  }

  const rule = COMPATIBLE[prop.type];
  if (rule.ok.includes(m.sourceType)) {
    return prop.required && sourceNullable
      ? { level: "warning", message: `Compatible, but source can be null and ${prop.name} is required.` }
      : { level: "ok", message: `${m.sourceType} → ${prop.type}` };
  }
  if (rule.warn.includes(m.sourceType)) return { level: "warning", message: `${m.sourceType} → ${prop.type} needs a conversion.` };
  return { level: "error", message: `Incompatible: ${m.sourceType} cannot map to ${prop.type}.` };
}

/** Required target properties of the given entities that no active mapping fills. */
export function missingRequiredTargets(mappings: FieldMapping[], registry: TargetSchemaRegistry, entities: string[]) {
  const mapped = new Set(mappings.filter((m) => m.status === "mapped" && m.targetEntity && m.targetProperty).map((m) => `${m.targetEntity}.${m.targetProperty}`));
  return registry.entities
    .filter((e) => entities.includes(e.name))
    .flatMap((e) => e.properties.filter((p) => p.required && !mapped.has(`${e.name}.${p.name}`)).map((p) => ({ entity: e.name, property: p.name, type: p.type })));
}

/** Target properties (of any entity) with no mapping from any resource. */
export function unmappedTargets(mappings: FieldMapping[], registry: TargetSchemaRegistry) {
  const mapped = new Set(mappings.filter((m) => m.status === "mapped").map((m) => `${m.targetEntity}.${m.targetProperty}`));
  return registry.entities.flatMap((e) => e.properties.filter((p) => !mapped.has(`${e.name}.${p.name}`)).map((p) => ({ entity: e.name, ...p })));
}
