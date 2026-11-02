# Demo-Application

A read-only React app over a hosted data platform (Enterprise, `https://<enterprise-host>`). The hosted server is the source of truth: the frontend sends it the query payloads in `frontend/src/queries/`, maps the responses into its own models and renders them. Nothing is stored.

Design notes: [CLAUDE.md](CLAUDE.md).

```text
Browser ──► frontend/ (React, :3000) ──/enterprise (Vite proxy adds the Bearer token)──► https://<enterprise-host>/api/v1/platform
```

`<enterprise-host>` stands for the Enterprise server's host name. It is set in `frontend/vite.config.ts` (proxy `target`) and is not repeated in the docs.

Endpoints used, relative to `https://<enterprise-host>/api/v1/platform`:

| Endpoint | Used by |
|---|---|
| `POST …/core/query-config-v2/execute?page=&size=` | Items, Parts, Sites and Companies lists, dashboard totals, hierarchy anchors |
| `GET …/core/data-configuration/table-configs/item-hierarchy` | Item Hierarchy configuration |
| `POST …/core/graph/match/execute` | Item Hierarchy tree, item Sources |
| `GET …/core/objects/partial/{id}` | Item, part and company Overview |

## What it does

| Tab | Shows | Enterprise calls |
|---|---|---|
| **Dashboards** | Totals for top-level BOMs, Items, Parts and Sites, each linking to its tab | the list queries with `size=1`; the BOM tile runs only the hierarchy anchor query |
| **Item Hierarchy** | Products expanded into BOM child items (with qty) and the parts each item sources | table config → anchor query → one batched graph match |
| **Items** | Paged item table; an item number opens the item's details | query-config `item-query.json` |
| **Item details** | **Overview** (item properties in sections: risk bands and scores, sourcing, geography, compliance) and **Sources** (the item's sourced parts) | partial object; graph match `item-sources-graph.json` |
| **Parts** | Paged part table; a part number opens the part's details | query-config `part-query.json` |
| **Part details** | **Overview** (risk, lifecycle, sourcing, origin and trade, technical, qualifications, compliance) | partial object |
| **Sites** | Paged site table | query-config `site-query.json` |
| **Companies** | Paged company table; a company name opens the company's details | query-config `company-query.json` |
| **Company details** | **Overview** (general, supplier risk, identifiers, financial, ownership, sourcing, certifications, ESG, cyber, Kharon) and **RapidRatings** (read live when the tab opens: profile, current rating, sector benchmark, data context, report links, and the Latest Ratings, Periods and Ratings tables) | partial object; RapidRatings through `/rapidratings` (see below) |

All tables page on the server (`page` from 0, default 25 rows) and search within the page on screen.

**RapidRatings** is found by the Z2 company name (legal suffixes such as "Inc" are ignored); an exact name match is shown directly, otherwise you choose from the RapidRatings companies whose name contains it. RapidRatings does not allow browser calls (no CORS) and its client secret must stay private, so the browser calls the same-origin `/rapidratings` path and [frontend/proxy/rapidRatings.ts](frontend/proxy/rapidRatings.ts) adds the token. Nothing is written to Z2.

## Configuration

Copy [frontend/.env.example](frontend/.env.example) to `frontend/.env` (git-ignored):

| Variable | Purpose |
|---|---|
| `VITE_ENTERPRISE_API_BASE_URL` | Path the browser calls; default `/enterprise` (the dev proxy) |
| `ENTERPRISE_API_TOKEN` | Bearer token for Enterprise. **Secret: only in `frontend/.env`, never in `.env.example` or Git.** Read only by the Vite dev proxy (Node side), so it never reaches the browser bundle |
| `ENTERPRISE_API_HOST` | Enterprise origin, `https://<enterprise-host>` (https, no path). Target of the Vite dev proxy (falls back to the built-in host when empty); also read by the Netlify Edge Function |
| `RAPIDRATINGS_CLIENT_ID`, `RAPIDRATINGS_CLIENT_SECRET` | RapidRatings OAuth client credentials. **Secret: only in `frontend/.env` / the Netlify environment.** Used only by the `/rapidratings` proxy (Vite dev/preview server, Netlify Edge Function), which gets and renews the access token; never sent to the browser |
| `RAPIDRATINGS_API_BASE_URL` | Optional; default `https://rridata.sandbox.rrilabs.co/v2.0` |

> **The hosted token expires after about 24 hours.** When it does, every tab shows "The Enterprise API token is missing or expired (401)". Put a fresh token in `frontend/.env` and restart `npm run dev`; Vite only reads `.env` at startup.

## Run locally

Requires Node.js 22.3+.

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:3000.

To build and serve the production bundle locally:

```bash
cd frontend
npm run build      # writes dist/
npm run preview    # serves dist/ on http://localhost:4173 with the same /enterprise proxy
```

The token is injected by the Vite proxy (`npm run dev` and `npm run preview`). Hosting `dist/` anywhere else needs an equivalent reverse proxy: forward `/enterprise` to `https://<enterprise-host>/api/v1/platform` and add the `Authorization` header. Enterprise allows CORS from any origin, so the only reason for the proxy is keeping the token out of the browser.

## Deploy on Netlify

[netlify.toml](netlify.toml) sets up the build:
- It builds `frontend/` with Node 22 and publishes `frontend/dist`.
- It serves `index.html` for page routes such as `/parts` or `/items/123`.

`/enterprise/*` is handled by the Edge Function [frontend/netlify/edge-functions/enterprise.ts](frontend/netlify/edge-functions/enterprise.ts). It is the production counterpart of the Vite proxy:
- It forwards GET/POST requests, their query string and body, to `https://<enterprise-host>/api/v1/platform/*` on the configured host only.
- It adds the Bearer token server-side.
- It passes Enterprise's responses through unchanged.

1. In Netlify, go to **Site configuration → Environment variables** and add these with the default scopes:

   | Key | Value |
   |---|---|
   | `ENTERPRISE_API_HOST` | `https://<enterprise-host>`: origin only, https, no path |
   | `ENTERPRISE_API_TOKEN` | the Enterprise Bearer token (secret) |

2. **Redeploy** (Deploys → Trigger deploy → Deploy site). The function only sees variable changes after a new deploy, so do this after every token renewal too.
3. Check with `POST https://<your-site>/enterprise/core/query-config-v2/execute?page=0&size=1` and a query body from `frontend/src/queries/`. It should return 200 with `results`.

Errors raised by the proxy itself have an `X-Enterprise-Proxy-Error` header, a `{ code, message }` body, and are shown as-is on the page:

| Code | Meaning |
|---|---|
| `PROXY_NOT_CONFIGURED` | A variable is missing, or the site was not redeployed after adding it |
| `PROXY_INVALID_HOST` | `ENTERPRISE_API_HOST` is not an https URL |
| `UPSTREAM_TIMEOUT` | No response headers within 38 s |
| `UPSTREAM_UNREACHABLE` | Enterprise could not be reached |

Errors from Enterprise itself (for example 401 for an expired token) pass through without that header.

> The deployed site has no login: anyone with its URL sees the data through your token.

## Tests

```bash
cd frontend && npm run typecheck && npm test
```

Tests render each page against mocked Enterprise responses (MSW) shaped like the real ones.

## Notes

- **Item Hierarchy speed:** the graph match for a page of products is slow (about 30–40 s and 2 MB for 12 products), so its request timeout is 180 s.
- **Frontend toolchain:** pinned to Vite 6, Vitest 3 and jsdom 26, which still support Node 22.3. Newer majors need Node 22.12+.
