import type { ApiError, OutreachCursorPage, PageResult, RridataPage } from "@/types/api";

const structureError = (what: string): ApiError => ({
  kind: "unknown",
  message: `Unexpected response structure from ${what}.`,
  hint: "The provider response did not match the documented envelope. Check the Schema Explorer for changes.",
});

/** Reads the page number from a provider `next`/`previous` URL (absolute rridata URL). */
export function pageFromUrl(url: string | null | undefined): number | undefined {
  if (!url) return undefined;
  try {
    const p = new URL(url, "https://placeholder.invalid").searchParams.get("page");
    return p ? Number(p) : 1; // `previous` for page 1 may omit ?page
  } catch {
    return undefined;
  }
}

/**
 * Adapts the rridata envelope {count, previous, next, results}. Navigation must follow next/previous
 * (requesting a page past the end returns 404/500 on the provider).
 */
export function adaptRridataPage<T>(raw: unknown, page: number, pageSize: number, what: string): PageResult<T> {
  const r = raw as Partial<RridataPage<T>> | null;
  if (!r || typeof r !== "object" || !Array.isArray(r.results) || typeof r.count !== "number") throw structureError(what);
  return {
    items: r.results,
    total: r.count,
    page,
    pageSize,
    hasNext: !!r.next,
    hasPrevious: !!r.previous,
    nextPage: pageFromUrl(r.next),
    previousPage: pageFromUrl(r.previous),
  };
}

/** Adapts the outreach cursor envelope {cursors: {..., total}, items}. */
export function adaptCursorPage<T>(raw: unknown, pageSize: number, what: string): PageResult<T> {
  const r = raw as Partial<OutreachCursorPage<T>> | null;
  if (!r || typeof r !== "object" || !Array.isArray(r.items) || typeof r.cursors?.total !== "number") throw structureError(what);
  return {
    items: r.items,
    total: r.cursors.total,
    page: 0,
    pageSize,
    hasNext: !!r.cursors.next_cursor,
    hasPrevious: !!r.cursors.prev_cursor,
    nextCursor: r.cursors.next_cursor ?? null,
    previousCursor: r.cursors.prev_cursor ?? null,
  };
}

/** Detail endpoints return a bare object. */
export function adaptObject<T>(raw: unknown, what: string): T {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw structureError(what);
  return raw as T;
}

/** Follows `next` until null. Each page is requested by the number the provider put in `next`. */
export async function collectAllPages<T>(
  fetchPage: (page: number) => Promise<PageResult<T>>,
  onProgress?: (loaded: number, total: number) => void,
  maxPages = 200,
): Promise<T[]> {
  const items: T[] = [];
  let page: number | undefined = 1;
  for (let i = 0; page && i < maxPages; i++) {
    const res = await fetchPage(page);
    items.push(...res.items);
    onProgress?.(items.length, res.total);
    page = res.hasNext ? res.nextPage : undefined;
  }
  return items;
}
