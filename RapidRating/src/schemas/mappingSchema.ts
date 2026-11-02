/** Mapping CONFIGURATION schema (Zod) used to validate saved state and JSON imports. */
import { z } from "zod";
import { storage } from "@/utils/storage";
import type { MappingConfiguration } from "@/types/mapping";

const fieldType = z.enum(["string", "integer", "number", "boolean", "date", "date-time", "uri", "object", "array", "null", "unknown"]);

export const fieldMappingSchema = z.object({
  sourceResource: z.enum(["companies", "portfolios", "periods", "ratings", "outreaches"]),
  sourcePath: z.string().min(1),
  sourceType: fieldType,
  targetEntity: z.string().optional(),
  targetProperty: z.string().optional(),
  status: z.enum(["mapped", "unmapped", "ignored"]),
  note: z.string().optional(),
});

export const mappingConfigurationSchema = z.object({
  version: z.literal(1),
  provider: z.literal("rapidratings"),
  targetSystem: z.string(),
  updatedAt: z.string(),
  mappings: z.array(fieldMappingSchema),
});

const KEY = "rr.mappingConfiguration";

export function emptyConfiguration(): MappingConfiguration {
  return { version: 1, provider: "rapidratings", targetSystem: "A1", updatedAt: new Date().toISOString(), mappings: [] };
}

export function loadMappingConfiguration(): MappingConfiguration {
  const parsed = mappingConfigurationSchema.safeParse(storage.get(KEY, null));
  return parsed.success ? parsed.data : emptyConfiguration();
}

export function saveMappingConfiguration(cfg: MappingConfiguration) {
  storage.set(KEY, { ...cfg, updatedAt: new Date().toISOString() });
}

export function parseMappingImport(text: string): { ok: true; config: MappingConfiguration } | { ok: false; error: string } {
  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    return { ok: false, error: "File is not valid JSON." };
  }
  const parsed = mappingConfigurationSchema.safeParse(json);
  if (!parsed.success) {
    return { ok: false, error: parsed.error.issues.slice(0, 5).map((i) => `${i.path.join(".") || "(root)"}: ${i.message}`).join("; ") };
  }
  return { ok: true, config: parsed.data };
}
