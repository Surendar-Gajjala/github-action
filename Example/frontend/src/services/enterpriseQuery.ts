import axios from 'axios';

import type { PageResponse } from '../types/page';
import { UserFacingError } from './userFacingError';

export { getErrorMessage } from './userFacingError';

/**
 * Client for the Enterprise Query Config V2 API. In dev the base is a same-origin path
 * (`/enterprise`) that the Vite proxy forwards to the hosted server, injecting the Bearer
 * token server-side — so no token is sent from, or exposed to, the browser here.
 */
const enterpriseClient = axios.create({
  baseURL: import.meta.env.VITE_ENTERPRISE_API_BASE_URL ?? '/enterprise',
  headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
  timeout: 60_000,
});

const EXECUTE_PATH = '/core/query-config-v2/execute';

/** Enterprise response envelope; only `results` and the paging metadata are used. */
interface ExecuteResponse {
  results?: unknown;
  totalElements?: number | null;
  hasMore?: boolean | null;
}

/** A failed Enterprise query, carrying a user-facing message that never contains credentials. */
export class EnterpriseQueryError extends UserFacingError {
  constructor(message: string, status: number | null = null) {
    super(message, status);
    this.name = 'EnterpriseQueryError';
  }
}

/** GET an Enterprise resource (path relative to the platform base). Errors are token-free {@link EnterpriseQueryError}s. */
export async function enterpriseGet<T>(path: string): Promise<T> {
  try {
    const { data } = await enterpriseClient.get<T>(path);
    return data;
  } catch (error) {
    throw toEnterpriseError(error);
  }
}

/** POST a JSON payload to an Enterprise endpoint. Errors are token-free {@link EnterpriseQueryError}s. */
export async function enterprisePost<T>(
  path: string,
  body: object,
  options: { params?: Record<string, number>; timeout?: number } = {},
): Promise<T> {
  try {
    const { data } = await enterpriseClient.post<T>(path, body, options);
    return data;
  } catch (error) {
    throw toEnterpriseError(error);
  }
}

/**
 * Executes one page of a query-config payload (one of the JSON files under
 * `src/queries/`) against the Enterprise execute endpoint
 * and maps each `results` row with `mapRow`. `page` is zero-based.
 */
export async function executeQuery<T>(
  query: object,
  page: number,
  size: number,
  mapRow: (row: Record<string, unknown>) => T,
): Promise<PageResponse<T>> {
  const data = await enterprisePost<ExecuteResponse>(EXECUTE_PATH, query, { params: { page, size } });

  if (typeof data !== 'object' || data === null || !Array.isArray(data.results)) {
    throw new EnterpriseQueryError('Unexpected response from the Enterprise API.');
  }

  const objects = data.results
    .filter((row): row is Record<string, unknown> => typeof row === 'object' && row !== null && !Array.isArray(row))
    .map(mapRow);

  const totalObjects = typeof data.totalElements === 'number' ? data.totalElements : null;
  const totalPages = totalObjects === null ? null : Math.max(1, Math.ceil(totalObjects / size));
  const hasMore =
    typeof data.hasMore === 'boolean'
      ? data.hasMore
      : totalObjects !== null
        ? (page + 1) * size < totalObjects
        : objects.length === size;

  return { count: objects.length, objects, page, size, totalObjects, totalPages, hasMore };
}

/** Maps a transport failure to a safe, user-facing message by status. */
function toEnterpriseError(error: unknown): EnterpriseQueryError {
  if (error instanceof EnterpriseQueryError) return error;
  if (!axios.isAxiosError(error)) {
    return new EnterpriseQueryError('The Enterprise API request failed.');
  }
  const status = error.response?.status ?? null;
  // Errors raised by the deployment's /enterprise proxy itself (not by Enterprise) carry this
  // header and a token-free `{ code, message }` body, e.g. missing server configuration.
  const proxyMessage = (error.response?.data as { message?: unknown } | undefined)?.message;
  if (error.response?.headers?.['x-enterprise-proxy-error'] && typeof proxyMessage === 'string') {
    return new EnterpriseQueryError(proxyMessage, status);
  }
  if (status === null) {
    return new EnterpriseQueryError(
      error.code === 'ECONNABORTED'
        ? 'The Enterprise API did not respond in time.'
        : 'Cannot reach the Enterprise API.',
    );
  }
  if (status === 400) return new EnterpriseQueryError('The Enterprise API rejected the query (400).', status);
  if (status === 401) {
    return new EnterpriseQueryError(
      'The Enterprise API token is missing or expired (401). Update ENTERPRISE_API_TOKEN in frontend/.env and restart the dev server.',
      status,
    );
  }
  if (status === 403) return new EnterpriseQueryError('Access to this Enterprise data was denied (403).', status);
  if (status === 429) return new EnterpriseQueryError('The Enterprise API rate limit was reached (429). Try again shortly.', status);
  if (status >= 500) return new EnterpriseQueryError(`The Enterprise API is unavailable (${status}).`, status);
  return new EnterpriseQueryError(`The Enterprise API request failed (${status}).`, status);
}
