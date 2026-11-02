export type FieldType = "string" | "integer" | "number" | "boolean" | "date" | "date-time" | "uri" | "object" | "array" | "null" | "unknown";

export type ResourceKey = "companies" | "portfolios" | "periods" | "ratings" | "outreaches";

/** Where a field definition came from. Declared = official provider schema; inferred = derived from real responses. */
export type FieldSource = "declared" | "inferred";

export interface SchemaField {
  /** Dot path; array items use "[]", e.g. latestRatings[].termPd.year2 */
  path: string;
  name: string;
  types: FieldType[];
  nullable: boolean;
  /** Only set when the source schema declares it. Inferred fields leave it undefined. */
  required?: boolean;
  source: FieldSource;
  description?: string;
  /** Number of sampled records containing this field (inferred only) */
  seenIn?: number;
  sampleSize?: number;
}

export interface ResourceSchema {
  resource: ResourceKey;
  source: FieldSource;
  /** e.g. "OpenAPI openapi.yaml (retrieved 2026-10-28)" or "Inferred from 20 records" */
  origin: string;
  discoveredAt: string;
  fields: SchemaField[];
}

export interface SchemaDiff {
  added: SchemaField[];
  removed: SchemaField[];
  typeChanged: { path: string; before: FieldType[]; after: FieldType[] }[];
}
