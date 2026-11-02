# RapidRatings Data Integration Dashboard

React + TypeScript dashboard for the **Data Integrations** project. RapidRatings is the first provider. It browses RapidRatings sandbox data (companies, portfolios, periods, ratings, outreach requests), inspects response schemas, maps fields to an A1 target schema, and runs manual retrievals.

It is a **React-only** application. There is no backend, database or Docker setup. The one exception is an optional dev-server proxy that runs inside `vite dev` (see [Authentication](#authentication-modes)).

Stack: React 18, TypeScript, Vite 5, React Router 6, Axios, TanStack Query 5, React Hook Form + Zod, Tailwind CSS 3, Lucide icons.

---

## Quick start

```bash
npm install
cp .env.example .env      # then edit .env (see below)
npm run dev               # http://localhost:5173
```

| Script | Purpose |
|---|---|
| `npm run dev` | Dev server (required for `proxy` mode) |
| `npm run build` | Type-check + production build to `dist/` |
| `npm run preview` | Serve the production build (no proxy: use `mock` or `direct`) |
| `npm run typecheck` | TypeScript only |

---

## Configuration

`.env.example` documents every variable. `.env` is git-ignored.

### Frontend variables (bundled into the browser app)

| Variable | Example | Purpose |
|---|---|---|
| `VITE_RAPIDRATINGS_DATA_API_URL` | `https://rridata.sandbox.rrilabs.co/v2.0` | Data API base (companies, portfolios, periods, ratings) |
| `VITE_RAPIDRATINGS_OUTREACH_API_URL` | `https://api.sandbox.rrilabs.co` | Outreach API base (separate host) |
| `VITE_RAPIDRATINGS_AUTH_URL` | `https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` | Token endpoint (display/reference) |
| `VITE_RAPIDRATINGS_AUTH_MODE` | `mock` \| `proxy` \| `direct` | Default auth mode (can be overridden in **Settings**) |

> **Never put secrets in `VITE_` variables.** Vite inlines them into the JavaScript bundle.

### Dev-proxy variables (NOT bundled)

`RR_CLIENT_ID`, `RR_CLIENT_SECRET` and `RR_TOKEN_URL` are read only by `vite.config.ts` on the developer's machine for `proxy` mode. The production build was scanned to confirm it contains neither the client ID, the secret nor the Basic-auth string.

---

## Authentication modes

The documented RapidRatings contract is OAuth 2.0 **client credentials**: `POST /v2.0/oauth/token/` with `Authorization: Basic base64(client_id:client_secret)` and `grant_type=client_credentials`. It returns a Bearer `access_token` valid for **1800 s** with **no refresh token**. Renewal means repeating the request. See `docs/RapidRatings_API_Reference.md`.

Two facts (verified 2026-10-28) shape the design:

1. A browser app cannot hold the client secret safely.
2. **The rridata host does not allow browser CORS** from `localhost`. It only returns `Access-Control-Allow-Origin` for `https://portal.sandbox.rrilabs.co`, and that includes the token endpoint. The Outreach host *does* allow `localhost`.

| Mode | How it works | Works for | Use it for |
|---|---|---|---|
| **mock** | Synthetic, fictional demo data generated in the browser, following the verified response structures and provider rules (page sizes, 400/404/500 behaviour). Clearly labelled **Demo data** everywhere. | Everything (fake data) | UI development without credentials |
| **proxy** | `vite dev` middleware (`/rr-proxy/*`) performs the token request with the `.env` credentials, caches the token, renews it 60 s before expiry and retries once on 401/403. The browser never sees the token or secret. GET only for the data API; GET/POST for outreach. | All endpoints | Local development against the real sandbox |
| **direct** | The browser calls the provider with an access token **pasted at runtime** in Settings (memory only, never stored or logged). No renewal is possible (no refresh token). | **Outreach only.** rridata calls fail with a clear CORS message. | Checking outreach from the browser |

The header shows the connection state. It reads **Connected** only after a real request succeeds against both hosts. "Partially connected", "Not connected" and "Not configured" are shown with the reason.

**Production:** neither `proxy` nor `direct` is a production design. Deploy a backend or API gateway that holds the credentials, performs the token exchange and forwards requests (the dev proxy in `vite.config.ts` is a reference implementation of exactly that logic).

---

## Features

| Page | What it does | API |
|---|---|---|
| **Overview** | Companies/portfolios count (provider `count`), periods/ratings retrieved (last manual sync), last successful request, connection status. Shows "Not retrieved yet" instead of invented numbers. | real |
| **Companies** | Server-side pagination (20/page, fixed), server-side name search (≥ 3 chars, a provider rule), portfolio filter, details drawer (detail endpoint, latest ratings, raw JSON), links to Periods/Ratings carrying the RRID. | `GET /companies/`, `/companies/{rrid}/` |
| **Portfolios** | Table, in-page search (the API has no search parameter), pagination via `next`, drawer with the companies in the portfolio via its documented `companies` link. | `GET /portfolios/`, `/portfolios/{id}/` |
| **Periods** | Company selector (or RRID input), every period (provider pages followed), filter + client paging, drawer with all fields, links to the matching rating. Currency and scale shown from the company record. | `GET /companies/{rrid}/periods/` |
| **Ratings** | Company selector, full rating history with the fields the API returns (FHR, CHS, PD, term PD, deltas, sub-scores, provider's own Strong/Average/Weak values), drawer, provider vs demo badge. **No custom score or risk-band mapping is applied.** | `GET /companies/{rrid}/ratings/` |
| **Outreaches** | Documented operations only: list (cursor paging, `q` filters, documented sort keys), detail, communications, **create** (Zod-validated against the OpenAPI request schema, with an explicit confirmation because it emails the counterparty). No PUT/PATCH/DELETE (none documented). | `/outreach-requests` |
| **Schema Explorer** | Per resource: declared fields (Outreach, from the provider's OpenAPI spec, with required flags) vs inferred fields (rridata publishes no spec), live discovery from a real page, nested paths, types, nullability, "seen in N/M", sample JSON (memory only), snapshots and a diff (added / removed / type changed). | real |
| **Schema Mapping** | Source field → A1 entity/property table, type-compatibility validation, missing required targets, unmapped source fields, unmapped targets, stale mappings, nested paths, save (browser), JSON export/import (Zod-validated). | none |
| **Sync History** | "Sync now" per resource with progress, no duplicate in-flight runs, partial-failure handling (periods/ratings per company), retry. Records are mapped and validated **in memory and discarded**. Shows retrieved vs processed and **"A1 sync: Not performed"**. | real (read-only) |
| **Settings** | Auth mode, runtime token (direct), connection test with per-host results; sync schedule (resource, enabled, frequency, timezone, time, day, last run, next planned run). | none |

---

## Demo features vs real capabilities

| Real (talks to RapidRatings) | Demo / browser-only |
|---|---|
| All five resource pages in `proxy` mode (Outreach also in `direct`) | Everything in `mock` mode (fictional data, labelled) |
| Connection status (derived from real requests only) | A1 target schema: an **illustrative example** in `src/schemas/targetSchemaRegistry.ts`. Replace it with real A1 metadata. |
| Schema discovery from live responses | Mapping configuration and schema snapshots: browser `localStorage` |
| Manual retrieval ("Sync now") | Sync history: browser `localStorage`, metadata only, not durable or shared |
| Outreach create (sends real emails) | Schedule: configuration and validation only. **Nothing executes on it.** |

## Limitations

- **No background scheduling.** Reliable schedules need a backend scheduler or job runner. Browser timers are deliberately not used.
- **No A1 write.** "Processed" means mapped and validated in memory. No A1 endpoint was specified.
- **Data retention.** Provider responses live only in the in-memory query cache (cleared on reload or mode switch). Only non-sensitive metadata (mappings, field-name snapshots, run counts, schedule) goes to `localStorage`. Confirm retention rules against the RapidRatings agreement before persisting anything.
- **Provider quirks handled:** trailing slashes required on rridata; pages past the end return 404 (companies) or **500** (others), so the UI only follows `next`/`previous`; unknown query parameters return 400; expired tokens return **403** on rridata and **401** on outreach; integer-looking decimals (`1575.0`) are treated as numbers.
- **Report downloads** (`reports[].downloadHref`) need API auth and aren't fetched by the UI.
- Sandbox has 0 outreach requests, so outreach lists show an empty state there.

---

## Project structure

```
vite.config.ts            Vite config + dev-only RapidRatings auth proxy (/rr-proxy)
src/
  app/                    App, router, React Query client (in-memory cache only)
  components/
    layout/               AppLayout, Sidebar, Header, Breadcrumbs
    tables/               DataTable, TablePagination (+ client paging hook)
    common/               Loading/Error/Empty states, StatusBadge, JsonViewer, Drawer,
                          PageHeader/DemoBanner, CompanySelector, ConnectionIndicator
  features/               overview, companies, portfolios, periods, ratings, outreaches,
                          schema-explorer, schema-mapping, sync-history, settings
  services/
    config.ts             Env config + auth-mode override
    httpClient.ts         Axios clients per host, timeouts, bounded retry, error normalisation
    rapidRatings*Service  One module per resource (documented operations only)
    rapidRatingsAuthService.ts  Auth modes + real connection checks
    syncService.ts        Manual sync engine (transient processing, run metadata)
    mock/mockServer.ts    Synthetic demo data generator
  schemas/                sourceSchemaRegistry (provider), targetSchemaRegistry (A1 example),
                          mappingSchema (Zod config schema) — kept separate
  hooks/                  React Query hooks per resource
  types/                  API, resource, schema and mapping types
  utils/                  responseAdapter, fieldTypeDetector, mappingValidator, transform, …
docs/                     Verified API reference (request/response/schemas/auth)
```

### Adding another provider

The provider-specific parts are the `services/rapidRatings*` modules, `schemas/sourceSchemaRegistry.ts`, the response adapter envelopes and the dev-proxy plugin. Pages consume provider-neutral `PageResult<T>`, `ApiError`, `ResourceSchema` and `MappingConfiguration` types, so a second provider adds its own services and source registry and reuses the tables, schema explorer, mapping, validation and sync machinery.

---

## Verification performed (2026-10-28)

- `npm run build` succeeds (TypeScript strict, no errors). The bundle contains no credential values.
- Dev proxy tested against the sandbox: token status (token never returned), portfolios, companies (name filter, paging), ratings, outreach, 400 passthrough, POST to the data API blocked (405).
- Logic tests (mock server rules, adapters, pagination, schema inference and diff, mapping validation, transform, import validation, timezone-aware next-run maths): 27/27 pass.
- Baseline schemas compared with live sandbox responses for companies, portfolios, periods and ratings: no differences.
- The UI was **not** visually tested in a browser during implementation. Run `npm run dev` and review each page.
