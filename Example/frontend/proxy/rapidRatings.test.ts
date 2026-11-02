// @vitest-environment node
import { createRapidRatingsProxy } from './rapidRatings';

const BASE = 'https://rr.example/v2.0';
const SECRET = 'top-secret-value';

type Call = { url: string; init: RequestInit };

/** A fake RapidRatings: token endpoint plus data endpoints answering with the given statuses in turn. */
function fakeUpstream(options: { tokenStatus?: number; dataStatuses?: number[]; expiresIn?: number } = {}) {
  const calls: Call[] = [];
  let tokens = 0;
  const dataStatuses = [...(options.dataStatuses ?? [200])];
  const fetch = (async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input);
    calls.push({ url, init });
    if (url.endsWith('/oauth/token/')) {
      if (options.tokenStatus && options.tokenStatus !== 200) {
        return new Response(JSON.stringify({ error: 'invalid_client', secret_echo: SECRET }), { status: options.tokenStatus });
      }
      tokens += 1;
      return Response.json({ access_token: `token-${tokens}`, expires_in: options.expiresIn ?? 1800, token_type: 'Bearer', scope: 'read' });
    }
    const status = dataStatuses.length > 1 ? dataStatuses.shift()! : dataStatuses[0];
    return new Response(JSON.stringify({ count: 0, next: null, previous: null, results: [] }), {
      status,
      headers: { 'Content-Type': 'application/json', 'X-Ratelimit-Remaining': '99', 'Set-Cookie': 'a=b' },
    });
  }) as typeof globalThis.fetch;
  const dataCalls = () => calls.filter((c) => !c.url.endsWith('/oauth/token/'));
  const tokenCalls = () => calls.filter((c) => c.url.endsWith('/oauth/token/'));
  return { fetch, calls, dataCalls, tokenCalls };
}

const request = (path: string, method = 'GET') => new Request(`http://localhost${path}`, { method });

function proxy(upstream: ReturnType<typeof fakeUpstream>, now = () => 0) {
  return createRapidRatingsProxy({ baseUrl: BASE, clientId: 'client', clientSecret: SECRET, fetch: upstream.fetch, now });
}

describe('RapidRatings proxy', () => {
  it('gets a token with Basic client credentials and forwards the GET with the Bearer token', async () => {
    const upstream = fakeUpstream();
    const response = await proxy(upstream)(request('/rapidratings/companies/?name=Sunex'));

    expect(response.status).toBe(200);
    const [token] = upstream.tokenCalls();
    expect(token.init.method).toBe('POST');
    expect((token.init.headers as Record<string, string>).Authorization).toBe(`Basic ${btoa(`client:${SECRET}`)}`);
    expect(token.init.body).toBe('grant_type=client_credentials');
    const [data] = upstream.dataCalls();
    expect(data.url).toBe(`${BASE}/companies/?name=Sunex`);
    expect((data.init.headers as Record<string, string>).Authorization).toBe('Bearer token-1');
  });

  it('passes rate-limit headers through but never cookies', async () => {
    const response = await proxy(fakeUpstream())(request('/rapidratings/companies/'));
    expect(response.headers.get('x-ratelimit-remaining')).toBe('99');
    expect(response.headers.get('set-cookie')).toBeNull();
  });

  it('reuses the cached token and renews it when fewer than 60 seconds remain', async () => {
    const upstream = fakeUpstream({ expiresIn: 1800 });
    let time = 0;
    const handle = proxy(upstream, () => time);

    await handle(request('/rapidratings/companies/'));
    time = 1_000_000; // 1000 s later: still valid
    await handle(request('/rapidratings/companies/'));
    expect(upstream.tokenCalls()).toHaveLength(1);

    time = 1_741_000; // 1741 s: 59 s left
    await handle(request('/rapidratings/companies/'));
    expect(upstream.tokenCalls()).toHaveLength(2);
  });

  it('shares one token request between concurrent calls', async () => {
    const upstream = fakeUpstream();
    const handle = proxy(upstream);
    await Promise.all([handle(request('/rapidratings/companies/')), handle(request('/rapidratings/portfolios/'))]);
    expect(upstream.tokenCalls()).toHaveLength(1);
  });

  it('on 403 gets a new token and retries exactly once', async () => {
    const upstream = fakeUpstream({ dataStatuses: [403, 200] });
    const response = await proxy(upstream)(request('/rapidratings/companies/ABC1234/'));
    expect(response.status).toBe(200);
    expect(upstream.tokenCalls()).toHaveLength(2);
    expect(upstream.dataCalls().map((c) => (c.init.headers as Record<string, string>).Authorization)).toEqual(['Bearer token-1', 'Bearer token-2']);
  });

  it('returns a second 403 instead of retrying again', async () => {
    const upstream = fakeUpstream({ dataStatuses: [403] });
    const response = await proxy(upstream)(request('/rapidratings/companies/ABC1234/'));
    expect(response.status).toBe(403);
    expect(upstream.dataCalls()).toHaveLength(2);
  });

  it('reports rejected credentials without echoing the token response or the secret', async () => {
    const upstream = fakeUpstream({ tokenStatus: 401 });
    const response = await proxy(upstream)(request('/rapidratings/companies/'));
    const body = await response.text();

    expect(response.status).toBe(502);
    expect(response.headers.get('x-rapidratings-proxy-error')).toBe('AUTH_FAILED');
    expect(body).not.toContain(SECRET);
    expect(upstream.tokenCalls()).toHaveLength(1);
    expect(upstream.dataCalls()).toHaveLength(0);
  });

  it.each([
    ['/rapidratings/oauth/token/', 'the token endpoint'],
    ['/rapidratings/companies', 'a path without the trailing slash'],
    ['/rapidratings/companies/../oauth/token/', 'path traversal'],
    ['/rapidratings/other/', 'an unknown resource'],
  ])('rejects %s (%s) without calling RapidRatings', async (path) => {
    const upstream = fakeUpstream();
    const response = await proxy(upstream)(request(path));
    expect(response.status).toBe(400);
    expect(upstream.calls).toHaveLength(0);
  });

  it('only allows GET', async () => {
    const upstream = fakeUpstream();
    const response = await proxy(upstream)(request('/rapidratings/companies/', 'POST'));
    expect(response.status).toBe(405);
    expect(upstream.calls).toHaveLength(0);
  });

  it('names the missing settings when credentials are not configured', async () => {
    const handle = createRapidRatingsProxy({ fetch: fakeUpstream().fetch });
    const response = await handle(request('/rapidratings/companies/'));
    expect(response.status).toBe(500);
    expect(await response.json()).toMatchObject({ code: 'PROXY_NOT_CONFIGURED' });
  });

  it('refuses a non-https base URL', async () => {
    const handle = createRapidRatingsProxy({ baseUrl: 'http://rr.example/v2.0', clientId: 'c', clientSecret: 's', fetch: fakeUpstream().fetch });
    const response = await handle(request('/rapidratings/companies/'));
    expect(await response.json()).toMatchObject({ code: 'PROXY_INVALID_HOST' });
  });
});
