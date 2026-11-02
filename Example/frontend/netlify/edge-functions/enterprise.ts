// Netlify Edge Function: the production counterpart of the Vite `/enterprise` proxy in
// vite.config.ts. Forwards /enterprise/* to the Enterprise platform API
// (`<ENTERPRISE_API_HOST>/api/v1/platform/*`) and adds the Bearer token server-side, so the
// token never reaches the browser or the published files.
//
// Netlify environment variables (Site configuration → Environment variables; redeploy after
// changing them):
//   ENTERPRISE_API_HOST   https://<enterprise-host>  (origin only, https)
//   ENTERPRISE_API_TOKEN  Bearer token (expires after ~24h)
//
// Errors raised by this proxy itself carry an `X-Enterprise-Proxy-Error` header and a JSON
// `{ code, message }` body; upstream responses (including upstream errors) pass through
// unchanged. Neither ever contains the token.

declare const Netlify: { env: { get(name: string): string | undefined } };

const PREFIX = '/enterprise';
const UPSTREAM_BASE_PATH = '/api/v1/platform';
/** Netlify requires response headers within ~40 s; stop waiting for upstream headers just before that. */
const UPSTREAM_TIMEOUT_MS = 38_000;
const ALLOWED_METHODS = new Set(['GET', 'POST']);

function proxyError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ code, message }), {
    status,
    headers: { 'Content-Type': 'application/json', 'X-Enterprise-Proxy-Error': code, 'Cache-Control': 'no-store' },
  });
}

/** The configured Enterprise origin, or null when missing / not an https origin. */
function upstreamOrigin(value: string | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value.trim());
    return url.protocol === 'https:' ? url.origin : null;
  } catch {
    return null;
  }
}

export default async (request: Request): Promise<Response> => {
  const rawHost = Netlify.env.get('ENTERPRISE_API_HOST');
  const token = Netlify.env.get('ENTERPRISE_API_TOKEN')?.trim();
  const origin = upstreamOrigin(rawHost);
  if (!rawHost || !token) {
    const missing = [!rawHost && 'ENTERPRISE_API_HOST', !token && 'ENTERPRISE_API_TOKEN'].filter(Boolean).join(', ');
    return proxyError(500, 'PROXY_NOT_CONFIGURED', `The Enterprise proxy is not configured: missing ${missing}.`);
  }
  if (!origin) {
    return proxyError(500, 'PROXY_INVALID_HOST', 'The Enterprise proxy is misconfigured: ENTERPRISE_API_HOST must be an https URL.');
  }
  if (!ALLOWED_METHODS.has(request.method)) {
    return proxyError(405, 'PROXY_METHOD_NOT_ALLOWED', `Method ${request.method} is not allowed.`);
  }

  // Only ever call <origin>/api/v1/platform/... on the configured host (no open proxy).
  const incoming = new URL(request.url);
  const rest = incoming.pathname.slice(PREFIX.length);
  const target = new URL(`${UPSTREAM_BASE_PATH}${rest}${incoming.search}`, origin);
  if (target.origin !== origin || !target.pathname.startsWith(`${UPSTREAM_BASE_PATH}/`)) {
    return proxyError(400, 'PROXY_INVALID_PATH', 'The requested Enterprise path is not allowed.');
  }

  // Forward only what the API needs; never the visitor's cookies or other headers.
  const headers = new Headers({ Accept: 'application/json', Authorization: `Bearer ${token}` });
  const contentType = request.headers.get('Content-Type');
  if (contentType) headers.set('Content-Type', contentType);

  // The timeout covers waiting for response headers only: large responses (the hierarchy
  // graph is ~2 MB) keep streaming after that without being cut off.
  const abort = new AbortController();
  const timer = setTimeout(() => abort.abort(), UPSTREAM_TIMEOUT_MS);
  let upstream: Response;
  try {
    upstream = await fetch(target, {
      method: request.method,
      headers,
      body: request.method === 'POST' ? await request.text() : undefined,
      redirect: 'manual',
      signal: abort.signal,
    });
  } catch {
    return abort.signal.aborted
      ? proxyError(504, 'UPSTREAM_TIMEOUT', 'The Enterprise API did not respond in time.')
      : proxyError(502, 'UPSTREAM_UNREACHABLE', 'The Enterprise API could not be reached.');
  } finally {
    clearTimeout(timer);
  }

  // fetch() has already decoded the body, so drop the upstream encoding/length headers.
  const responseHeaders = new Headers(upstream.headers);
  responseHeaders.delete('content-encoding');
  responseHeaders.delete('content-length');
  responseHeaders.delete('set-cookie');
  return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
};

export const config = { path: '/enterprise/*' };
