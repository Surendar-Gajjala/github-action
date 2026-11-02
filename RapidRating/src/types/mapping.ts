import type { FieldType, ResourceKey } from "./schema";

export type TargetType = "string" | "integer" | "decimal" | "boolean" | "date" | "datetime" | "json";

export interface TargetProperty {
  name: string;
  type: TargetType;
  required: boolean;
  description?: string;
}

export interface TargetEntity {
  name: string;
  description?: string;
  properties: TargetProperty[];
}

export interface TargetSchemaRegistry {
  system: string;
  version: string;
  /** true while the A1 schema is an illustrative example, not project documentation */
  isExample: boolean;
  entities: TargetEntity[];
}

export type MappingStatus = "mapped" | "unmapped" | "ignored";

export interface FieldMapping {
  sourceResource: ResourceKey;
  sourcePath: string;
  sourceType: FieldType;
  targetEntity?: string;
  targetProperty?: string;
  status: MappingStatus;
  note?: string;
}

export interface MappingConfiguration {
  version: 1;
  provider: "rapidratings";
  targetSystem: string;
  updatedAt: string;
  mappings: FieldMapping[];
}

export type ValidationLevel = "ok" | "warning" | "error" | "none";

export interface ValidationResult {
  level: ValidationLevel;
  message: string;
}
