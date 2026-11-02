# RapidRatings Integration – Solution Overview and Implementation Guide

| | |
|---|---|
| **Program** | Z2 Enterprise – Data Integrations |
| **Integration** | RapidRatings |
| **Target DataModel** | Existing Z2 `Company` DataModel (`docs/Best/Z2company.json`, objectType id `144`, `dbName: company`) |
| **Source environment investigated** | RapidRatings sandbox (`rridata.sandbox.rrilabs.co`, `api.sandbox.rrilabs.co`) |
| **Requirement source** | `docs/Best/Z2Text` |
| **Document status** | Proposed solution design, ready for implementation planning. Confirm the items in Section 13 before production rollout. |
| **Date** | 2026-10-30 |

### How to read this document

Every statement that matters for implementation carries one of these labels:

| Label | Meaning |
|---|---|
| **[Verified]** | Observed against the RapidRatings sandbox (2026-10-28 / 2026-10-30). Details and real responses in `docs/api/`. |
| **[Z2 model]** | Read directly from the existing Company DataModel in `Z2company.json`. |
| **[Proposed]** | Design decision recommended by this document. |
| **[Confirm]** | Must be confirmed with RapidRatings or the Z2 platform team before production. |

### Requirement coverage

The six common integration functions from `Z2Text` and where each is answered:

| # | Requirement | Section |
|---|---|---|
| 1 | Integration Authentication | 3 |
| 2 | Schema | 5 |
| 3 | Schema Mapping to Z2 Enterprise | 6 |
| 4 | Syncing of Schema and Data | 7 |
| 5 | Manual and Scheduled Syncs | 8 |
| 6 | Sync Schedule and Change History | 9 |
| – | Step-by-step: authenticate → get company details → map to Z2 Company → show on Company Details | 4 |

---

## 1. Executive Summary

Z2 Enterprise offers a **Data Integrations** area where a customer enables integrations per tenant. This document defines how RapidRatings is added as one of those integrations.

When a tenant enables RapidRatings and stores its Client ID and Client Secret, the Z2 backend:

1. obtains an OAuth 2.0 access token from RapidRatings (Client Credentials grant);
2. reads the companies in the tenant's chosen scope (optionally a RapidRatings portfolio);
3. reads each company's details, financial **Periods** and **Ratings** using its RapidRatings ID (**RRID**);
4. links each RapidRatings company to the right Z2 Company record;
5. writes the data into a new **`RapidRatings` NESTED group** on the existing Z2 Company DataModel, with three `PROPS_ARRAY` collections: **Latest Ratings**, **Periods** and **Ratings**;
6. shows the RapidRatings profile and the three lists on the Company Details page;
7. runs on demand (**Sync now**) or on a **schedule**, recording every run and every configuration change.

The design copies the conventions already used in the DataModel for another external provider, **Kharon Risk Data** (`kharon`): a provider NESTED group with summary scalars plus `PROPS_ARRAY` collections, displayed as Company Details sections.

> **Important correction to the requirement notes.** `Z2Text` says "frontend call the authentication api and getToken". That cannot work and must not be built that way: **[Verified]** the RapidRatings data host (including the token endpoint) does not return CORS headers for browser origins, so a browser call fails; and the Client Secret would be exposed to every user. **All RapidRatings calls, including authentication, are made by the Z2 backend.** The frontend only calls Z2 platform APIs (Section 10).

---

## 2. Architecture

```text
 ┌──────────────────────────── Z2 Enterprise UI (browser) ────────────────────────────┐
 │ Data Integrations: catalogue · RapidRatings connection · schema · mapping ·        │
 │ Sync now · schedule · sync history · change history                                │
 │ Company Details: RapidRatings section · Latest Ratings · Periods · Ratings tables  │
 └───────────────────────────────┬────────────────────────────────────────────────────┘
                                 │ Z2 platform APIs only (tenant-authorised)
 ┌───────────────────────────────▼────────────────────────────────────────────────────┐
 │ Z2 Data Integrations backend (framework, provider-neutral)                         │
 │  Tenant integration config · Credential reference (secret manager)                 │
 │  Source schema registry · Mapping registry · Company matching                      │
 │  Sync orchestrator + job queue · Scheduler · Sync-run history · Change history     │
 └───────────────────────────────┬────────────────────────────────────────────────────┘
                                 │ provider adapter contract
 ┌───────────────────────────────▼────────────────────────────────────────────────────┐
 │ RapidRatings adapter (provider-specific)                                           │
 │  Token provider (cache + early renewal) · HTTP client (retry, rate limit)          │
 │  Pagination (follow `next`) · Response DTOs + validation · Schema inference        │
 └───────────────┬───────────────────────────────────────────────┬────────────────────┘
                 │ HTTPS + Bearer                                │ (out of scope v1)
   rridata.sandbox.rrilabs.co/v2.0                    api.sandbox.rrilabs.co
   oauth/token · portfolios · companies               outreach-requests
   companies/{rrid}/ · periods/ · ratings/
                 │
 ┌───────────────▼────────────────────────────────────────────────────────────────────┐
 │ Z2 data layer: Company record → rapidratings (NESTED)                              │
 │   profile scalars · latest_ratings[] · periods[] · ratings[]   (PROPS_ARRAY)       │
 └────────────────────────────────────────────────────────────────────────────────────┘
```

Responsibilities:

| Layer | Owns |
|---|---|
| Z2 Data Integrations framework | Everything common to all providers: tenant enablement, credential references, schema registry, mapping, matching, orchestration, scheduling, run history, audit. |
| RapidRatings adapter | Everything specific to RapidRatings: token request, endpoints, paging rules, error semantics, response parsing. A second provider adds its own adapter and reuses the framework. |
| Z2 data layer | The Company record and its `rapidratings` group. |

---

## 3. Integration Authentication

### 3.1 Contract **[Verified]**

| Item | Value |
|---|---|
| Endpoint | `POST https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` (trailing slash is part of the URL; `GET` returns 405) |
| Grant | OAuth 2.0 **Client Credentials** |
| Client authentication | `Authorization: Basic base64(client_id:client_secret)` |
| Body | `grant_type=client_credentials` (`application/x-www-form-urlencoded`; JSON also accepted) |
| Response | `{"access_token": "…", "expires_in": 1800, "token_type": "Bearer", "scope": "uid introspection openid"}` |
| Lifetime | 1800 s (30 minutes) |
| Refresh token | **Not issued.** Renewal = repeat the same request. |
| Token use | `Authorization: Bearer <access_token>`; the same token is accepted by the data host and the outreach host |

Request:

```http
POST /v2.0/oauth/token/ HTTP/1.1
Host: rridata.sandbox.rrilabs.co
Authorization: Basic <base64(client_id:client_secret)>
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
```

Error behaviour **[Verified]**:

| Where | Status | Meaning |
|---|---|---|
| Token endpoint | 400 | Unsupported `grant_type` |
| Token endpoint | 401 | Wrong client ID/secret, **or** missing `Authorization` header, **or** missing `grant_type` (so 401 is not always "bad credentials") |
| Data API | 401 | `Authorization` header missing |
| Data API | **403** `Not Authorized.` | Invalid **or expired** token |
| Outreach API | **401** | Invalid or expired token |

Error body on the data host: `{"Request": {"message": "...", "info": {}}}`.

### 3.2 Step-by-step authentication flow

```text
Admin (UI)            Z2 backend                       Secret manager       RapidRatings
    │ 1. Enable RapidRatings, enter Client ID/Secret, environment
    │──────────────────────▶│ 2. Store secret ─────────────▶│ (returns reference)
    │                       │ 3. Save TenantIntegration with credentialRef (never the secret)
    │ 4. "Test connection"  │
    │──────────────────────▶│ 5. Resolve secret ◀───────────│
    │                       │ 6. POST /oauth/token/ (Basic) ─────────────────────────▶│
    │                       │◀──────────────── 7. access_token, expires_in=1800 ──────│
    │                       │ 8. Cache token in memory: expiresAt = now + 1800 s
    │                       │ 9. GET /v2.0/portfolios/ (Bearer) ─────────────────────▶│
    │                       │◀──────────────────────────────── 10. 200 {count: 5} ────│
    │◀── 11. "Connected" + timestamp + scope (token never returned) ──│
```

1. A tenant administrator opens **Data Integrations → RapidRatings → Connect** and enters Client ID, Client Secret and environment (sandbox / production).
2. The backend stores the secret in the approved secret manager and keeps only a **credential reference**.
3. The backend saves the `TenantIntegration` record (status `CONFIGURED`). The UI never receives the secret back.
4. The administrator clicks **Test connection**.
5. The backend resolves the secret for that tenant + environment.
6. It sends the token request (3.1).
7. It reads `access_token` and `expires_in` from the response.
8. It caches the token **in server memory only**, keyed by `(tenantId, environment)`, with `expiresAt = issuedAt + expires_in`.
9. It calls a low-impact authorised endpoint, `GET /v2.0/portfolios/`, to prove data access.
10. A `200` confirms the credentials *and* the data permission.
11. The UI shows **Connected**, the check time and the granted scope. Status becomes `ACTIVE`.

### 3.3 Token lifecycle rules **[Proposed]**, based on **[Verified]** behaviour

| Rule | Detail |
|---|---|
| Reuse | Reuse the cached token for every call; never request a token per call. |
| Early renewal | Request a new token when fewer than **60 s** remain (`now >= expiresAt − 60 s`). |
| Single flight | Concurrent workers for the same tenant share one in-flight token request (lock/promise per tenant). |
| Expired-token retry | On **403** from the data host (401 from outreach), drop the cached token, get a new one and **retry the request once**. If it fails again, it is a permission error, not expiry: stop and report. |
| No loops | Never retry a token request on 400/401 from the token endpoint; mark the connection `AUTH_FAILED`. |
| Timeouts | 30 s connect/read timeout on token and data calls. |
| Secrecy | Never log, trace, store in sync history or return to the UI: client secret, Basic header, access token, token response body. |
| Rotation | A credential update replaces the secret reference, clears the cached token, re-runs Test connection, and writes a change-history entry (without values). |

Token provider (illustrative, language-neutral):

```text
getToken(tenant, env, force=false):
    entry = cache[tenant, env]
    if not force and entry and now < entry.expiresAt - 60s: return entry.token
    with lock(tenant, env):                       # single flight
        secret = secretManager.resolve(tenantIntegration.credentialRef)
        resp = POST tokenUrl, Basic(secret.clientId, secret.clientSecret), grant_type=client_credentials
        if resp.status != 200: raise AuthError(category(resp))     # sanitized, no body echo
        cache[tenant, env] = { token: resp.access_token, expiresAt: now + resp.expires_in }
        return cache[tenant, env].token

callApi(request):
    resp = send(request, Bearer getToken())
    if resp.status in (403 on rridata, 401 on outreach):
        resp = send(request, Bearer getToken(force=true))         # exactly one retry
    return resp
```

### 3.4 Connection states **[Proposed]**

`NOT_CONFIGURED → CONFIGURED → ACTIVE`, with `AUTH_FAILED` (token request rejected), `PERMISSION_FAILED` (token OK, data call 403 after renewal), `DISABLED` (tenant switched it off). Syncs run only in `ACTIVE`.

---

## 4. Step-by-Step: Authenticate, Get Company Details, Map, Display

Worked example with the company from `Z2Text`: **Rite Aid Corporation, RRID `KawjbGJ`**. All responses below are real sandbox data **[Verified]**.

### Step 1 – Get a token
Section 3.2, steps 5–8.

### Step 2 – (Optional) choose the portfolio scope
`GET /v2.0/portfolios/` → 5 portfolios in the sandbox, e.g. `{"id": 27070, "name": "z2Data Sandbox", "companies": ".../companies/?portfolio=27070"}`. Portfolio scoping is optional: a tenant can sync all companies its account can see, one or more portfolios, or an explicit list of RRIDs.

### Step 3 – List the companies in scope
`GET /v2.0/companies/?portfolio=27070` → `{"count": …, "next": "...&page=2", "results": [ …20 companies… ]}`.
Follow `next` until it is `null` (20 per page, fixed). Each item already contains every list field, including `latestRatings`.

### Step 4 – Get the company details
`GET /v2.0/companies/KawjbGJ/` (trailing slash required). Returns the list fields **plus** `reports` and `sectorAverages`:

```json
{
  "id": "KawjbGJ", "name": "Rite Aid Corporation", "country": "USA", "ticker": "RAD", "exchange": "US", "sector": "Retail",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/",
  "financials": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/financials/",
  "periods": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/periods/",
  "ratings": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/ratings/",
  "latestRatings": [ { "id": "2020YE", "fhr": 35, "chs": 37, "...": "5 items: 2020YE, 2020Q3, 2020Q2, 2020Q1, 2019YE" } ],
  "reportLinks": {
    "fhr": "https://portal.sandbox.rrilabs.co/reportsapi/31136/fhr",
    "financialDialog": "https://portal.sandbox.rrilabs.co/reportsapi/31136/financial_dialogue",
    "peerBenchmark": "https://portal.sandbox.rrilabs.co/reportsapi/31136/peer_benchmark"
  },
  "address": "", "industryClassificationSystem": null, "industryClassificationCode": null,
  "currency": "USD", "dataScale": "millions", "dataConfidential": "PUB",
  "externalSourceId": null, "externalSourceIds": [],
  "reports": [ { "reportType": "fhr", "downloadHref": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/reports/?reportType=fhr" }, "..." ],
  "sectorAverages": { "fhr": 55.6, "chs": 48.5 }
}
```

### Step 5 – Get the Periods
`GET /v2.0/companies/KawjbGJ/periods/` → `count: 27`, 10 per page, newest first (`2020YE, 2020Q3, 2020Q2, …`). Follow `next` (3 pages). One period:

```json
{ "id": "2020YE", "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/periods/2020YE/",
  "eqyYear": 2020, "period": "YE", "financialDate": "2020-02-29", "currency": "USD",
  "salesRevenue": 21928.393, "earningsBeforeInterestAndTax": 152.271, "interestExpense": 229.657,
  "interestReceived": null, "companyTaxExpense": 387.607, "abnormalItems": -92.793, "netProfitAfterTax": -469.219,
  "bankCashBalances": 218.18, "totalCurrentAssets": 3700.641, "fixedAssets": 4119.094, "financialAssets": null,
  "totalAssets": 9452.369, "debitOwnedWithinOneYear": 8.84, "totalCurrentLiabilities": 2766.463,
  "termLoans": 3096.594, "totalLiabilities": 8777.842, "totalShareholderEquity": 674.527,
  "cashFromOperationsFromCashFlow": null, "netInvestingCashFlow": -86.542, "netFundingCashFlow": -326.652 }
```

Note the nulls (`interestReceived`, `financialAssets`, `cashFromOperationsFromCashFlow`) and negative values: both must be stored exactly as received.

### Step 6 – Get the Ratings
`GET /v2.0/companies/KawjbGJ/ratings/` → `count: 27`, same IDs as the periods, newest first. Follow `next` (3 pages). Each rating has the same shape as a `latestRatings` item.

### Step 7 – Link to the Z2 Company record
Resolve `KawjbGJ` to a Z2 Company using the matching order in Section 6.7. Unmatched companies go to a review list; nothing is created or linked automatically by name.

### Step 8 – Map and write
Transform with the active mapping (Section 6) and upsert into the Company record:

```json
{
  "company_name": "Rite Aid Corporation",
  "rapidratings": {
    "rrid": "KawjbGJ",
    "source_company_name": "Rite Aid Corporation",
    "source_country": "USA",
    "ticker": "RAD", "exchange": "US", "sector": "Retail",
    "address": "",
    "company_url": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/",
    "financials_url": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/financials/",
    "periods_url": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/periods/",
    "ratings_url": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/ratings/",
    "currency": "USD", "data_scale": "millions", "data_confidentiality": "PUB",
    "industry_classification_system": null, "industry_classification_code": null,
    "external_source_id": null, "external_source_ids": [],
    "report_link_fhr": "https://portal.sandbox.rrilabs.co/reportsapi/31136/fhr",
    "report_link_financial_dialog": "https://portal.sandbox.rrilabs.co/reportsapi/31136/financial_dialogue",
    "report_link_peer_benchmark": "https://portal.sandbox.rrilabs.co/reportsapi/31136/peer_benchmark",
    "sector_average_fhr": 55.6, "sector_average_chs": 48.5,
    "current_rating_id": "2020YE", "current_fhr": 35, "current_chs": 37,
    "current_probability_default": 1.5853, "current_financial_date": "2020-02-29",
    "last_synced_at": "<sync completion timestamp>",
    "latest_ratings": [
      { "rating_id": "2020YE", "eqy_year": 2020, "period": "YE", "financial_date": "2020-02-29",
        "reporting_period": "2020-05-15", "rating_url": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/ratings/2020YE/",
        "fhr": 35, "chs": 37, "probability_default": 1.5853,
        "term_pd_year_2": 8.24, "term_pd_year_3": 12.18, "term_pd_year_4": 15.85, "term_pd_year_5": 19.4,
        "term_pd_year_6": 23.04, "term_pd_year_7": 26.93, "term_pd_year_8": 31.16, "term_pd_year_9": 35.81,
        "term_pd_year_10": 40.85, "delta": 13, "simulated_fhr": 37, "simulated_fhr_delta": 9,
        "operating_profitability": 36, "net_profitability": 5, "capital_structure_efficiency": 82,
        "cost_structure_efficiency": 50, "leverage": "Weak", "liquidity": "Weak", "earnings_performance": "Weak" }
    ],
    "periods": [
      { "period_id": "2020YE", "eqy_year": 2020, "period": "YE", "financial_date": "2020-02-29", "currency": "USD",
        "period_url": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/periods/2020YE/",
        "sales_revenue": 21928.393, "earnings_before_interest_and_tax": 152.271, "interest_expense": 229.657,
        "interest_received": null, "company_tax_expense": 387.607, "abnormal_items": -92.793,
        "net_profit_after_tax": -469.219, "bank_cash_balances": 218.18, "total_current_assets": 3700.641,
        "fixed_assets": 4119.094, "financial_assets": null, "total_assets": 9452.369,
        "debit_owned_within_one_year": 8.84, "total_current_liabilities": 2766.463, "term_loans": 3096.594,
        "total_liabilities": 8777.842, "total_shareholder_equity": 674.527,
        "cash_from_operations_from_cash_flow": null, "net_investing_cash_flow": -86.542, "net_funding_cash_flow": -326.652 }
    ],
    "ratings": [ { "rating_id": "2020YE", "...": "same fields as latest_ratings; one element per rating (27)" } ]
  }
}
```

`company_name` is shown only to identify the record: the sync **never changes** it (Section 6.4).

### Step 9 – Display
Company Details shows a **RapidRatings** section with the profile fields and three tables: **Latest Ratings**, **Periods**, **Ratings** (Section 6.8).

---

## 5. Schema

### 5.1 How the source schema is obtained **[Verified]**

| Host | Machine-readable schema | Approach |
|---|---|---|
| rridata (companies, portfolios, periods, ratings) | **None** (`/openapi.json` returns 403) | **Inferred** from real responses + the documented field list in `docs/api/` |
| outreach | OpenAPI spec at `https://api.sandbox.rrilabs.co/openapi.yaml` | **Declared** (out of scope for v1) |

There is no schema-discovery endpoint. Schema discovery = sample real responses, infer field paths and types, compare with the registered version (Section 7.1).

### 5.2 Company source schema

Types as supplied in `Z2Text`, refined with the verified docs where a field was only seen as `null`.

| Source path | Supplied type | Verified type / notes |
|---|---|---|
| `id` | string | RRID, 7 characters, e.g. `KawjbGJ`. Primary provider key. |
| `name` | string | Legal name |
| `country` | string | ISO-3 code observed (`USA`) |
| `ticker` | string | Exact, case-sensitive |
| `exchange` | string | e.g. `US` |
| `sector` | string | RapidRatings sector, e.g. `Retail` |
| `href`, `financials`, `periods`, `ratings` | uri | Absolute URLs |
| `latestRatings` | array | **Newest first, up to 5** (2–5 observed). `latestRatings[0]` = current rating. |
| `latestRatings[]` fields | see 5.4 | Same shape as a Ratings item |
| `reportLinks.fhr`, `.financialDialog`, `.peerBenchmark` | uri | Portal links for users |
| `address` | string | May be `""` |
| `industryClassificationSystem` | null | `string \| null` |
| `industryClassificationCode` | null | `string \| null` |
| `currency` | string | e.g. `USD` |
| `dataScale` | string | e.g. `millions`. Applies to every period amount. |
| `dataConfidential` | string | `PUB` for public companies; other values for private companies **[Confirm]** |
| `externalSourceId` | null | `string \| null`: the customer's own ID for the company, if mapped on the RapidRatings side |
| `externalSourceIds` | array | Array; element type not observed (always `[]` in sandbox) **[Confirm]** |
| `reports[]` (detail only) | – | `{reportType, downloadHref}`; downloads need API authentication |
| `sectorAverages` (detail only) | – | `{fhr: number, chs: number}` |

### 5.3 Periods source schema

| Source path | Supplied type | Verified type / notes |
|---|---|---|
| `id` | string | `eqyYear` + `period`, e.g. `2020YE` |
| `href` | uri | |
| `eqyYear` | integer | Fiscal/equity year |
| `period` | string | `Q1`, `Q2`, `Q3`, `YE` |
| `financialDate` | date | Statement date |
| `currency` | string | |
| `salesRevenue`, `earningsBeforeInterestAndTax`, `interestExpense`, `interestReceived`, `companyTaxExpense`, `abnormalItems`, `netProfitAfterTax`, `bankCashBalances`, `totalCurrentAssets`, `fixedAssets`, `totalAssets`, `debitOwnedWithinOneYear`, `totalCurrentLiabilities`, `termLoans`, `totalLiabilities`, `totalShareholderEquity`, `netInvestingCashFlow`, `netFundingCashFlow` | number | `number \| null`. **Every amount can be null** (`interestReceived` is null for Rite Aid 2020YE). Values can be negative. |
| `financialAssets` | null | `number \| null` |
| `cashFromOperationsFromCashFlow` | null | `number \| null` (null in every sandbox sample) |

`debitOwnedWithinOneYear` is the provider's own spelling (short-term debt); keep it in the source path.

### 5.4 Ratings source schema (also `latestRatings[]`)

| Source path | Supplied type | Verified type / meaning |
|---|---|---|
| `id` | string | Equals the matching period `id` |
| `href` | uri | |
| `eqyYear` | integer | |
| `period` | string | `Q1`, `Q2`, `Q3`, `YE` |
| `financialDate` | date | Statement date the rating is based on |
| `reportingPeriod` | date | Date the rating was produced |
| `fhr` | integer | Financial Health Rating, 0–100, 12-month default risk; higher = healthier |
| `chs` | integer | Core Health Score, 0–100, 2–3 year resilience |
| `probabilityDefault` | number | 1-year PD **in percent** (`1.5853` = 1.59 %) |
| `termPd.year2` … `termPd.year10` | number | Cumulative PD in percent over N years |
| `delta` | integer | FHR change vs. previous rating; `null` for the oldest |
| `simulatedFhr` | integer | |
| `simulatedFhrDelta` | integer | Nullable |
| `operatingProfitability`, `netProfitability`, `capitalStructureEfficiency`, `costStructureEfficiency` | integer | Sub-scores 0–100 |
| `leverage`, `liquidity`, `earningsPerformance` | string | Observed: `Strong`, `Average`, `Weak` |

Metric meanings are as recorded during the sandbox investigation; confirm the official definitions before building any display rule on them **[Confirm]**.

### 5.5 Paging and filters **[Verified]**

| Resource | Paging | Accepted parameters | Page past the end |
|---|---|---|---|
| Portfolios | `page` + `next` | `page` | **500** |
| Companies | `page` + `next`, 20/page fixed | `page`, `portfolio`, `name` (≥ 3 chars, contains), `ticker` (exact), `externalSourceId` | 404 |
| Periods | `page` + `next`, 10/page fixed, newest first | `page` | **500** |
| Ratings | `page` + `next`, 10/page fixed, newest first | `page` | **500** |

Any other query parameter returns **400**. There is **no** changed-since / incremental filter. Trailing slashes are required. Rate-limit headers: `X-Ratelimit-Limit` (sandbox 30,000,000), `X-Ratelimit-Remaining`, `X-Ratelimit-Reset`, `Retry-After`.

**Rule:** always follow the `next` URL; never compute page numbers (an out-of-range page returns a 500 that must not be retried).

---

## 6. Schema Mapping to Z2 Enterprise

### 6.1 Facts about the existing Company DataModel **[Z2 model]**

| Fact | Evidence in `Z2company.json` |
|---|---|
| Company is `objectType` id 144, `dbName: company`, `dataModelType: CORE`, primary key `company_name` | root object |
| Property groups use `dataType: NESTED`; repeatable records use `PROPS_ARRAY` | `financial`, `kharon`, `z2.sanctions`, `kharon.risks` |
| A `PROPS_ARRAY` can contain another `PROPS_ARRAY` | `kharon.risks.topics` |
| A `PROPS_ARRAY` child can be `required` | `z2.sanctions.list_key`, `.status`, `.last_checked` |
| There is **no URI type**; URLs are `STRING` | `kharon.risks.applied_source_url`, `certifications.*.source_url` |
| Data types in use | `STRING, TEXT, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, TIMESTAMP, ENUM, LOV, LOV_ARRAY, STRING_ARRAY, USER_ARRAY, EMAIL, PHONE, JSON, NESTED, PROPS_ARRAY` |
| Dates display via `uiHints: {"displayType": "DATE"}`; links via `uiHints.displayType: "LINK"` | `kharon.last_modified`, `z2data.z2_company_id` |
| Every property has `name, dbName, path, label, description, isPrimaryKey, required, searchable, filterable, dataType, dataModelType, extDataModelId, canUpload, hidden, properties, isCalculated, isAi, config, uiHints`; `id`, `dataEnvId` and timestamps are assigned by the platform | all properties |
| Ownership layers: `CORE`/2278, `PACKAGE`/236, `APPLICATION`/431 (`z2data`), `CUSTOMER`/512 | `dataModelType` / `extDataModelId` |
| Company Details is layout-driven: `config.objectDetails.tabs[].layout.sections[]` with `fields` blocks binding `objectProperty` by `dbName`; a `PROPS_ARRAY` is shown as a one-column section bound to the array | sections `kharon`, `kharon_risks`, `z2_sanctions` |
| Existing groups that must be preserved untouched | `z2data`, `basic_info`, `ownership`, `financial`, `esg`, `cyber`, `certifications`, `preferred_sourcing`, `supplier_risk`, `z2`, `kharon`, `parts_summary`, `test_group`, `traceability` |

Consequence: adding properties to the DataModel **does not** show them on Company Details. The layout sections in 6.8 must be added as well.

### 6.2 Target structure **[Proposed]**

As requested in `Z2Text`, RapidRatings becomes a new top-level NESTED group on Company, next to `financial` and `kharon` (the Kharon group is the precedent):

```text
Z2 Company (existing objectType 144)
├── company_name, company_tier, owners          (existing, unchanged)
├── Basic Information  basic_info  (existing NESTED)
├── Financial          financial   (existing NESTED: Moody's Credit Rating, D&B Rating)
├── Kharon Risk Data   kharon      (existing NESTED)
├── … all other existing groups unchanged
└── RapidRatings       rapidratings  (NEW NESTED)
    ├── Profile:   rrid, source_company_name, source_country, ticker, exchange, sector, address
    ├── URLs:      company_url, financials_url, periods_url, ratings_url
    ├── Data:      currency, data_scale, data_confidentiality,
    │              industry_classification_system, industry_classification_code,
    │              external_source_id, external_source_ids
    ├── Reports:   report_link_fhr, report_link_financial_dialog, report_link_peer_benchmark
    ├── Benchmark: sector_average_fhr, sector_average_chs
    ├── Current:   current_rating_id, current_fhr, current_chs,
    │              current_probability_default, current_financial_date   (from latestRatings[0])
    ├── Sync:      last_synced_at
    ├── Latest Ratings  latest_ratings  (PROPS_ARRAY)  ← company.latestRatings (newest 5)
    ├── Periods         periods         (PROPS_ARRAY)  ← /companies/{rrid}/periods/ (full history)
    └── Ratings         ratings         (PROPS_ARRAY)  ← /companies/{rrid}/ratings/ (full history)
```

Design choices:

| Choice | Reason |
|---|---|
| Group at Company root, not inside `financial` | Matches the requirement and the Kharon precedent; keeps provider data separately owned and removable. |
| `current_*` scalars | `PROPS_ARRAY` children are not searchable/filterable in the model (`kharon.risks` precedent). Scalars let users filter and sort companies by current FHR/CHS, exactly like Kharon's summary scalars (`max_risk_score`, `total_risks_count`). |
| `termPd` flattened to `term_pd_year_2 … term_pd_year_10` (DOUBLE) | No NESTED-inside-PROPS_ARRAY example exists in the model, and flat columns render directly in the table. |
| `latest_ratings` kept as its own array | Explicitly requested. It is a convenience snapshot (newest 5) and is **replaced wholesale** on every sync; the full, keyed history is in `ratings`. The two are never merged. |
| URLs as `STRING` with `uiHints.displayType: "LINK"` | No URI type exists; same pattern as existing link properties. |
| Provider values preserved | `source_country` is not written into `basic_info.country` (an LOV), `source_company_name` never overwrites `company_name`. Copying into canonical fields is a separate, opt-in enrichment rule. |

### 6.3 Type conversion rules

| Source type | Z2 `dataType` | Rule |
|---|---|---|
| string | `STRING` | As is. Empty string stays `""`. |
| uri | `STRING` + `uiHints.displayType: "LINK"` (`type: "EXTERNAL"`, `newTab: true`) | As is. |
| integer | `INTEGER` | Reject non-integers. |
| number | `DOUBLE` | Parse `1575.0`-style values as numbers. Keep sign and full precision. |
| date (`YYYY-MM-DD`) | `DATE` + `uiHints.displayType: "DATE"` | No time-zone shift. |
| null | (target type of the field) | Store `null`. **Never** convert to `0` or `""`. |
| `termPd` object | 9 × `DOUBLE` | `termPd.yearN` → `term_pd_year_N`. |
| array of objects | `PROPS_ARRAY` | One element per item, keyed (6.6). |
| `externalSourceIds` | `STRING_ARRAY` | **[Confirm]** element type with RapidRatings. |
| `leverage`, `liquidity`, `earningsPerformance` | `STRING` | Keep provider text. Upgrade to `ENUM` with badge `uiHints` (existing ENUM pattern) only after the allowed values are confirmed **[Confirm]**. |

No currency conversion and no rescaling: amounts stay in `currency` at `data_scale` (e.g. millions), which are stored on the same record.

### 6.4 Company profile mapping

| Source path | Target path (`company.`) | Label | dataType | Rule |
|---|---|---|---|---|
| `id` | `rapidratings.rrid` | RRID | STRING | Required when the group is populated; searchable, filterable; identity key |
| `name` | `rapidratings.source_company_name` | Source Company Name | STRING | Provider name; never overwrites `company_name` |
| `country` | `rapidratings.source_country` | Source Country | STRING | ISO-3 as received |
| `ticker` | `rapidratings.ticker` | Ticker | STRING | Filterable |
| `exchange` | `rapidratings.exchange` | Exchange | STRING | |
| `sector` | `rapidratings.sector` | Sector | STRING | Filterable |
| `address` | `rapidratings.address` | Source Address | TEXT | `""` preserved; does not touch `basic_info` |
| `href` | `rapidratings.company_url` | Company URL | STRING (LINK) | |
| `financials` | `rapidratings.financials_url` | Financials URL | STRING (LINK) | |
| `periods` | `rapidratings.periods_url` | Periods URL | STRING (LINK) | |
| `ratings` | `rapidratings.ratings_url` | Ratings URL | STRING (LINK) | |
| `currency` | `rapidratings.currency` | Currency | STRING | |
| `dataScale` | `rapidratings.data_scale` | Data Scale | STRING | Shown next to every amount table |
| `dataConfidential` | `rapidratings.data_confidentiality` | Data Confidentiality | STRING | Not converted to boolean |
| `industryClassificationSystem` | `rapidratings.industry_classification_system` | Industry Classification System | STRING | Nullable |
| `industryClassificationCode` | `rapidratings.industry_classification_code` | Industry Classification Code | STRING | Nullable |
| `externalSourceId` | `rapidratings.external_source_id` | External Source ID | STRING | Nullable; also a matching input (6.7) |
| `externalSourceIds` | `rapidratings.external_source_ids` | External Source IDs | STRING_ARRAY | **[Confirm]** element type |
| `reportLinks.fhr` | `rapidratings.report_link_fhr` | FHR Report | STRING (LINK) | |
| `reportLinks.financialDialog` | `rapidratings.report_link_financial_dialog` | Financial Dialogue Report | STRING (LINK) | |
| `reportLinks.peerBenchmark` | `rapidratings.report_link_peer_benchmark` | Peer Benchmark Report | STRING (LINK) | |
| `sectorAverages.fhr` (detail) | `rapidratings.sector_average_fhr` | Sector Average FHR | DOUBLE | Benchmark, not the company's score |
| `sectorAverages.chs` (detail) | `rapidratings.sector_average_chs` | Sector Average CHS | DOUBLE | |
| `latestRatings[0].id` | `rapidratings.current_rating_id` | Current Rating Period | STRING | Derived; filterable |
| `latestRatings[0].fhr` | `rapidratings.current_fhr` | Current FHR | INTEGER | Derived; filterable |
| `latestRatings[0].chs` | `rapidratings.current_chs` | Current CHS | INTEGER | Derived; filterable |
| `latestRatings[0].probabilityDefault` | `rapidratings.current_probability_default` | Current PD (%) | DOUBLE | Derived |
| `latestRatings[0].financialDate` | `rapidratings.current_financial_date` | Current Financial Date | DATE | Derived |
| (sync) | `rapidratings.last_synced_at` | Last Synced | TIMESTAMP | Set by the sync, like `kharon.last_modified` |
| `reports[]` (detail) | – | – | – | **Not mapped**: API download links require API authentication and are useless to users; `reportLinks` covers user access. |
| `latestRatings` | `rapidratings.latest_ratings` | Latest Ratings | PROPS_ARRAY | 6.5 |
| (periods endpoint) | `rapidratings.periods` | Periods | PROPS_ARRAY | 6.5 |
| (ratings endpoint) | `rapidratings.ratings` | Ratings | PROPS_ARRAY | 6.5 |

### 6.5 Array mappings

**`rapidratings.periods` (PROPS_ARRAY)** — source `GET /companies/{rrid}/periods/`, every page:

| Source | Target child (`rapidratings.periods.`) | Label | dataType | Required |
|---|---|---|---|---|
| `id` | `period_id` | Period ID | STRING | **yes** (element key) |
| `eqyYear` | `eqy_year` | EQY Year | INTEGER | |
| `period` | `period` | Period | STRING | |
| `financialDate` | `financial_date` | Financial Date | DATE | |
| `currency` | `currency` | Currency | STRING | |
| `href` | `period_url` | Period URL | STRING (LINK) | |
| `salesRevenue` | `sales_revenue` | Sales Revenue | DOUBLE | |
| `earningsBeforeInterestAndTax` | `earnings_before_interest_and_tax` | EBIT | DOUBLE | |
| `interestExpense` | `interest_expense` | Interest Expense | DOUBLE | |
| `interestReceived` | `interest_received` | Interest Received | DOUBLE | |
| `companyTaxExpense` | `company_tax_expense` | Company Tax Expense | DOUBLE | |
| `abnormalItems` | `abnormal_items` | Abnormal Items | DOUBLE | |
| `netProfitAfterTax` | `net_profit_after_tax` | Net Profit After Tax | DOUBLE | |
| `bankCashBalances` | `bank_cash_balances` | Bank Cash Balances | DOUBLE | |
| `totalCurrentAssets` | `total_current_assets` | Total Current Assets | DOUBLE | |
| `fixedAssets` | `fixed_assets` | Fixed Assets | DOUBLE | |
| `financialAssets` | `financial_assets` | Financial Assets | DOUBLE | |
| `totalAssets` | `total_assets` | Total Assets | DOUBLE | |
| `debitOwnedWithinOneYear` | `debit_owned_within_one_year` | Debt Due Within One Year | DOUBLE | |
| `totalCurrentLiabilities` | `total_current_liabilities` | Total Current Liabilities | DOUBLE | |
| `termLoans` | `term_loans` | Term Loans | DOUBLE | |
| `totalLiabilities` | `total_liabilities` | Total Liabilities | DOUBLE | |
| `totalShareholderEquity` | `total_shareholder_equity` | Total Shareholder Equity | DOUBLE | |
| `cashFromOperationsFromCashFlow` | `cash_from_operations_from_cash_flow` | Cash From Operations | DOUBLE | |
| `netInvestingCashFlow` | `net_investing_cash_flow` | Net Investing Cash Flow | DOUBLE | |
| `netFundingCashFlow` | `net_funding_cash_flow` | Net Funding Cash Flow | DOUBLE | |

**`rapidratings.ratings` (PROPS_ARRAY)** — source `GET /companies/{rrid}/ratings/`, every page; **`rapidratings.latest_ratings` (PROPS_ARRAY)** — source `company.latestRatings`. Both use the same children:

| Source | Target child | Label | dataType | Required |
|---|---|---|---|---|
| `id` | `rating_id` | Rating Period ID | STRING | **yes** (element key; equals the period ID) |
| `eqyYear` | `eqy_year` | EQY Year | INTEGER | |
| `period` | `period` | Period | STRING | |
| `financialDate` | `financial_date` | Financial Date | DATE | |
| `reportingPeriod` | `reporting_period` | Reporting Period | DATE | |
| `href` | `rating_url` | Rating URL | STRING (LINK) | |
| `fhr` | `fhr` | FHR | INTEGER | |
| `chs` | `chs` | CHS | INTEGER | |
| `probabilityDefault` | `probability_default` | Probability of Default (%) | DOUBLE | |
| `termPd.year2` … `termPd.year10` | `term_pd_year_2` … `term_pd_year_10` | Term PD Year 2 … Year 10 (%) | DOUBLE × 9 | |
| `delta` | `delta` | FHR Delta | INTEGER | |
| `simulatedFhr` | `simulated_fhr` | Simulated FHR | INTEGER | |
| `simulatedFhrDelta` | `simulated_fhr_delta` | Simulated FHR Delta | INTEGER | |
| `operatingProfitability` | `operating_profitability` | Operating Profitability | INTEGER | |
| `netProfitability` | `net_profitability` | Net Profitability | INTEGER | |
| `capitalStructureEfficiency` | `capital_structure_efficiency` | Capital Structure Efficiency | INTEGER | |
| `costStructureEfficiency` | `cost_structure_efficiency` | Cost Structure Efficiency | INTEGER | |
| `leverage` | `leverage` | Leverage | STRING | |
| `liquidity` | `liquidity` | Liquidity | STRING | |
| `earningsPerformance` | `earnings_performance` | Earnings Performance | STRING | |

Every source property in `Z2Text` is mapped; the only unmapped source field is `reports[]`, for the reason given in 6.4.

### 6.6 Property definitions in the platform format

Each new property uses exactly the shape already in `Z2company.json`. `id`, `dataEnvId`, `createdDate`, `modifiedDate` are assigned by the platform and must not be invented. `dataModelType` / `extDataModelId` decide which layer owns the group; Kharon uses `CORE` / `2278`, the Z2 Data application uses `APPLICATION` / `431`. Which layer RapidRatings belongs to is a platform decision **[Confirm]**; the placeholder below marks it.

Group:

```json
{
  "name": "RapidRatings",
  "dbName": "rapidratings",
  "path": "rapidratings",
  "label": "RapidRatings",
  "description": "Financial health data about this company sourced from RapidRatings. Profile and current-rating scalars come from the company record; `periods` and `ratings` carry the full per-period history keyed by period ID.",
  "isPrimaryKey": false,
  "required": false,
  "searchable": false,
  "filterable": false,
  "dataType": "NESTED",
  "dataModelType": "<PLATFORM_LAYER>",
  "extDataModelId": "<PLATFORM_ASSIGNED>",
  "canUpload": false,
  "hidden": false,
  "isCalculated": false,
  "isAi": false,
  "config": {},
  "uiHints": {},
  "properties": [ "...children below..." ]
}
```

Scalar child (RRID):

```json
{
  "name": "RRID", "dbName": "rrid", "path": "rapidratings.rrid", "label": "RRID",
  "description": "RapidRatings company identifier (e.g. `KawjbGJ`). Stable key used for every RapidRatings API call and for sync upserts.",
  "isPrimaryKey": false, "required": false, "searchable": true, "filterable": true,
  "dataType": "STRING", "dataModelType": "<PLATFORM_LAYER>", "extDataModelId": "<PLATFORM_ASSIGNED>",
  "canUpload": false, "hidden": false, "properties": [], "isCalculated": false, "isAi": false,
  "config": {}, "uiHints": {}
}
```

Link child (URL):

```json
{
  "name": "FHR Report", "dbName": "report_link_fhr", "path": "rapidratings.report_link_fhr", "label": "FHR Report",
  "description": "RapidRatings portal link to the Financial Health Rating report.",
  "dataType": "STRING", "searchable": false, "filterable": false, "required": false,
  "uiHints": { "displayType": "LINK", "link": { "href": "{value}", "type": "EXTERNAL", "color": "blue", "newTab": true } },
  "...": "remaining standard keys as above"
}
```

The `href` placeholder for a link that points at the property's own value must follow the platform's link template convention **[Confirm]**; the existing examples template on `{id}`.

Array with key child:

```json
{
  "name": "Periods", "dbName": "periods", "path": "rapidratings.periods", "label": "Periods",
  "description": "Financial statement figures per reporting period from RapidRatings, newest first. One element per period ID. Amounts are in `rapidratings.currency` at `rapidratings.data_scale`.",
  "dataType": "PROPS_ARRAY", "searchable": false, "filterable": false, "required": false,
  "properties": [
    { "name": "Period ID", "dbName": "period_id", "path": "rapidratings.periods.period_id", "label": "Period ID",
      "description": "RapidRatings period ID (`eqyYear` + `period`, e.g. `2020YE`). Element key.",
      "dataType": "STRING", "required": true, "searchable": true, "filterable": true },
    { "name": "Financial Date", "dbName": "financial_date", "path": "rapidratings.periods.financial_date", "label": "Financial Date",
      "dataType": "DATE", "uiHints": { "displayType": "DATE" } },
    { "name": "Sales Revenue", "dbName": "sales_revenue", "path": "rapidratings.periods.sales_revenue", "label": "Sales Revenue",
      "dataType": "DOUBLE" }
  ],
  "...": "remaining standard keys as above; one child per row of the 6.5 table"
}
```

Extension rules:

1. Add the group; **do not** change any existing property, ID, path or layout section.
2. Keep `name`, `dbName`, `label`, `path` consistent; child `path` = parent `path` + `.` + child `dbName`.
3. `canUpload: false` on the whole group so only the integration writes it **[Confirm]** that this blocks manual edits in the UI.
4. Apply through the platform's DataModel API/migration mechanism, then validate the result against the deployed runtime before enabling the integration for a tenant.

### 6.7 Company matching (RRID → Z2 Company)

The RapidRatings company must be attached to the right Z2 Company record. Matching order **[Proposed]**:

1. **Existing link:** a Z2 Company whose `rapidratings.rrid` equals the RRID (or an `ExternalIdentityMapping (tenantId, provider, rrid) → companyId` row).
2. **Customer key:** if the tenant configures it, `externalSourceId` from RapidRatings equals a chosen Z2 identifier (e.g. an ERP/vendor ID property) **[Confirm]** which Z2 property holds that ID.
3. **Suggested candidates** for manual review using name, country, ticker; shown to the user, never applied automatically.
4. **Unmatched list:** the user links to an existing Company or explicitly creates a new one.

Constraints: one RRID links to at most one Company per tenant; the link and every manual decision are written to change history; a company is never linked by fuzzy name alone.

### 6.8 Company Details layout **[Proposed]**

Following the Kharon sections exactly (`kharon` = fields, `kharon_risks` = one-column table), add four sections to the `details` tab after `kharon`:

```json
[
  { "id": "rapidratings", "title": "RapidRatings", "preset": "single",
    "columns": [[ { "id": "rapidratings-fields", "kind": "fields", "columns": 2, "bindings": [
      { "id": "rapidratings_rrid", "path": [], "label": "RRID", "target": { "kind": "objectProperty", "dbName": "rapidratings.rrid" } },
      { "id": "rapidratings_source_company_name", "path": [], "label": "Source Company Name", "target": { "kind": "objectProperty", "dbName": "rapidratings.source_company_name" } },
      { "id": "rapidratings_current_fhr", "path": [], "label": "Current FHR", "target": { "kind": "objectProperty", "dbName": "rapidratings.current_fhr" } },
      { "id": "rapidratings_current_chs", "path": [], "label": "Current CHS", "target": { "kind": "objectProperty", "dbName": "rapidratings.current_chs" } },
      { "id": "rapidratings_source_country", "path": [], "label": "Source Country", "target": { "kind": "objectProperty", "dbName": "rapidratings.source_country" } },
      { "id": "rapidratings_sector", "path": [], "label": "Sector", "target": { "kind": "objectProperty", "dbName": "rapidratings.sector" } },
      { "id": "rapidratings_ticker", "path": [], "label": "Ticker", "target": { "kind": "objectProperty", "dbName": "rapidratings.ticker" } },
      { "id": "rapidratings_exchange", "path": [], "label": "Exchange", "target": { "kind": "objectProperty", "dbName": "rapidratings.exchange" } },
      { "id": "rapidratings_currency", "path": [], "label": "Currency", "target": { "kind": "objectProperty", "dbName": "rapidratings.currency" } },
      { "id": "rapidratings_data_scale", "path": [], "label": "Data Scale", "target": { "kind": "objectProperty", "dbName": "rapidratings.data_scale" } },
      { "id": "rapidratings_report_link_fhr", "path": [], "label": "FHR Report", "target": { "kind": "objectProperty", "dbName": "rapidratings.report_link_fhr" } },
      { "id": "rapidratings_last_synced_at", "path": [], "label": "Last Synced", "target": { "kind": "objectProperty", "dbName": "rapidratings.last_synced_at" } }
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

The remaining profile scalars are added as further bindings in the first section. Alternative: put the same four sections in a dedicated `RapidRatings` tab (same `tabs[]` shape as the existing tabs) if the Details tab becomes too long.

To verify in the UI runtime **[Confirm]**: how the array table renders ~30 columns (Periods) and 27 rows (column selection, horizontal scroll, sort by period), and how `null` is shown (should be blank or "—", never `0`). Raw Properties shows the same group and arrays automatically, within the renderer's supported types.

### 6.9 Mapping validation (before a mapping version can be activated)

- Every source path exists in the active source schema version; every target path exists in the deployed Company DataModel.
- Source and target types are compatible per 6.3, or have an explicit transformation.
- Array key children (`period_id`, `rating_id`) are mapped; `rrid` is mapped.
- `termPd.year2 … year10` are all mapped individually.
- No two source fields map to the same target without an approved rule.
- New source fields found by schema sync are reported as unmapped (drift), never silently dropped.
- The mapping version and DataModel version are recorded on every sync run.

---

## 7. Syncing of Schema and Data

Schema sync and data sync are **separate operations**. A schema sync never writes Company data; a data sync never changes the schema.

### 7.1 Schema synchronization

Trigger: on connection activation, on demand (**Refresh schema**), and automatically at the start of each data sync as a lightweight drift check.

1. Authenticate (Section 3).
2. Sample: first page of companies, one company detail, first page of periods and of ratings for that company.
3. Infer a schema snapshot: every field path (nested paths such as `termPd.year2`, array items as `latestRatings[]`), observed JSON types, nullability, "seen in N of M samples". Treat `1575.0` as a number; a field seen only as `null` keeps its documented type (Section 5) rather than a guessed one.
4. Compare with the active registered version: **added**, **removed**, **type changed**, **nullability changed**.
5. Re-validate the active mapping against the new snapshot (6.9).
6. Result:
   - no change → record "checked, unchanged";
   - additive change (new field) → save a new version as `PENDING_REVIEW`, notify the admin, data sync continues;
   - breaking change (mapped field removed or type changed) → save as `PENDING_REVIEW` and **block** data sync for the affected resource until the mapping is updated and approved.
7. Write a change-history entry for every new schema version.

Schema snapshot record (illustrative):

```json
{
  "schemaVersionId": "sv-0004",
  "provider": "RAPIDRATINGS", "resource": "periods", "origin": "INFERRED",
  "environment": "sandbox", "discoveredAt": "2026-10-30T09:00:00Z", "status": "ACTIVE",
  "fields": [
    { "path": "id", "types": ["string"], "nullable": false, "seenIn": "10/10" },
    { "path": "interestReceived", "types": ["number"], "nullable": true, "seenIn": "10/10" }
  ],
  "diffFromPrevious": { "added": [], "removed": [], "typeChanged": [] }
}
```

### 7.2 Data synchronization pipeline

```text
Start run ─▶ Auth ─▶ Resolve scope ─▶ List companies ─▶ for each company (bounded parallelism):
   detail ─▶ match to Z2 Company ─▶ periods (all pages) ─▶ ratings (all pages)
   ─▶ validate ─▶ map ─▶ upsert ─▶ record per-company outcome
─▶ finalise run (counts, status) ─▶ write sync history
```

1. Create a `SyncRun` (status `QUEUED` → `RUNNING`) with tenant, trigger, scope, config/schema/mapping/DataModel versions.
2. Get or reuse the token.
3. Resolve scope: all visible companies, portfolio IDs, or explicit RRIDs.
4. List companies (`/companies/?portfolio=…`), following `next`.
5. For each company:
   1. Read `/companies/{rrid}/` (detail adds `sectorAverages`).
   2. Match to a Z2 Company (6.7). Unmatched → record as `UNMATCHED`, skip the rest for this company.
   3. Read all periods, following `next`.
   4. Read all ratings, following `next`.
   5. Validate: each period/rating `href` contains the same RRID; `id` present; types match the schema; ratings and periods agree on `financialDate` for the same ID (mismatch is reported, not "fixed").
   6. Map with the active mapping version.
   7. Upsert (7.3).
   8. Record the outcome: `CREATED`, `UPDATED`, `UNCHANGED`, `UNMATCHED`, `FAILED` (with sanitized reason).
6. Finalise: `SUCCEEDED`, `PARTIALLY_SUCCEEDED` (some companies failed), `FAILED` (auth/schema block/nothing processed) or `CANCELLED`.

API call budget **[Verified]** volumes: per company 1 detail + ⌈periods/10⌉ + ⌈ratings/10⌉ ≈ 1 + 3 + 3 = 7 calls; sandbox scope of 96 companies ≈ 5 list pages + 672 calls. Sandbox rate limit (30,000,000) is not a constraint; still honour `Retry-After` and use bounded parallelism (e.g. 4 companies at a time) **[Proposed]**.

### 7.3 Upsert and identity rules

| Record | Key | Behaviour |
|---|---|---|
| Company link | `(tenantId, provider=RAPIDRATINGS, rrid)` | One Z2 Company per RRID per tenant. |
| Profile scalars | Company record | Overwrite with the latest values; `last_synced_at` = run time. |
| `periods[]` element | `(rrid, period_id)` | Insert new IDs; update changed values of existing IDs; **never delete** an element because it is missing from a response (history is kept). |
| `ratings[]` element | `(rrid, rating_id)` | Same as periods. |
| `latest_ratings[]` | whole array | Replaced with the company's current `latestRatings` on every successful company read (it is a snapshot). |
| `current_*` scalars | Company record | Recomputed from `latestRatings[0]`. |

- Never join or associate by company name, array position or response order; a rating is attached to a period only when **RRID and period ID both match**.
- Re-running the same sync produces no duplicates and, if nothing changed, the outcome `UNCHANGED` (compare a hash of the mapped record to skip no-op writes).
- Element order in stored arrays: newest first by `eqy_year`, then `period` (`YE` > `Q3` > `Q2` > `Q1`).
- If the provider ever returns more than one rating per period ID, the run reports it as a data error **[Confirm]** one-rating-per-period in production.
- Writing into the Company record uses the platform's record update API with optimistic locking, so a concurrent user edit elsewhere on the Company record is not lost **[Confirm]** the API.

### 7.4 Incremental strategy (no delta filter exists **[Verified]**)

1. **First sync / weekly reconciliation:** full read as in 7.2.
2. **Regular sync:** read the company list (cheap: 20 per call); for each company compare `latestRatings[0].id` and `fhr` with the stored `current_rating_id` / `current_fhr`.
   - unchanged → refresh profile scalars only;
   - changed or new → read periods and ratings **page 1** (newest 10) and continue to the next page only while unseen period IDs appear.
3. Ask RapidRatings whether production offers a changed-since mechanism **[Confirm]**.

### 7.5 Data quality rules

- `null` stays `null`; no defaults are invented for missing ratings or amounts.
- Amounts keep their sign, currency and scale.
- A record missing `rrid` or a period/rating ID is rejected and reported.
- A failure for one company does not stop the others; the run becomes `PARTIALLY_SUCCEEDED`.
- Raw provider payloads are not stored beyond the run (only the mapped record), pending the retention decision **[Confirm]**.

---

## 8. Manual and Scheduled Syncs

Both triggers enqueue **the same job** and run **the same pipeline** (Section 7.2). There is no second code path.

### 8.1 Manual sync

1. An authorised user (integration admin) opens **Data Integrations → RapidRatings** and clicks **Sync now**.
2. The user picks the scope: all in-scope companies (default), one portfolio, or one company (also available as **Sync this company** on Company Details).
3. The backend checks: integration `ACTIVE`, an approved mapping exists, no breaking schema change pending, and no run already active for an overlapping scope (if one is, it returns that run instead of starting another).
4. It creates `SyncRun {triggerType: MANUAL, requestedBy: userId}` and enqueues the job.
5. The UI polls `GET …/sync-runs/{runId}` and shows progress (`companies processed / total`), then the final counts and per-company errors.
6. A failed run can be **retried**; the retry is a new run with `retryOf` pointing to the original.

### 8.2 Scheduled sync

| Setting | Values **[Proposed]**, subject to the platform scheduler **[Confirm]** |
|---|---|
| Enabled | on / off |
| Frequency | Daily, Weekly (day of week), Monthly (day of month), or a cron expression if the platform supports it |
| Time and time zone | e.g. 02:00 `Europe/London`; evaluated with DST rules |
| Scope | All / selected portfolios / selected companies |
| Mode | Regular (7.4 step 2) or Full; recommended: daily regular + weekly full |
| Retry policy | Max attempts (e.g. 3) and backoff for transient failures only |
| Next run / last run | Computed and shown read-only |

Recommendation: **daily** regular sync. Ratings are produced per reporting period (`reportingPeriod` dates), so sub-daily schedules add API load without new data.

Scheduler behaviour:

- The platform scheduler/queue creates `SyncRun {triggerType: SCHEDULED}`; never a browser timer or a long-lived HTTP request.
- One occurrence enqueues at most one run (idempotency key = schedule ID + occurrence time).
- **Overlap:** if the previous run is still active, the new occurrence is skipped and recorded as `SKIPPED_OVERLAP`.
- **Missed runs** (platform down): run once at next start-up, not once per missed occurrence.
- **Disabled integration or `AUTH_FAILED`:** occurrences are recorded as `SKIPPED` with the reason; no retries against bad credentials.
- Permanent errors (auth, permission, schema block, mapping invalid) are not retried; transient errors (timeouts, 429, 5xx other than out-of-range pages) are retried with bounded backoff.

---

## 9. Sync Schedule and Change History

Two separate histories: **sync-run history** (what ran) and **change history** (who changed what configuration).

### 9.1 Sync-run history

| Field | Description |
|---|---|
| `runId` | Unique run ID |
| `tenantId`, `integrationId` | Scope |
| `triggerType` | `MANUAL`, `SCHEDULED`, `RETRY` |
| `requestedBy` | User ID (manual) or schedule ID |
| `status` | `QUEUED`, `RUNNING`, `SUCCEEDED`, `PARTIALLY_SUCCEEDED`, `FAILED`, `CANCELLED`, `SKIPPED`, `SKIPPED_OVERLAP` |
| `startedAt`, `completedAt`, `durationMs` | UTC |
| `scope` | All / portfolio IDs / RRIDs |
| `mode` | `FULL` or `REGULAR` |
| `configVersion`, `sourceSchemaVersion`, `mappingVersion`, `dataModelVersion` | Versions used |
| `counts` | `companiesListed`, `matched`, `unmatched`, `created`, `updated`, `unchanged`, `failed`, `periodsWritten`, `ratingsWritten` |
| `apiCalls`, `retries` | Operational metrics |
| `retryOf` | Original run, for retries |
| `errorSummary` | Sanitized, actionable; never secrets, tokens or raw payloads |
| `correlationId` | Trace ID across backend logs |

Per-company outcomes are stored as child rows: `runId, rrid, companyId, outcome, reason`.

Example:

```json
{
  "runId": "run-2026-10-30-0001", "triggerType": "SCHEDULED", "status": "PARTIALLY_SUCCEEDED",
  "startedAt": "2026-10-30T02:00:00Z", "completedAt": "2026-10-30T02:03:41Z",
  "scope": { "portfolios": [27070] }, "mode": "REGULAR",
  "mappingVersion": "m-3", "sourceSchemaVersion": "sv-0004",
  "counts": { "companiesListed": 96, "matched": 90, "unmatched": 6, "updated": 12, "unchanged": 77, "failed": 1 },
  "errorSummary": "1 company failed: ratings request timed out after 2 retries (rrid XAZPzdJ)."
}
```

UI: list filtered by date range, trigger, status; run detail with per-company outcomes; **Retry** action.

### 9.2 Sync schedule history

Every change to the schedule creates a new **schedule version** and a change-history entry: enabled state, frequency/cron, time, time zone, scope, mode, retry policy. The schedule screen shows the current version, who changed it and when, next run and last run outcome.

### 9.3 Integration change history (audit)

Recorded actions: integration enabled/disabled; environment changed; credentials rotated (event only, never values); connection tested; schema version discovered/approved/rejected; mapping created/edited/validated/approved/activated; company manually linked/unlinked; schedule changed; manual sync started; DataModel extension applied.

| Field | Description |
|---|---|
| `changeId` | Unique ID |
| `tenantId`, `integrationId` | Scope |
| `actor` | User ID, or `SYSTEM` for automatic actions |
| `action` | e.g. `SCHEDULE_UPDATED`, `MAPPING_ACTIVATED`, `CREDENTIALS_ROTATED` |
| `entity` | Changed object (schedule, mapping, credential, company link…) |
| `before`, `after` | Changed values where safe; secrets are recorded as `"***changed***"` |
| `version` | Resulting config/schedule/mapping/schema version |
| `timestamp` | UTC |
| `correlationId` / `runId` | Where relevant |
| `comment` | Optional reason |

Change history is **append-only**; users can read it, nobody can edit or delete entries.

---

## 10. Platform Components, Entities and APIs **[Proposed]**

### 10.1 Configuration entities

| Entity | Key fields |
|---|---|
| `IntegrationDefinition` | `provider=RAPIDRATINGS`, display name, adapter version, capabilities (auth, schema, mapping, sync, schedule) |
| `TenantIntegration` | tenantId, provider, environment, base URLs, `credentialRef`, status, scope (portfolios / RRIDs), `configVersion` |
| `SourceSchemaVersion` | resource, origin (`INFERRED`/`DECLARED`), fields, status, discoveredAt |
| `FieldMapping` (versioned) | sourcePath, targetPath, transformation, required, status (`DRAFT`/`APPROVED`/`ACTIVE`) |
| `ExternalIdentityMapping` | tenantId, provider, rrid, companyId, linkedBy, linkedAt |
| `SyncSchedule` (versioned) | enabled, frequency, time, timeZone, scope, mode, retryPolicy, nextRunAt |
| `SyncRun` + `SyncRunCompanyResult` | Section 9.1 |
| `IntegrationChangeHistory` | Section 9.3 |

### 10.2 Platform API (called by the Z2 UI; only the backend calls RapidRatings)

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/api/integrations` | Catalogue with per-tenant status |
| `PUT` | `/api/integrations/rapidratings/connection` | Save environment + credentials (write-only secret) |
| `POST` | `/api/integrations/rapidratings/connection/test` | Test connection (Section 3.2) |
| `PATCH` | `/api/integrations/rapidratings` | Enable / disable, scope |
| `GET` | `/api/integrations/rapidratings/portfolios` | Portfolios for scope selection |
| `POST` | `/api/integrations/rapidratings/schema/refresh` | Run schema sync |
| `GET` | `/api/integrations/rapidratings/schema/versions[/{id}/diff]` | Versions and diff |
| `GET`/`PUT` | `/api/integrations/rapidratings/mappings` | Read / edit draft mapping |
| `POST` | `/api/integrations/rapidratings/mappings/{version}/validate` · `/activate` | Validate, activate |
| `GET`/`POST` | `/api/integrations/rapidratings/matches` | Unmatched list; link / unlink a company |
| `POST` | `/api/integrations/rapidratings/sync-runs` | Sync now (scope in body) |
| `GET` | `/api/integrations/rapidratings/sync-runs[/{runId}]` | History, detail, progress |
| `POST` | `/api/integrations/rapidratings/sync-runs/{runId}/retry` | Retry |
| `GET`/`PUT` | `/api/integrations/rapidratings/schedule` | Read / update schedule |
| `GET` | `/api/integrations/rapidratings/change-history` | Audit log |

Routes are illustrative; align naming and authorization with Z2 platform conventions. All routes are tenant-scoped and require the integration-admin permission except read-only history.

---

## 11. Error Handling

| Situation | Behaviour |
|---|---|
| Token request 400/401 | `AUTH_FAILED`; no retry; admin notified |
| Data call 403 (rridata) / 401 (outreach) | Renew token, retry once; second failure = `PERMISSION_FAILED` |
| Data call 401 on rridata | Adapter defect (header missing); fail the run |
| 400 `Not valid query parameter` | Adapter/config defect; do not retry; report the parameter |
| 404 on a detail URL | Check trailing slash; otherwise unknown RRID/period → company outcome `FAILED` |
| 404/500 on a page past the end | Prevented by following `next`; never retried |
| 429 | Wait `Retry-After` (capped), bounded retries |
| Timeout / 502 / 503 / 504 | Up to 2 retries with exponential backoff |
| Unexpected JSON / type | Fail that company; record schema drift |
| RRID in `href` differs from requested RRID | Reject the record; data-quality error |
| Period/rating `financialDate` disagree for one ID | Store both as received; report a warning |
| Unmatched company | `UNMATCHED`, listed for review; nothing written |
| Breaking schema change | Block affected data syncs until mapping re-approved |
| Mapping invalid for the deployed DataModel | Block sync; show validation errors |

---

## 12. Security

- Client secret only in the secret manager; the database holds a reference. Tokens only in server memory.
- No RapidRatings call from the browser (also technically impossible: no CORS on rridata **[Verified]**).
- Secrets, Basic headers, tokens and raw payloads never appear in logs, traces, sync history, change history or API responses.
- Tenant isolation: every entity, cache entry, job and query is keyed by `tenantId`; sandbox and production credentials are separate.
- Only integration admins can change credentials, mapping, schedule or start syncs; changes are audited (9.3).
- Outreach is excluded from v1: creating an outreach request sends **real emails** to the counterparty, even from the sandbox **[Verified]**.

---

## 13. Open Questions **[Confirm]**

| # | Area | Question |
|---|---|---|
| 1 | DataModel ownership | Which `dataModelType` / `extDataModelId` layer owns `rapidratings` (Kharon uses `CORE`/2278)? |
| 2 | DataModel UI | Does `canUpload: false` make the group read-only in the UI? How does the `PROPS_ARRAY` table render ~30 columns and how is `null` displayed? |
| 3 | Link template | Correct `uiHints.link.href` template for a property whose value is the URL. |
| 4 | Record write API | Platform API for updating a Company record (and its arrays) from a backend job, with optimistic locking. |
| 5 | Matching | Which Z2 property holds a customer/ERP ID comparable to RapidRatings `externalSourceId`? |
| 6 | Scheduler | Supported frequencies, cron support, time-zone/DST handling, queue API. |
| 7 | Production API | Same paging, page sizes, errors, token lifetime and scopes as the sandbox? Any changed-since mechanism? |
| 8 | Rating semantics | Official definitions for FHR, CHS, PD, term PD; allowed values of leverage/liquidity/earningsPerformance (for ENUM). |
| 9 | Units | Can `dataScale` differ between a company's periods? |
| 10 | `externalSourceIds` | Element type. |
| 11 | `financials` link | Does `/companies/{rrid}/financials/` add data beyond `periods/`? (not exercised) |
| 12 | Private companies | `dataConfidential` values for private companies and any display restrictions. |
| 13 | Retention | Contractual limits on storing ratings history, report links and raw payloads. |
| 14 | One rating per period | Guaranteed in production? |

---

## 14. Implementation Plan

| Phase | Deliverables | Done when |
|---|---|---|
| **1. Foundation & authentication** | `IntegrationDefinition`, `TenantIntegration`, secret-manager credential refs, token provider (cache, early renewal, single flight, one retry), Test connection, connection states, change history for config | An admin connects sandbox credentials and sees **Connected**; no secret/token in logs or responses |
| **2. Adapter & schema** | HTTP client (timeouts, retry, `Retry-After`, follow `next`), DTOs for company/detail/period/rating, schema inference, schema registry + diff, Refresh schema | Schema versions for companies, periods, ratings saved; a removed field shows as a breaking diff |
| **3. DataModel extension** | `rapidratings` group + 3 `PROPS_ARRAY`s added via platform mechanism (6.2–6.6); Company Details sections (6.8) | Group visible in Raw Properties; sections render on Company Details with test data |
| **4. Mapping & matching** | Versioned mapping with validation (6.9), default mapping = tables 6.4/6.5, matching service + unmatched review UI | Default mapping validates and activates; RRID links to a Company; unmatched list works |
| **5. Data sync + manual runs** | Orchestrator, queue job, upsert rules (7.3), incremental strategy (7.4), Sync now (company/portfolio/all), progress, run history, retry | Rite Aid `KawjbGJ` shows 27 periods and 27 ratings; a second run reports `UNCHANGED` and creates no duplicates |
| **6. Scheduling & audit** | Schedule config + versions, scheduler integration, overlap/missed-run handling, change-history UI | A daily schedule runs the same pipeline; schedule edits appear in change history |
| **7. Hardening** | Metrics/alerts (auth failures, run failures, stale `last_synced_at`), tenant-isolation tests, pilot tenant | Pilot tenant runs a week of scheduled syncs without manual fixes |

### 14.1 Acceptance tests

1. Connect with valid credentials → **Connected**; invalid credentials → `AUTH_FAILED`, no retry loop.
2. Token older than 29 minutes is renewed before use; a forced 403 triggers exactly one renewal + retry.
3. Schema refresh stores a version; adding a field in a fixture produces an additive diff; removing a mapped field blocks sync.
4. Default mapping validates; every `Z2Text` source property is mapped or explicitly excluded (`reports[]`).
5. Sync `KawjbGJ`: profile fields, `sector_average_*`, `current_*`, 5 latest ratings, 27 periods, 27 ratings stored; `interest_received` for 2020YE is `null`, `net_profit_after_tax` is `-469.219`.
6. Re-run → no duplicate elements; outcome `UNCHANGED`.
7. A rating whose `href` RRID differs from the company is rejected.
8. Period IDs are never joined across companies (Rite Aid 2019YE vs Tyson Foods 2019YE).
9. Manual and scheduled runs produce identical results for the same scope.
10. Overlapping scheduled occurrence → `SKIPPED_OVERLAP`.
11. Sync history shows trigger, status, timestamps, scope, counts, versions and sanitized errors.
12. Schedule and mapping changes appear in change history with actor and before/after values; credential rotation shows no values.
13. Tenant A cannot see tenant B's runs, mappings, tokens or data.

---

## Appendix A – Reference prototype in this repository

The React dashboard in this repository (`src/`) already exercises much of this design against the sandbox: a dev-only token proxy in `vite.config.ts` (client credentials, cache, renewal 60 s early, one retry on 401/403), per-resource services that follow `next`, schema discovery with declared vs inferred fields and snapshot diffs, a mapping screen with type-compatibility validation, and a manual sync with per-run history. It is a prototype only: no backend, no Z2 persistence, an illustrative target schema, no matching, no scheduler execution. Use it to see the API behaviour; implement the production integration in the Z2 backend as described above.

## Appendix B – Sources

- `docs/Best/Z2Text` – requirements, endpoints, raw property inventories, target tree.
- `docs/Best/Z2company.json` – existing Company DataModel (properties, types, conventions, Company Details layout).
- `docs/api/01-authentication.md` … `06-outreach-requests.md`, `docs/RapidRatings_API_Reference.md` – sandbox behaviour verified 2026-10-28.
- Live sandbox reads on 2026-10-30 for Rite Aid (`KawjbGJ`): company detail, period `2020YE`, periods and ratings lists.
