/**
 * Normalisation rules for raw Enterprise `results` values, shared by every frontend
 * mapper, so every entity treats empty and inconsistent values the same way. None of
 * these functions throw for bad values.
 */

/** Trimmed text, or null for null/undefined/blank. Arrays are joined with ", ". */
export function text(value: unknown): string | null {
  if (Array.isArray(value)) {
    const parts = list(value, scalarText);
    return parts.length ? parts.join(', ') : null;
  }
  return scalarText(value);
}

/** A finite number from a JSON number or numeric string, else null. */
export function num(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  const s = scalarText(value);
  if (s === null) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

/** A whole number from a JSON number or numeric string, else null. */
export function int(value: unknown): number | null {
  const n = num(value);
  return n !== null && Number.isInteger(n) ? n : null;
}

/** A boolean from a JSON boolean, number or common true/false spellings, else null. */
export function bool(value: unknown): boolean | null {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  const s = scalarText(value);
  if (s === null) return null;
  switch (s.toLowerCase()) {
    case 'true':
    case 't':
    case 'yes':
    case 'y':
    case '1':
      return true;
    case 'false':
    case 'f':
    case 'no':
    case 'n':
    case '0':
      return false;
    default:
      return null;
  }
}

/**
 * Upper-cased key for matching enum-like values ("not_assessed", "Not-Assessed" and
 * "NOT ASSESSED" all become "NOT ASSESSED"); null when blank.
 */
export function enumKey(value: unknown): string | null {
  const s = text(value);
  return s === null ? null : s.toUpperCase().replace(/[_\-\s]+/g, ' ').trim();
}

/**
 * Normalises a list-like value: an array, an array-like string such as `"[]"` /
 * `"['A', 'B']"`, a comma-separated string, or a single scalar. Elements the converter
 * cannot handle (returns null) are dropped.
 */
export function list<T>(value: unknown, convert: (element: unknown) => T | null): T[] {
  if (value === null || value === undefined) return [];
  const elements: unknown[] = Array.isArray(value)
    ? value
    : typeof value === 'string'
      ? splitListText(value)
      : [value];
  return elements.map(convert).filter((element): element is T => element !== null);
}

/** Trimmed text of a scalar, or null for null/undefined/blank/objects/arrays. */
export function scalarText(value: unknown): string | null {
  if (value === null || value === undefined || typeof value === 'object') return null;
  const s = String(value).trim();
  return s === '' ? null : s;
}

/**
 * Readable labels for status-like codes (`PRODN_APPROVED` → "Production Approved",
 * `N_A` → "N/A"). Unlisted codes pass through unchanged.
 */
const CODE_LABELS: Record<string, string> = {
  PRODN_APPROVED: 'Production Approved',
  UN_QUAL: 'Unqualified',
  UNQUALIFIED: 'Unqualified',
  CONDITIONAL: 'Conditional',
  PRELIMINARY: 'Preliminary',
  DESIGN: 'Design',
  OBSOLETE: 'Obsolete',
  APPROVED: 'Approved',
  PENDING: 'Pending',
  REJECTED: 'Rejected',
  N_A: 'N/A',
  YES: 'Yes',
  NO: 'No',
  NO_DATA: 'No Data',
  NOT_LOCKED: 'Not Locked',
  LOCKED: 'Locked',
  EXCL_ITEM: 'Excluded Item',
  IMPLICIT: 'Implicit',
  NOT_ASSESSED: 'Not Assessed',
  UNKNOWN: 'Unknown',
};

/** The label for a code, the trimmed original text when unknown, or null when blank. */
export function codeLabel(value: unknown): string | null {
  const s = text(value);
  return s === null ? null : (CODE_LABELS[s.toUpperCase()] ?? s);
}

function splitListText(value: string): string[] {
  let s = value.trim();
  if (s.startsWith('[') && s.endsWith(']')) s = s.slice(1, -1);
  return s
    .split(',')
    .map((part) => stripQuotes(part.trim()))
    .filter((part) => part !== '');
}

function stripQuotes(value: string): string {
  if (value.length >= 2) {
    const first = value[0];
    if ((first === '"' || first === "'") && first === value[value.length - 1]) {
      return value.slice(1, -1).trim();
    }
  }
  return value;
}
