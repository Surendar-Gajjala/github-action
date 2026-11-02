# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

React-only dashboard (React 18, TypeScript strict, Vite 5, TanStack Query 5, Axios, React Hook Form + Zod, Tailwind 3, Lucide) for browsing RapidRatings sandbox data, inspecting response schemas, mapping fields to an A1 target schema and running manual retrievals. RapidRatings is the first of several planned data-integration providers. There is deliberately **no backend, database or Docker**; the only server-side code is a dev-only proxy inside `vite.config.ts`. The original brief is in `imp/impDetails.txt`.

## Commands

```bash
npm run dev        # http://localhost:5173 (strictPort); required for "proxy" auth mode
npm run build      # tsc -b + vite build -> dist/ (this is also the type check gate)
npm run typecheck  # tsc -b only
npm run preview    # serves dist/ — no proxy, so only mock/direct modes work
```

There is no lint config and no test runner or test files in the repo (the README's "27/27 logic tests" were ad-hoc and not committed). Verify changes with `npm run build`; check the proxy with `curl http://localhost:5173/rr-proxy/status` (returns token expiry/scope, never the token).

Path alias: `@/` → `src/`.

## Architecture

### Auth modes drive everything
`getAuthMode()` (`src/services/config.ts`) returns `mock | proxy | direct`: a Settings-page override in localStorage (`rr.authModeOverride`) wins over `VITE_RAPIDRATINGS_AUTH_MODE`.
- **mock** — `apiRequest` short-circuits to `services/mock/mockServer.ts`, which generates fictional data that mimics the real response shapes *and* provider rules (fixed page sizes, 400/404/500 behaviour). Pages show `<DemoBanner />` in this mode.
- **proxy** — Axios base URL becomes `/rr-proxy/data` or `/rr-proxy/outreach`; the Vite plugin in `vite.config.ts` does the OAuth client-credentials exchange using non-`VITE_` env vars (`RR_CLIENT_ID`, `RR_CLIENT_SECRET`, `RR_TOKEN_URL`), caches the token, renews 60 s before expiry and retries once on 401/403. Data API is GET-only through the proxy; outreach allows GET/POST.
- **direct** — browser calls the hosts with a token pasted in Settings (memory only). Only works for outreach: the rridata host blocks browser CORS from localhost.

Never put secrets in `VITE_` variables (they are inlined into the bundle), and never persist tokens or provider response data in localStorage.

### Request layering
`features/*Page.tsx` → `hooks/use*.ts` (React Query; **query keys include `getAuthMode()`** so switching mode never shows cached data from another mode) → `services/rapidRatings*Service.ts` (one module per resource, documented operations only) → `services/httpClient.ts` `apiRequest(host, path, opts)`, the single entry point for all provider calls. `httpClient` has one Axios client per host (`data` = rridata, `outreach` = separate API host), bounded GET-only retry on 429/502/503/504, records success/error per host in `apiActivity.ts` (which drives the header's connection indicator), and normalises every failure into a secret-free `ApiError`.

Services convert raw responses with `utils/responseAdapter.ts` into the provider-neutral `PageResult<T>`: `adaptRridataPage` (page-number envelopes with `count/next/previous`) vs `adaptCursorPage` (outreach cursor paging); `collectAllPages` follows `next` links for full retrievals.

### Provider quirks the code depends on
- rridata paths need trailing slashes (`companies/`, `portfolios/{id}/`).
- Page sizes are fixed by the provider (see `config.pageSize`); pages past the end return 404 (companies) or **500** (others), so the UI only navigates via `next`/`previous` — which is why `TablePagination` is prev/next only.
- Unknown query params return 400; company `name` search needs ≥ 3 chars.
- Expired token: 403 on rridata, 401 on outreach.

### Schemas and mapping (kept as three separate concerns)
- `schemas/sourceSchemaRegistry.ts` — provider (source) schemas. Outreach is *declared* from the provider OpenAPI spec; rridata resources are *inferred* (no spec published), with a baseline plus live discovery via `utils/fieldTypeDetector.ts` and snapshot diffs.
- `schemas/targetSchemaRegistry.ts` — A1 target schema; **illustrative example only**, to be replaced by real A1 metadata.
- `schemas/mappingSchema.ts` — Zod schema for the mapping configuration; validated by `utils/mappingValidator.ts`, applied by `utils/transform.ts`.

`services/syncService.ts` ("Sync now") retrieves via the services, maps/validates records **in memory and discards them**; only run metadata is kept. There is no A1 write ("A1 sync: Not performed") and no background scheduler — `features/settings/schedule.ts` is configuration/next-run calculation only.

Browser-persisted state (all via `utils/storage.ts`, non-sensitive only): `rr.authModeOverride`, `rr.mappingConfiguration`, `rr.schemaSnapshots`, `rr.syncHistory`, `rr.syncSchedule`. Small shared stores use `utils/store.ts`.

### Adding a provider
Provider-specific code is limited to `services/rapidRatings*`, `schemas/sourceSchemaRegistry.ts`, the response-adapter envelopes and the dev-proxy plugin. Pages, tables, schema explorer, mapping, validation and sync consume provider-neutral types (`PageResult<T>`, `ApiError`, `ResourceSchema`, `MappingConfiguration`) and should stay that way.

## UI conventions

- Styling is Tailwind plus shared component classes in `src/styles/index.css` (`.card`, `.card-flush`, `.toolbar`, `.btn-*`, `.input`, `.label`, `.th`, `.td`, `.link`). The visual target is `docs/ReferanceImage.jpeg`: white surfaces, sentence-case table headers with column dividers, outlined compact badges (`StatusBadge` tones), blue identifier links.
- Table pages: `<PageHeader>` (full-bleed, bottom border) → `<DemoBanner />` → `<div className="card-flush">` with a `.toolbar` row, then `DataTable` + `TablePagination`. `.card-flush` bleeds to the edges of `<main>` (`-mx-4 sm:-mx-8`), so don't use it inside grid columns.
- Loading/error/empty states use `LoadingState`, `ErrorState`, `EmptyState`; record details open in `Drawer` with `DetailList` and `JsonViewer`.
- Display "Not retrieved yet" / `fmt(undefined)` rather than inventing values; no custom score or risk-band mapping is applied to ratings — show provider values as returned.

## Reference docs

- `docs/api/` — verified per-endpoint API docs (auth, companies, portfolios, periods, ratings, outreach); `docs/RapidRatings_API_Reference.md` is the consolidated reference.
- `docs/postman/` — Postman collection and sandbox environment.
- The Vite watcher ignores `docs/`, `imp/` and `dist/` (files there can be locked on Windows and crash the watcher with EBUSY).
