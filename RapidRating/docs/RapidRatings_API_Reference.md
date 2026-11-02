# RapidRatings API: Complete Integration Reference (Sandbox)

**Project:** Data Integrations, integration #1: RapidRatings
**Environment:** Sandbox (`rridata.sandbox.rrilabs.co`, `api.sandbox.rrilabs.co`)
**Verified:** 2026-10-28. Every endpoint was called live with the project credentials. Responses are real unless marked *(from OpenAPI spec)*.
**Config:** all URLs and credentials are read from `.env` (`RR_*` variables). Secrets are never written in this document.

---

## Contents

1. Quick reference (all endpoints on one page)
2. Configuration (`.env`)
3. Authentication: protocol
4. Authentication: implementation (tested Node.js client)
5. JSON schemas (all request/response objects)
6. Endpoint: Authentication (`RR_TOKEN_URL`)
7. Endpoint: Companies (`RR_COMPANIES_URL`)
8. Endpoint: Portfolios (`RR_PORTFOLIOS_URL`)
9. Endpoint: Periods / Financials (`RR_PERIODS_URL`)
10. Endpoint: Ratings (`RR_RATINGS_URL`)
11. Endpoint: Outreach Requests (`RR_OUTREACH_URL`)
12. Cross-API behaviour and gotchas

---

# 1. Quick reference

| API | Method | URL | Auth | Paging |
|---|---|---|---|---|
| Token | `POST` | `https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` | Basic (client id:secret) | n/a |
| Companies list | `GET` | `https://rridata.sandbox.rrilabs.co/v2.0/companies/` | Bearer | `page`/`next`, 20 per page |
| Company detail | `GET` | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/` | Bearer | n/a |
| Portfolios list | `GET` | `https://rridata.sandbox.rrilabs.co/v2.0/portfolios/` | Bearer | `page`/`next` |
| Portfolio detail | `GET` | `https://rridata.sandbox.rrilabs.co/v2.0/portfolios/{id}/` | Bearer | n/a |
| Periods list | `GET` | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/` | Bearer | `page`/`next`, 10 per page |
| Period detail | `GET` | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/{periodId}/` | Bearer | n/a |
| Ratings list | `GET` | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/` | Bearer | `page`/`next`, 10 per page |
| Rating detail | `GET` | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/{ratingId}/` | Bearer | n/a |
| Outreach list | `GET` | `https://api.sandbox.rrilabs.co/outreach-requests` | Bearer | `limit`/`cursor` |
| Outreach create | `POST` | `https://api.sandbox.rrilabs.co/outreach-requests` | Bearer | n/a (**sends real emails**) |
| Outreach detail | `GET` | `https://api.sandbox.rrilabs.co/outreach-requests/{id}` | Bearer | n/a |
| Outreach comms | `GET` | `https://api.sandbox.rrilabs.co/outreach-requests/{id}/communications` | Bearer | n/a |

### How the data connects

```
oauth/token  ->  Bearer token (30 min, valid on BOTH hosts)
     |
portfolios/27070 --> companies/?portfolio=27070 --> companies/{rrid}/ --+--> periods/{eqyYear}{period}/  (financials)
                                                                      +--> ratings/{eqyYear}{period}/  (FHR, CHS, PD)
outreach-requests --(is_rated = true, rrid)--> companies/{rrid}/
```

Keys: **company = `rrid`** (e.g. `XAZPzdJ`). **Period and rating = (`rrid`, `id`)** where `id` = `eqyYear`+`period` (e.g. `2019YE`); periods and ratings join one-to-one on it.

---

# 2. Configuration (`.env`)

```dotenv
# --- Credentials (OAuth client credentials) ---
RR_CLIENT_ID=<client id>
RR_CLIENT_SECRET=<client secret>
RR_BASIC_AUTH=<base64(RR_CLIENT_ID:RR_CLIENT_SECRET)>

# --- Portfolio ---
RR_PORTFOLIO_ID=27070

# --- Base URLs ---
RR_API_BASE_URL=https://rridata.sandbox.rrilabs.co/v2.0
RR_OUTREACH_BASE_URL=https://api.sandbox.rrilabs.co

# --- Endpoints ---
RR_TOKEN_URL=https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/
RR_COMPANIES_URL=https://rridata.sandbox.rrilabs.co/v2.0/companies/
RR_PORTFOLIOS_URL=https://rridata.sandbox.rrilabs.co/v2.0/portfolios/
RR_PERIODS_URL=https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/
RR_RATINGS_URL=https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/
RR_OUTREACH_URL=https://api.sandbox.rrilabs.co/outreach-requests
```

`.env` is git-ignored; `.env.example` is the safe-to-commit template.

---

# 3. Authentication: protocol

| Item | Value |
|---|---|
| Standard | OAuth 2.0, **Client Credentials** grant |
| Token URL | `POST https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` (trailing slash required) |
| Client auth | `Authorization: Basic base64(client_id:client_secret)` |
| Body | `grant_type=client_credentials` (form-urlencoded; JSON also accepted) |
| Returns | `access_token`, `token_type: "Bearer"`, `expires_in: 1800`, `scope: "uid introspection openid"` |
| Lifetime | **30 minutes** |
| Refresh token | **None**: repeat the same request to renew |
| Use | `Authorization: Bearer <access_token>` on **every** call, on **both** hosts |

### Flow

```
Client                                       RapidRatings
  | POST /oauth/token/                             |
  |   Authorization: Basic <b64(id:secret)>        |
  |   grant_type=client_credentials  ------------> |
  | <------------  200 {access_token, expires_in}  |
  |                                                |
  | GET /v2.0/companies/                           |
  |   Authorization: Bearer <token>  ------------> |
  | <------------  200 {count, next, results}      |
  |   ... reuse token until ~60 s before expiry    |
  |                                                |
  | (token expired) GET ...  -------------------->  |
  | <------------  403 (rridata) / 401 (outreach)  |
  |   -> request a new token, retry the call once  |
```

### Auth rules for the client

1. **Cache** the token in memory, and renew when fewer than 60 s are left (`expiresAt = now + expires_in*1000`).
2. On **401 or 403** from a data call, get a **new token and retry once**. rridata answers an expired token with **403 `Not Authorized.`**; outreach answers with **401**.
3. A missing `grant_type` returns **401** (not 400), so don't read every 401 on the token call as bad credentials.
4. Never log `RR_CLIENT_SECRET`, `RR_BASIC_AUTH` or `access_token`.
5. Use timeouts (30 s), and retry `429`/`5xx` with backoff, honouring `Retry-After`.

---

# 4. Authentication: implementation (tested Node.js client)

Node.js 18+ (uses built-in `fetch`), no dependencies. Covers token caching, auto-refresh, retry on expired tokens, 429/5xx backoff, both pagination styles, both error formats and helpers for every endpoint.

**Test run against the sandbox (2026-10-28):**

```
token ok: cPcI*** expires in s: 1800
cached: true
portfolio: z2Data Sandbox
companies in portfolio: 96 first: KawjbGJ Rite Aid Corporation FHR 35
Tyson periods: 26 ratings: 26
outreach requests: 0
after bad token, company: Tyson Foods, Inc.        <- 403 auto-refresh works (rridata)
after bad token, outreach ok: 0                    <- 401 auto-refresh works (outreach)
error handling: true 404 Not Found.
error handling: 400 Not a valid sort
```

### `rrClient.mjs`

```javascript
// RapidRatings API client: auth (token cache + auto-refresh), paging, retry.
// Node 18+ (built-in fetch). Reads config from .env (RR_* variables).
import fs from "node:fs";

// --- Minimal .env loader (no dependencies) ---
export function loadEnv(path = ".env") {
  for (const line of fs.readFileSync(path, "utf8").split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m && !(m[1] in process.env)) process.env[m[1]] = m[2];
  }
}

export class RapidRatingsClient {
  constructor({
    tokenUrl = process.env.RR_TOKEN_URL,
    clientId = process.env.RR_CLIENT_ID,
    clientSecret = process.env.RR_CLIENT_SECRET,
    apiBase = process.env.RR_API_BASE_URL,
    outreachBase = process.env.RR_OUTREACH_BASE_URL,
    timeoutMs = 30000,
    maxRetries = 3,
  } = {}) {
    if (!tokenUrl || !clientId || !clientSecret) throw new Error("Missing RR_TOKEN_URL / RR_CLIENT_ID / RR_CLIENT_SECRET");
    Object.assign(this, { tokenUrl, apiBase, outreachBase, timeoutMs, maxRetries });
    this.basic = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
    this.token = null;
    this.expiresAt = 0;
  }

  // --- Auth: OAuth2 client credentials, HTTP Basic client auth ---
  async getToken(force = false) {
    // Refresh 60s before expiry (tokens live 1800s, no refresh_token is issued)
    if (!force && this.token && Date.now() < this.expiresAt - 60_000) return this.token;
    const res = await this.#fetch(this.tokenUrl, {
      method: "POST",
      headers: { Authorization: `Basic ${this.basic}`, "Content-Type": "application/x-www-form-urlencoded" },
      body: "grant_type=client_credentials",
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok || !body.access_token) {
      throw new RapidRatingsError(`Token request failed (${res.status})`, res.status, body);
    }
    this.token = body.access_token;
    this.expiresAt = Date.now() + body.expires_in * 1000;
    return this.token;
  }

  // --- Authenticated request with token refresh + retry ---
  async request(url, { method = "GET", body, query } = {}) {
    if (query) url += (url.includes("?") ? "&" : "?") + new URLSearchParams(query);
    let refreshed = false;
    for (let attempt = 0; ; attempt++) {
      const res = await this.#fetch(url, {
        method,
        headers: {
          Authorization: `Bearer ${await this.getToken()}`,
          Accept: "application/json",
          ...(body ? { "Content-Type": "application/json" } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
      });
      // rridata returns 403 for an expired token, outreach returns 401: refresh once on either
      if ((res.status === 401 || res.status === 403) && !refreshed) {
        refreshed = true;
        await this.getToken(true);
        continue;
      }
      if ((res.status === 429 || res.status >= 502) && attempt < this.maxRetries) {
        const wait = Number(res.headers.get("retry-after")) || 2 ** attempt;
        await sleep(Math.min(wait, 60) * 1000);
        continue;
      }
      const data = await res.json().catch(() => null);
      if (!res.ok) throw new RapidRatingsError(errorMessage(data) ?? `HTTP ${res.status}`, res.status, data);
      return data;
    }
  }

  // --- rridata paging: follow `next` until null (never guess page numbers: past-the-end = 500) ---
  async *paginate(url, query) {
    let next = url, q = query;
    while (next) {
      const page = await this.request(next, { query: q });
      yield* page.results;
      next = page.next;
      q = undefined; // `next` already carries the filters
    }
  }

  // --- Outreach paging: cursor-based ---
  async *paginateCursor(url, query = {}) {
    let cursor;
    do {
      const page = await this.request(url, { query: { ...query, ...(cursor ? { cursor } : {}) } });
      yield* page.items;
      cursor = page.cursors?.next_cursor;
    } while (cursor);
  }

  // --- Endpoint helpers ---
  portfolios()           { return this.paginate(`${this.apiBase}/portfolios/`); }
  portfolio(id)          { return this.request(`${this.apiBase}/portfolios/${id}/`); }
  companies(filters)     { return this.paginate(`${this.apiBase}/companies/`, filters); }
  company(rrid)          { return this.request(`${this.apiBase}/companies/${rrid}/`); }
  periods(rrid)          { return this.paginate(`${this.apiBase}/companies/${rrid}/periods/`); }
  period(rrid, id)       { return this.request(`${this.apiBase}/companies/${rrid}/periods/${id}/`); }
  ratings(rrid)          { return this.paginate(`${this.apiBase}/companies/${rrid}/ratings/`); }
  rating(rrid, id)       { return this.request(`${this.apiBase}/companies/${rrid}/ratings/${id}/`); }
  outreachRequests(q)    { return this.paginateCursor(`${this.outreachBase}/outreach-requests`, { limit: 100, ...q }); }
  outreachRequest(id)    { return this.request(`${this.outreachBase}/outreach-requests/${id}`); }
  outreachComms(id)      { return this.request(`${this.outreachBase}/outreach-requests/${id}/communications`); }
  // Sends real emails to the counterparty: call only with explicit approval
  createOutreachRequest(payload) { return this.request(`${this.outreachBase}/outreach-requests`, { method: "POST", body: payload }); }

  async #fetch(url, init) {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), this.timeoutMs);
    try { return await fetch(url, { ...init, signal: ctl.signal }); }
    finally { clearTimeout(t); }
  }
}

export class RapidRatingsError extends Error {
  constructor(message, status, body) { super(message); this.status = status; this.body = body; }
}

// rridata: {"Request": {"message": ...}}  |  outreach: RFC 7807 {"detail": ...}
function errorMessage(data) {
  if (!data) return null;
  if (data.detail) return Array.isArray(data.detail) ? data.detail.join("; ") : data.detail;
  const scope = Object.values(data)[0];
  return scope?.message ?? null;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
```

### Usage

```javascript
import { loadEnv, RapidRatingsClient, RapidRatingsError } from "./rrClient.mjs";
loadEnv(".env");
const rr = new RapidRatingsClient();

// All companies in the configured portfolio (auto-paginates, 20/page)
for await (const c of rr.companies({ portfolio: process.env.RR_PORTFOLIO_ID })) {
  const latest = c.latestRatings[0];
  console.log(c.id, c.name, latest?.id, "FHR", latest?.fhr, "CHS", latest?.chs);
}

// Full history for one company
for await (const r of rr.ratings("XAZPzdJ")) console.log(r.id, r.fhr);
for await (const p of rr.periods("XAZPzdJ")) console.log(p.id, p.salesRevenue);

// Outreach (cursor paging)
for await (const o of rr.outreachRequests({ q: "is_rated=true" })) console.log(o.outreach_request_id, o.rrid);

// Errors
try { await rr.company("ZZZZZZZ"); }
catch (e) { if (e instanceof RapidRatingsError) console.error(e.status, e.message, e.body); }
```

### Same auth in PowerShell (quick tests)

```powershell
$basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$($env:RR_CLIENT_ID):$($env:RR_CLIENT_SECRET)"))
$tok   = Invoke-RestMethod -Method Post -Uri $env:RR_TOKEN_URL `
           -Headers @{ Authorization = "Basic $basic" } `
           -Body @{ grant_type = "client_credentials" } -ContentType "application/x-www-form-urlencoded"
$h     = @{ Authorization = "Bearer $($tok.access_token)" }
Invoke-RestMethod -Uri "$($env:RR_COMPANIES_URL)?portfolio=$($env:RR_PORTFOLIO_ID)" -Headers $h
```

### Same auth in cURL (Git Bash)

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')
curl "$RR_COMPANIES_URL?portfolio=$RR_PORTFOLIO_ID" -H "Authorization: Bearer $TOKEN"
```

---
# 5. JSON schemas

JSON Schema (draft 2020-12) for every object the integration sends or receives. Built from the live sandbox responses and, for outreach, RapidRatings' OpenAPI spec. Types marked `["x","null"]` were seen as `null` in real data (or are documented as nullable).

### 5.1 TokenRequest (form body of `POST /oauth/token/`)

```json
{
  "$id": "TokenRequest",
  "type": "object",
  "required": ["grant_type"],
  "properties": {
    "grant_type": { "const": "client_credentials" }
  }
}
```
Header: `Authorization: Basic base64(client_id:client_secret)`.

### 5.2 TokenResponse

```json
{
  "$id": "TokenResponse",
  "type": "object",
  "required": ["access_token", "expires_in", "token_type"],
  "properties": {
    "access_token": { "type": "string" },
    "expires_in":   { "type": "integer", "description": "seconds, 1800 observed" },
    "token_type":   { "const": "Bearer" },
    "scope":        { "type": "string", "examples": ["uid introspection openid"] }
  }
}
```

### 5.3 RridataError (all rridata `/v2.0` errors)

```json
{
  "$id": "RridataError",
  "type": "object",
  "description": "Single key = scope: 'Request', 'Portfolio', 'Companies' or 'Not found'",
  "minProperties": 1,
  "maxProperties": 1,
  "additionalProperties": {
    "type": "object",
    "required": ["message"],
    "properties": {
      "message": { "type": "string" },
      "info":    { "type": "object" }
    }
  }
}
```

### 5.4 Page<T> (rridata list envelope)

```json
{
  "$id": "Page",
  "type": "object",
  "required": ["count", "previous", "next", "results"],
  "properties": {
    "count":    { "type": "integer", "description": "total across all pages" },
    "previous": { "type": ["string", "null"], "format": "uri" },
    "next":     { "type": ["string", "null"], "format": "uri", "description": "null on last page; keeps filters" },
    "results":  { "type": "array", "items": { "$ref": "<T>" } }
  }
}
```
Page sizes: companies 20, periods 10, ratings 10 (fixed).

### 5.5 Portfolio

```json
{
  "$id": "Portfolio",
  "type": "object",
  "required": ["id", "name", "href", "companies"],
  "properties": {
    "id":        { "type": "integer" },
    "name":      { "type": "string" },
    "href":      { "type": "string", "format": "uri" },
    "companies": { "type": "string", "format": "uri", "description": "/companies/?portfolio={id}" }
  }
}
```

### 5.6 Rating (used in `/ratings/` and `Company.latestRatings[]`)

```json
{
  "$id": "Rating",
  "type": "object",
  "required": ["id", "eqyYear", "period", "financialDate", "fhr", "chs", "probabilityDefault"],
  "properties": {
    "id":                         { "type": "string", "pattern": "^[0-9]{4}(Q1|Q2|Q3|YE)$" },
    "href":                       { "type": "string", "format": "uri" },
    "eqyYear":                    { "type": "integer" },
    "period":                     { "enum": ["Q1", "Q2", "Q3", "YE"] },
    "financialDate":              { "type": "string", "format": "date" },
    "reportingPeriod":            { "type": "string", "format": "date" },
    "fhr":                        { "type": "integer", "minimum": 0, "maximum": 100 },
    "chs":                        { "type": "integer", "minimum": 0, "maximum": 100 },
    "probabilityDefault":         { "type": "number", "description": "1-year PD, percent" },
    "termPd": {
      "type": "object",
      "description": "cumulative PD in percent",
      "properties": {
        "year2":  { "type": "number" }, "year3": { "type": "number" }, "year4": { "type": "number" },
        "year5":  { "type": "number" }, "year6": { "type": "number" }, "year7": { "type": "number" },
        "year8":  { "type": "number" }, "year9": { "type": "number" }, "year10": { "type": "number" }
      }
    },
    "delta":                      { "type": ["integer", "null"] },
    "simulatedFhr":               { "type": "integer" },
    "simulatedFhrDelta":          { "type": ["integer", "null"] },
    "operatingProfitability":     { "type": "integer", "minimum": 0, "maximum": 100 },
    "netProfitability":           { "type": "integer", "minimum": 0, "maximum": 100 },
    "capitalStructureEfficiency": { "type": "integer", "minimum": 0, "maximum": 100 },
    "costStructureEfficiency":    { "type": "integer", "minimum": 0, "maximum": 100 },
    "leverage":                   { "enum": ["Strong", "Average", "Weak"] },
    "liquidity":                  { "enum": ["Strong", "Average", "Weak"] },
    "earningsPerformance":        { "enum": ["Strong", "Average", "Weak"] }
  }
}
```

### 5.7 Company (list item; detail adds `reports` and `sectorAverages`)

```json
{
  "$id": "Company",
  "type": "object",
  "required": ["id", "name", "country", "href", "latestRatings"],
  "properties": {
    "id":                           { "type": "string", "description": "RRID, e.g. XAZPzdJ" },
    "name":                         { "type": "string" },
    "country":                      { "type": "string", "description": "ISO-3, e.g. USA" },
    "ticker":                       { "type": "string" },
    "exchange":                     { "type": "string" },
    "sector":                       { "type": "string" },
    "href":                         { "type": "string", "format": "uri" },
    "financials":                   { "type": "string", "format": "uri" },
    "periods":                      { "type": "string", "format": "uri" },
    "ratings":                      { "type": "string", "format": "uri" },
    "latestRatings":                { "type": "array", "maxItems": 5, "items": { "$ref": "Rating" }, "description": "newest first" },
    "reportLinks": {
      "type": "object",
      "properties": {
        "fhr":             { "type": "string", "format": "uri" },
        "financialDialog": { "type": "string", "format": "uri" },
        "peerBenchmark":   { "type": "string", "format": "uri" }
      }
    },
    "address":                      { "type": "string" },
    "industryClassificationSystem": { "type": ["string", "null"] },
    "industryClassificationCode":   { "type": ["string", "null"] },
    "currency":                     { "type": "string" },
    "dataScale":                    { "type": "string", "examples": ["millions"] },
    "dataConfidential":             { "type": "string", "examples": ["PUB"] },
    "externalSourceId":             { "type": ["string", "null"] },
    "externalSourceIds":            { "type": "array" },

    "reports": {
      "description": "detail endpoint only",
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "reportType":   { "enum": ["fhr", "financial_dialogue", "peer_benchmark"] },
          "downloadHref": { "type": "string", "format": "uri" }
        }
      }
    },
    "sectorAverages": {
      "description": "detail endpoint only",
      "type": "object",
      "properties": { "fhr": { "type": "number" }, "chs": { "type": "number" } }
    }
  }
}
```

### 5.8 Period (financial statement figures)

All amounts are in `currency`, scaled by the company's `dataScale`. Every amount can be `null`.

```json
{
  "$id": "Period",
  "type": "object",
  "required": ["id", "eqyYear", "period", "financialDate", "currency"],
  "properties": {
    "id":                             { "type": "string", "pattern": "^[0-9]{4}(Q1|Q2|Q3|YE)$" },
    "href":                           { "type": "string", "format": "uri" },
    "eqyYear":                        { "type": "integer" },
    "period":                         { "enum": ["Q1", "Q2", "Q3", "YE"] },
    "financialDate":                  { "type": "string", "format": "date" },
    "currency":                       { "type": "string" },
    "salesRevenue":                   { "type": ["number", "null"] },
    "earningsBeforeInterestAndTax":   { "type": ["number", "null"] },
    "interestExpense":                { "type": ["number", "null"] },
    "interestReceived":               { "type": ["number", "null"] },
    "companyTaxExpense":              { "type": ["number", "null"] },
    "abnormalItems":                  { "type": ["number", "null"] },
    "netProfitAfterTax":              { "type": ["number", "null"] },
    "bankCashBalances":               { "type": ["number", "null"] },
    "totalCurrentAssets":             { "type": ["number", "null"] },
    "fixedAssets":                    { "type": ["number", "null"] },
    "financialAssets":                { "type": ["number", "null"] },
    "totalAssets":                    { "type": ["number", "null"] },
    "debitOwnedWithinOneYear":        { "type": ["number", "null"], "description": "short-term debt (API spelling)" },
    "totalCurrentLiabilities":        { "type": ["number", "null"] },
    "termLoans":                      { "type": ["number", "null"] },
    "totalLiabilities":               { "type": ["number", "null"] },
    "totalShareholderEquity":         { "type": ["number", "null"] },
    "cashFromOperationsFromCashFlow": { "type": ["number", "null"] },
    "netInvestingCashFlow":           { "type": ["number", "null"] },
    "netFundingCashFlow":             { "type": ["number", "null"] }
  }
}
```

### 5.9 ProblemDetails (all outreach errors, RFC 7807)

```json
{
  "$id": "ProblemDetails",
  "type": "object",
  "required": ["status"],
  "properties": {
    "type":     { "type": "string", "default": "about:blank" },
    "title":    { "type": ["string", "null"] },
    "status":   { "type": "integer" },
    "detail":   { "type": ["string", "array"], "description": "string, or array of strings for some 400s" },
    "instance": { "type": "string", "format": "uri" },
    "errors":   { "type": "array", "items": { "type": "string" }, "description": "validation errors on POST" }
  }
}
```

### 5.10 CursorPage<T> (outreach list envelope)

```json
{
  "$id": "CursorPage",
  "type": "object",
  "required": ["cursors", "items"],
  "properties": {
    "cursors": {
      "type": "object",
      "required": ["query", "total"],
      "properties": {
        "first_cursor": { "type": "string" },
        "last_cursor":  { "type": "string" },
        "next_cursor":  { "type": ["string", "null"], "description": "absent/null = last page" },
        "prev_cursor":  { "type": ["string", "null"] },
        "query":        { "type": "object", "description": "echo of limit/sort/q applied" },
        "total":        { "type": "integer" }
      }
    },
    "items": { "type": "array", "items": { "$ref": "<T>" } }
  }
}
```

### 5.11 Contact, Counterparty, RelationshipManager

```json
{
  "$defs": {
    "Contact": {
      "type": "object",
      "required": ["first_name", "last_name", "email", "country"],
      "properties": {
        "first_name": { "type": "string", "minLength": 1, "maxLength": 255 },
        "last_name":  { "type": "string", "minLength": 1, "maxLength": 255 },
        "email":      { "type": "string", "format": "email", "minLength": 1, "maxLength": 255 },
        "country":    { "type": "string", "description": "ISO-3166 alpha-3" },
        "tel":        { "type": ["string", "null"] }
      }
    },
    "AdditionalContact": {
      "type": "object",
      "required": ["email"],
      "properties": {
        "contact_ref": { "type": ["string", "null"], "readOnly": true },
        "email":       { "type": "string", "format": "email", "minLength": 1, "maxLength": 254 },
        "first_name":  { "type": "string", "minLength": 1, "maxLength": 255 },
        "last_name":   { "type": "string", "minLength": 1, "maxLength": 255 },
        "tel":         { "type": ["string", "null"], "maxLength": 255 }
      }
    },
    "Counterparty": {
      "type": "object",
      "required": ["company_name", "contact"],
      "properties": {
        "company_name":        { "type": "string", "minLength": 1, "maxLength": 255 },
        "contact":             { "$ref": "#/$defs/Contact" },
        "reference_id":        { "type": ["string", "null"], "description": "your own ID, free text" },
        "additional_contacts": { "type": "array", "maxItems": 1, "items": { "$ref": "#/$defs/AdditionalContact" },
                                 "description": "max 1 on create; 409 if org not on Intercom delivery" }
      }
    },
    "RelationshipManager": {
      "type": "object",
      "required": ["name", "email"],
      "properties": {
        "name":  { "type": "string", "minLength": 1, "maxLength": 255 },
        "email": { "type": "string", "format": "email", "minLength": 1, "maxLength": 255 }
      }
    }
  }
}
```

### 5.12 OutreachRequestCreate (body of `POST /outreach-requests`)

```json
{
  "$id": "OutreachRequestCreate",
  "type": "object",
  "required": ["counterparty", "relationship_manager"],
  "properties": {
    "counterparty":         { "$ref": "#/$defs/Counterparty" },
    "relationship_manager": { "$ref": "#/$defs/RelationshipManager" }
  }
}
```

### 5.13 OutreachRequest (response item / detail / 201 body)

```json
{
  "$id": "OutreachRequest",
  "type": "object",
  "required": ["counterparty", "relationship_manager"],
  "properties": {
    "outreach_request_id":   { "type": "string", "readOnly": true },
    "counterparty":          { "$ref": "#/$defs/Counterparty" },
    "relationship_manager":  { "$ref": "#/$defs/RelationshipManager" },
    "created_at":            { "type": "string", "format": "date-time", "readOnly": true },
    "modified_at":           { "type": "string", "format": "date-time", "readOnly": true },
    "is_rated":              { "type": "boolean", "readOnly": true },
    "rrid":                  { "type": ["string", "null"], "readOnly": true, "description": "link to /v2.0/companies/{rrid}/" },
    "status":                { "type": ["string", "null"], "readOnly": true, "description": "display only, values may change" },
    "status_history": {
      "type": "array", "readOnly": true,
      "items": {
        "type": "object",
        "properties": {
          "status":            { "type": "string" },
          "status_detail":     { "type": ["string", "null"] },
          "status_comment":    { "type": ["string", "null"] },
          "status_reasons":    { "type": ["array", "null"], "items": { "type": "string" } },
          "status_updated_at": { "type": "string", "format": "date-time" }
        }
      }
    },
    "financial_submissions": {
      "type": "array", "readOnly": true, "description": "newest first",
      "items": { "type": "object", "properties": { "created_at": { "type": "string", "format": "date-time" } } }
    },
    "_embedded": { "type": "object", "readOnly": true }
  }
}
```

### 5.14 OutreachCommunications

```json
{
  "$id": "OutreachCommunications",
  "type": "object",
  "required": ["communications"],
  "properties": {
    "communications": {
      "type": "object",
      "required": ["introductions"],
      "properties": {
        "introductions": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["channel", "sent_at"],
            "properties": {
              "channel": { "enum": ["email"] },
              "sent_at": { "type": "string", "format": "date-time" }
            }
          }
        }
      }
    }
  }
}
```

---
# 6. Endpoint: Authentication (`RR_TOKEN_URL`)

Every RapidRatings API call needs a **Bearer access token**. You get the token from this endpoint using the client ID and secret, then send it on all other requests.

> Verified against the sandbox on 2026-10-28. All examples below are real responses, with token values redacted.

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_TOKEN_URL` |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` |
| **Method** | `POST` (GET returns `405`) |
| **Auth type** | OAuth 2.0, **Client Credentials** grant |
| **Client authentication** | HTTP Basic: `Authorization: Basic base64(client_id:client_secret)` |
| **Request body** | `grant_type=client_credentials` (form-urlencoded or JSON) |
| **Token type returned** | `Bearer` |
| **Token lifetime** | `1800` seconds (**30 minutes**) |
| **Refresh token** | Not issued. Request a new token when the current one expires. |

> Note: the trailing slash in the URL (`/token/`) is part of the endpoint.

### Related `.env` variables

| Variable | Purpose |
|---|---|
| `RR_TOKEN_URL` | Token endpoint URL |
| `RR_CLIENT_ID` | OAuth client ID (key) |
| `RR_CLIENT_SECRET` | OAuth client secret |
| `RR_BASIC_AUTH` | Pre-encoded `base64(RR_CLIENT_ID:RR_CLIENT_SECRET)` |

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Basic <RR_BASIC_AUTH>` | Yes |
| `Content-Type` | `application/x-www-form-urlencoded` (or `application/json`) | Yes |

### Body

Form-urlencoded (recommended):

```
grant_type=client_credentials
```

JSON (also accepted):

```json
{ "grant_type": "client_credentials" }
```

### Raw HTTP

```http
POST /v2.0/oauth/token/ HTTP/1.1
Host: rridata.sandbox.rrilabs.co
Authorization: Basic <RR_BASIC_AUTH>
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
```

### cURL (Git Bash)

```bash
set -a; . ./.env; set +a

curl -X POST "$RR_TOKEN_URL" \
  -H "Authorization: Basic $RR_BASIC_AUTH" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials"
```

Alternative: let curl build the Basic header from the ID and secret:

```bash
curl -X POST "$RR_TOKEN_URL" -u "$RR_CLIENT_ID:$RR_CLIENT_SECRET" -d "grant_type=client_credentials"
```

### PowerShell

```powershell
$headers = @{ Authorization = "Basic $env:RR_BASIC_AUTH" }
$body    = @{ grant_type = "client_credentials" }

$resp = Invoke-RestMethod -Method Post -Uri $env:RR_TOKEN_URL `
          -Headers $headers -Body $body `
          -ContentType "application/x-www-form-urlencoded"
$resp.access_token
```

---

## 3. Response

### Success: `200 OK`

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
```

```json
{
  "access_token": "Lf4A**************************",
  "expires_in": 1800,
  "token_type": "Bearer",
  "scope": "uid introspection openid"
}
```

| Field | Type | Description |
|---|---|---|
| `access_token` | string | Token to send as `Authorization: Bearer <access_token>` on all API calls |
| `expires_in` | integer | Seconds until the token expires (1800 = 30 min) |
| `token_type` | string | Always `Bearer` |
| `scope` | string | Scopes granted to the client (space-separated) |

### Error responses

All errors use the same JSON envelope:

```json
{ "Request": { "message": "<description>", "info": {} } }
```

| HTTP status | When it happens (tested) | Response `message` |
|---|---|---|
| `400 Bad Request` | Unsupported `grant_type` (e.g. `password`) | `Bad Request.` |
| `401 Unauthorized` | Wrong client ID/secret, missing `Authorization` header, **or missing `grant_type`** | `Valid authentication credentials were not provided.` |
| `405 Method Not Allowed` | Using `GET` instead of `POST` | `Method not allowed.` |

> A missing `grant_type` returns **401, not 400**, so a 401 does not always mean bad credentials. Check the body first.

---

## 4. Using the token

Send the token on every data endpoint request:

```http
GET /v2.0/portfolios/ HTTP/1.1
Host: rridata.sandbox.rrilabs.co
Authorization: Bearer <access_token>
```

```bash
curl "$RR_PORTFOLIOS_URL" -H "Authorization: Bearer <access_token>"
```

Verified: `GET /v2.0/portfolios/` with a fresh token returned `200` with `{"count": 5, "previous": null, "next": null, "results": [...]}`.

### Bearer token errors on data endpoints

| HTTP status | When | Response `message` |
|---|---|---|
| `401 Unauthorized` | No / empty `Authorization` header | `Valid authentication credentials were not provided.` |
| `403 Forbidden` | Invalid or expired Bearer token | `Not Authorized.` |

> An expired token returns **403**, not 401. The client should treat 403 on a data call as "token may be expired": get a new token and retry once.

---

## 5. Implementation notes (for the integration client)

1. **Cache the token** in memory and reuse it until it is close to expiry. Don't request a new token for every call.
2. **Refresh early:** request a new token when fewer than ~60 seconds are left (`issued_at + expires_in - 60`).
3. **No refresh token** is issued. Renewing means repeating the same client-credentials request.
4. **Retry once on 401/403** from a data endpoint: clear the cached token, get a new one, and repeat the call. If it fails again, raise an error.
5. **Never log** `RR_CLIENT_SECRET`, `RR_BASIC_AUTH`, or `access_token` values.
6. **Timeouts:** use a connect/read timeout (e.g. 30s) and retry on network errors and `5xx` with backoff.

---

# 7. Endpoint: Companies (`RR_COMPANIES_URL`)

Returns the companies your account can see, each with its identifiers, the latest Financial Health Ratings (FHR) and links to its periods, ratings, financials and reports. This is the main entry point of the integration: the company `id` (the **RRID**) is the key for every other company endpoint.

> Verified against the sandbox on 2026-10-28. All examples below are real responses (trimmed where marked).
> Requires a Bearer token. See 01-authentication.md (section 6).

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_COMPANIES_URL` |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/companies/` |
| **List** | `GET /v2.0/companies/` |
| **Detail** | `GET /v2.0/companies/{rrid}/` |
| **Auth** | `Authorization: Bearer <access_token>` |
| **Response format** | JSON (`application/json; charset=utf-8`) |
| **Pagination** | Page-number based, **20 per page, fixed** (page size cannot be changed) |
| **Sandbox volume** | 96 companies (5 pages) |

> Note: the **trailing slash is required**. `/companies/XAZPzdJ` (no slash) returns `404`.

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <access_token>` | Yes |
| `Accept` | `application/json` | Optional |

### Query parameters (list endpoint)

Only the parameters below are accepted. **Any other parameter returns `400 Not valid query parameter: <name>`.**

| Parameter | Type | Behaviour (tested) | Example |
|---|---|---|---|
| `page` | int ≥ 1 | Page number. `0` returns 400; a page past the end returns 404 | `?page=2` |
| `portfolio` | int | Only companies in that portfolio. Must be an integer; an unknown ID returns 404 | `?portfolio=27070` |
| `name` | string (min 3 chars) | **Case-insensitive, partial** ("contains") match on the company name | `?name=foods` |
| `ticker` | string | **Exact, case-sensitive** match (`TSN` matches, `tsn` returns 0) | `?ticker=TSN` |
| `externalSourceId` | string | Matches your own external/ERP ID if one is mapped to the company | `?externalSourceId=V1001` |

Parameters can be combined, e.g. `?portfolio=631&name=inc`.

**Not supported** (all tested, all return 400): `page_size`, `limit`, `search`, `country`, `sector`, `exchange`, `id`, `rrid`, `duns`, `ordering`, `modified_since`, `updated_since`.
There is **no incremental/"changed since" filter**, so a sync has to read the full list.

### Path parameter (detail endpoint)

| Parameter | Type | Description |
|---|---|---|
| `rrid` | string (7 chars, e.g. `XAZPzdJ`) | RapidRatings company ID, from `results[].id` in the list |

### Examples

```http
GET /v2.0/companies/?portfolio=27070&page=1 HTTP/1.1
Host: rridata.sandbox.rrilabs.co
Authorization: Bearer <access_token>
```

cURL (Git Bash):

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')

# List (first page) of the configured portfolio
curl "$RR_COMPANIES_URL?portfolio=$RR_PORTFOLIO_ID" -H "Authorization: Bearer $TOKEN"

# Search by name
curl "$RR_COMPANIES_URL?name=tyson" -H "Authorization: Bearer $TOKEN"

# Single company
curl "${RR_COMPANIES_URL}XAZPzdJ/" -H "Authorization: Bearer $TOKEN"
```

PowerShell:

```powershell
$h = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Uri "$($env:RR_COMPANIES_URL)?portfolio=$($env:RR_PORTFOLIO_ID)" -Headers $h
```

---

## 3. Response: List `200 OK`

### Envelope

```json
{
  "count": 96,
  "previous": null,
  "next": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?page=2",
  "results": [ { ...company... }, ... ]
}
```

| Field | Type | Description |
|---|---|---|
| `count` | integer | Total matching companies across all pages |
| `previous` | string \| null | Full URL of the previous page; `null` on page 1 |
| `next` | string \| null | Full URL of the next page; `null` on the last page. Filters are kept (e.g. `?portfolio=27070&page=2`) |
| `results` | array | Up to 20 company objects |

### Company object (real example, `latestRatings` trimmed to 1 of 5)

```json
{
  "id": "KawjbGJ",
  "name": "Rite Aid Corporation",
  "country": "USA",
  "ticker": "RAD",
  "exchange": "US",
  "sector": "Retail",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/",
  "financials": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/financials/",
  "periods": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/periods/",
  "ratings": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/ratings/",
  "latestRatings": [
    {
      "id": "2020YE",
      "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/ratings/2020YE/",
      "eqyYear": 2020,
      "period": "YE",
      "financialDate": "2020-02-29",
      "reportingPeriod": "2020-05-15",
      "fhr": 35,
      "chs": 37,
      "probabilityDefault": 1.5853,
      "termPd": {
        "year2": 8.24, "year3": 12.18, "year4": 15.85, "year5": 19.4, "year6": 23.04,
        "year7": 26.93, "year8": 31.16, "year9": 35.81, "year10": 40.85
      },
      "delta": 13,
      "simulatedFhr": 37,
      "simulatedFhrDelta": 9,
      "operatingProfitability": 36,
      "netProfitability": 5,
      "capitalStructureEfficiency": 82,
      "costStructureEfficiency": 50,
      "leverage": "Weak",
      "liquidity": "Weak",
      "earningsPerformance": "Weak"
    }
  ],
  "reportLinks": {
    "fhr": "https://portal.sandbox.rrilabs.co/reportsapi/31136/fhr",
    "financialDialog": "https://portal.sandbox.rrilabs.co/reportsapi/31136/financial_dialogue",
    "peerBenchmark": "https://portal.sandbox.rrilabs.co/reportsapi/31136/peer_benchmark"
  },
  "address": "",
  "industryClassificationSystem": null,
  "industryClassificationCode": null,
  "currency": "USD",
  "dataScale": "millions",
  "dataConfidential": "PUB",
  "externalSourceId": null,
  "externalSourceIds": []
}
```

### Company fields

| Field | Type | Description |
|---|---|---|
| `id` | string | **RRID**, the RapidRatings company ID (primary key) |
| `name` | string | Company legal name |
| `country` | string | ISO-3 country code (e.g. `USA`) |
| `ticker` | string | Stock ticker (public companies) |
| `exchange` | string | Exchange code (e.g. `US`) |
| `sector` | string | RapidRatings sector (e.g. `Retail`, `Media`) |
| `href` | string | URL of this company's detail endpoint |
| `financials` | string | URL of the company's financial statements |
| `periods` | string | URL of the company's financial periods (`RR_PERIODS_URL`) |
| `ratings` | string | URL of the company's full rating history (`RR_RATINGS_URL`) |
| `latestRatings` | array | Most recent ratings, **newest first, up to 5** (observed 2–5) |
| `reportLinks` | object | Portal links to PDF reports: `fhr`, `financialDialog`, `peerBenchmark` |
| `address` | string | Address; may be empty `""` |
| `industryClassificationSystem` | string \| null | e.g. NAICS/SIC system name, if set |
| `industryClassificationCode` | string \| null | Industry code, if set |
| `currency` | string | Currency of the financials (e.g. `USD`) |
| `dataScale` | string | Scale of financial figures (e.g. `millions`) |
| `dataConfidential` | string | `PUB` = public company data (private companies use other values) |
| `externalSourceId` | string \| null | Your own system's ID for this company, if mapped |
| `externalSourceIds` | array | All external IDs mapped to this company |

### `latestRatings[]` fields

| Field | Type | Description |
|---|---|---|
| `id` | string | Rating ID = `eqyYear` + `period`, e.g. `2020YE`, `2020Q3` |
| `href` | string | URL of this rating's detail |
| `eqyYear` | integer | Equity/fiscal year of the financials |
| `period` | string | `Q1`, `Q2`, `Q3` or `YE` (year end) |
| `financialDate` | date | Date of the financial statements used |
| `reportingPeriod` | date | Date the rating was produced/published |
| `fhr` | integer 0–100 | **Financial Health Rating**: short-term (12-month) default risk. Higher = healthier |
| `chs` | integer 0–100 | **Core Health Score**: medium-term (2–3 year) operating resilience |
| `probabilityDefault` | number (%) | 1-year probability of default |
| `termPd` | object | Cumulative probability of default (%) for years 2–10 (`year2` … `year10`) |
| `delta` | integer \| null | FHR change vs. the previous period (`null` if there is none) |
| `simulatedFhr` | integer | Simulated/forward-looking FHR |
| `simulatedFhrDelta` | integer \| null | Change in simulated FHR |
| `operatingProfitability` | integer 0–100 | Sub-score |
| `netProfitability` | integer 0–100 | Sub-score |
| `capitalStructureEfficiency` | integer 0–100 | Sub-score |
| `costStructureEfficiency` | integer 0–100 | Sub-score |
| `leverage` | string | `Strong` / `Average` / `Weak` |
| `liquidity` | string | `Strong` / `Average` / `Weak` |
| `earningsPerformance` | string | `Strong` / `Average` / `Weak` |

---

## 4. Response: Detail `200 OK`

`GET /v2.0/companies/XAZPzdJ/` returns a **single company object (no `count`/`results` envelope)** with every list field **plus**:

```json
{
  "id": "XAZPzdJ",
  "name": "Tyson Foods, Inc.",
  "...": "... all list fields ...",
  "reports": [
    { "reportType": "fhr",                "downloadHref": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/reports/?reportType=fhr" },
    { "reportType": "financial_dialogue", "downloadHref": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/reports/?reportType=financial_dialogue" },
    { "reportType": "peer_benchmark",     "downloadHref": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/reports/?reportType=peer_benchmark" }
  ],
  "sectorAverages": { "fhr": 52.6, "chs": 45.8 }
}
```

| Extra field | Type | Description |
|---|---|---|
| `reports` | array | API download links for the PDF reports (`fhr`, `financial_dialogue`, `peer_benchmark`) |
| `sectorAverages` | object | Average `fhr` and `chs` for the company's sector (useful for benchmarking) |

---

## 5. Error responses

Errors use the envelope `{ "<Scope>": { "message": "...", "info": {} } }`. The scope key is usually `Request`, but can be `Portfolio` or `Companies`.

| HTTP | When (tested) | Response body |
|---|---|---|
| `400` | Unknown query parameter | `{"Request":{"message":"Not valid query parameter: sector.","info":{}}}` |
| `400` | `portfolio` not an integer | `{"Request":{"message":"Bad type for parameter: portfolio (int).","info":{}}}` |
| `400` | `name` shorter than 3 chars | `{"Request":{"message":"Invalid parameter: name must be at least 3 characters.","info":{}}}` |
| `400` | `page=0` | `{"Request":{"message":"Invalid parameter: page must to be a positive integer.","info":{}}}` |
| `401` | No `Authorization` header | `{"Request":{"message":"Valid authentication credentials were not provided.","info":{}}}` |
| `403` | Invalid / expired token | `{"Request":{"message":"Not Authorized.","info":{}}}` |
| `404` | Page past the end (`?page=999`) | `{"Companies":{"message":"Not Found.","info":{}}}` |
| `404` | Unknown portfolio | `{"Portfolio":{"message":"Portfolio 999999 not Found.","info":{}}}` |
| `404` | Unknown RRID / missing trailing slash | `{"Request":{"message":"Not Found.","info":{}}}` |

> **POST is also supported** on `/companies/` (creates a private company). Sending an empty body returns `400` listing the required fields: `name`, `country`, `currency`, `dataConfidential`, `dataScale`. This was **not executed** (to avoid creating data) and is out of scope for the read integration.

---

## 6. Rate limiting

Every response includes rate-limit headers:

```
X-Ratelimit-Limit: 30000000
X-Ratelimit-Remaining: 29999998
X-Ratelimit-Reset: 1793168282      (Unix epoch seconds)
Retry-After: 3464                  (seconds until the window resets)
```

The sandbox limit is very high, but the client should still check `X-Ratelimit-Remaining` and, on `429`, wait `Retry-After` seconds before retrying.

---

## 7. Implementation notes (for the integration client)

1. **Paginate by following `next`** until it is `null`. Don't build page numbers yourself; `next` already keeps the filters.
2. **Scope by portfolio**: use `?portfolio=$RR_PORTFOLIO_ID` (27070) so you only pull the companies you monitor.
3. **There is no delta filter.** Do a full read each run and detect changes on your side (e.g. compare `latestRatings[0].id` / `fhr` per RRID).
4. **Use `id` (RRID) as the primary key** and map it to your supplier/vendor ID. `externalSourceId` can hold that mapping on the RapidRatings side.
5. `latestRatings[0]` is the **current rating**. For the full history use the `ratings` URL (see the Ratings doc).
6. Expect `null` in `delta`, `simulatedFhrDelta`, `industryClassification*` and `externalSourceId`, and `""` in `address`.
7. Unknown parameters fail hard (400), so validate filters before sending.
8. On `403`, get a new token and retry once (tokens last 30 min).

---

# 8. Endpoint: Portfolios (`RR_PORTFOLIOS_URL`)

Lists the portfolios (named groups of monitored companies) your account can see. Use this to find the portfolio ID, then pull its companies with `/companies/?portfolio={id}`.

> Verified against the sandbox on 2026-10-28. All examples below are real responses.
> Requires a Bearer token. See 01-authentication.md (section 6).

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_PORTFOLIOS_URL` (and `RR_PORTFOLIO_ID` = `27070`) |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/portfolios/` |
| **List** | `GET /v2.0/portfolios/` |
| **Detail** | `GET /v2.0/portfolios/{portfolioId}/` |
| **Auth** | `Authorization: Bearer <access_token>` |
| **Methods** | `GET` only (POST/DELETE return `405`): read-only |
| **Pagination** | Same envelope as companies; all 5 sandbox portfolios fit on page 1 |

> The **trailing slash is required** (`/portfolios/27070` without it returns `404`).

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <access_token>` | Yes |

### Query parameters (list)

| Parameter | Type | Notes |
|---|---|---|
| `page` | int ≥ 1 | `page=1` works. **`page=2` returns `500 Internal Server Error`** in the sandbox (out-of-range page bug), so rely on `next` instead |

No other parameters are accepted (`?name=` and anything else return `400 Not valid query parameter`).

### Path parameter (detail)

| Parameter | Type | Description |
|---|---|---|
| `portfolioId` | integer | Portfolio ID from `results[].id` (e.g. `27070`) |

### Examples

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')

curl "$RR_PORTFOLIOS_URL" -H "Authorization: Bearer $TOKEN"                       # list
curl "${RR_PORTFOLIOS_URL}${RR_PORTFOLIO_ID}/" -H "Authorization: Bearer $TOKEN"  # detail
```

```powershell
Invoke-RestMethod -Uri "$($env:RR_PORTFOLIOS_URL)$($env:RR_PORTFOLIO_ID)/" -Headers @{ Authorization = "Bearer $token" }
```

---

## 3. Response

### List: `200 OK`

```json
{
  "count": 5,
  "previous": null,
  "next": null,
  "results": [
    { "id": 631,   "name": "Fortune 500",            "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/631/",   "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=631" },
    { "id": 137,   "name": "INTERNATIONAL COVERAGE", "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/137/",   "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=137" },
    { "id": 36,    "name": "US COVERAGE",            "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/36/",    "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=36" },
    { "id": 27070, "name": "z2Data Sandbox",         "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/27070/", "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=27070" },
    { "id": 27072, "name": "z2Data Sandbox-Private", "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/27072/", "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=27072" }
  ]
}
```

### Detail: `200 OK`

`GET /v2.0/portfolios/27070/` returns a single object (no envelope):

```json
{
  "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=27070",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/27070/",
  "id": 27070,
  "name": "z2Data Sandbox"
}
```

### Fields

| Field | Type | Description |
|---|---|---|
| `id` | integer | Portfolio ID, used as the `portfolio` filter on `/companies/` |
| `name` | string | Portfolio display name |
| `href` | string | URL of this portfolio's detail endpoint |
| `companies` | string | Ready-made URL listing this portfolio's companies |

The portfolio object has **no company count**. Call the `companies` URL and read `count`.

### Sandbox portfolios (company counts from `/companies/?portfolio=`)

| ID | Name | Companies |
|---|---|---|
| 36 | US COVERAGE | 96 |
| 137 | INTERNATIONAL COVERAGE | 0 |
| 631 | Fortune 500 | 37 |
| **27070** | **z2Data Sandbox** (our `RR_PORTFOLIO_ID`) | **96** |
| 27072 | z2Data Sandbox-Private | 0 |

---

## 4. Error responses

| HTTP | When (tested) | Response body |
|---|---|---|
| `400` | Unknown query parameter | `{"Request":{"message":"Not valid query parameter: name.","info":{}}}` |
| `401` | No `Authorization` header | `{"Request":{"message":"Valid authentication credentials were not provided.","info":{}}}` |
| `403` | Invalid / expired token | `{"Request":{"message":"Not Authorized.","info":{}}}` |
| `404` | Unknown portfolio ID | `{"Portfolio":{"message":"Portfolio 999999 not Found.","info":{}}}` |
| `404` | Non-numeric ID / missing trailing slash | `{"Request":{"message":"Not Found.","info":{}}}` |
| `405` | POST / DELETE | `{"Request":{"message":"Method not allowed.","info":{}}}` |
| `500` | Page past the end (`?page=2`) | `{"Request":{"message":"Internal Server Error.","info":{}}}` |

---

## 5. Implementation notes

1. Call this once at the start of a sync to **check that `RR_PORTFOLIO_ID` still exists** (404 means stop and alert).
2. Use the returned `companies` URL (or `/companies/?portfolio=27070`) to get the company list.
3. Follow `next` for paging and never request a page number past it, because that returns **500**, not 404.
4. Portfolios are read-only through this API. Membership changes happen in the RapidRatings portal.

---

# 9. Endpoint: Periods / Financials (`RR_PERIODS_URL`)

Returns the **financial statement figures** for each reporting period of a company (quarterly and year-end): revenue, profit, assets, liabilities, cash flows and so on. These are the inputs behind each rating.

> Verified against the sandbox on 2026-10-28 using Tyson Foods (`XAZPzdJ`) and Rite Aid (`KawjbGJ`). Examples are real responses.
> Requires a Bearer token. See 01-authentication.md (section 6).

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_PERIODS_URL` (replace `{rrid}` at runtime) |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/` |
| **List** | `GET /v2.0/companies/{rrid}/periods/` |
| **Detail** | `GET /v2.0/companies/{rrid}/periods/{periodId}/` |
| **Auth** | `Authorization: Bearer <access_token>` |
| **Methods** | `GET` only (POST returns `405`) |
| **Pagination** | Page-number based, **10 per page, fixed** |
| **Order** | Newest period first |
| **Sandbox volume** | ~26–27 periods per company (2014 → 2020), i.e. 3 pages |

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <access_token>` | Yes |

### Path parameters

| Parameter | Type | Description |
|---|---|---|
| `rrid` | string | RapidRatings company ID (`companies[].id`, e.g. `XAZPzdJ`) |
| `periodId` | string | Period ID = `eqyYear` + `period`, e.g. `2019YE`, `2020Q2` (detail only) |

### Query parameters

| Parameter | Type | Notes |
|---|---|---|
| `page` | int ≥ 1 | Page number. **A page past the end returns `500`** (not 404) |

Nothing else is accepted. `eqyYear`, `year`, `period`, `from`, `financialDate`, `page_size` and `limit` all return `400 Not valid query parameter`. To get a single period, use the detail URL.

### Examples

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')
RRID=XAZPzdJ
URL=${RR_PERIODS_URL/\{rrid\}/$RRID}

curl "$URL" -H "Authorization: Bearer $TOKEN"            # page 1 (latest 10 periods)
curl "$URL?page=2" -H "Authorization: Bearer $TOKEN"     # next page
curl "${URL}2019YE/" -H "Authorization: Bearer $TOKEN"   # one period
```

```powershell
$url = $env:RR_PERIODS_URL.Replace("{rrid}", "XAZPzdJ")
Invoke-RestMethod -Uri $url -Headers @{ Authorization = "Bearer $token" }
```

---

## 3. Response

### List: `200 OK`

```json
{
  "count": 26,
  "previous": null,
  "next": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/periods/?page=2",
  "results": [ { ...period... }, "... 10 per page ..." ]
}
```

Page IDs observed for Tyson Foods: page 1 = `2020Q2 … 2018Q1`, page 2 = next 10, page 3 = `2015Q2 … 2014Q1` (6 items, `next: null`).

### Period object (real, `GET .../XAZPzdJ/periods/2019YE/`)

```json
{
  "id": "2019YE",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/periods/2019YE/",
  "eqyYear": 2019,
  "period": "YE",
  "financialDate": "2019-09-28",
  "currency": "USD",
  "salesRevenue": 42405.0,
  "earningsBeforeInterestAndTax": 2882.0,
  "interestExpense": 462.0,
  "interestReceived": 11.0,
  "companyTaxExpense": 396.0,
  "abnormalItems": 14.0,
  "netProfitAfterTax": 2035.0,
  "bankCashBalances": 484.0,
  "totalCurrentAssets": 7169.0,
  "fixedAssets": 7282.0,
  "financialAssets": null,
  "totalAssets": 33097.0,
  "debitOwnedWithinOneYear": 2102.0,
  "totalCurrentLiabilities": 5513.0,
  "termLoans": 9830.0,
  "totalLiabilities": 18871.0,
  "totalShareholderEquity": 14226.0,
  "cashFromOperationsFromCashFlow": null,
  "netInvestingCashFlow": -3464.0,
  "netFundingCashFlow": 1171.0
}
```

> The API returns these keys in no fixed order; they're grouped here for readability. The detail response is the same object as a list item.

### Fields

Amounts are in the company's `currency`, at the company's `dataScale` (e.g. **millions**, see the company record).

| Field | Type | Description |
|---|---|---|
| `id` | string | Period ID (`eqyYear` + `period`), e.g. `2019YE` |
| `href` | string | URL of this period |
| `eqyYear` | integer | Fiscal/equity year |
| `period` | string | `Q1`, `Q2`, `Q3`, `YE` |
| `financialDate` | date | Statement date for the period |
| `currency` | string | Currency code |
| **Income statement** | | |
| `salesRevenue` | number | Revenue / sales |
| `earningsBeforeInterestAndTax` | number | EBIT |
| `interestExpense` | number | Interest paid |
| `interestReceived` | number | Interest income |
| `companyTaxExpense` | number | Income tax expense |
| `abnormalItems` | number \| null | Exceptional / one-off items |
| `netProfitAfterTax` | number | Net income |
| **Balance sheet** | | |
| `bankCashBalances` | number | Cash and equivalents |
| `totalCurrentAssets` | number | Current assets |
| `fixedAssets` | number | Fixed assets (PP&E) |
| `financialAssets` | number \| null | Financial / investment assets |
| `totalAssets` | number | Total assets |
| `debitOwnedWithinOneYear` | number | Short-term debt (due within 1 year). *Spelling is the API's own.* |
| `totalCurrentLiabilities` | number | Current liabilities |
| `termLoans` | number | Long-term debt |
| `totalLiabilities` | number | Total liabilities |
| `totalShareholderEquity` | number | Shareholders' equity |
| **Cash flow** | | |
| `cashFromOperationsFromCashFlow` | number \| null | Operating cash flow (null in all sandbox samples) |
| `netInvestingCashFlow` | number | Investing cash flow |
| `netFundingCashFlow` | number | Financing cash flow |

All numeric fields can be `null` when a figure isn't reported, so treat every amount as nullable.

---

## 4. Error responses

| HTTP | When (tested) | Response body |
|---|---|---|
| `400` | Unknown query parameter | `{"Request":{"message":"Not valid query parameter: eqyYear.","info":{}}}` |
| `401` | No `Authorization` header | `{"Request":{"message":"Valid authentication credentials were not provided.","info":{}}}` |
| `403` | Invalid / expired token | `{"Request":{"message":"Not Authorized.","info":{}}}` |
| `404` | Unknown RRID | `{"Request":{"message":"Not Found.","info":{}}}` |
| `404` | Unknown period ID (e.g. `2030YE`) | `{"Request":{"message":"Not Found.","info":{}}}` |
| `405` | POST | `{"Request":{"message":"Method not allowed.","info":{}}}` |
| `500` | Page past the end (`?page=4` when there are 3) | `{"Request":{"message":"Internal Server Error.","info":{}}}` |

---

## 5. Implementation notes

1. **Key = (`rrid`, `id`)**, e.g. (`XAZPzdJ`, `2019YE`). Upsert on that pair.
2. **Initial load:** follow `next` until `null` to get the full history. **Incremental:** page 1 (latest 10) is usually enough. Load only IDs you don't have yet.
3. Don't retry a `500` that comes from asking for a page past the end. Only follow `next`.
4. Get `dataScale` (e.g. `millions`) from the company record and store it with the figures. Amounts aren't in base units.
5. Period IDs match rating IDs one-to-one (see 05-ratings.md (section 10)), so a period can be joined to its rating on `id`.

---

# 10. Endpoint: Ratings (`RR_RATINGS_URL`)

Returns a company's **full rating history**, one rating per financial period: FHR, CHS, probability of default, term PDs and sub-scores. The `latestRatings` array on the company record holds only the newest 5; this endpoint returns all of them.

> Verified against the sandbox on 2026-10-28 using Tyson Foods (`XAZPzdJ`). Examples are real responses.
> Requires a Bearer token. See 01-authentication.md (section 6).

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_RATINGS_URL` (replace `{rrid}` at runtime) |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/` |
| **List** | `GET /v2.0/companies/{rrid}/ratings/` |
| **Detail** | `GET /v2.0/companies/{rrid}/ratings/{ratingId}/` |
| **Auth** | `Authorization: Bearer <access_token>` |
| **Methods** | `GET` only (POST returns `405`) |
| **Pagination** | Page-number based, **10 per page, fixed** |
| **Order** | Newest rating first |
| **Sandbox volume** | 26 ratings for Tyson Foods (2014Q1 → 2020Q2), i.e. 3 pages |

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <access_token>` | Yes |

### Path parameters

| Parameter | Type | Description |
|---|---|---|
| `rrid` | string | RapidRatings company ID (e.g. `XAZPzdJ`) |
| `ratingId` | string | `eqyYear` + `period`, e.g. `2019YE` (detail only) |

### Query parameters

| Parameter | Type | Notes |
|---|---|---|
| `page` | int ≥ 1 | Page number. **A page past the end returns `500`** |

Nothing else is accepted (`eqyYear`, `period` and `page_size` return `400 Not valid query parameter`).

### Examples

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')
URL=${RR_RATINGS_URL/\{rrid\}/XAZPzdJ}

curl "$URL" -H "Authorization: Bearer $TOKEN"            # latest 10 ratings
curl "$URL?page=2" -H "Authorization: Bearer $TOKEN"     # older ratings
curl "${URL}2019YE/" -H "Authorization: Bearer $TOKEN"   # one rating
```

```powershell
$url = $env:RR_RATINGS_URL.Replace("{rrid}", "XAZPzdJ")
Invoke-RestMethod -Uri $url -Headers @{ Authorization = "Bearer $token" }
```

---

## 3. Response

### List: `200 OK`

```json
{
  "count": 26,
  "previous": null,
  "next": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/ratings/?page=2",
  "results": [ { ...rating... }, "... 10 per page ..." ]
}
```

### Rating object (real, first item of page 1)

```json
{
  "id": "2020Q2",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/ratings/2020Q2/",
  "eqyYear": 2020,
  "period": "Q2",
  "financialDate": "2020-03-28",
  "reportingPeriod": "2020-05-15",
  "fhr": 49,
  "chs": 48,
  "probabilityDefault": 0.3343,
  "termPd": {
    "year2": 3.83, "year3": 6.52, "year4": 9.35, "year5": 12.29, "year6": 15.39,
    "year7": 18.73, "year8": 22.42, "year9": 26.54, "year10": 31.12
  },
  "delta": 0,
  "simulatedFhr": 49,
  "simulatedFhrDelta": -1,
  "operatingProfitability": 30,
  "netProfitability": 66,
  "capitalStructureEfficiency": 12,
  "costStructureEfficiency": 63,
  "leverage": "Average",
  "liquidity": "Average",
  "earningsPerformance": "Average"
}
```

### Detail: `200 OK`

`GET .../XAZPzdJ/ratings/2019YE/` returns the same object shape without the envelope:

```json
{"id": "2019YE", "eqyYear": 2019, "period": "YE", "financialDate": "2019-09-28", "reportingPeriod": "2020-05-15",
 "fhr": 47, "chs": 50, "probabilityDefault": 0.4149, "delta": -9, "simulatedFhr": 47, "simulatedFhrDelta": -9,
 "leverage": "Average", "liquidity": "Weak", "earningsPerformance": "Average", "...": "..."}
```

### Fields

| Field | Type | Description |
|---|---|---|
| `id` | string | Rating ID = `eqyYear` + `period` (same as the matching period ID) |
| `href` | string | URL of this rating |
| `eqyYear` | integer | Fiscal/equity year |
| `period` | string | `Q1`, `Q2`, `Q3`, `YE` |
| `financialDate` | date | Statement date the rating is based on |
| `reportingPeriod` | date | Date the rating was produced/published |
| `fhr` | integer 0–100 | **Financial Health Rating**: 12-month default risk. Higher = healthier |
| `chs` | integer 0–100 | **Core Health Score**: 2–3 year operating efficiency/resilience |
| `probabilityDefault` | number (%) | 1-year probability of default (`0.3343` = 0.33%) |
| `termPd.year2` … `termPd.year10` | number (%) | Cumulative probability of default over 2–10 years |
| `delta` | integer \| null | FHR change vs. the previous rating (`null` for the oldest) |
| `simulatedFhr` | integer | Simulated FHR |
| `simulatedFhrDelta` | integer \| null | Change in simulated FHR |
| `operatingProfitability` | integer 0–100 | Sub-score |
| `netProfitability` | integer 0–100 | Sub-score |
| `capitalStructureEfficiency` | integer 0–100 | Sub-score |
| `costStructureEfficiency` | integer 0–100 | Sub-score |
| `leverage` | string | `Strong` / `Average` / `Weak` |
| `liquidity` | string | `Strong` / `Average` / `Weak` |
| `earningsPerformance` | string | `Strong` / `Average` / `Weak` |

### FHR risk bands (RapidRatings standard scale)

| FHR | Risk level |
|---|---|
| 80–100 | Very Low Risk |
| 60–79 | Low Risk |
| 40–59 | Medium Risk |
| 20–39 | High Risk |
| 0–19 | Very High Risk |

> These bands follow RapidRatings' published methodology; the API doesn't return them. Confirm them with RapidRatings before relying on them in business rules.

---

## 4. Error responses

| HTTP | When (tested) | Response body |
|---|---|---|
| `400` | Unknown query parameter | `{"Request":{"message":"Not valid query parameter: period.","info":{}}}` |
| `401` | No `Authorization` header | `{"Request":{"message":"Valid authentication credentials were not provided.","info":{}}}` |
| `403` | Invalid / expired token | `{"Request":{"message":"Not Authorized.","info":{}}}` |
| `404` | Unknown RRID | `{"Request":{"message":"Not Found.","info":{}}}` |
| `404` | Unknown rating ID (e.g. `2030YE`) | `{"Not found":{"message":"Not Found.","info":{}}}` (note the different scope key) |
| `405` | POST | `{"Request":{"message":"Method not allowed.","info":{}}}` |
| `500` | Page past the end (`?page=4`) | `{"Request":{"message":"Internal Server Error.","info":{}}}` |

---

## 5. Implementation notes

1. **Key = (`rrid`, `id`)**. Join to periods on the same key.
2. **Current rating** = `results[0]` on page 1, the same as `companies[].latestRatings[0]`. For a daily sync, the company list alone is enough to spot new ratings. Call this endpoint only for companies whose `latestRatings[0].id` changed, or for the initial history load.
3. Follow `next` until `null`. Never ask for a page past it (that returns 500).
4. Store `probabilityDefault` and `termPd` as decimals in **percent units**, not fractions.
5. `reportingPeriod` can be the same date for many periods (back-filled history), so order by `eqyYear` + `period`, not `reportingPeriod`.

---

# 11. Endpoint: Outreach Requests (`RR_OUTREACH_URL`)

Outreach requests ask RapidRatings to **contact a (usually private) counterparty and collect its financials** so it can be rated. You can list your requests and their status, create new ones, and see the communication attempts. Once a request is rated, it carries the company's `rrid`, which links into the Companies/Ratings APIs.

> Verified against the sandbox on 2026-10-28. The sandbox account has **0 outreach requests**, so list examples with data come from RapidRatings' OpenAPI spec (`https://api.sandbox.rrilabs.co/openapi.yaml`). All error responses below are real.
> Uses the **same Bearer token** as the other APIs. See 01-authentication.md (section 6).

---

## 1. Endpoint details

This is a **different API host and style** from the `rridata` endpoints.

| Item | Value |
|---|---|
| **Env variable** | `RR_OUTREACH_URL` (base: `RR_OUTREACH_BASE_URL`) |
| **URL (sandbox)** | `https://api.sandbox.rrilabs.co/outreach-requests` |
| **Auth** | `Authorization: Bearer <access_token>` (OAuth scope `uid`). Basic auth is **not** accepted |
| **Trailing slash** | Optional (`/outreach-requests` and `/outreach-requests/` both work) |
| **Pagination** | **Cursor-based** (`limit` + `cursor`), not page numbers |
| **Error format** | RFC 7807 `application/problem+json` (different from rridata) |
| **Rate limit** | `X-Ratelimit-Limit: 500000` (vs 30,000,000 on rridata) |
| **API spec** | `https://api.sandbox.rrilabs.co/openapi.yaml` |

### Operations

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/outreach-requests` | List your organization's outreach requests (with status) |
| `POST` | `/outreach-requests` | Create a new outreach request |
| `GET` | `/outreach-requests/{outreach_request_id}` | Get one request |
| `GET` | `/outreach-requests/{outreach_request_id}/communications` | Communication attempts (emails sent) for one request |

---

## 2. List: `GET /outreach-requests`

### Query parameters

| Parameter | Type | Description (tested) |
|---|---|---|
| `limit` | integer | Results per page. Default `20` (`1000` was accepted). Non-integer returns 400 |
| `cursor` | string | Value of `next_cursor` / `prev_cursor` from the previous response. An invalid value returns `400 Invalid Cursor` |
| `sort` | string | `id`, `counterparty_company_name`, `reference_id`, `created_date`. Prefix `-` = descending, `+` or none = ascending. Default `-id`. Invalid values return `400 Not a valid sort` |
| `q` | string (URL-encoded) | Filter: `counterparty_company_name=…`, `reference_id=…`, `is_rated=true\|false`. Partial, case-insensitive, combined with AND |
| `embed` | string | Include embedded resources, e.g. `(surveys(results(href)))` |

Any other parameter returns `400 Extra query parameter(s) <name> not in spec`.

### Example request

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')

curl "$RR_OUTREACH_URL?limit=50&sort=-created_date" -H "Authorization: Bearer $TOKEN"
curl "$RR_OUTREACH_URL?q=is_rated%3Dtrue"           -H "Authorization: Bearer $TOKEN"   # q must be URL-encoded
```

```powershell
Invoke-RestMethod -Uri "$($env:RR_OUTREACH_URL)?limit=50" -Headers @{ Authorization = "Bearer $token" }
```

### Response `200 OK`: real sandbox (empty)

```json
{
  "cursors": {
    "query": { "limit": 20, "sort": "-id" },
    "total": 0
  },
  "items": []
}
```

### Response `200 OK`: with data (from the OpenAPI spec)

```json
{
  "cursors": {
    "first_cursor": "22FERSFFGE2324",
    "last_cursor": "WEFAGEWH35236EWGDS",
    "next_cursor": "WWDASFAGE325623ETTYWE",
    "prev_cursor": "112425WFASBDEW",
    "query": { "limit": 10 },
    "total": 23115
  },
  "items": [
    {
      "outreach_request_id": "1234",
      "counterparty": {
        "company_name": "Bob's Burgers",
        "contact": {
          "country": "USA",
          "email": "joe.bloggs@example.com",
          "first_name": "Joe",
          "last_name": "Bloggs",
          "tel": "353851234567"
        },
        "reference_id": "your-ref-1234"
      },
      "relationship_manager": { "email": "joe.bloggs@example.com", "name": "Joe Bloggs" },
      "created_at": "2018-12-10T13:45:00Z",
      "modified_at": "2018-12-10T13:45:00Z",
      "financial_submissions": [ { "created_at": "2019-08-24T14:15:22Z" } ],
      "is_rated": true,
      "rrid": "Axtr4P"
    }
  ]
}
```

### `cursors` fields

| Field | Type | Description |
|---|---|---|
| `total` | integer | Total matching requests |
| `query` | object | Echo of the parameters applied (`limit`, `sort`, `q`) |
| `next_cursor` | string \| null | Pass as `cursor` to get the next page. Missing or `null` = last page |
| `prev_cursor` | string \| null | Previous page |
| `first_cursor` / `last_cursor` | string | First / last page |

When there's no data, only `query` and `total` are returned.

### Outreach request (item) fields

| Field | Type | Description |
|---|---|---|
| `outreach_request_id` | string | Unique ID of the request |
| `counterparty.company_name` | string | Company to be contacted |
| `counterparty.reference_id` | string \| null | **Your** free-text reference (e.g. ERP supplier ID) |
| `counterparty.contact.first_name` / `last_name` | string | Primary contact name |
| `counterparty.contact.email` | string | Primary contact email |
| `counterparty.contact.country` | string | ISO-3166 alpha-3 country code |
| `counterparty.contact.tel` | string \| null | Phone |
| `counterparty.additional_contacts[]` | array | Extra contacts (`email` required; `first_name`, `last_name`, `tel`, read-only `contact_ref`). Intercom-delivered orgs only |
| `relationship_manager.name` / `email` | string | Your internal owner of the relationship |
| `created_at` / `modified_at` | date-time | Created / last modified (UTC) |
| `financial_submissions[].created_at` | date-time | When the counterparty uploaded financials (newest first) |
| `is_rated` | boolean | `true` once a rating exists |
| `rrid` | string \| null | RRID of the rated company. Use it with `/v2.0/companies/{rrid}/` |
| `status` | string \| null | Current status (e.g. `Request Created`). **Display only; values may change without notice** |
| `status_history[]` | array | `status`, `status_detail`, `status_comment`, `status_reasons[]`, `status_updated_at` |
| `_embedded` | object | Present when `embed` is used (e.g. survey result links) |

---

## 3. Create: `POST /outreach-requests`

> Creating a request makes RapidRatings **send real emails to the counterparty contact**. It was **not executed** during testing. Only the validation errors were checked.

### Request

```http
POST /outreach-requests HTTP/1.1
Host: api.sandbox.rrilabs.co
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "counterparty": {
    "company_name": "Bob's Burgers",
    "reference_id": "your-ref-1234",
    "contact": {
      "first_name": "Joe",
      "last_name": "Bloggs",
      "email": "joe.bloggs@example.com",
      "country": "USA",
      "tel": "353851234567"
    }
  },
  "relationship_manager": {
    "name": "Joe Bloggs",
    "email": "joe.bloggs@example.com"
  }
}
```

### Body fields

| Field | Required | Rules |
|---|---|---|
| `counterparty` | **Yes** | object |
| `counterparty.company_name` | **Yes** | 1–255 chars |
| `counterparty.contact` | **Yes** | object |
| `counterparty.contact.first_name` | **Yes** | 1–255 chars |
| `counterparty.contact.last_name` | **Yes** | 1–255 chars |
| `counterparty.contact.email` | **Yes** | email, 1–255 chars |
| `counterparty.contact.country` | **Yes** | ISO-3166 alpha-3 (e.g. `USA`) |
| `counterparty.contact.tel` | No | string |
| `counterparty.reference_id` | No | free text: put your supplier/vendor ID here |
| `counterparty.additional_contacts` | No | **max 1** on create (more returns 400); returns **409** if your org isn't on Intercom delivery |
| `relationship_manager` | **Yes** | object |
| `relationship_manager.name` | **Yes** | 1–255 chars |
| `relationship_manager.email` | **Yes** | email, 1–255 chars |

### Response

`201 Created` returns the created outreach request object (same fields as a list item, with the new `outreach_request_id`).

---

## 4. Get one: `GET /outreach-requests/{id}`

`200 OK` returns a single outreach request object (same fields as a list item).

## 5. Communications: `GET /outreach-requests/{id}/communications`

`200 OK` (from spec):

```json
{
  "communications": {
    "introductions": [
      { "channel": "email", "sent_at": "2019-08-24T14:15:22Z" },
      { "channel": "email", "sent_at": "2019-09-24T14:15:22Z" }
    ]
  }
}
```

| Field | Type | Description |
|---|---|---|
| `communications.introductions[]` | array | Each outreach attempt RapidRatings made (may be empty) |
| `.channel` | string | Currently always `email` |
| `.sent_at` | date-time | When it was sent |

---

## 6. Error responses (all real, from the sandbox)

Format (RFC 7807):

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Not a valid sort" }
```

Validation errors add an `errors` array.

| HTTP | When (tested) | `detail` |
|---|---|---|
| `400` | Invalid `sort` | `Not a valid sort` |
| `400` | Non-integer `limit` | `["invalid literal for int() with base 10: 'abc'"]` |
| `400` | Unknown query parameter | `Extra query parameter(s) foo not in spec` |
| `400` | Invalid `cursor` | `Invalid Cursor` |
| `400` | POST with `{}` | `Input errors`, `errors: ["'counterparty' is a required property", "'relationship_manager' is a required property"]` |
| `400` | POST with no body | `Input errors`, `errors: ["None is not of type 'object'"]` |
| `401` | No `Authorization` header, or Basic auth | `No authorization token provided` |
| `401` | Invalid / expired Bearer token | `The server could not verify that you are authorized to access the URL requested. …` |
| `403` | GET an ID that doesn't exist / isn't yours | `Sorry, you do not have permission to access this resource` (title `Permission denied.`) |
| `404` | Communications for an unknown ID | `Solicitation matching query does not exist.` |

> An invalid token returns **401** here, while rridata returns **403**. The client's "refresh token and retry" logic must handle both.

---

## 7. Implementation notes

1. **Same token, different host.** Use `RR_OUTREACH_BASE_URL`, and parse errors as problem+json (`detail`), not `{"Request": {...}}`.
2. **Paging:** loop while `cursors.next_cursor` is set, passing it as `cursor`. Use a large `limit` (e.g. 100) to cut down on calls.
3. **Status sync:** poll the list with `sort=-created_date` (or `q=is_rated=false` for open requests). When `is_rated` becomes `true`, take `rrid` and pull the company and ratings from the rridata API.
4. **Map your supplier ID into `counterparty.reference_id`** on create, so results can be matched back without fuzzy name matching.
5. **Don't build logic on `status` text.** RapidRatings says its values may change. Use `is_rated`, `rrid` and `financial_submissions` instead.
6. **POST has real-world side effects** (emails to suppliers). Gate it behind an explicit flag or approval, and never call it automatically in tests.
---

# 12. Cross-API behaviour and gotchas

| Behaviour | rridata (`/v2.0/...`) | Outreach (`api.sandbox...`) |
|---|---|---|
| Host | `rridata.sandbox.rrilabs.co` | `api.sandbox.rrilabs.co` |
| Auth | Bearer | Bearer (same token; Basic is rejected) |
| Invalid / expired token | **403** `Not Authorized.` | **401** `Unauthorized` |
| No token | 401 | 401 |
| Paging | `page` + `next` URL (companies 20, periods/ratings 10 per page, fixed) | `limit` + `cursor` (`next_cursor`) |
| Page past the end | companies **404**, portfolios/periods/ratings **500** | n/a |
| Unknown query parameter | 400 `Not valid query parameter: X.` | 400 `Extra query parameter(s) X not in spec` |
| Error body | `{"<Scope>": {"message", "info"}}` | RFC 7807 `{type, title, status, detail[, errors]}` |
| Trailing slash | **Required** (no slash = 404) | Optional |
| Incremental ("changed since") filter | **None**, so do a full read and diff | `sort=-created_date`, `q=is_rated=...` |
| Rate limit header | `X-Ratelimit-Limit: 30000000` | `X-Ratelimit-Limit: 500000` |
| Write operations | POST `/companies/` (creates private company; not used) | POST `/outreach-requests` (**emails real people**) |

### Recommended sync logic

1. `GET /portfolios/{RR_PORTFOLIO_ID}/`: confirm the portfolio exists (404 = stop and alert).
2. `GET /companies/?portfolio={id}`: follow `next`, then upsert companies by `id` (RRID).
3. For each company, compare `latestRatings[0].id` with what's stored. If it's new or changed, pull `/ratings/` and `/periods/` (page 1 for incremental, all pages for the initial load) and upsert on (`rrid`, `id`).
4. `GET /outreach-requests?sort=-created_date`: when `is_rated` becomes `true`, take `rrid` and run step 3 for it.
5. Never call POST endpoints automatically.

### Sandbox facts (2026-10-28)

| Item | Value |
|---|---|
| Portfolios | 5: 36 US COVERAGE (96), 137 INTERNATIONAL COVERAGE (0), 631 Fortune 500 (37), **27070 z2Data Sandbox (96)**, 27072 z2Data Sandbox-Private (0) |
| Companies in 27070 | 96 (5 pages) |
| Ratings / periods per company | about 26–27 (2014Q1 to 2020Q2/YE) |
| Outreach requests | 0 |
| Sample RRIDs | `XAZPzdJ` Tyson Foods, `KawjbGJ` Rite Aid |
| OpenAPI spec (outreach host) | `https://api.sandbox.rrilabs.co/openapi.yaml` |
