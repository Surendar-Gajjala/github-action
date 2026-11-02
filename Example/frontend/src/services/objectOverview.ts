import type { OverviewField, OverviewSection } from '../types/itemDetails';
import { EnterpriseQueryError, enterpriseGet } from './enterpriseQuery';
import { bool, codeLabel, list, num, scalarText, text } from './enterpriseValues';

/**
 * One Enterprise object (any entity) from the partial-object endpoint: every property
 * keyed by its dotted name (e.g. `env_compliance.eu_rohs.status`), each wrapped with
 * metadata of which only `value` is used.
 */
export interface EnterpriseObject {
  objectId: number;
  objectTypeDbName: string;
  properties: Record<string, { value?: unknown } | null>;
}

/** Reads raw property values of one object by name; absent properties read as undefined. */
export type ValueOf = (property: string) => unknown;

/** One Overview field: its label, how to read its display value, and an optional explanation. */
export interface FieldSpec {
  label: string;
  value: (get: ValueOf) => string | null;
  reason?: (get: ValueOf) => string | null;
}

export interface SectionSpec {
  title: string;
  fields: FieldSpec[];
}

const OBJECT_PATH = '/core/objects/partial';

type Json = Record<string, unknown>;
const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null && !Array.isArray(value);

/**
 * Fetches one object of the expected entity type. Enterprise answers an unknown id with
 * an empty 200, so that, and an object of another type, become a 404 "not found" error.
 */
export async function fetchObject(id: number, entity: 'item' | 'part' | 'company'): Promise<EnterpriseObject> {
  const data = await enterpriseGet<unknown>(`${OBJECT_PATH}/${id}`);
  if (
    !isObject(data) ||
    typeof data.objectId !== 'number' ||
    typeof data.objectTypeDbName !== 'string' ||
    data.objectTypeDbName.toLowerCase() !== entity
  ) {
    throw new EnterpriseQueryError(`No ${entity} exists with this id.`, 404);
  }
  return { ...(data as unknown as EnterpriseObject), properties: isObject(data.properties) ? (data.properties as EnterpriseObject['properties']) : {} };
}

/** A reader for an object's property values. */
export function valuesOf(object: EnterpriseObject): ValueOf {
  return (property) => object.properties[property]?.value;
}

/** Builds display-ready sections from a field table. */
export function buildSections(sections: SectionSpec[], get: ValueOf): OverviewSection[] {
  return sections.map(({ title, fields }) => ({
    title,
    fields: fields.map(
      ({ label, value, reason }): OverviewField => ({ label, value: value(get), reason: reason ? reason(get) : null }),
    ),
  }));
}

// ---- Field readers (each returns display text or null) -------------------------------

/** Plain text. */
export const asText = (property: string) => (get: ValueOf) => text(get(property));

/** A status-like code as a readable label (`NON_COMPLIANT` → "Non Compliant"). */
export const asCode = (property: string) => (get: ValueOf) => humanizeCode(get(property));

/** A list of codes or text (`["TH", "MY"]` → "TH, MY"). */
export const asList = (property: string) => (get: ValueOf) => {
  const values = list(get(property), (v) => humanizeCode(v));
  return values.length ? values.join(', ') : null;
};

/** A boolean as Yes / No. */
export const asYesNo = (property: string) => (get: ValueOf) => {
  const value = bool(get(property));
  return value === null ? null : value ? 'Yes' : 'No';
};

/** A number with an optional suffix (`"17"`, " weeks" → "17 weeks"). */
export const asNumber = (property: string, suffix = '') => (get: ValueOf) => {
  const value = num(get(property));
  return value === null ? null : `${value.toLocaleString('en-US')}${suffix}`;
};

/** A value with its unit held in a sibling property (`parametric.voltage_rating.value` + `.unit`). */
export const asMeasure = (base: string) => (get: ValueOf) => {
  const value = num(get(`${base}.value`));
  if (value === null) return null;
  const unit = text(get(`${base}.unit`));
  return unit ? `${value} ${unit}` : String(value);
};

/** A risk band as-is (LOW / MEDIUM / HIGH), shown as a coloured badge in the Risk section. */
export const asBand = (property: string) => (get: ValueOf) => text(get(property))?.toUpperCase() ?? null;

/** The reason line under a risk band: its score and, when given, what drives it. */
export const scoreReason = (base: string, driver?: { property: string; label: string }) => (get: ValueOf) => {
  const score = num(get(`${base}.score`));
  const drivenBy = driver ? humanizeCode(get(driver.property)) : null;
  const parts = [score !== null ? `Score ${score}` : null, drivenBy ? `${driver!.label}: ${drivenBy}` : null].filter(Boolean);
  return parts.length ? parts.join(' · ') : null;
};

/** Upper-case words of 3+ letters that read better title-cased; short codes (US, EXW, Z2) stay as-is. */
export function humanizeCode(value: unknown): string | null {
  const label = codeLabel(scalarText(value));
  if (label === null || !/^[A-Z0-9_]+$/.test(label) || !/[A-Z]/.test(label)) return label;
  if (!label.includes('_') && label.length <= 3) return label;
  return label
    .split('_')
    .filter(Boolean)
    .map((word) => (ACRONYMS.has(word) ? word : word[0] + word.slice(1).toLowerCase()))
    .join(' ');
}

const ACRONYMS = new Set(['EU', 'US', 'OTS', 'SVHC', 'PFAS', 'ROHS', 'NCNR', 'EOL', 'LTB', 'NRND', 'ECCN', 'HTS', 'BOM', 'AML', 'PO']);
