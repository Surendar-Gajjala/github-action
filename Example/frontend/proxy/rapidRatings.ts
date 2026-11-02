// Same-origin proxy for the RapidRatings data API, shared by the Vite dev/preview servers
// (vite.config.ts) and the Netlify Edge Function (netlify/edge-functions/rapidratings.ts).
//
// The browser cannot call RapidRatings itself: the data host sends no
// Access-Control-Allow-Origin header, and the OAuth client secret must never reach the bundle.
// So the browser calls `/rapidratings/<resource>/...` and this handler:
//   - obtains an access token with the client credentials (POST /oauth/token/, Basic auth),
//     caches it in memory and renews it when fewer than 60 s remain;
//   - forwards the GET with `Authorization: Bearer <token>`;
//   - on a 403 (RapidRatings' answer to an invalid or expired token) discards the token,
//     gets a new one and retries exactly once.
// Only GET on /companies/... and /portfolios/... is forwarded (no open proxy, never the token
// endpoint). Errors raised here carry `X-RapidRatings-Proxy-Error` and a `{ code, message }`
// body; RapidRatings responses pass through. Neither ever contains the secret or the token.
//
// Web APIs only (fetch, Request, Response, URL, btoa): runs on Node 22 and on Deno.

export interface RapidRatingsProxyConfig {
  /** e.g. https://rridata.sandbox.rrilabs.co/v2.0 (https, with the version path). */
  baseUrl?: string;
  clientId?: string;
  clientSecret?: string;
  /** Injected in tests. */
  fetch?: typeof fetch;
  now?: () => number;
}

export const RAPIDRATINGS_PREFIX = '/rapidratings';
export const DEFAULT_RAPIDRATINGS_BASE_URL = 'https://rridata.sandbox.rrilabs.co/v2.0';

const TIMEOUT_MS = 30_000;
const RENEW_MARGIN_MS = 60_000;
const DEFAULT_EXPIRES_IN_S = 1800;
/** `/companies/`, `/companies/{rrid}/`, `/companies/{rrid}/periods/[{id}/]`, same for ratings and portfolios. */
const ALLOWED_PATH = /^\/(?:companies|portfolios)\/(?:[A-Za-z0-9]+\/){0,3}$/;
const PASSED_HEADERS = ['content-type', 'x-ratelimit-limit', 'x-ratelimit-remaining', 'x-ratelimit-reset', 'retry-after'];

class ProxyFailure extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function proxyError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ code, message }), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-RapidRatings-Proxy-Error': code, 'Cache-Control': 'no-store' },
  });
}

/** The configured base URL without a trailing slash, or null when it is not https. */
function normaliseBaseUrl(value: string | undefined): string | null {
  try {
    const url = new URL((value?.trim() || DEFAULT_RAPIDRATINGS_BASE_URL).replace(/\/+$/, ''));
    return url.protocol === 'https:' ? `${url.origin}${url.pathname}` : null;
  } catch {
    return null;
  }
}

export function createRapidRatingsProxy(config: RapidRatingsProxyConfig): (request: Request) => Promise<Response> {
  const send = config.fetch ?? ((input, init) => fetch(input, init));
  const now = config.now ?? Date.now;
  const baseUrl = normaliseBaseUrl(config.baseUrl);
  const clientId = config.clientId?.trim();
  const clientSecret = config.clientSecret?.trim();

  let cached: { token: string; expiresAt: number } | null = null;
  let inFlight: Promise<string> | null = null;

  async function call(url: string, init: RequestInit): Promise<Response> {
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), TIMEOUT_MS);
    try {
      return await send(url, { ...init, redirect: 'manual', signal: abort.signal });
    } catch {
      throw abort.signal.aborted
        ? new ProxyFailure(504, 'UPSTREAM_TIMEOUT', 'RapidRatings did not respond in time.')
        : new ProxyFailure(502, 'UPSTREAM_UNREACHABLE', 'RapidRatings could not be reached.');
    } finally {
      clearTimeout(timer);
    }
  }

  async function requestToken(): Promise<string> {
    const response = await call(`${baseUrl}/oauth/token/`, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${btoa(`${clientId}:${clientSecret}`)}`,
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
      body: 'grant_type=client_credentials',
    });
    if (response.status === 400 || response.status === 401) {
      // Never echo the token endpoint's body.
      throw new ProxyFailure(
        502,
        'AUTH_FAILED',
        'RapidRatings rejected the client credentials. Check RAPIDRATINGS_CLIENT_ID and RAPIDRATINGS_CLIENT_SECRET.',
      );
    }
    if (!response.ok) {
      throw new ProxyFailure(502, 'TOKEN_UNAVAILABLE', `RapidRatings could not issue an access token (${response.status}).`);
    }
    const body = (await response.json().catch(() => null)) as { access_token?: unknown; expires_in?: unknown } | null;
    if (typeof body?.access_token !== 'string' || !body.access_token) {
      throw new ProxyFailure(502, 'TOKEN_UNAVAILABLE', 'RapidRatings returned an unexpected token response.');
    }
    const expiresIn = typeof body.expires_in === 'number' && body.expires_in > 0 ? body.expires_in : DEFAULT_EXPIRES_IN_S;
    cached = { token: body.access_token, expiresAt: now() + expiresIn * 1000 };
    return cached.token;
  }

  /** The cached token, or a new one; concurrent callers share one token request. */
  function getToken(force: boolean): Promise<string> {
    if (force) cached = null;
    if (cached && now() < cached.expiresAt - RENEW_MARGIN_MS) return Promise.resolve(cached.token);
    inFlight ??= requestToken().finally(() => {
      inFlight = null;
    });
    return inFlight;
  }

  const get = async (url: string, force: boolean) =>
    call(url, { method: 'GET', headers: { Accept: 'application/json', Authorization: `Bearer ${await getToken(force)}` } });

  return async (request) => {
    if (request.method !== 'GET') {
      return proxyError(405, 'PROXY_METHOD_NOT_ALLOWED', `Method ${request.method} is not allowed.`);
    }
    const incoming = new URL(request.url);
    const path = incoming.pathname.startsWith(`${RAPIDRATINGS_PREFIX}/`) ? incoming.pathname.slice(RAPIDRATINGS_PREFIX.length) : '';
    if (!ALLOWED_PATH.test(path)) {
      return proxyError(400, 'PROXY_INVALID_PATH', 'The requested RapidRatings path is not allowed.');
    }
    if (!clientId || !clientSecret) {
      const missing = [!clientId && 'RAPIDRATINGS_CLIENT_ID', !clientSecret && 'RAPIDRATINGS_CLIENT_SECRET'].filter(Boolean).join(', ');
      return proxyError(500, 'PROXY_NOT_CONFIGURED', `The RapidRatings proxy is not configured: missing ${missing}.`);
    }
    if (!baseUrl) {
      return proxyError(500, 'PROXY_INVALID_HOST', 'The RapidRatings proxy is misconfigured: RAPIDRATINGS_API_BASE_URL must be an https URL.');
    }

    const target = `${baseUrl}${path}${incoming.search}`;
    try {
      let upstream = await get(target, false);
      if (upstream.status === 403) upstream = await get(target, true);

      const headers = new Headers({ 'Cache-Control': 'no-store' });
      for (const name of PASSED_HEADERS) {
        const value = upstream.headers.get(name);
        if (value) headers.set(name, value);
      }
      return new Response(await upstream.arrayBuffer(), { status: upstream.status, headers });
    } catch (error) {
      if (error instanceof ProxyFailure) return proxyError(error.status, error.code, error.message);
      return proxyError(502, 'UPSTREAM_UNREACHABLE', 'RapidRatings could not be reached.');
    }
  };
}
