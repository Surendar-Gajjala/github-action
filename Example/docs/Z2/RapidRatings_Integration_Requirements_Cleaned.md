# RapidRatings Integration – Requirements and Implementation Brief

**Program:** Z2 Enterprise – Data Integrations
**Integration:** RapidRatings
**Target DataModel:** Existing Z2 Company DataModel
**Source environment:** RapidRatings sandbox (API behaviour verified 2026-10-28 and 2026-10-30)
**Document status:** Implementation-ready requirements. Items marked ❓ must be confirmed with the Z2 platform team or RapidRatings before the phase that needs them (Section 17).

---

## 0. How to Use This Document

This document is self-contained. A developer, or an AI coding assistant given this document as its instructions, should be able to design, build and test the integration from it alone.

**Instructions for the implementer**

1. Build the integration in the **Z2 backend**. The browser never calls RapidRatings.
2. Implement every **MUST** requirement. Implement **SHOULD** requirements unless there is a documented reason not to.
3. Follow the implementation order in Section 18. Each phase ends with its acceptance criteria passing.
4. Use the API facts in Section 3 exactly as written; they were observed against the real sandbox. Do not invent endpoints, query parameters, response fields or DataModel configuration keys.
5. Where something is marked ❓, build behind a clear interface and leave a visible TODO; do not guess.
6. Keep provider data exactly as received: never turn `null` into `0`, never change signs, never rescale or convert currency.
7. Requirement IDs (e.g. `AUTH-03`) must appear in code comments, test names or commit messages where the requirement is implemented, so every requirement can be traced to code and a test.

**Conventions**

| Mark | Meaning |
|---|---|
| **MUST** / **SHOULD** / **MAY** | Requirement priority |
| ✅ | Verified against the RapidRatings sandbox or the existing Z2 Company DataModel |
| 💡 | Proposed design decision |
| ❓ | To confirm before the phase that needs it |

---

## 1. Objective and Scope

Z2 Enterprise provides a **Data Integrations** area where each customer (tenant) enables the external integrations it needs. RapidRatings, a provider of financial health ratings, is one of them.

When a tenant enables RapidRatings, Z2 authenticates with RapidRatings, retrieves company profiles with their financial periods and ratings, maps them into a new **RapidRatings** group on the existing Z2 Company record, shows them on the Company Details page, and keeps them current through manual and scheduled syncs, with full run and change history.

The integration provides the six common integration capabilities:

| # | Capability | Section |
|---|---|---|
| 1 | Integration authentication and connection testing | 4 |
| 2 | Source schema discovery and management | 6, 10 |
| 3 | Mapping the source schema to the Z2 Company DataModel | 7 |
| 4 | Separate synchronization of schema and data | 10, 11 |
| 5 | Manual and scheduled synchronization | 12 |
| 6 | Sync schedule, sync history and configuration change history | 13 |

| In scope (release 1) | Out of scope |
|---|---|
| Tenant enablement, credentials, connection test | Outreach requests (Section 15) |
| Portfolios (for scoping), companies, company details, periods, ratings | Writing anything back to RapidRatings |
| Versioned source schema with change detection | Creating Z2 companies automatically from RapidRatings data |
| Mapping into the existing Company DataModel, including **Company Name** (`company_name`) and **Country** (`basic_info.country`), filled only when empty (Section 7.4.1) | Overwriting any existing Company Name, Country or other existing Company value |
| Linking RapidRatings companies to Z2 companies, with admin review | Custom scores or risk bands derived from RapidRatings values |
| Company Details display | Downloading RapidRatings PDF reports into Z2 |
| Manual and scheduled syncs; run, schedule and change history | Alerts or workflows triggered by rating changes |

---

## 2. Architecture and High-Level Flow

### 2.1 Components

```text
┌───────────────────────────────────────────────────────────────┐
│ 1. Z2 UI (browser)                                            │
│    Data Integrations screens · Company Details page           │
└───────────────────────────────┬───────────────────────────────┘
                                │ Z2 platform APIs only
┌───────────────────────────────▼───────────────────────────────┐
│ 2. Z2 Data Integrations framework (backend, all providers)    │
│    Tenant config · Secret references · Schema registry        │
│    Mapping · Company linking · Sync orchestrator + queue      │
│    Scheduler · Sync history · Change history                  │
└───────────────────────────────┬───────────────────────────────┘
                                │ adapter interface
┌───────────────────────────────▼───────────────────────────────┐
│ 3. RapidRatings adapter (backend, RapidRatings only)          │
│    Token handling · API client · Paging · Response validation │
└───────────────────────────────┬───────────────────────────────┘
                                │ HTTPS + Bearer token
┌───────────────────────────────▼───────────────────────────────┐
│ 4. RapidRatings API (external)                                │
│    oauth/token · portfolios · companies · periods · ratings   │
└───────────────────────────────────────────────────────────────┘

   Results are written by (2) into:
┌───────────────────────────────────────────────────────────────┐
│ 5. Z2 Company record (Z2 data layer)                          │
│    company_name · basic_info.country · rapidratings group     │
│    (profile · latest_ratings · periods · ratings)             │
└───────────────────────────────────────────────────────────────┘
```

### 2.2 Responsibilities

| # | Component | Does | Never does |
|---|---|---|---|
| 1 | **Z2 UI** | Shows screens; calls Z2 platform APIs | Call RapidRatings; see the secret or token |
| 2 | **Data Integrations framework** | Stores configuration and secret references; runs syncs; links, maps and writes Company records; records history | Contain RapidRatings-specific URLs, paging or error rules |
| 3 | **RapidRatings adapter** | Gets and renews the token; calls the endpoints; follows `next`; validates and returns provider records | Write to Z2 data; decide mapping or linking |
| 4 | **RapidRatings API** | Returns company, period and rating data | – (external, read-only for Z2) |
| 5 | **Z2 Company record** | Holds the mapped data shown on Company Details | – |

### 2.3 End-to-end flow

**A. One-time setup (per tenant)**

| Step | Who | Action | Result |
|---|---|---|---|
| A1 | Admin | Enables RapidRatings in Data Integrations | Integration created for the tenant |
| A2 | Admin | Enters Client ID and Client Secret | Secret stored in the secret manager; status `CONFIGURED` |
| A3 | Admin | Clicks **Test connection** | Backend gets a token and reads portfolios; status `ACTIVE` |
| A4 | Admin | Chooses scope: all companies, portfolios or selected companies | Scope saved |
| A5 | Admin | Reviews and activates the default mapping | Mapping version active |
| A6 | Admin | Optionally sets a schedule (e.g. daily) | Schedule saved |

**B. Every sync (manual or scheduled; same pipeline)**

| Step | Component | Action | Result |
|---|---|---|---|
| B1 | Framework | Creates a sync run | Run `RUNNING` |
| B2 | Adapter | Gets or reuses the access token | Bearer token |
| B3 | Adapter | Lists the companies in scope | List of RRIDs |
| B4 | Adapter | For each RRID: reads company details, all periods, all ratings | Provider records |
| B5 | Framework | Links the RRID to a Z2 Company | Linked, or listed for review |
| B6 | Framework | Validates and maps the records | Mapped Company data |
| B7 | Framework | Updates the Company record | `rapidratings` group updated; Country filled only if empty; Company Name never overwritten |
| B8 | Framework | Finishes the run and saves counts and errors | Entry in sync history |

**C. Viewing:** a user opens a Company; Company Details shows the RapidRatings profile and the Latest Ratings, Periods and Ratings tables.

### 2.4 Sync sequence

```text
Z2 UI        Framework               Adapter                RapidRatings API     Company record
  │ Sync now      │                      │                          │                   │
  │──────────────▶│ create run           │                          │                   │
  │               │── sync(scope) ──────▶│ POST /oauth/token/ ─────▶│                   │
  │               │                      │◀───────── token ─────────│                   │
  │               │                      │ GET /companies/ ────────▶│                   │
  │               │                      │◀──────── companies ──────│                   │
  │               │                      │ per RRID:                │                   │
  │               │                      │ GET /companies/{rrid}/ ─▶│                   │
  │               │                      │ GET …/periods/ (all) ───▶│                   │
  │               │                      │ GET …/ratings/ (all) ───▶│                   │
  │               │◀── provider records ─│◀──────── data ───────────│                   │
  │               │ link · validate · map│                          │                   │
  │               │── update ──────────────────────────────────────────────────────────▶│
  │◀── progress ──│ finish run, write history                       │                   │
```

### 2.5 Requirements

| ID | Priority | Requirement |
|---|---|---|
| ARCH-01 | MUST | All RapidRatings calls, including the token request, are made by the Z2 backend. The browser only calls Z2 platform APIs. ✅ The RapidRatings data host does not send CORS headers, so browser calls fail anyway. |
| ARCH-02 | MUST | Common capabilities (configuration, secrets, schema registry, mapping, linking, orchestration, scheduling, history) live in a provider-neutral framework; RapidRatings-specific logic (token request, endpoints, paging, error meanings, parsing) lives in a RapidRatings adapter. |
| ARCH-03 | MUST | Manual and scheduled syncs execute the same pipeline (steps B1–B8). |
| ARCH-04 | MUST | Only the framework writes to Z2 data; the adapter only reads from RapidRatings and returns provider records. |

---

## 3. RapidRatings API Contract ✅

### 3.1 Endpoints

Base URL (sandbox): `https://rridata.sandbox.rrilabs.co/v2.0` ❓ production base URL.

| Resource | Method | Path | Paging | Purpose |
|---|---|---|---|---|
| Token | POST | `/oauth/token/` | – | Obtain an access token |
| Portfolios | GET | `/portfolios/`, `/portfolios/{id}/` | `page` + `next` | Portfolios visible to the account |
| Companies | GET | `/companies/` | `page` + `next`, **20 per page (fixed)** | Company list; each item includes `latestRatings` |
| Company details | GET | `/companies/{rrid}/` | – | One company: all list fields **plus** `reports` and `sectorAverages` |
| Periods | GET | `/companies/{rrid}/periods/`, `/periods/{periodId}/` | `page` + `next`, **10 per page (fixed)**, newest first | Financial statements per period |
| Ratings | GET | `/companies/{rrid}/ratings/`, `/ratings/{ratingId}/` | `page` + `next`, **10 per page (fixed)**, newest first | One rating per period, full history |

List response envelope: `{"count": 27, "previous": null, "next": "https://…/periods/?page=2", "results": [ … ]}`.

### 3.2 Behaviour rules

| Behaviour | Value |
|---|---|
| Trailing slash | **Required** on every path. `/companies/{rrid}` without the slash returns 404. |
| Accepted query parameters | Companies: `page`, `portfolio` (integer), `name` (≥ 3 characters, case-insensitive contains), `ticker` (exact, case-sensitive), `externalSourceId`. Portfolios, periods, ratings: `page` only. |
| Unknown query parameter | `400 Not valid query parameter: <name>` |
| Page past the end | Companies: 404. Portfolios, periods, ratings: **500**. |
| Changed-since / incremental filter | **None.** `modified_since`, `updated_since`, `ordering`, `page_size`, `limit` all return 400. |
| Missing `Authorization` header | 401 |
| Invalid or expired token | **403** `Not Authorized.` |
| Error body | `{"Request": {"message": "...", "info": {}}}` (scope key may also be `Portfolio` or `Companies`) |
| Rate-limit headers | `X-Ratelimit-Limit`, `X-Ratelimit-Remaining`, `X-Ratelimit-Reset`, `Retry-After` |
| Numbers | Integer-valued decimals arrive as `1575.0` |

| ID | Priority | Requirement |
|---|---|---|
| API-01 | MUST | Paginate only by following the `next` URL until it is `null`. Never build page numbers. |
| API-02 | MUST | Always send the trailing slash. |
| API-03 | MUST | Send only the accepted query parameters listed above. |
| API-04 | MUST | Treat 404/500 for an out-of-range page as a defect, not a transient error: never retry it. |
| API-05 | MUST | Use a 30-second connect/read timeout on every call. |
| API-06 | SHOULD | Read `X-Ratelimit-Remaining` and, on 429, wait `Retry-After` seconds (capped) before retrying. |

---

## 4. Integration Authentication

### 4.1 Token contract ✅

| Item | Value |
|---|---|
| Request | `POST https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` (GET returns 405) |
| Client authentication | `Authorization: Basic base64(client_id:client_secret)` |
| Body | `grant_type=client_credentials` with `Content-Type: application/x-www-form-urlencoded` (JSON also accepted) |
| Response fields | `access_token` (string), `expires_in` (seconds), `token_type` (`Bearer`), `scope` (space-separated string) |
| Lifetime | 1800 seconds (30 minutes) |
| Refresh token | **Not issued.** Renewal means repeating the same request. |
| Use | `Authorization: Bearer <access_token>` on every data call |

```http
POST /v2.0/oauth/token/ HTTP/1.1
Host: rridata.sandbox.rrilabs.co
Authorization: Basic <base64(client_id:client_secret)>
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
```

Token endpoint errors ✅: **400** unsupported `grant_type`; **401** wrong client ID or secret, missing `Authorization` header, **or missing `grant_type`** (so a 401 does not always mean bad credentials); **405** for GET.

### 4.2 Step by step

1. The administrator opens **Data Integrations → RapidRatings → Connect** and enters Client ID, Client Secret and environment (sandbox or production).
2. The backend stores the secret in the Z2 secret manager and keeps only a **credential reference** on the tenant's integration record (status `CONFIGURED`). The secret is never returned to the UI.
3. The administrator clicks **Test connection**.
4. The backend resolves the secret for that tenant and environment.
5. It sends the token request (4.1).
6. It reads `access_token` and `expires_in` and caches the token **in server memory only**, keyed by tenant and environment, with `expiresAt = now + expires_in`.
7. It calls `GET /v2.0/portfolios/` with the Bearer token. A 200 proves both credentials and data access.
8. The UI shows **Connected**, the check time and the granted scope. Status becomes `ACTIVE`.

### 4.3 Requirements

| ID | Priority | Requirement |
|---|---|---|
| AUTH-01 | MUST | Store the Client Secret only in the Z2 secret manager; persist only a reference. |
| AUTH-02 | MUST | Never expose the secret, the Basic header, the access token or the token response to the browser, logs, traces, sync history, change history or API responses. |
| AUTH-03 | MUST | Cache the token per tenant and environment in server memory and reuse it for every call. |
| AUTH-04 | MUST | Request a new token when fewer than 60 seconds of validity remain. |
| AUTH-05 | MUST | On 403 from the data API, discard the cached token, obtain a new one and retry the request **exactly once**. A second 403 is a permission failure: stop and report it. |
| AUTH-06 | MUST | Never retry the token request after a 400 or 401 from the token endpoint; set the connection to `AUTH_FAILED`. |
| AUTH-07 | SHOULD | Concurrent workers for the same tenant share a single in-flight token request. |
| AUTH-08 | MUST | Provide **Test connection** as in 4.2; it must not start a data sync. |
| AUTH-09 | MUST | Credential rotation replaces the secret reference, clears the cached token, re-runs Test connection and writes a change-history entry without any secret value. |
| AUTH-10 | MUST | Keep sandbox and production credentials separate. |

Connection states: `NOT_CONFIGURED → CONFIGURED → ACTIVE`, plus `AUTH_FAILED`, `PERMISSION_FAILED` and `DISABLED`. Syncs run only in `ACTIVE`.

Token handling (pseudocode):

```text
getToken(tenant, env, force = false):
    entry = cache[tenant, env]
    if not force and entry exists and now < entry.expiresAt - 60s:
        return entry.token
    lock(tenant, env):                                   # one request at a time per tenant
        secret = secretManager.resolve(integration.credentialRef)
        resp = POST tokenUrl, Basic(secret.clientId, secret.clientSecret), "grant_type=client_credentials"
        if resp.status != 200: raise AuthError(sanitized category)   # never echo the body
        cache[tenant, env] = { token: resp.access_token, expiresAt: now + resp.expires_in }
        return cache[tenant, env].token

callApi(request):
    resp = send(request, Bearer getToken(tenant, env))
    if resp.status == 403:
        resp = send(request, Bearer getToken(tenant, env, force = true))   # exactly one retry
    return resp
```

---

## 5. Request Sequence: Token → Company Details → Z2 Record

The calls one company sync makes, in order. `{rrid}` is the company's RapidRatings ID; `{portfolioId}` is a configured portfolio.

| Step | Call | Take from the response | Next |
|---|---|---|---|
| 1 | `POST /v2.0/oauth/token/` | `access_token`, `expires_in` | Cache the token (Section 4) |
| 2 | `GET /v2.0/portfolios/` *(optional)* | Portfolio `id` values | Only when the scope uses portfolios |
| 3 | `GET /v2.0/companies/?portfolio={portfolioId}` (or without the filter), following `next` | Each company's `id` (the RRID) | One company sync per RRID |
| 4 | `GET /v2.0/companies/{rrid}/` | All company fields (Section 6.1), including `latestRatings`, `reportLinks`, `sectorAverages` | Profile and current-rating mapping (7.4) |
| 5 | `GET /v2.0/companies/{rrid}/periods/`, following `next` until `null` | Every period object (Section 6.2) | One `periods[]` element per period `id` (7.5) |
| 6 | `GET /v2.0/companies/{rrid}/ratings/`, following `next` until `null` | Every rating object (Section 6.3) | One `ratings[]` element per rating `id` (7.6) |
| 7 | – | – | Link the RRID to a Z2 Company (Section 9) |
| 8 | – | – | Validate, map and update the Company record (Sections 7 and 11) |

Shape of the updated Company record (field names only; values come from the mapping tables in Section 7):

```json
{
  "company_name": "<string, set only when empty or on create – 7.4.1>",
  "basic_info": { "country": "<Z2 country LOV value, set only when empty – 7.4.1>" },
  "rapidratings": {
    "rrid": "<string>", "source_company_name": "<string>", "source_country": "<string>",
    "ticker": "<string>", "exchange": "<string>", "sector": "<string>",
    "currency": "<string>", "data_scale": "<string>", "data_confidentiality": "<string>",
    "sector_average_fhr": "<double>", "sector_average_chs": "<double>",
    "current_rating_id": "<string>", "current_fhr": "<integer>", "current_chs": "<integer>",
    "current_probability_default": "<double>", "current_financial_date": "<date>",
    "last_synced_at": "<timestamp>",
    "latest_ratings": [ { "rating_id": "<string>", "...": "children in 7.6" } ],
    "periods": [ { "period_id": "<string>", "...": "children in 7.5" } ],
    "ratings": [ { "rating_id": "<string>", "...": "children in 7.6" } ]
  }
}
```

`company_name` and `basic_info.country` are existing Company fields. RapidRatings `name` and `country` are written to them **only when they are empty** (or when an administrator creates a new Company from RapidRatings); an existing value is never overwritten (Section 7.4.1). The provider values are always kept in `source_company_name` and `source_country`.

9. **Display** on Company Details (Section 8).

---

## 6. RapidRatings Source Schema

Types are as observed in the sandbox. A field seen only as `null` keeps the type documented here; it is never typed as "null".

### 6.1 Company (list and detail)

| Source property | Type | Notes |
|---|---|---|
| `id` | string | **RRID**, a 7-character alphanumeric string. Key for all company calls. |
| `name` | string | Provider company name |
| `country` | string | ISO-3 code (e.g. `USA`) |
| `ticker` | string | Exact, case-sensitive |
| `exchange` | string | e.g. `US` |
| `sector` | string | e.g. `Retail` |
| `href`, `financials`, `periods`, `ratings` | URI | Absolute resource URLs |
| `latestRatings` | array of rating objects | **Newest first, up to 5** (2–5 observed). `latestRatings[0]` is the current rating. |
| `reportLinks.fhr`, `.financialDialog`, `.peerBenchmark` | URI | Portal links for users |
| `address` | string | May be `""` |
| `industryClassificationSystem`, `industryClassificationCode` | string \| null | Null in all sandbox samples |
| `currency` | string | e.g. `USD` |
| `dataScale` | string | e.g. `millions`; applies to every period amount |
| `dataConfidential` | string | `PUB` for public companies ❓ values for private companies |
| `externalSourceId` | string \| null | The customer's own ID for this company, if set on the RapidRatings side |
| `externalSourceIds` | array | Always `[]` in the sandbox ❓ element type |
| `reports[]` | array | **Detail endpoint only.** `{reportType, downloadHref}`; downloading requires API authentication |
| `sectorAverages` | object | **Detail endpoint only.** `{fhr: number, chs: number}` |

### 6.2 Periods

| Source property | Type | Notes |
|---|---|---|
| `id` | string | `eqyYear` + `period`, e.g. `2020YE`, `2019Q3` |
| `href` | URI | |
| `eqyYear` | integer | |
| `period` | string | `Q1`, `Q2`, `Q3`, `YE` |
| `financialDate` | date | `YYYY-MM-DD` |
| `currency` | string | |
| `salesRevenue`, `earningsBeforeInterestAndTax`, `interestExpense`, `interestReceived`, `companyTaxExpense`, `abnormalItems`, `netProfitAfterTax`, `bankCashBalances`, `totalCurrentAssets`, `fixedAssets`, `financialAssets`, `totalAssets`, `debitOwnedWithinOneYear`, `totalCurrentLiabilities`, `termLoans`, `totalLiabilities`, `totalShareholderEquity`, `cashFromOperationsFromCashFlow`, `netInvestingCashFlow`, `netFundingCashFlow` | number \| null | **Every amount can be null and can be negative.** `cashFromOperationsFromCashFlow` was null in every sample. Amounts are in `currency` at the company's `dataScale`. `debitOwnedWithinOneYear` (short-term debt) is the provider's own spelling. |

### 6.3 Ratings (also each `latestRatings` item)

| Source property | Type | Meaning |
|---|---|---|
| `id` | string | Equals the matching period `id` |
| `href` | URI | |
| `eqyYear` | integer | |
| `period` | string | `Q1`, `Q2`, `Q3`, `YE` |
| `financialDate` | date | Statement date the rating is based on |
| `reportingPeriod` | date | Date the rating was produced |
| `fhr` | integer | Financial Health Rating, 0–100, 12-month default risk; higher is healthier |
| `chs` | integer | Core Health Score, 0–100, 2–3 year resilience |
| `probabilityDefault` | number | 1-year probability of default **in percent** (a value of `1.5` means 1.5 %, not 150 %) |
| `termPd.year2` … `termPd.year10` | number | Cumulative probability of default in percent over N years |
| `delta` | integer \| null | FHR change vs. the previous rating; null for the oldest |
| `simulatedFhr` | integer | |
| `simulatedFhrDelta` | integer \| null | |
| `operatingProfitability`, `netProfitability`, `capitalStructureEfficiency`, `costStructureEfficiency` | integer | Sub-scores 0–100 |
| `leverage`, `liquidity`, `earningsPerformance` | string | Observed values: `Strong`, `Average`, `Weak` |

❓ Official metric definitions must be confirmed before building any display rule on them.

---

## 7. Mapping to the Existing Z2 Company DataModel

### 7.1 Facts about the existing Company DataModel ✅

| Fact | Consequence |
|---|---|
| Property groups use `dataType: NESTED`; repeated records use `PROPS_ARRAY`. | RapidRatings is a NESTED group with three PROPS_ARRAY lists. |
| Another external provider, **Kharon Risk Data** (`kharon`), already uses this pattern: summary values plus a `risks` PROPS_ARRAY, shown as Company Details sections. | Follow the Kharon pattern exactly. |
| A PROPS_ARRAY can contain another PROPS_ARRAY (`kharon.risks.topics`); no NESTED group inside a PROPS_ARRAY exists. | Flatten `termPd` into separate columns. |
| A PROPS_ARRAY child can be `required` (e.g. the sanctions list key). | Make `period_id` and `rating_id` required. |
| There is no URI type; URLs are `STRING`. Links display through `uiHints: {"displayType": "LINK"}`. Dates display through `uiHints: {"displayType": "DATE"}`. | Map URIs to STRING with a link hint. |
| Data types available: `STRING, TEXT, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, TIMESTAMP, ENUM, LOV, LOV_ARRAY, STRING_ARRAY, USER_ARRAY, EMAIL, PHONE, JSON, NESTED, PROPS_ARRAY`. | Use only these. |
| Each property has `name, dbName, path, label, description, isPrimaryKey, required, searchable, filterable, dataType, dataModelType, extDataModelId, canUpload, hidden, properties, isCalculated, isAi, config, uiHints`. `id`, `dataEnvId` and timestamps are assigned by the platform. | Never invent IDs. |
| PROPS_ARRAY children are not searchable or filterable. | Add single-value "current" fields for filtering. |
| Company Details is layout-driven: tabs → sections → `fields` blocks with bindings to property `dbName`s. | Adding properties alone does not display them (Section 8). |

### 7.2 Target structure 💡

```text
Z2 Company (existing; no existing property definition is changed)
├── company_name           ← RapidRatings name     (only when empty / on create, 7.4.1)
├── Basic Information
│   └── country            ← RapidRatings country  (only when empty, 7.4.1)
├── Financial, Kharon Risk Data, … (existing, untouched)
└── RapidRatings  rapidratings  (NEW NESTED)
    ├── Profile:         rrid, source_company_name, source_country, ticker, exchange, sector, address
    ├── URLs:            company_url, financials_url, periods_url, ratings_url
    ├── Data context:    currency, data_scale, data_confidentiality,
    │                    industry_classification_system, industry_classification_code,
    │                    external_source_id, external_source_ids
    ├── Report links:    report_link_fhr, report_link_financial_dialog, report_link_peer_benchmark
    ├── Benchmark:       sector_average_fhr, sector_average_chs
    ├── Current rating:  current_rating_id, current_fhr, current_chs,
    │                    current_probability_default, current_financial_date
    ├── last_synced_at
    ├── latest_ratings   (PROPS_ARRAY)  ← company.latestRatings, replaced on every sync
    ├── periods          (PROPS_ARRAY)  ← periods endpoint, full history
    └── ratings          (PROPS_ARRAY)  ← ratings endpoint, full history
```

### 7.3 Type conversion

| Source type | Z2 dataType | Rule |
|---|---|---|
| string | `STRING` | As received; `""` stays `""` |
| URI | `STRING` + link `uiHints` | As received |
| integer | `INTEGER` | Reject non-integers |
| number | `DOUBLE` | Parse `1575.0` as a number; keep sign and precision |
| date | `DATE` + date `uiHints` | No time-zone shift |
| null | (the field's own type) | Store `null`; never `0` or `""` |
| `termPd` object | 9 × `DOUBLE` | `termPd.yearN` → `term_pd_year_N` |
| array of objects | `PROPS_ARRAY` | One element per item, keyed (Section 11) |
| `externalSourceIds` | `STRING_ARRAY` | ❓ element type |
| `leverage`, `liquidity`, `earningsPerformance` | `STRING` | May become `ENUM` once allowed values are confirmed ❓ |

### 7.4 Company profile mapping

| Source | Target `rapidratings.` | Label | Z2 type | Rule |
|---|---|---|---|---|
| `id` | `rrid` | RRID | STRING | Searchable, filterable; identity key |
| `name` | `source_company_name` | Source Company Name | STRING | Provider value as received; also mapped to `company_name` (7.4.1) |
| `country` | `source_country` | Source Country | STRING | Provider value as received; also mapped to `basic_info.country` (7.4.1) |
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
| `dataConfidential` | `data_confidentiality` | Data Confidentiality | STRING | Not converted to boolean |
| `industryClassificationSystem` | `industry_classification_system` | Industry Classification System | STRING | Nullable |
| `industryClassificationCode` | `industry_classification_code` | Industry Classification Code | STRING | Nullable |
| `externalSourceId` | `external_source_id` | External Source ID | STRING | Nullable; also a linking input |
| `externalSourceIds` | `external_source_ids` | External Source IDs | STRING_ARRAY | ❓ |
| `reportLinks.fhr` | `report_link_fhr` | FHR Report | STRING (link) | |
| `reportLinks.financialDialog` | `report_link_financial_dialog` | Financial Dialogue Report | STRING (link) | |
| `reportLinks.peerBenchmark` | `report_link_peer_benchmark` | Peer Benchmark Report | STRING (link) | |
| `sectorAverages.fhr` | `sector_average_fhr` | Sector Average FHR | DOUBLE | Detail endpoint |
| `sectorAverages.chs` | `sector_average_chs` | Sector Average CHS | DOUBLE | Detail endpoint |
| `latestRatings[0].id` | `current_rating_id` | Current Rating Period | STRING | Derived; filterable |
| `latestRatings[0].fhr` | `current_fhr` | Current FHR | INTEGER | Derived; filterable |
| `latestRatings[0].chs` | `current_chs` | Current CHS | INTEGER | Derived; filterable |
| `latestRatings[0].probabilityDefault` | `current_probability_default` | Current PD (%) | DOUBLE | Derived |
| `latestRatings[0].financialDate` | `current_financial_date` | Current Financial Date | DATE | Derived |
| (sync time) | `last_synced_at` | Last Synced | TIMESTAMP | Set by the sync |
| `reports[]` | – | – | – | **Not mapped:** download links need API credentials; users use the report links instead |

### 7.4.1 Company Name and Country mapping (never overwritten)

RapidRatings `name` and `country` map to the existing Company properties below. **An existing value is never overwritten.** The provider values are always stored in the RapidRatings group as well (`source_company_name`, `source_country`).

| Source | Z2 Company property ✅ | Z2 definition ✅ | Transformation |
|---|---|---|---|
| `name` | **Company Name** | `"name": "Company Name"`, `"dbName": "company_name"`, `"path": "company_name"`, `dataType: STRING`, primary key, required | As received |
| `country` | **Country** | `"name": "Country"`, `"dbName": "country"`, `"path": "basic_info.country"`, `dataType: LOV` (in the Basic Information group) | Translate the ISO-3 code (e.g. `USA`) to the matching Z2 country LOV value through a lookup table |

When the values are written:

| Situation | Company Name (`company_name`) | Country (`basic_info.country`) |
|---|---|---|
| Sync of a **linked** Company, Z2 field **has a value** | Not changed | Not changed |
| Sync of a **linked** Company, Z2 field **is empty** | – (always filled: it is the required primary key) | Set from RapidRatings `country` |
| Administrator **creates a new Company** from an unlinked RapidRatings company (LINK-06) | Set from RapidRatings `name` | Set from RapidRatings `country` |
| RapidRatings value is empty or `null` | Nothing written | Nothing written |
| ISO-3 code has no matching LOV value | – | Nothing written; warning reported |

When a Z2 value differs from the RapidRatings value, Z2's value stays; the difference is visible on Company Details through `source_company_name` and `source_country`.

No other existing Company field has a RapidRatings equivalent; all others are left untouched. `ticker`, `exchange`, `sector` and `address` have no existing Company field, so they live only in the RapidRatings group.

### 7.5 Periods mapping (`rapidratings.periods`, PROPS_ARRAY)

| Source | Target child | Label | Z2 type |
|---|---|---|---|
| `id` | `period_id` | Period ID | STRING, **required** (element key) |
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

### 7.6 Ratings and Latest Ratings mapping (`rapidratings.ratings`, `rapidratings.latest_ratings`, PROPS_ARRAY)

Both lists use the same children.

| Source | Target child | Label | Z2 type |
|---|---|---|---|
| `id` | `rating_id` | Rating Period ID | STRING, **required** (element key) |
| `eqyYear` | `eqy_year` | EQY Year | INTEGER |
| `period` | `period` | Period | STRING |
| `financialDate` | `financial_date` | Financial Date | DATE |
| `reportingPeriod` | `reporting_period` | Reporting Period | DATE |
| `href` | `rating_url` | Rating URL | STRING (link) |
| `fhr` | `fhr` | FHR | INTEGER |
| `chs` | `chs` | CHS | INTEGER |
| `probabilityDefault` | `probability_default` | Probability of Default (%) | DOUBLE |
| `termPd.year2` … `termPd.year10` | `term_pd_year_2` … `term_pd_year_10` | Term PD Year 2 … Year 10 (%) | DOUBLE × 9 |
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

### 7.7 Property definition format

Every new property follows the existing DataModel shape. Example for the group and one array:

```json
{
  "name": "RapidRatings", "dbName": "rapidratings", "path": "rapidratings", "label": "RapidRatings",
  "description": "Financial health data about this company sourced from RapidRatings.",
  "isPrimaryKey": false, "required": false, "searchable": false, "filterable": false,
  "dataType": "NESTED", "dataModelType": "<PLATFORM_LAYER>", "extDataModelId": "<PLATFORM_ASSIGNED>",
  "canUpload": false, "hidden": false, "isCalculated": false, "isAi": false,
  "config": {}, "uiHints": {},
  "properties": [
    {
      "name": "Periods", "dbName": "periods", "path": "rapidratings.periods", "label": "Periods",
      "description": "Financial statement figures per reporting period, newest first. Amounts are in rapidratings.currency at rapidratings.data_scale.",
      "dataType": "PROPS_ARRAY", "required": false, "searchable": false, "filterable": false,
      "properties": [
        { "name": "Period ID", "dbName": "period_id", "path": "rapidratings.periods.period_id", "label": "Period ID",
          "dataType": "STRING", "required": true, "searchable": true, "filterable": true },
        { "name": "Financial Date", "dbName": "financial_date", "path": "rapidratings.periods.financial_date", "label": "Financial Date",
          "dataType": "DATE", "uiHints": { "displayType": "DATE" } }
      ]
    }
  ]
}
```

### 7.8 Requirements

| ID | Priority | Requirement |
|---|---|---|
| MAP-01 | MUST | Add the `rapidratings` group to the existing Company DataModel without changing any existing property definition, ID, path or layout. (Values of `company_name` and `basic_info.country` are updated by MAP-06; their definitions are not changed.) |
| MAP-02 | MUST | Implement every mapping in 7.4–7.6. Every source property is mapped, except `reports[]`, which is excluded for the stated reason. |
| MAP-03 | MUST | Child `path` = parent `path` + `.` + child `dbName`; names in snake_case. |
| MAP-04 | MUST | Never write `id`, `dataEnvId` or timestamps; never invent `dataModelType` / `extDataModelId` values ❓ (Q1). |
| MAP-05 | MUST | Store `null` as `null`, keep signs and precision, never rescale or convert currency. |
| MAP-06 | MUST | Map RapidRatings `name` → **Company Name** (`company_name`) and `country` → **Country** (`basic_info.country`) as defined in 7.4.1. **Never overwrite an existing value**: write only when the Z2 field is empty or when an administrator creates a new Company (LINK-06). Never clear a field with an empty value. Leave every other existing Company field untouched. |
| MAP-11 | MUST | Record every value written to `company_name` or `basic_info.country` (field, new value, run ID or user) in sync history and change history. |
| MAP-12 | MUST | Translate the ISO-3 country code to the Z2 country LOV through a lookup; an unknown code writes nothing and produces a warning. |
| MAP-13 | MUST | When creating a Company from RapidRatings, if the name already exists as a Company Name in the tenant, do not create a duplicate: offer to link the existing Company instead. |
| MAP-07 | MUST | `latest_ratings` is replaced wholesale on each sync and is never merged with `ratings`. |
| MAP-08 | MUST | Mappings are versioned and validated before activation: every source path exists in the active schema version, every target path exists in the DataModel, types are compatible (7.3), key children (`rrid`, `period_id`, `rating_id`) are mapped, all nine `term_pd_year_*` are mapped, and no two sources map to one target. |
| MAP-09 | MUST | Each sync run records the mapping version and DataModel version it used. |
| MAP-10 | SHOULD | Set `canUpload: false` on the group so only the integration writes it ❓ (Q2). |

---

## 8. Company Details Presentation

Add four sections to the Company Details **Details** tab, after the Kharon sections, following the same layout format:

```json
[
  { "id": "rapidratings", "title": "RapidRatings", "preset": "single",
    "columns": [[ { "id": "rapidratings-fields", "kind": "fields", "columns": 2, "bindings": [
      { "id": "rapidratings_rrid", "path": [], "label": "RRID", "target": { "kind": "objectProperty", "dbName": "rapidratings.rrid" } },
      { "id": "rapidratings_current_fhr", "path": [], "label": "Current FHR", "target": { "kind": "objectProperty", "dbName": "rapidratings.current_fhr" } },
      { "id": "rapidratings_current_chs", "path": [], "label": "Current CHS", "target": { "kind": "objectProperty", "dbName": "rapidratings.current_chs" } }
    ] } ]] },
  { "id": "rapidratings_latest_ratings", "title": "RapidRatings › Latest Ratings", "preset": "single",
    "columns": [[ { "id": "rapidratings_latest_ratings-table", "kind": "fields", "columns": 1, "bindings": [
      { "id": "rapidratings_latest_ratings", "path": [], "label": "RapidRatings › Latest Ratings", "target": { "kind": "objectProperty", "dbName": "rapidratings.latest_ratings" } } ] } ]] },
  { "id": "rapidratings_periods", "title": "RapidRatings › Periods", "preset": "single",
    "columns": [[ { "id": "rapidratings_periods-table", "kind": "fields", "columns": 1, "bindings": [
      { "id": "rapidratings_periods", "path": [], "label": "RapidRatings › Periods", "target": { "kind": "objectProperty", "dbName": "rapidratings.periods" } } ] } ]] },
  { "id": "rapidratings_ratings", "title": "RapidRatings › Ratings", "preset": "single",
    "columns": [[ { "id": "rapidratings_ratings-table", "kind": "fields", "columns": 1, "bindings": [
      { "id": "rapidratings_ratings", "path": [], "label": "RapidRatings › Ratings", "target": { "kind": "objectProperty", "dbName": "rapidratings.ratings" } } ] } ]] }
]
```

| ID | Priority | Requirement |
|---|---|---|
| DISP-01 | MUST | The RapidRatings section shows the profile (RRID, source name, country, ticker, exchange, sector, address), current rating (FHR, CHS, PD, period), sector averages, currency, data scale, report links and last synced time. |
| DISP-02 | MUST | Latest Ratings, Periods and Ratings each show as a table, newest period first. |
| DISP-03 | MUST | `null` displays as blank or "—", never `0`. |
| DISP-04 | SHOULD | Labels show units: "(%)" on PD and term PD; the data scale next to amount tables. |
| DISP-05 | SHOULD | If the Details tab becomes too long, the same sections may move to a dedicated **RapidRatings** tab. ❓ wide-table rendering (Q2). |

---

## 9. Company Linking (RRID → Z2 Company)

| ID | Priority | Requirement |
|---|---|---|
| LINK-01 | MUST | Link in this order: (1) a Z2 Company whose `rapidratings.rrid` already equals the RRID; (2) if the tenant configures it, `externalSourceId` equal to a chosen Z2 identifier ❓ (Q4); (3) otherwise list candidates by name, country and ticker for the administrator to confirm. |
| LINK-02 | MUST | Never link automatically on a name match alone. |
| LINK-03 | MUST | One RRID links to at most one Z2 Company per tenant. |
| LINK-04 | MUST | Unlinked companies appear in a review list; no data is written for them. |
| LINK-05 | MUST | Every manual link and unlink is written to change history. |
| LINK-06 | SHOULD | From the review list, an administrator can **create a new Company** from an unlinked RapidRatings company: Company Name = RapidRatings `name`, Country = mapped RapidRatings `country`, the RRID linked, and the RapidRatings group filled on the next sync. Never automatic (MAP-13 applies). |

---

## 10. Schema Synchronization

Schema sync and data sync are separate: schema sync never writes company data; data sync never changes the schema. RapidRatings publishes no machine-readable schema for this API ✅, so the schema is inferred from responses.

| ID | Priority | Requirement |
|---|---|---|
| SCH-01 | MUST | Run schema sync on connection activation, on demand (**Refresh schema**), and as a quick check at the start of each data sync. |
| SCH-02 | MUST | Sample: first page of companies, one company detail, first page of periods and of ratings. |
| SCH-03 | MUST | Build a snapshot of every field path (nested paths like `termPd.year2`, array items like `latestRatings[]`), observed types, nullability and "seen in N of M samples". |
| SCH-04 | MUST | Compare with the active version: added, removed, type changed, nullability changed. |
| SCH-05 | MUST | No change → record "checked". New fields → save a new version as pending review; data sync continues. A mapped field removed or changed → save as pending review and **block** data sync for that resource until the mapping is updated and approved. |
| SCH-06 | MUST | Never change the Company DataModel or an approved mapping automatically. |
| SCH-07 | MUST | Every new schema version writes a change-history entry. |

Snapshot example:

```json
{
  "schemaVersionId": "sv-0004", "provider": "RAPIDRATINGS", "resource": "periods", "origin": "INFERRED",
  "environment": "sandbox", "discoveredAt": "2026-10-30T09:00:00Z", "status": "ACTIVE",
  "fields": [
    { "path": "id", "types": ["string"], "nullable": false, "seenIn": "10/10" },
    { "path": "interestReceived", "types": ["number"], "nullable": true, "seenIn": "10/10" }
  ],
  "diffFromPrevious": { "added": [], "removed": [], "typeChanged": [] }
}
```

---

## 11. Data Synchronization

### 11.1 Pipeline

```text
Start run ─▶ token ─▶ resolve scope ─▶ list companies (follow next) ─▶ for each company:
     details ─▶ link ─▶ all periods ─▶ all ratings ─▶ validate ─▶ map ─▶ update record ─▶ record outcome
─▶ finish run: status + counts ─▶ sync history
```

### 11.2 Record identity and update rules

| Record | Key | Rule |
|---|---|---|
| Company link | `tenantId + RAPIDRATINGS + rrid` | One Z2 Company per RRID per tenant |
| Profile and `current_*` values | Company record | Overwritten with the latest values; `current_*` recomputed from `latestRatings[0]` |
| `company_name`, `basic_info.country` | Company record | Written only when empty or on create (7.4.1); never overwritten; each write recorded |
| `periods[]` element | `rrid + period_id` | Insert new, update changed, **never delete** an element missing from a response |
| `ratings[]` element | `rrid + rating_id` | Same as periods |
| `latest_ratings[]` | whole list | Replaced on every successful company read |

### 11.3 Requirements

| ID | Priority | Requirement |
|---|---|---|
| SYNC-01 | MUST | Create a run record before starting, with tenant, trigger, scope, mode, and config/schema/mapping/DataModel versions. |
| SYNC-02 | MUST | Scope is one of: all companies visible to the account, selected portfolio IDs, or selected RRIDs. |
| SYNC-03 | MUST | For each company: read details, link (Section 9), read **all** periods and ratings by following `next`, validate, map, update. |
| SYNC-04 | MUST | Validate before writing: the RRID inside each period/rating `href` equals the company RRID; IDs are present; types match the active schema. Reject a record that fails and report it. |
| SYNC-05 | MUST | Attach a rating to a period only when **both** RRID and period ID match. Never match by name, list position or response order. |
| SYNC-06 | MUST | Apply the update rules in 11.2. Re-running the same sync creates no duplicates and changes nothing if the source did not change. |
| SYNC-07 | MUST | A failure for one company does not stop the others; the run ends `PARTIALLY_SUCCEEDED`. |
| SYNC-08 | MUST | Do not store raw provider responses beyond the run; store only the mapped record ❓ retention (Q9). |
| SYNC-09 | SHOULD | Skip the write when the mapped record is unchanged (compare a hash) and count it as `UNCHANGED`. |
| SYNC-10 | SHOULD | Process companies with bounded parallelism (e.g. 4 at a time). |
| SYNC-11 | SHOULD | Store list elements newest first by `eqy_year`, then period (`YE`, `Q3`, `Q2`, `Q1`). |
| SYNC-12 | SHOULD | Report, but do not "fix", a period and rating with the same ID but different `financial_date`. |
| SYNC-13 | MUST | Update Company records through the platform's record-update API with optimistic locking, so concurrent user edits are not lost ❓ (Q3). |

### 11.4 Keeping regular syncs light

RapidRatings has no changed-since filter ✅.

| ID | Priority | Requirement |
|---|---|---|
| SYNC-14 | MUST | **Full mode** (first sync and weekly): read everything as in 11.1. |
| SYNC-15 | SHOULD | **Regular mode:** read the company list; for each company compare `latestRatings[0].id` and `fhr` with the stored `current_rating_id` and `current_fhr`. Unchanged → refresh profile values only. Changed or new → read periods and ratings from page 1 and continue only while unseen IDs appear. |

Call budget for a full sync: `⌈companies / 20⌉` list calls + per company `1 + ⌈periods / 10⌉ + ⌈ratings / 10⌉` calls.

---

## 12. Manual and Scheduled Synchronization

| ID | Priority | Requirement |
|---|---|---|
| RUN-01 | MUST | **Sync now**: an integration administrator starts a sync for all in-scope companies, one portfolio, or one company (also available on Company Details). |
| RUN-02 | MUST | Before starting, check: connection `ACTIVE`, an approved mapping exists, no blocking schema change. If a run for an overlapping scope is active, return that run instead of starting another. |
| RUN-03 | MUST | The UI shows live progress (companies processed / total), then status, start and end times, scope, counts and a readable error summary. |
| RUN-04 | MUST | A failed run can be retried; the retry is a new run that references the original. |
| RUN-05 | MUST | **Scheduled sync** settings: enabled, frequency (daily, weekly with day, monthly with day, or cron if supported ❓), time, time zone, scope, mode (regular or full), retry policy. Next and last run are shown read-only. |
| RUN-06 | MUST | The platform scheduler creates the run; never a browser timer or a long-running HTTP request. Manual and scheduled runs execute the same job. |
| RUN-07 | MUST | One schedule occurrence creates at most one run (key: schedule ID + occurrence time). |
| RUN-08 | MUST | An occurrence that overlaps an active run is skipped and recorded as `SKIPPED_OVERLAP`. |
| RUN-09 | MUST | Missed occurrences (platform downtime) run once at the next start, not once per missed occurrence. |
| RUN-10 | MUST | When the connection is `DISABLED` or `AUTH_FAILED`, occurrences are recorded as `SKIPPED` with the reason. |
| RUN-11 | MUST | Retry only transient failures (timeouts, 429, 502/503/504), at most 2 times with exponential backoff. Never retry authentication, permission, schema-block or mapping errors. |
| RUN-12 | SHOULD | Default recommendation: daily regular sync plus weekly full sync. |
| RUN-13 | MUST | Evaluate schedules in the configured time zone, including daylight-saving changes. |

---

## 13. Sync Schedule and Change History

### 13.1 Sync-run history

| ID | Priority | Requirement |
|---|---|---|
| HIST-01 | MUST | Store each run with the fields below, plus one result row per company (`runId, rrid, companyId, outcome, reason`). |
| HIST-02 | MUST | The UI lists runs filtered by date range, trigger and status, and shows run detail with per-company results. |

| Field | Description |
|---|---|
| `runId`, `tenantId`, `integrationId` | Identity and scope |
| `triggerType` | `MANUAL`, `SCHEDULED`, `RETRY` |
| `requestedBy` | User ID or schedule ID |
| `status` | `QUEUED`, `RUNNING`, `SUCCEEDED`, `PARTIALLY_SUCCEEDED`, `FAILED`, `CANCELLED`, `SKIPPED`, `SKIPPED_OVERLAP` |
| `startedAt`, `completedAt`, `durationMs` | UTC |
| `scope`, `mode` | Companies in scope; `FULL` or `REGULAR` |
| `configVersion`, `sourceSchemaVersion`, `mappingVersion`, `dataModelVersion` | Versions used |
| `counts` | `companiesListed`, `linked`, `unlinked`, `created`, `updated`, `unchanged`, `failed`, `periodsWritten`, `ratingsWritten` |
| `apiCalls`, `retries` | Operational metrics |
| `retryOf` | Original run, for retries |
| `errorSummary` | Sanitized and actionable; never secrets, tokens or raw responses |
| `correlationId` | Trace ID across logs |

Company outcomes: `CREATED`, `UPDATED`, `UNCHANGED`, `UNLINKED`, `FAILED`.

### 13.2 Schedule history

| ID | Priority | Requirement |
|---|---|---|
| HIST-03 | MUST | Every schedule change creates a new schedule version and a change-history entry. |
| HIST-04 | MUST | The schedule screen shows current settings, who changed them and when, next run and last run result. |

### 13.3 Configuration change history (audit)

| ID | Priority | Requirement |
|---|---|---|
| HIST-05 | MUST | Record: enable/disable, environment change, credential rotation (event only), connection test, schema version discovered/approved/rejected, mapping created/edited/validated/activated, company linked/unlinked, Company created from RapidRatings, Company Name or Country filled from RapidRatings, schedule changed, manual sync started, DataModel extension applied. |
| HIST-06 | MUST | Each entry has `changeId, tenantId, integrationId, actor (user or SYSTEM), action, entity, before, after, version, timestamp (UTC), correlationId/runId, comment`. Secret changes are recorded as `"***changed***"`. |
| HIST-07 | MUST | Change history is append-only: readable, never editable or deletable. |

---

## 14. Security and Error Handling

### 14.1 Security

| ID | Priority | Requirement |
|---|---|---|
| SEC-01 | MUST | Client Secret in the secret manager only; tokens in server memory only (AUTH-01, AUTH-02). |
| SEC-02 | MUST | HTTPS for all provider calls. |
| SEC-03 | MUST | Every configuration, cache entry, job, query and record is keyed by tenant; no tenant can read another tenant's data, runs or history. |
| SEC-04 | MUST | Only integration administrators change credentials, scope, mapping, links or schedule, or start syncs. Business users read RapidRatings data; auditors read history. |

### 14.2 Error handling

| Situation | Required behaviour |
|---|---|
| Token request 400/401 | `AUTH_FAILED`; no retry; administrator notified |
| Data call 403 | New token, one retry; second 403 → `PERMISSION_FAILED` |
| Data call 401 | Adapter defect (header missing); fail the run |
| 400 `Not valid query parameter` | Adapter defect; no retry; report the parameter |
| 404 on a detail URL | Check the trailing slash; otherwise unknown RRID or period → company `FAILED` |
| 404/500 for a page past the end | Prevented by following `next`; never retried |
| 429 | Wait `Retry-After` (capped); bounded retries |
| Timeout / 502 / 503 / 504 | Up to 2 retries with exponential backoff |
| Unexpected JSON or type | Fail that company; flag schema drift |
| RRID in `href` differs from the company | Reject the record; data-quality error |
| Company cannot be linked | `UNLINKED`; listed for review; nothing written |
| Breaking schema change | Block affected syncs until the mapping is approved |

### 14.3 Monitoring

| ID | Priority | Requirement |
|---|---|---|
| OPS-01 | SHOULD | Alert when a tenant enters `AUTH_FAILED`, a scheduled run fails, two consecutive runs are partial, a breaking schema change blocks sync, or a linked company's `last_synced_at` is older than two schedule intervals. |
| OPS-02 | SHOULD | Track API calls, retries and rate-limit responses per tenant. |

---

## 15. Outreach API – Out of Scope

Endpoint: `https://api.sandbox.rrilabs.co/outreach-requests` (separate host).

Known ✅: GET/POST on `/outreach-requests`, GET on `/outreach-requests/{id}` and `/outreach-requests/{id}/communications`; cursor paging (`limit` + `cursor`); the same access token works; an OpenAPI specification is published; expired token returns 401. **Creating an outreach request sends real emails to the counterparty, even in the sandbox.**

| ID | Priority | Requirement |
|---|---|---|
| OUT-01 | MUST | Do not call the Outreach API in release 1. It requires its own business approval workflow before inclusion. |

---

## 16. Acceptance Criteria

| # | Scenario | Expected result | Covers |
|---|---|---|---|
| 1 | Connect with valid sandbox credentials | **Connected**; scope shown; no secret or token in any log, response or history | AUTH-01..08 |
| 2 | Connect with a wrong secret | `AUTH_FAILED`; exactly one token request | AUTH-06 |
| 3 | Token with < 60 s left | A new token is requested before the next call | AUTH-04 |
| 4 | Data call returns 403 | One renewal + one retry; a second 403 → `PERMISSION_FAILED` | AUTH-05 |
| 5 | Sync one linked company | Profile, sector averages and `current_*` values stored; `latest_ratings` equals the provider's `latestRatings`; one `periods[]` element per provider period and one `ratings[]` element per provider rating (counts equal the endpoints' `count`) | SYNC-03, MAP-02 |
| 6 | A period with a `null` amount and a negative amount | Stored as `null` and with its negative sign; nothing converted to `0` | MAP-05 |
| 7 | Re-run the same sync | No duplicate elements; outcome `UNCHANGED` | SYNC-06, SYNC-09 |
| 8 | Two different companies have the same period ID | Their periods and ratings are never mixed | SYNC-05 |
| 9 | A rating whose `href` RRID differs from the company | Rejected and reported | SYNC-04 |
| 10 | A company's period disappears from one response | Stored element kept | SYNC-06 |
| 11 | Fixture adds a field | Additive schema version; sync continues | SCH-05 |
| 12 | Fixture removes a mapped field | Sync blocked until mapping approved | SCH-05 |
| 13 | One company times out repeatedly | Others succeed; run `PARTIALLY_SUCCEEDED` | SYNC-07 |
| 14 | Same scope via Sync now and via schedule | Identical stored results | ARCH-03 |
| 15 | Schedule occurrence while a run is active | `SKIPPED_OVERLAP` | RUN-08 |
| 16 | Edit schedule and mapping | New versions; change history shows actor, before, after | HIST-03..06 |
| 17 | Rotate credentials | Change history entry without values; token cache cleared | AUTH-09 |
| 18 | Tenant B views tenant A's runs or data | Denied | SEC-03 |
| 19 | Open Company Details for a synced company | RapidRatings section plus three tables; nulls blank, not 0 | DISP-01..03 |
| 20 | Unlinked company | Listed for review; nothing written | LINK-04 |
| 21 | Sync a linked Company whose Company Name and Country differ from RapidRatings | `company_name` and `basic_info.country` unchanged; RapidRatings values stored in `source_company_name` and `source_country` | MAP-06 |
| 22 | Sync a linked Company whose Country is empty | `basic_info.country` = LOV value matching the RapidRatings `country`; write recorded | MAP-06, MAP-11, MAP-12 |
| 23 | Create a Company from an unlinked RapidRatings company | New Company with Company Name = RapidRatings `name`, Country mapped, RRID linked | LINK-06, MAP-06 |
| 24 | Create from RapidRatings when the name already exists as a Company Name | No duplicate created; link to the existing Company offered | MAP-13 |

---

## 17. Open Questions

| # | Area | Question | Owner | Needed by |
|---|---|---|---|---|
| Q1 | Z2 DataModel | Which model layer (`dataModelType` / `extDataModelId`) owns the RapidRatings group? (Kharon uses the CORE layer.) | Z2 platform | Phase 3 |
| Q2 | Z2 UI | Does `canUpload: false` make the group read-only? How does a ~30-column, ~27-row list table render, and how is `null` displayed? What is the link template for a property whose value is a URL? | Z2 platform / UI | Phase 3 |
| Q3 | Z2 platform | Record-update API (with optimistic locking) for backend jobs; job queue; scheduler capabilities (frequencies, cron, time zones) | Z2 platform | Phase 5 |
| Q4 | Linking | Which Z2 property holds a customer/ERP ID comparable to RapidRatings `externalSourceId`? | Product | Phase 4 |
| Q5 | RapidRatings production | Base URL; same paging, page sizes, errors, token lifetime and scopes as the sandbox; any changed-since option | RapidRatings | Phase 2 |
| Q6 | Rating definitions | Official definitions of FHR, CHS, PD, term PD; allowed values of leverage, liquidity, earnings performance | RapidRatings | Phase 4 |
| Q7 | Data | Can `dataScale` differ between a company's periods? Element type of `externalSourceIds`? Values of `dataConfidential` for private companies? Is one rating per period guaranteed? | RapidRatings | Phase 4 |
| Q8 | Financials link | Does `/companies/{rrid}/financials/` (not exercised) add data beyond `periods/`? | RapidRatings | Phase 4 |
| Q9 | Retention | Contractual limits on storing rating history, report links and raw responses | Legal / Product | Before pilot |
| Q10 | Company Name / Country | What are the Z2 country LOV values, for the ISO-3 lookup? Is Company Name unique per tenant (for MAP-13)? Which platform API creates a new Company record (LINK-06)? | Z2 platform | Phase 4 |

---

## 18. Implementation Order

| Phase | Build | Done when |
|---|---|---|
| 1. Foundation and authentication | Integration and tenant-configuration records, secret references, token handling (AUTH-*), Test connection, connection states, change history for configuration | Acceptance 1–4, 17 pass |
| 2. Adapter and schema | HTTP client (API-*), response models and validation, schema inference, versioned schema registry with diff (SCH-*) | Acceptance 11–12 pass |
| 3. DataModel and display | `rapidratings` group and three lists (MAP-01..04, 7.7), Company Details sections (DISP-*) | Group visible in raw properties; sections render with test data |
| 4. Mapping and linking | Versioned mapping with validation (MAP-05..10), linking and review list (LINK-*) | Default mapping activates; acceptance 20 passes |
| 5. Data sync and manual runs | Pipeline, update rules, regular/full modes (SYNC-*), Sync now, progress, run history, retry (RUN-01..04, HIST-01..02) | Acceptance 5–10, 13, 19 pass |
| 6. Scheduling and audit | Schedules, versions, overlap and missed-run handling (RUN-05..13), schedule and change history (HIST-03..07) | Acceptance 14–16 pass |
| 7. Hardening and pilot | Monitoring (OPS-*), tenant isolation (SEC-*), one pilot tenant behind a feature switch | Acceptance 18 passes; pilot runs a week of scheduled syncs without manual fixes |

**Rollback:** disabling the integration stops all syncs immediately. Synced data stays on the Company records and can be hidden by removing the Company Details sections. The integration never overwrites existing Company values, so nothing needs restoring; values it filled into empty fields are listed in the change history (MAP-11).

---

## 19. Expected Outcome

A tenant-configurable RapidRatings integration in Z2 Enterprise that:

- authenticates securely from the backend;
- retrieves company profiles with their full financial-period and rating history;
- maps them into a new `RapidRatings` group on the existing Company DataModel;
- shows them on Company Details;
- keeps them current through manual and scheduled syncs, without duplicates or lost history;
- records every run and every configuration change.
