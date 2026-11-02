import type { FieldType, ResourceKey, ResourceSchema, SchemaDiff, SchemaField } from "@/types/schema";

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const DATE_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$/;
const URI = /^https?:\/\//i;

export function detectType(value: unknown): FieldType {
  if (value === null) return "null";
  if (Array.isArray(value)) return "array";
  switch (typeof value) {
    case "boolean":
      return "boolean";
    case "number":
      return Number.isInteger(value) ? "integer" : "number";
    case "string":
      if (DATE.test(value)) return "date";
      if (DATE_TIME.test(value)) return "date-time";
      if (URI.test(value)) return "uri";
      return "string";
    case "object":
      return "object";
    default:
      return "unknown";
  }
}

interface Acc {
  types: Set<FieldType>;
  seenIn: number;
}

/**
 * Infers a field list from real response records. Nested objects are walked; array items use "[]".
 * An integer field that also holds decimals is widened to "number". Required-ness is never inferred.
 */
export function inferSchema(records: unknown[], resource: ResourceKey, origin: string): ResourceSchema {
  const acc = new Map<string, Acc>();

  const walk = (value: unknown, path: string, seen: Set<string>) => {
    const t = detectType(value);
    if (path) {
      const a = acc.get(path) ?? { types: new Set<FieldType>(), seenIn: 0 };
      a.types.add(t);
      if (!seen.has(path)) {
        a.seenIn++;
        seen.add(path);
      }
      acc.set(path, a);
    }
    if (t === "object") for (const [k, v] of Object.entries(value as object)) walk(v, path ? `${path}.${k}` : k, seen);
    if (t === "array") for (const item of value as unknown[]) walk(item, `${path}[]`, seen);
  };

  for (const r of records) walk(r, "", new Set());

  const fields: SchemaField[] = [...acc.entries()].map(([path, a]) => {
    let types = [...a.types].filter((t) => t !== "null");
    if (types.includes("integer") && types.includes("number")) types = types.filter((t) => t !== "integer");
    return {
      path,
      name: path.split(/\.|\[\]/).filter(Boolean).pop() ?? path,
      types: types.length ? types : ["null"],
      nullable: a.types.has("null"),
      source: "inferred",
      seenIn: a.seenIn,
      sampleSize: records.length,
    };
  });

  return { resource, source: "inferred", origin, discoveredAt: new Date().toISOString(), fields: fields.sort((x, y) => x.path.localeCompare(y.path)) };
}

export function diffSchemas(previous: SchemaField[], current: SchemaField[]): SchemaDiff {
  const prev = new Map(previous.map((f) => [f.path, f]));
  const curr = new Map(current.map((f) => [f.path, f]));
  // JSON numbers like 1575.0 parse as integers, so integer vs number is not a meaningful change
  const key = (t: FieldType[]) => [...new Set(t.map((x) => (x === "integer" ? "number" : x)))].sort().join("|");
  return {
    added: current.filter((f) => !prev.has(f.path)),
    removed: previous.filter((f) => !curr.has(f.path)),
    typeChanged: current
      .filter((f) => prev.has(f.path) && key(prev.get(f.path)!.types) !== key(f.types) && !f.types.includes("null") && !prev.get(f.path)!.types.includes("null"))
      .map((f) => ({ path: f.path, before: prev.get(f.path)!.types, after: f.types })),
  };
}

/** Primary type used for mapping validation. */
export function primaryType(f: Pick<SchemaField, "types">): FieldType {
  return f.types.find((t) => t !== "null") ?? "null";
}
