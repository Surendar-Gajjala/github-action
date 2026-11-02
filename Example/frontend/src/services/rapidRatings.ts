import axios from 'axios';

import type { RapidRatingsCompany, RapidRatingsDetails, RapidRatingsSearchResult } from '../types/rapidRatings';
import { mapCompany, mapPeriod, mapRating } from './rapidRatingsMapper';
import { UserFacingError } from './userFacingError';

/**
 * Client for the RapidRatings data API. The browser calls the same-origin `/rapidratings`
 * path; the Vite dev/preview servers and the Netlify Edge Function (proxy/rapidRatings.ts)
 * add the access token there, so no credential or token is ever sent from, or exposed to,
 * the browser. RapidRatings itself does not allow cross-origin browser calls.
 */
const rapidRatingsClient = axios.create({
  baseURL: import.meta.env.VITE_RAPIDRATINGS_API_BASE_URL ?? '/rapidratings',
  headers: { Accept: 'application/json' },
  timeout: 60_000,
});

/** A failed RapidRatings request, with a message that never contains credentials or tokens. */
export class RapidRatingsError extends UserFacingError {
  constructor(message: string, status: number | null = null) {
    super(message, status);
    this.name = 'RapidRatingsError';
  }
}

/** RapidRatings list envelope. */
interface ListResponse {
  count?: unknown;
  next?: unknown;
  results?: unknown;
}

type Json = Record<string, unknown>;
const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null && !Array.isArray(value);

/** RapidRatings needs at least 3 characters for a name search. */
const MIN_SEARCH_LENGTH = 3;
/** Name search: at most 5 pages of 20 companies. */
const MAX_SEARCH_PAGES = 5;
/** Periods and ratings: 10 per page; 50 pages is far beyond any observed history. */
const MAX_HISTORY_PAGES = 50;

/** Words dropped from the end of a company name before searching and comparing. */
const LEGAL_SUFFIXES = new Set([
  'inc', 'incorporated', 'corp', 'corporation', 'co', 'company', 'ltd', 'limited', 'llc', 'plc',
  'ag', 'gmbh', 'sa', 'nv', 'bv', 'kk', 'oy', 'ab', 'spa', 'srl', 'pte', 'pty',
]);

const words = (name: string) =>
  name
    .toLowerCase()
    .replace(/&/g, ' and ')
    .split(/[^a-z0-9]+/)
    .filter(Boolean);

function withoutLegalSuffixes(parts: string[]): string[] {
  const kept = [...parts];
  while (kept.length > 1 && LEGAL_SUFFIXES.has(kept[kept.length - 1])) kept.pop();
  return kept;
}

/** Comparable form of a company name: case, punctuation and trailing legal suffixes ignored. */
export function normaliseCompanyName(name: string): string {
  return withoutLegalSuffixes(words(name)).join(' ');
}

/**
 * The text sent as RapidRatings' `name` filter (a case-insensitive "contains"): the name
 * without trailing legal suffixes, so "Sunex Inc" also finds "Sunex Inc." and "Sunex, Inc".
 */
export function searchTermFor(name: string): string | null {
  const original = name.trim().split(/\s+/);
  while (original.length > 1 && LEGAL_SUFFIXES.has(original[original.length - 1].toLowerCase().replace(/[^a-z]/g, ''))) {
    original.pop();
  }
  const term = original.join(' ').replace(/[,.]+$/, '');
  if (term.length >= MIN_SEARCH_LENGTH) return term;
  return name.trim().length >= MIN_SEARCH_LENGTH ? name.trim() : null;
}

/**
 * The proxy path of a RapidRatings `next` URL. `next` is an absolute URL on the RapidRatings
 * host; only its resource path and query are kept, so paging always goes through the proxy.
 */
export function toProxyPath(next: string): string | null {
  try {
    const url = new URL(next);
    const match = url.pathname.match(/\/(?:companies|portfolios)\/.*$/);
    return match ? `${match[0]}${url.search}` : null;
  } catch {
    return null;
  }
}

async function get<T>(path: string, params?: Record<string, string>): Promise<T> {
  try {
    const { data } = await rapidRatingsClient.get<T>(path, { params });
    return data;
  } catch (error) {
    throw toRapidRatingsError(error);
  }
}

/** Every `results` element of a list, following `next` until it is null (never building page numbers). */
async function getAllPages(path: string, maxPages: number, params?: Record<string, string>): Promise<Json[]> {
  const results: Json[] = [];
  let next: string | null = path;
  let first = true;
  for (let page = 0; next !== null && page < maxPages; page++) {
    const data: ListResponse = await get<ListResponse>(next, first ? params : undefined);
    first = false;
    if (!isObject(data) || !Array.isArray(data.results)) {
      throw new RapidRatingsError('Unexpected response from RapidRatings.');
    }
    results.push(...data.results.filter(isObject));
    next = typeof data.next === 'string' && data.next ? toProxyPath(data.next) : null;
  }
  return results;
}

/**
 * RapidRatings companies whose name contains the Z2 company name (legal suffixes removed),
 * plus the one whose name equals it, if any. Nothing is linked or written.
 */
export async function findRapidRatingsCompanies(companyName: string): Promise<RapidRatingsSearchResult> {
  const searchTerm = searchTermFor(companyName);
  if (searchTerm === null) return { searchTerm, candidates: [], exactMatch: null };

  let raw: Json[];
  try {
    raw = await getAllPages('/companies/', MAX_SEARCH_PAGES, { name: searchTerm });
  } catch (error) {
    // RapidRatings answers a company list page that does not exist with 404.
    if (error instanceof RapidRatingsError && error.status === 404) raw = [];
    else throw error;
  }
  const candidates = raw.map(mapCompany).filter((company): company is RapidRatingsCompany & { rrid: string } => company.rrid !== null);
  const wanted = normaliseCompanyName(companyName);
  const exactMatch = candidates.find((c) => c.sourceCompanyName !== null && normaliseCompanyName(c.sourceCompanyName) === wanted) ?? null;
  return { searchTerm, candidates, exactMatch };
}

/** One company's details with its full period and rating history (read in parallel). */
export async function fetchRapidRatingsDetails(rrid: string): Promise<RapidRatingsDetails> {
  const base = `/companies/${encodeURIComponent(rrid)}`;
  const [company, periods, ratings] = await Promise.all([
    get<unknown>(`${base}/`),
    getAllPages(`${base}/periods/`, MAX_HISTORY_PAGES),
    getAllPages(`${base}/ratings/`, MAX_HISTORY_PAGES),
  ]);
  if (!isObject(company)) throw new RapidRatingsError('Unexpected response from RapidRatings.');
  return {
    company: mapCompany(company),
    periods: periods.map(mapPeriod),
    ratings: ratings.map(mapRating),
    retrievedAt: new Date().toISOString(),
  };
}

/** Maps a transport failure to a safe, user-facing message by status. */
function toRapidRatingsError(error: unknown): RapidRatingsError {
  if (error instanceof RapidRatingsError) return error;
  if (!axios.isAxiosError(error)) return new RapidRatingsError('The RapidRatings request failed.');
  const status = error.response?.status ?? null;
  // Errors raised by the /rapidratings proxy itself (e.g. missing credentials) carry this
  // header and a credential-free `{ code, message }` body.
  const proxyMessage = (error.response?.data as { message?: unknown } | undefined)?.message;
  if (error.response?.headers?.['x-rapidratings-proxy-error'] && typeof proxyMessage === 'string') {
    return new RapidRatingsError(proxyMessage, status);
  }
  if (status === null) {
    return new RapidRatingsError(error.code === 'ECONNABORTED' ? 'RapidRatings did not respond in time.' : 'Cannot reach RapidRatings.');
  }
  if (status === 400) return new RapidRatingsError('RapidRatings rejected the request (400).', status);
  if (status === 401 || status === 403) return new RapidRatingsError(`RapidRatings denied access to this data (${status}).`, status);
  if (status === 404) return new RapidRatingsError('RapidRatings has no data at this address (404).', status);
  if (status === 429) return new RapidRatingsError('The RapidRatings rate limit was reached (429). Try again shortly.', status);
  if (status >= 500) return new RapidRatingsError(`RapidRatings is unavailable (${status}).`, status);
  return new RapidRatingsError(`The RapidRatings request failed (${status}).`, status);
}
