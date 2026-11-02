/** Safe display helpers: missing values render as an em dash, never as invented placeholders. */
export const EMPTY = "—";

export function fmt(value: unknown): string {
  if (value === null || value === undefined || value === "") return EMPTY;
  if (typeof value === "number") return Number.isInteger(value) ? value.toLocaleString() : value.toLocaleString(undefined, { maximumFractionDigits: 4 });
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (Array.isArray(value)) return value.length ? `${value.length} item(s)` : EMPTY;
  if (typeof value === "object") return "{…}";
  return String(value);
}

/** Provider dates are kept as-is (YYYY-MM-DD); date-times are shown in local time with the raw value in a tooltip. */
export function fmtDateTime(value: string | null | undefined): string {
  if (!value) return EMPTY;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString();
}

export function relativeTime(value: string | undefined): string {
  if (!value) return "Never";
  const s = Math.round((Date.now() - new Date(value).getTime()) / 1000);
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.round(s / 60)}m ago`;
  if (s < 86400) return `${Math.round(s / 3600)}h ago`;
  return new Date(value).toLocaleString();
}

export const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(" ");
