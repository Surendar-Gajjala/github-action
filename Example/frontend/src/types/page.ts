/**
 * One page of any entity, built from an Enterprise query response.
 * `page` is zero-based; totals are null when the source does not report them.
 */
export interface PageResponse<T> {
  count: number;
  objects: T[];
  page: number;
  size: number;
  totalObjects: number | null;
  totalPages: number | null;
  hasMore: boolean;
}
