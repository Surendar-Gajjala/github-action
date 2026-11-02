/**
 * SOURCE schema definitions (RapidRatings). Kept separate from target schemas and mapping config.
 *
 *  - Outreaches: DECLARED by the provider's OpenAPI spec (api.sandbox.rrilabs.co/openapi.yaml,
 *    retrieved 2026-10-28). Required flags come from that spec.
 *  - Companies / Portfolios / Periods / Ratings: the rridata host publishes no schema
 *    (/openapi.json returns 403), so these are INFERRED. A baseline captured from validated sandbox
 *    responses on 2026-10-28 is included; the Schema Explorer can re-discover them live.
 */
import { createStore } from "@/utils/store";
import { storage } from "@/utils/storage";
import { diffSchemas } from "@/utils/fieldTypeDetector";
import type { FieldType, ResourceKey, ResourceSchema, SchemaDiff, SchemaField } from "@/types/schema";

export const RESOURCES: { key: ResourceKey; label: string; endpoint: string; host: "data" | "outreach" }[] = [
  { key: "companies", label: "Companies", endpoint: "GET /v2.0/companies/", host: "data" },
  { key: "portfolios", label: "Portfolios", endpoint: "GET /v2.0/portfolios/", host: "data" },
  { key: "periods", label: "Periods", endpoint: "GET /v2.0/companies/{rrid}/periods/", host: "data" },
  { key: "ratings", label: "Ratings", endpoint: "GET /v2.0/companies/{rrid}/ratings/", host: "data" },
  { key: "outreaches", label: "Outreaches", endpoint: "GET /outreach-requests", host: "outreach" },
];

type Row = [path: string, type: FieldType, nullable?: boolean, required?: boolean, description?: string];

const fields = (rows: Row[], source: "declared" | "inferred"): SchemaField[] =>
  rows.map(([path, type, nullable = false, required, description]) => ({
    path,
    name: path.split(/\.|\[\]/).filter(Boolean).pop() ?? path,
    types: [type],
    nullable,
    required: source === "declared" ? (required ?? false) : undefined,
    source,
    description,
  }));

const RATING_ROWS = (prefix = ""): Row[] => [
  [`${prefix}id`, "string"], [`${prefix}href`, "uri"], [`${prefix}eqyYear`, "integer"], [`${prefix}period`, "string"],
  [`${prefix}financialDate`, "date"], [`${prefix}reportingPeriod`, "date"], [`${prefix}fhr`, "integer"], [`${prefix}chs`, "integer"],
  [`${prefix}probabilityDefault`, "number"], [`${prefix}termPd`, "object"],
  ...[2, 3, 4, 5, 6, 7, 8, 9, 10].map((y): Row => [`${prefix}termPd.year${y}`, "number"]),
  [`${prefix}delta`, "integer", true], [`${prefix}simulatedFhr`, "integer"], [`${prefix}simulatedFhrDelta`, "integer", true],
  [`${prefix}operatingProfitability`, "integer"], [`${prefix}netProfitability`, "integer"],
  [`${prefix}capitalStructureEfficiency`, "integer"], [`${prefix}costStructureEfficiency`, "integer"],
  [`${prefix}leverage`, "string"], [`${prefix}liquidity`, "string"], [`${prefix}earningsPerformance`, "string"],
];

const BASELINE_ORIGIN = "Inferred baseline from validated sandbox responses (2026-10-28)";
const BASELINE_AT = "2026-10-28T00:00:00.000Z";

const BASELINE: Record<Exclude<ResourceKey, "outreaches">, ResourceSchema> = {
  companies: {
    resource: "companies", source: "inferred", origin: BASELINE_ORIGIN, discoveredAt: BASELINE_AT,
    fields: fields([
      ["id", "string"], ["name", "string"], ["country", "string"], ["ticker", "string"], ["exchange", "string"], ["sector", "string"],
      ["href", "uri"], ["financials", "uri"], ["periods", "uri"], ["ratings", "uri"], ["latestRatings", "array"], ["latestRatings[]", "object"],
      ...RATING_ROWS("latestRatings[]."),
      ["reportLinks", "object"], ["reportLinks.fhr", "uri"], ["reportLinks.financialDialog", "uri"], ["reportLinks.peerBenchmark", "uri"],
      ["address", "string"], ["industryClassificationSystem", "null", true], ["industryClassificationCode", "null", true],
      ["currency", "string"], ["dataScale", "string"], ["dataConfidential", "string"], ["externalSourceId", "null", true], ["externalSourceIds", "array"],
    ], "inferred"),
  },
  portfolios: {
    resource: "portfolios", source: "inferred", origin: BASELINE_ORIGIN, discoveredAt: BASELINE_AT,
    fields: fields([["id", "integer"], ["name", "string"], ["href", "uri"], ["companies", "uri"]], "inferred"),
  },
  periods: {
    resource: "periods", source: "inferred", origin: BASELINE_ORIGIN, discoveredAt: BASELINE_AT,
    fields: fields([
      ["id", "string"], ["href", "uri"], ["eqyYear", "integer"], ["period", "string"], ["financialDate", "date"], ["currency", "string"],
      ["salesRevenue", "number"], ["earningsBeforeInterestAndTax", "number"], ["interestExpense", "number"], ["interestReceived", "number"],
      ["companyTaxExpense", "number"], ["abnormalItems", "number", true], ["netProfitAfterTax", "number"], ["bankCashBalances", "number"],
      ["totalCurrentAssets", "number"], ["fixedAssets", "number"], ["financialAssets", "null", true], ["totalAssets", "number"],
      ["debitOwnedWithinOneYear", "number"], ["totalCurrentLiabilities", "number"], ["termLoans", "number"], ["totalLiabilities", "number"],
      ["totalShareholderEquity", "number"], ["cashFromOperationsFromCashFlow", "null", true], ["netInvestingCashFlow", "number"], ["netFundingCashFlow", "number"],
    ], "inferred"),
  },
  ratings: { resource: "ratings", source: "inferred", origin: BASELINE_ORIGIN, discoveredAt: BASELINE_AT, fields: fields(RATING_ROWS(), "inferred") },
};

/** Declared by OpenAPI: OutreachRequestOutput */
export const DECLARED_OUTREACH: ResourceSchema = {
  resource: "outreaches",
  source: "declared",
  origin: "Provider OpenAPI spec: api.sandbox.rrilabs.co/openapi.yaml (OutreachRequestOutput), retrieved 2026-10-28",
  discoveredAt: BASELINE_AT,
  fields: fields([
    ["outreach_request_id", "string", false, false, "Unique identifier of the Outreach Request (read-only)"],
    ["counterparty", "object", false, true, "The counterparty which the rating is requested for"],
    ["counterparty.company_name", "string", false, true, "Company name to be contacted"],
    ["counterparty.reference_id", "string", true, false, "Your free-text reference ID"],
    ["counterparty.contact", "object", false, true, "Primary contact"],
    ["counterparty.contact.first_name", "string", false, true],
    ["counterparty.contact.last_name", "string", false, true],
    ["counterparty.contact.email", "string", false, true, "format: email"],
    ["counterparty.contact.country", "string", false, true, "ISO-3166 Alpha 3"],
    ["counterparty.contact.tel", "string", true, false],
    ["counterparty.additional_contacts", "array", false, false, "Intercom-delivered orgs only"],
    ["counterparty.additional_contacts[].contact_ref", "string", true, false],
    ["counterparty.additional_contacts[].email", "string", false, true],
    ["counterparty.additional_contacts[].first_name", "string", false, false],
    ["counterparty.additional_contacts[].last_name", "string", false, false],
    ["counterparty.additional_contacts[].tel", "string", true, false],
    ["relationship_manager", "object", false, true],
    ["relationship_manager.name", "string", false, true],
    ["relationship_manager.email", "string", false, true, "format: email"],
    ["created_at", "date-time", false, false],
    ["modified_at", "date-time", false, false],
    ["is_rated", "boolean", false, false, "True once a rating exists"],
    ["rrid", "string", true, false, "RRID of the rated company"],
    ["status", "string", true, false, "Display only; values may change without notice"],
    ["status_history", "array", false, false],
    ["status_history[].status", "string", false, false],
    ["status_history[].status_detail", "string", true, false],
    ["status_history[].status_comment", "string", true, false],
    ["status_history[].status_reasons", "array", true, false],
    ["status_history[].status_updated_at", "date-time", false, false],
    ["financial_submissions", "array", false, false],
    ["financial_submissions[].created_at", "date-time", false, false],
    ["_embedded", "object", false, false, "Present when `embed` is used"],
  ], "declared"),
};

// --- Discovered (live) schemas: held in application state; snapshots store field metadata only ---
const SNAPSHOT_KEY = "rr.schemaSnapshots";

interface SchemaState {
  discovered: Partial<Record<ResourceKey, ResourceSchema>>;
  /** Raw sample held in memory only (never persisted). */
  samples: Partial<Record<ResourceKey, unknown>>;
  snapshots: Partial<Record<ResourceKey, ResourceSchema>>;
}

export const schemaStore = createStore<SchemaState>({
  discovered: {},
  samples: {},
  snapshots: storage.get(SNAPSHOT_KEY, {}),
});

export function recordDiscovery(schema: ResourceSchema, sample: unknown) {
  schemaStore.set((s) => ({ ...s, discovered: { ...s.discovered, [schema.resource]: schema }, samples: { ...s.samples, [schema.resource]: sample } }));
}

/** Saves the current discovered schema (field names/types only, no values) as the comparison baseline. */
export function saveSnapshot(resource: ResourceKey) {
  const current = schemaStore.get().discovered[resource];
  if (!current) return;
  const snapshots = { ...schemaStore.get().snapshots, [resource]: current };
  storage.set(SNAPSHOT_KEY, snapshots);
  schemaStore.set((s) => ({ ...s, snapshots }));
}

/** Compares the live schema with the saved snapshot, or with the built-in baseline if none. */
export function compareWithPrevious(resource: ResourceKey): { against: string; diff: SchemaDiff } | null {
  const { discovered, snapshots } = schemaStore.get();
  const current = discovered[resource];
  if (!current) return null;
  const prev = snapshots[resource] ?? (resource === "outreaches" ? undefined : BASELINE[resource]);
  if (!prev) return null;
  return {
    against: snapshots[resource] ? `Snapshot from ${new Date(prev.discoveredAt).toLocaleString()}` : prev.origin,
    diff: diffSchemas(prev.fields, current.fields),
  };
}

export function declaredSchema(resource: ResourceKey): ResourceSchema | undefined {
  return resource === "outreaches" ? DECLARED_OUTREACH : undefined;
}

/** Fields available for mapping: declared where the provider declares them, else latest inferred (live or baseline). */
export function sourceFieldsFor(resource: ResourceKey): ResourceSchema {
  if (resource === "outreaches") return DECLARED_OUTREACH;
  return schemaStore.get().discovered[resource] ?? BASELINE[resource];
}
