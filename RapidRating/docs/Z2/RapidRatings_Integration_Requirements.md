# RapidRatings Integration – Build Guide

| | |
|---|---|
| **Program** | Z2 Enterprise – Data Integrations |
| **Integration** | RapidRatings (company financial health ratings) |
| **Target DataModel** | Existing Z2 Company DataModel |

**How to use this guide**

1. Read **Part 1** (overview) and **Part 2** (rules). The rules apply to every step.
2. Build **Part 3** step by step, in order. Each step has three blocks: **Build** (what to make), **Rules** (how it must behave) and **Done when** (the tests that prove it).
3. Look up endpoints, API behaviour and field mappings in **Part 4** (reference).

---

# Part 1 – Overview

## 1.1 Goal

Z2 Enterprise has a **Data Integrations** area where each customer (tenant) enables external integrations. You are adding **RapidRatings**.

When a tenant enables RapidRatings, Z2 must:

1. connect to RapidRatings with the tenant's Client ID and Client Secret;
2. read company profiles, their financial periods and their ratings;
3. link each RapidRatings company to an **existing** Z2 Company;
4. store the data in a new **`rapidratings`** group on that Company record;
5. show the data on the Company Details page;
6. keep it up to date with manual and scheduled syncs;
7. record every sync run and every configuration change.

## 1.2 Scope

| In scope | Out of scope |
|---|---|
| Token handling, connection test | Outreach API (`api.sandbox.rrilabs.co/outreach-requests`): it sends real emails |
| Portfolios, companies, company details, periods, ratings | Creating Z2 Companies |
| Schema sync with versioning and review | A separate RapidRatings Company object type |
| Mapping into the new `rapidratings` group | Changing any existing Company property, group or layout |
| Company linking with an admin review list | A RapidRatings-specific scheduler |
| Manual and scheduled data sync | Storing raw API responses |
| Sync history and change history | |

## 1.3 Expected result

A tenant administrator configures and tests RapidRatings in Z2. Z2 retrieves the companies in scope with their periods and ratings, stores them in the `rapidratings` group of the linked Company records, and shows them on Company Details. Schema sync and data sync run independently. Every manual and scheduled run is traceable in the sync history and the change history.

---

# Part 2 – Rules That Apply Everywhere

**Security**

1. Only the Z2 backend calls RapidRatings. The browser calls Z2 APIs only.
2. The Client Secret and the access token never leave the backend: never in a UI response, log, error or history entry.
3. Everything (config, token cache, jobs, records, history) is separated by tenant.

**Data integrity**

4. Keep provider data exactly as received: `null` stays `null` (never `0` or `""`), signs are kept, nothing is rescaled or currency-converted.
5. Never create a Z2 Company and never overwrite an existing Company value. Data goes into the `rapidratings` group. The only existing field the integration may write is an **empty** Country.
6. Match records only by RRID and period ID, never by name, list position or response order.
7. `latest_ratings` (from the company response) and `ratings` (from the ratings endpoint) are separate collections. Never fill one from the other.

**Processing**

8. Paginate only by following the `next` URL. Never build page numbers.
9. Manual and scheduled syncs run the same code.
10. Schema sync and data sync are separate jobs. Schema sync never writes Company data; data sync never changes the schema, the DataModel or a mapping.
11. Report authentication, permission, validation and provider errors as distinct error types. Retries are always bounded.

---

# Part 3 – Build Steps

## Architecture

Requests flow in one direction: **Z2 UI → Z2 platform APIs → Data Integrations framework → RapidRatings adapter → RapidRatings API** (HTTPS, Bearer token).

| Component | Does | Never does |
|---|---|---|
| **Z2 UI** (browser) | Data Integrations screens and Company Details page; calls Z2 APIs | Calls RapidRatings; sees the secret or token |
| **Data Integrations framework** (backend, shared by all providers) | Config, secret references, schema registry, mapping, company linking, sync runner and queue, scheduler, history; writes Company records | Contains RapidRatings-specific URLs or rules |
| **RapidRatings adapter** (backend, RapidRatings only) | Token handling, API client, paging, response validation | Writes Z2 data; decides mapping or linking |

## End-to-end flow

**Setup (once per tenant)**

1. Admin enables RapidRatings and enters the Client ID and Client Secret.
2. Admin clicks **Test connection** → status `ACTIVE`.
3. Admin chooses the scope: all companies, selected portfolios or selected companies.
4. Admin approves the schema, activates the mapping and optionally sets a schedule.

**Every sync (manual or scheduled)**

1. Create the sync run record.
2. Get or reuse the token.
3. `GET /companies/` (with `portfolio` if the scope needs it) and follow `next` → list of RRIDs.
4. For each RRID:
   1. `GET /companies/{rrid}/`
   2. Link the RRID to a Z2 Company, or add it to the review list and skip it.
   3. `GET /companies/{rrid}/periods/` and follow `next`.
   4. `GET /companies/{rrid}/ratings/` and follow `next`.
   5. Validate → map → update the Company record.
   6. Save the company result.
5. Finish the run: status, counts and errors go to the sync history.

**Viewing:** a user opens a Company; Company Details shows the RapidRatings profile and the Latest Ratings, Periods and Ratings tables.

---

## Step 1 – Connection and authentication

**Build**

- Integration configuration per tenant: environment (sandbox / production), credential reference, scope, status.
- Client Secret stored in the Z2 secret manager; only its reference is saved.
- Token handling in the adapter (see **4.3 Token request**).
- **Test connection**: get a token, then call `GET /v2.0/portfolios/`. A 200 means the credentials and data access work.
- Connection states: `NOT_CONFIGURED → CONFIGURED → ACTIVE`, plus `AUTH_FAILED`, `PERMISSION_FAILED`, `DISABLED`.

**Rules**

- Keep the token in server memory only, per tenant and environment, with `expiresAt = now + expires_in`.
- Reuse the token; request a new one when less than 60 seconds remain.
- Data call returns 403 → get a new token and retry **once**. A second 403 → `PERMISSION_FAILED`.
- Token request returns 400 or 401 → `AUTH_FAILED`; never retry it.
- Rotating credentials: replace the secret, clear the cached token, test again, record the change without values.
- Syncs run only when the status is `ACTIVE`.
- Optional: workers of the same tenant share one in-flight token request.

**Done when**

| Test | Expected |
|---|---|
| Connect with valid credentials | `ACTIVE`; no secret or token in any log, response or history |
| Connect with a wrong secret | `AUTH_FAILED`; exactly one token request |
| Token with < 60 s left | New token before the next call |
| Data call returns 403 | One renewal and one retry; second 403 → `PERMISSION_FAILED` |
| Rotate credentials | History entry without values; token cache cleared |

---

## Step 2 – API client and schema sync

**Build**

- API client with one method per endpoint (see **4.1 Endpoints**) and a helper that follows `next` until it is `null`.
- Response validation against the approved schema version.
- Schema sync that builds and versions a schema per resource from real responses. RapidRatings publishes no schema file for this API.

**Rules – API client**

- Always send the trailing slash and only the accepted query parameters (see **4.2 API behaviour**).
- Follow `next`; never build page numbers and never retry a past-the-end 404/500.
- 30-second timeout. On 429, wait for `Retry-After` and retry at most 2 times.
- Errors carry the HTTP status and the API message, never the token.

**Rules – schema sync**

1. Runs on activation, when an admin clicks **Refresh schema**, and as a check before each data sync.
2. Samples the first page of companies, then the details, periods and ratings of the first company.
3. Records each field path (e.g. `termPd.year2`), its type and whether it can be `null`.
4. Compares the result with the approved version:

   | Finding | Result |
   |---|---|
   | No change | Marked "checked" |
   | Field is `null` where it had a type before | Not a type change; field marked nullable |
   | Field missing from some sampled items | Not removed; removed only if missing from every item |
   | New field | New version for review; sync continues |
   | Mapped field removed or retyped | New version for review; **sync blocked** until the mapping is approved |

5. The first run saves version 1 as `PENDING_REVIEW`. An admin approves it before the first data sync.
6. Every new version goes to the change history. The DataModel and approved mappings are never changed automatically.

**Done when**

| Test | Expected |
|---|---|
| Read a 3-page list | All items returned; only `next` URLs called |
| 429 with `Retry-After` | Waits and retries; stops after 2 |
| First schema sync | Version 1 `PENDING_REVIEW`; sync blocked until approved |
| Field `null` in sample, number before | No type change; sync continues |
| Source adds a field | New version for review; sync continues |
| Source removes or retypes a mapped field | Sync blocked until mapping approved |

---

## Step 3 – DataModel extension and Company Details

**Build**

- The new `rapidratings` property group, with its three lists, on the existing Company DataModel.
- Four new sections on the Company Details page.

**The existing DataModel stays untouched**

The Company DataModel already contains properties, property groups (`NESTED`, e.g. Basic Information, Financial with Moody's Credit Rating and D&B Rating, Kharon Risk Data) and array props (`PROPS_ARRAY`). None of them is changed, renamed, moved or removed. The integration only **adds** the `rapidratings` group.

**Structure of the `rapidratings` group**

- The group is `dataType: NESTED`, path `rapidratings`.
- Single values are properties with path `rapidratings.<dbName>`.
- The three lists are `dataType: PROPS_ARRAY`; their row fields have path `rapidratings.<list>.<dbName>`.
- Source objects are flattened into properties (`sectorAverages.fhr` → `sector_average_fhr`, `termPd.year2` → `term_pd_year_2`), because a `PROPS_ARRAY` row cannot contain a `NESTED` group.
- There is no URL type: URLs are `STRING` with `uiHints: {"displayType": "LINK"}`. Dates use `uiHints: {"displayType": "DATE"}`.
- Rows inside a list cannot be filtered, so the newest rating is also stored as single `current_*` properties.

**Contents of the group** (every field with its type is in **4.4 Field mappings**)

| Part | Target properties | Source |
|---|---|---|
| Profile | `rrid`, `source_company_name`, `source_country`, `ticker`, `exchange`, `sector`, `address` | Company: `id`, `name`, `country`, `ticker`, `exchange`, `sector`, `address` |
| URLs | `company_url`, `financials_url`, `periods_url`, `ratings_url` | Company: `href`, `financials`, `periods`, `ratings` |
| Data context | `currency`, `data_scale`, `data_confidentiality`, `industry_classification_system`, `industry_classification_code`, `external_source_id`, `external_source_ids` | Company: matching fields |
| Report links | `report_link_fhr`, `report_link_financial_dialog`, `report_link_peer_benchmark` | Company: `reportLinks.*` |
| Benchmark | `sector_average_fhr`, `sector_average_chs` | Company details: `sectorAverages.*` |
| Current rating | `current_rating_id`, `current_fhr`, `current_chs`, `current_probability_default`, `current_financial_date` | Company: `latestRatings[0]` |
| Sync | `last_synced_at` | Time of the sync |
| `latest_ratings` list | One row per item; replaced each sync | Company: `latestRatings[]` |
| `periods` list | One row per period; full history | `GET /companies/{rrid}/periods/` |
| `ratings` list | One row per rating; full history | `GET /companies/{rrid}/ratings/` |

**Property definition keys**

| Key | Value |
|---|---|
| `name`, `label` | Label from **4.4 Field mappings**, e.g. `Period ID` |
| `dbName` | Target from **4.4 Field mappings**, e.g. `period_id` |
| `path` | Parent path + `.` + `dbName`, e.g. `rapidratings.periods.period_id` |
| `dataType` | Type from **4.4 Field mappings**; the group is `NESTED`, the lists are `PROPS_ARRAY` |
| `required` | `true` only for `period_id` and `rating_id` |
| `searchable`, `filterable` | `true` for fields marked filterable; `false` for list fields |
| `uiHints` | `{"displayType": "LINK"}` for URLs, `{"displayType": "DATE"}` for dates |
| `canUpload` | `false` on the group, so only the integration writes it |
| `dataModelType`, `extDataModelId` | Provided by the Z2 platform team |
| `id`, `dataEnvId`, timestamps | Never set; the platform assigns them |

**Company Details sections**

Adding properties to the DataModel does not make them visible: the layout must list the RapidRatings fields and tables explicitly. Add these sections to the Details tab, after the existing sections and in the same layout format.

| Section id | Title | Shows | Layout |
|---|---|---|---|
| `rapidratings` | RapidRatings | RRID, source name, country, ticker, exchange, sector, address, current FHR / CHS / PD / period, sector averages, currency, data scale, report links, last synced | Fields, 2 columns |
| `rapidratings_latest_ratings` | RapidRatings › Latest Ratings | `rapidratings.latest_ratings` | Table |
| `rapidratings_periods` | RapidRatings › Periods | `rapidratings.periods` | Table |
| `rapidratings_ratings` | RapidRatings › Ratings | `rapidratings.ratings` | Table |

**Rules**

- Do not change any existing property definition, ID, path or layout.
- Tables show the newest entry first.
- `null` shows as blank or "—", never `0`.
- Show "(%)" on PD and term PD columns, and the data scale next to amount tables.

**Done when**

| Test | Expected |
|---|---|
| Compare the DataModel before and after | Only the `rapidratings` group is added; every existing property, group and array prop is unchanged |
| Open Company Details with test data | RapidRatings section and three tables shown; nulls blank |

---

## Step 4 – Mapping and company linking

**Build**

- Versioned mapping configuration from RapidRatings fields to Company properties (see **4.4 Field mappings**).
- Company linking to existing Z2 Companies, with a review list where an admin links companies that could not be matched.

**Rules – mapping**

1. **What is mapped:** the first mapping version maps every field in **4.4 Field mappings**. Only `reports[]` is left out.
2. **Where it goes:** every target is inside the `rapidratings` group. The single exception is `basic_info.country`, written only when empty.
3. **How each source shape maps:**

   | Source shape | Target | Example |
   |---|---|---|
   | Single value | Property of the group | `ticker` → `rapidratings.ticker` |
   | Object | Flattened properties | `sectorAverages.fhr` → `rapidratings.sector_average_fhr` |
   | First list item | `current_*` properties | `latestRatings[0].fhr` → `rapidratings.current_fhr` |
   | Array of objects | One `PROPS_ARRAY` row per item | each period → one `rapidratings.periods` row |
   | Array of strings | `STRING_ARRAY` | `externalSourceIds` → `rapidratings.external_source_ids` |

4. One source may feed two targets (`country` → `source_country` and `basic_info.country`), but one target path takes only one source. The same field name in different lists is allowed (`rapidratings.periods.eqy_year` and `rapidratings.ratings.eqy_year` are different paths).
5. **Activation checks.** A mapping version can be activated only when:
   - the schema version it is based on is approved (Step 2);
   - every source field exists in that schema;
   - every target path exists in the DataModel (Step 3 is deployed);
   - source and target types are compatible (see **Type conversion**); `country` → `basic_info.country` uses the ISO-3 → LOV lookup instead;
   - the keys are mapped: `rrid`, `periods.period_id`, `ratings.rating_id`, `latest_ratings.rating_id`, and all nine `term_pd_year_*` in both rating lists;
   - no target path has two sources.
6. **Versions:** every change creates a new version. Old versions are kept, only one is active, and each sync run records the version it used.

**Rules – Company Name and Country**

The integration never creates a Company and never overwrites one of these values.

| Source | Z2 property | Z2 definition | Behaviour |
|---|---|---|---|
| `name` | Company Name | `dbName: company_name`, STRING, primary key, required | Never written; used only to suggest link candidates |
| `country` | Country | `dbName: country`, path `basic_info.country`, LOV | Written only when empty; ISO-3 code → LOV value via a lookup table |

| Situation | Company Name | Country |
|---|---|---|
| Z2 value exists | Not changed | Not changed |
| Z2 value empty | – (required, so never empty) | Set from RapidRatings; recorded in change history |
| RapidRatings value empty | Nothing written | Nothing written |
| Country code has no LOV match | – | Nothing written; warning logged |

The RapidRatings values are always kept in `source_company_name` and `source_country`.

**Rules – linking**

Try these in order:

1. A Z2 Company whose `rapidratings.rrid` already equals the RRID.
2. If configured, a Z2 Company whose chosen identifier equals `externalSourceId`.
3. Otherwise, show candidates (by name, country, ticker) for an admin to confirm.

- Never link automatically on a name match alone.
- One RRID links to at most one Z2 Company per tenant.
- Unlinked companies go to the review list and nothing is written for them. An admin links them to an existing Company or leaves them unlinked.
- Record every link and unlink in the change history.

**Done when**

| Test | Expected |
|---|---|
| Unlinked company | In review list; nothing written |
| Linked Company whose name and country differ from RapidRatings | Company Name and Country unchanged; RapidRatings values in `source_*` fields |
| Linked Company with empty Country | Country set from RapidRatings; recorded |
| Admin links a review-list company | RRID saved on that Company; data stored on the next sync |
| Any sync | No Z2 Company is created |

---

## Step 5 – Data sync and Sync now

**Build**

- The sync pipeline (see **End-to-end flow**).
- **Sync now** for all companies, one portfolio or one company (also from Company Details).
- Live progress (companies done / total), sync history and retry.

**Update rules**

| Data | Key | Rule |
|---|---|---|
| Company link | tenant + RRID | One Z2 Company per RRID |
| Profile and `current_*` | Company record | Replaced with the latest values |
| Company Name | Company record | Never written |
| Country | Company record | Only when empty (Step 4) |
| `periods[]` row | RRID + `period_id` | Add new, update changed, **never delete** |
| `ratings[]` row | RRID + `rating_id` | Add new, update changed, **never delete** |
| `latest_ratings[]` | Whole list | Replaced each sync |

**Rules**

- Create the run record first (tenant, trigger, scope, mode, versions used).
- Before Sync now, check: status `ACTIVE`, an approved mapping exists, no blocking schema change. If a sync for the same scope is already running, show that run instead of starting a new one.
- Before writing, check: the RRID in each period/rating `href` matches the company, IDs exist, types match the schema. Reject and report any record that fails.
- Attach a rating to a period only when **both** RRID and period ID match.
- Re-running a sync creates no duplicates. If nothing changed, skip the write and count the company as `UNCHANGED`.
- One failing company does not stop the others; the run ends `PARTIALLY_SUCCEEDED`.
- Do not keep raw API responses after the run; store only the mapped data.
- Write through the platform's record-update API with optimistic locking.
- Process a few companies in parallel (e.g. 4).
- Store list rows newest first: by `eqy_year`, then `YE`, `Q3`, `Q2`, `Q1`.
- A failed run can be retried; the retry is a new run linked to the original.

**Sync modes** (RapidRatings has no changed-since filter)

| Mode | When | Behaviour |
|---|---|---|
| `FULL` | First sync and weekly | Read everything |
| `REGULAR` | Other runs | Read the company list and compare each company's `latestRatings[0].id` and `fhr` with the stored `current_rating_id` and `current_fhr`. Same → refresh the profile only. Different or new → read periods and ratings from page 1 and stop when known IDs appear. |

**Sync history** – one record per run:

| Field | Description |
|---|---|
| `runId`, `tenantId`, `integrationId` | Identity |
| `triggerType` | `MANUAL`, `SCHEDULED`, `RETRY` |
| `requestedBy` | User or schedule |
| `status` | `QUEUED`, `RUNNING`, `SUCCEEDED`, `PARTIALLY_SUCCEEDED`, `FAILED`, `CANCELLED`, `SKIPPED`, `SKIPPED_OVERLAP` |
| `startedAt`, `completedAt` | UTC |
| `scope`, `mode` | Companies in scope; `FULL` / `REGULAR` |
| `schemaVersion`, `mappingVersion` | Versions used |
| `counts` | listed, linked, unlinked, updated, unchanged, failed |
| `retryOf` | Original run, for retries |
| `errorSummary` | Readable; never secrets, tokens or raw responses |

Plus one result per company: `runId`, `rrid`, `companyId`, `outcome` (`UPDATED`, `UNCHANGED`, `UNLINKED`, `FAILED`), `reason`. The UI lists runs with filters (date, trigger, status) and shows the per-company results.

**Done when**

| Test | Expected |
|---|---|
| Sync a linked company | Profile, sector averages and `current_*` stored; one `periods[]` row per period and one `ratings[]` row per rating |
| Period with a `null` and a negative amount | Stored as `null` and negative; nothing turned into `0` |
| Run the same sync again | No duplicates; company `UNCHANGED` |
| Two companies with the same period ID | Never mixed |
| Rating `href` RRID differs from the company | Rejected and reported |
| A period missing from one response | Stored row kept |
| One company keeps timing out | Others succeed; run `PARTIALLY_SUCCEEDED` |

---

## Step 6 – Scheduled sync and history

**Build**

- Schedule settings: on/off, frequency (daily, weekly, monthly), time, time zone, scope, mode (regular / full), retry policy. Show the next and last run.
- Schedule history and change history screens.

**Rules – scheduling**

- The existing Z2 platform scheduler starts runs, using the same sync job as Sync now. Follow its conventions.
- Recommended default: daily `REGULAR` sync plus weekly `FULL` sync.
- One schedule time creates at most one run.
- If a run is still active, the new one is skipped as `SKIPPED_OVERLAP`.
- Runs missed during downtime run once at the next start.
- If the connection is `DISABLED` or `AUTH_FAILED`, the run is recorded as `SKIPPED`.
- Retry only timeouts, 429 and 502/503/504, up to 2 times with backoff. Never retry auth, permission, schema or mapping errors.
- Use the configured time zone, including daylight saving.

**Rules – schedule history:** every schedule change creates a new version. The schedule screen shows the current settings, who changed them and when, the next run and the last result.

**Rules – change history (audit)**

- Record: enable/disable, credential rotation (event only), connection test, schema approved/rejected, mapping changes, company linked/unlinked, Country filled, schedule changes, manual sync started.
- Each entry: `changeId`, `tenantId`, `actor`, `action`, `entity`, `before`, `after`, `timestamp`, `runId`, `comment`. Secrets are recorded as `"***changed***"`.
- The history can be read but never edited or deleted.

**Done when**

| Test | Expected |
|---|---|
| Same scope via Sync now and via schedule | Same stored result |
| Scheduled run while another is running | `SKIPPED_OVERLAP` |
| Edit schedule and mapping | New versions; change history shows who, before and after |

---

## Step 7 – Errors, security and pilot

**Error handling**

| Situation | Behaviour |
|---|---|
| Token request 400/401 | `AUTH_FAILED`; no retry; notify admin |
| Data call 403 | New token, one retry; second 403 → `PERMISSION_FAILED` |
| Data call 401 | Bug (missing header); fail the run |
| 400 unknown parameter | Bug; no retry |
| 404 on a detail URL | Check the trailing slash; otherwise company `FAILED` |
| 404/500 past the last page | Cannot happen when following `next`; never retry |
| 429 | Wait for `Retry-After`; limited retries |
| Timeout / 502 / 503 / 504 | Up to 2 retries with backoff |
| Unexpected data | Fail that company; flag a schema change |
| RRID in `href` does not match | Reject the record |
| Company not linked | `UNLINKED`; review list; nothing written |
| Breaking schema change | Block sync until the mapping is approved |

**Security**

- Secret in the secret manager only; token in server memory only; HTTPS for all calls.
- Only integration admins change settings, links or schedules, or start syncs. Business users read RapidRatings data; auditors read history.

**Monitoring** (optional): alert when a tenant is `AUTH_FAILED`, a scheduled run fails, two runs in a row are partial, a schema change blocks sync, or `last_synced_at` is older than two schedule intervals. Track API calls, retries and 429s per tenant.

**Pilot:** enable one tenant behind a feature switch and run scheduled syncs for a week.

**Rollback:** disable the integration to stop all syncs. Synced data stays on the Company records and can be hidden by removing the Company Details sections. Existing Company values are never overwritten, so nothing needs restoring.

**Done when**

| Test | Expected |
|---|---|
| Tenant B opens tenant A's data | Denied |
| Pilot | A week of scheduled syncs without manual fixes |

---

# Part 4 – Reference

## 4.1 Endpoints

Base URL (sandbox): `https://rridata.sandbox.rrilabs.co/v2.0`

| Resource | Method | Path | Paging |
|---|---|---|---|
| Token | POST | `/oauth/token/` | – |
| Portfolios | GET | `/portfolios/` | `page` + `next` |
| Companies | GET | `/companies/` | `page` + `next`, 20 per page (fixed) |
| Company details | GET | `/companies/{rrid}/` | – |
| Periods | GET | `/companies/{rrid}/periods/` | `page` + `next`, 10 per page (fixed), newest first |
| Ratings | GET | `/companies/{rrid}/ratings/` | `page` + `next`, 10 per page (fixed), newest first |

- `{rrid}` is the 7-character RapidRatings company ID, e.g. `KawjbGJ` in `GET /v2.0/companies/KawjbGJ/`. Use the RRID for every company-specific request.
- The company list already includes `latestRatings` (newest first, up to 5). Company details adds `reports` and `sectorAverages`.
- List response shape: `{"count": n, "previous": url|null, "next": url|null, "results": [ … ]}`.

## 4.2 API behaviour

| Topic | Behaviour |
|---|---|
| Trailing slash | Required on every path; without it the API returns 404 |
| Query parameters | Companies: `page`, `portfolio`, `name` (min. 3 chars), `ticker`, `externalSourceId`. Others: `page` only. Any other parameter returns 400. |
| Page past the end | Companies: 404. Portfolios, periods, ratings: 500 |
| Changed-since filter | None |
| Missing `Authorization` header | 401 |
| Invalid or expired token | 403 |
| Error body | `{"Request": {"message": "...", "info": {}}}` |
| Rate limit | Headers `X-Ratelimit-Remaining`, `Retry-After` |
| Numbers | Whole numbers may arrive as decimals (`1575.0`) |

## 4.3 Token request

| Item | Value |
|---|---|
| Request | `POST /v2.0/oauth/token/` |
| Header | `Authorization: Basic base64(client_id:client_secret)` |
| Body | `grant_type=client_credentials` (`application/x-www-form-urlencoded`) |
| Response | `access_token`, `expires_in` (seconds), `token_type` (`Bearer`), `scope` |
| Lifetime | 30 minutes |
| Refresh token | None; repeat the same request to renew |
| Use | `Authorization: Bearer <access_token>` on every call |
| Errors | 400 = unsupported grant type; 401 = wrong credentials, missing header **or** missing `grant_type`; 405 = GET used |

## 4.4 Field mappings

**Source data notes**

- Any field can be `null`; store it as `null`.
- Period amounts can be negative and are in the company's `currency` at its `dataScale` (e.g. millions).
- `probabilityDefault` and `termPd` values are **percentages** (`1.5` = 1.5 %).
- `fhr`, `chs` and the sub-scores are 0–100. `leverage`, `liquidity` and `earningsPerformance` are `Strong`, `Average` or `Weak`.
- Period and rating IDs are year + period (e.g. `2020YE`, `2019Q3`); a rating ID equals its period ID.

### Type conversion

| Source | Z2 type | Rule |
|---|---|---|
| string | `STRING` | As received |
| URI | `STRING` (link) | As received |
| integer | `INTEGER` | |
| number | `DOUBLE` | Keep sign and precision |
| date | `DATE` | No time-zone shift |
| null | Field's own type | Store `null`, never `0` or `""` |
| `termPd` object | 9 × `DOUBLE` | `termPd.yearN` → `term_pd_year_N` |
| Array of objects | `PROPS_ARRAY` | One row per element |
| `externalSourceIds` | `STRING_ARRAY` | |

### Company fields (`rapidratings.*`)

| Source | Target | Label | Type | Note |
|---|---|---|---|---|
| `id` | `rrid` | RRID | STRING | Searchable, filterable; identity key |
| `name` | `source_company_name` | Source Company Name | STRING | Used to suggest link candidates |
| `country` | `source_country` | Source Country | STRING | Also used for Country (Step 4) |
| `ticker` | `ticker` | Ticker | STRING | Filterable |
| `exchange` | `exchange` | Exchange | STRING | |
| `sector` | `sector` | Sector | STRING | Filterable |
| `address` | `address` | Source Address | TEXT | |
| `href` | `company_url` | Company URL | STRING (link) | |
| `financials` | `financials_url` | Financials URL | STRING (link) | |
| `periods` | `periods_url` | Periods URL | STRING (link) | |
| `ratings` | `ratings_url` | Ratings URL | STRING (link) | |
| `currency` | `currency` | Currency | STRING | |
| `dataScale` | `data_scale` | Data Scale | STRING | |
| `dataConfidential` | `data_confidentiality` | Data Confidentiality | STRING | |
| `industryClassificationSystem` | `industry_classification_system` | Industry Classification System | STRING | |
| `industryClassificationCode` | `industry_classification_code` | Industry Classification Code | STRING | |
| `externalSourceId` | `external_source_id` | External Source ID | STRING | Also used for linking |
| `externalSourceIds` | `external_source_ids` | External Source IDs | STRING_ARRAY | |
| `reportLinks.fhr` | `report_link_fhr` | FHR Report | STRING (link) | |
| `reportLinks.financialDialog` | `report_link_financial_dialog` | Financial Dialogue Report | STRING (link) | |
| `reportLinks.peerBenchmark` | `report_link_peer_benchmark` | Peer Benchmark Report | STRING (link) | |
| `sectorAverages.fhr` | `sector_average_fhr` | Sector Average FHR | DOUBLE | Company details only |
| `sectorAverages.chs` | `sector_average_chs` | Sector Average CHS | DOUBLE | Company details only |
| `latestRatings[0].id` | `current_rating_id` | Current Rating Period | STRING | Filterable |
| `latestRatings[0].fhr` | `current_fhr` | Current FHR | INTEGER | Filterable |
| `latestRatings[0].chs` | `current_chs` | Current CHS | INTEGER | Filterable |
| `latestRatings[0].probabilityDefault` | `current_probability_default` | Current PD (%) | DOUBLE | |
| `latestRatings[0].financialDate` | `current_financial_date` | Current Financial Date | DATE | |
| (sync time) | `last_synced_at` | Last Synced | TIMESTAMP | |
| `reports[]` | – | – | – | Not mapped: links need API auth; report links are used instead |

### Period fields (`rapidratings.periods`)

| Source | Target | Label | Type |
|---|---|---|---|
| `id` | `period_id` | Period ID | STRING, required (row key) |
| `eqyYear` | `eqy_year` | EQY Year | INTEGER |
| `period` | `period` | Period | STRING |
| `financialDate` | `financial_date` | Financial Date | DATE |
| `currency` | `currency` | Currency | STRING |
| `href` | `period_url` | Period URL | STRING (link) |
| `salesRevenue` | `sales_revenue` | Sales Revenue | DOUBLE |
| `earningsBeforeInterestAndTax` | `earnings_before_interest_and_tax` | EBIT | DOUBLE |
| `interestExpense` | `interest_expense` | Interest Expense | DOUBLE |
| `interestReceived` | `interest_received` | Interest Received | DOUBLE |
| `companyTaxExpense` | `company_tax_expense` | Company Tax Expense | DOUBLE |
| `abnormalItems` | `abnormal_items` | Abnormal Items | DOUBLE |
| `netProfitAfterTax` | `net_profit_after_tax` | Net Profit After Tax | DOUBLE |
| `bankCashBalances` | `bank_cash_balances` | Bank Cash Balances | DOUBLE |
| `totalCurrentAssets` | `total_current_assets` | Total Current Assets | DOUBLE |
| `fixedAssets` | `fixed_assets` | Fixed Assets | DOUBLE |
| `financialAssets` | `financial_assets` | Financial Assets | DOUBLE |
| `totalAssets` | `total_assets` | Total Assets | DOUBLE |
| `debitOwnedWithinOneYear` | `debit_owned_within_one_year` | Debt Due Within One Year | DOUBLE |
| `totalCurrentLiabilities` | `total_current_liabilities` | Total Current Liabilities | DOUBLE |
| `termLoans` | `term_loans` | Term Loans | DOUBLE |
| `totalLiabilities` | `total_liabilities` | Total Liabilities | DOUBLE |
| `totalShareholderEquity` | `total_shareholder_equity` | Total Shareholder Equity | DOUBLE |
| `cashFromOperationsFromCashFlow` | `cash_from_operations_from_cash_flow` | Cash From Operations | DOUBLE |
| `netInvestingCashFlow` | `net_investing_cash_flow` | Net Investing Cash Flow | DOUBLE |
| `netFundingCashFlow` | `net_funding_cash_flow` | Net Funding Cash Flow | DOUBLE |

### Rating fields (`rapidratings.ratings` and `rapidratings.latest_ratings`)

Both lists use the same fields.

| Source | Target | Label | Type |
|---|---|---|---|
| `id` | `rating_id` | Rating Period ID | STRING, required (row key) |
| `eqyYear` | `eqy_year` | EQY Year | INTEGER |
| `period` | `period` | Period | STRING |
| `financialDate` | `financial_date` | Financial Date | DATE |
| `reportingPeriod` | `reporting_period` | Reporting Period | DATE |
| `href` | `rating_url` | Rating URL | STRING (link) |
| `fhr` | `fhr` | FHR | INTEGER |
| `chs` | `chs` | CHS | INTEGER |
| `probabilityDefault` | `probability_default` | Probability of Default (%) | DOUBLE |
| `termPd.year2` … `termPd.year10` | `term_pd_year_2` … `term_pd_year_10` | Term PD Year 2 … 10 (%) | DOUBLE × 9 |
| `delta` | `delta` | FHR Delta | INTEGER |
| `simulatedFhr` | `simulated_fhr` | Simulated FHR | INTEGER |
| `simulatedFhrDelta` | `simulated_fhr_delta` | Simulated FHR Delta | INTEGER |
| `operatingProfitability` | `operating_profitability` | Operating Profitability | INTEGER |
| `netProfitability` | `net_profitability` | Net Profitability | INTEGER |
| `capitalStructureEfficiency` | `capital_structure_efficiency` | Capital Structure Efficiency | INTEGER |
| `costStructureEfficiency` | `cost_structure_efficiency` | Cost Structure Efficiency | INTEGER |
| `leverage` | `leverage` | Leverage | STRING |
| `liquidity` | `liquidity` | Liquidity | STRING |
| `earningsPerformance` | `earnings_performance` | Earnings Performance | STRING |
