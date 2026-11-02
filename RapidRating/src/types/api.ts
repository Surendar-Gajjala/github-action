/** rridata list envelope (verified: companies, portfolios, periods, ratings). */
export interface RridataPage<T> {
  count: number;
  previous: string | null;
  next: string | null;
  results: T[];
}

/** Outreach list envelope (cursor pagination, OpenAPI spec + verified empty response). */
export interface OutreachCursorPage<T> {
  cursors: {
    first_cursor?: string;
    last_cursor?: string;
    next_cursor?: string | null;
    prev_cursor?: string | null;
    query?: Record<string, unknown>;
    total: number;
  };
  items: T[];
}

/** Provider-neutral page shape produced by utils/responseAdapter. */
export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasNext: boolean;
  hasPrevious: boolean;
  nextPage?: number;
  previousPage?: number;
  nextCursor?: string | null;
  previousCursor?: string | null;
}

export type AuthMode = "mock" | "proxy" | "direct";

export type ApiErrorKind =
  | "bad-request"
  | "unauthorized"
  | "forbidden"
  | "not-found"
  | "rate-limited"
  | "server"
  | "timeout"
  | "network"
  | "cors"
  | "config"
  | "unknown";

/** Normalised, secret-free error used everywhere in the UI. */
export interface ApiError {
  kind: ApiErrorKind;
  status?: number;
  message: string;
  hint?: string;
  retryAfterSeconds?: number;
}

/** Marks whether the data shown came from the provider or from the synthetic demo generator. */
export type DataOrigin = "provider" | "demo";
