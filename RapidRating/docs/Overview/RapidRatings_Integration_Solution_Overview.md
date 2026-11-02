# RapidRatings Integration – Solution Overview

**Program:** Z2 Enterprise – Data Integrations  
**Integration:** RapidRatings  
**Target DataModel:** Existing Z2 `Company` DataModel (`A1company.json`)  
**Source environment investigated:** RapidRatings sandbox  
**Document status:** Proposed solution design based on the supplied API response samples and existing Company DataModel. Confirm open API-contract and platform-runtime questions before production rollout.  
**Last reviewed:** 2026-10-30, against the RapidRatings sandbox API behaviour verified on 2026-10-28 (`docs/api/`) and the reference prototype in this repository (Section 24). Statements marked *verified* were observed against the sandbox; they still need confirming against the contracted production API.

---

## 1. Executive Summary

This document describes the proposed end-to-end integration of RapidRatings with the Data Integrations capability in Z2 Enterprise. It covers connection authentication, source-schema discovery, mapping to the Z2 Company DataModel, schema and data synchronization, manual and scheduled execution, and synchronization/change history.

The integration allows a tenant administrator to connect a RapidRatings account, select the portfolios to monitor, discover companies, associate RapidRatings companies with existing Z2 companies/suppliers, retrieve reporting-period financial data and ratings, and synchronize the normalized information into Z2 Enterprise.

The design uses a reusable integration framework for common capabilities and a provider-specific RapidRatings adapter for API communication and source-specific transformation. Z2 remains responsible for tenant configuration, credential references, schema registry, mapping configuration, sync orchestration, scheduling, run history, and audit history. The RapidRatings adapter is responsible for authentication with RapidRatings, calling its APIs, parsing provider responses, and preparing source records for the platform mapping engine.

The target is the existing `Company` DataModel in `A1company.json`. The proposed extension groups RapidRatings data under the existing `Financial` nested group. It does not require a separate top-level Company object. Company-level information is stored once, while multiple period records are retained as repeatable period entries, with period financials and rating metrics associated by the stable source period identifier.

> **Sample identity note (resolved):** The supplied `Companies.json` example is Rite Aid Corporation (RRID `KawjbGJ`), while the supplied `PeriodById.json` and `GetRatingByPeriodId.json` examples belong to Tyson Foods, Inc. (RRID `XAZPzdJ`). Both are real sandbox companies, so the samples are internally consistent but describe two different companies. A matched set for one company (`XAZPzdJ`: company detail, period `2019YE`, rating `2019YE`) has since been retrieved and documented in `docs/api/`. The rule stands: the integration must validate the company RRID at every step and must never merge records merely because the period identifier (e.g. `2019YE`) matches.

## 2. Business Objectives

1. Allow a tenant to configure and validate a RapidRatings connection securely.
2. Discover source portfolios and companies accessible to the connected RapidRatings account.
3. Match source companies to existing Z2 Company/Supplier records using a stable external identifier or an approved matching process.
4. Discover the RapidRatings company, period, and rating response schemas.
5. Configure and validate source-to-target mapping to the Z2 Company DataModel.
6. Retrieve and preserve multiple reporting periods and their associated financial/rating values.
7. Support full/manual and scheduled synchronization using the same orchestration path.
8. Provide run status, counts, actionable errors, retry information, and source freshness.
9. Audit integration configuration, schema, mapping, and schedule changes.
10. Establish a reusable design for additional third-party data providers.

## 3. Scope and Boundaries

### 3.1 In scope

- Tenant-level RapidRatings integration configuration.
- Secure credential storage and connection validation.
- OAuth 2.0 Client Credentials flow as observed in the sandbox investigation.
- RapidRatings data API host configuration.
- Portfolio and company discovery.
- Company details, company reporting periods, and period-specific ratings retrieval.
- Source schema discovery and versioning.
- Mapping to the existing Z2 `Company` DataModel.
- RRID-to-Z2 Company/Supplier matching and unmatched review.
- Full/manual and scheduled synchronization.
- Data validation, idempotent upsert, duplicate prevention, and partial-failure handling.
- Sync schedule, sync-run history, and configuration change history.
- Downstream use of normalized company risk/financial information in Z2 views and approved workflows.

### 3.2 Out of scope unless separately approved

- Writing or changing financial assessments in RapidRatings.
- Treating RapidRatings as the master system for Z2 company identity.
- Automatically creating or linking a Z2 company from a fuzzy company-name match.
- Defining business risk thresholds or supplier escalation policies without business-owner approval.
- Automatically submitting outreach requests. Outreach is a separate capability and requires confirmation of endpoint permissions, request schema, user approval, and the authorizing business workflow.
- Storing raw provider payloads beyond contractual permissions and approved retention policy.

## 4. High-Level Architecture

```text
┌──────────────────────────────────────────────────────────────────────┐
│                         Z2 Enterprise UI                              │
│                                                                      │
│  Data Integrations                                                   │
│  • Integration catalogue and RapidRatings connection                 │
│  • Authentication / connection status                                │
│  • Source schema discovery                                           │
│  • Z2 target schema selection                                        │
│  • Field mapping and validation                                      │
│  • Manual sync / schedule                                           │
│  • Sync history / configuration change history                      │
└───────────────────────────────────┬──────────────────────────────────┘
                                    │ Platform APIs
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│             Z2 Integration Management and Orchestration              │
│                                                                      │
│  Tenant Integration Config  | Credential Reference                  │
│  Source Schema Registry      | Target Schema Reference               │
│  Mapping Registry            | Sync Orchestrator / Scheduler         │
│  Sync Run History            | Configuration / Change Audit         │
└───────────────────────────────────┬──────────────────────────────────┘
                                    │ Provider adapter contract
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     RapidRatings Adapter                              │
│                                                                      │
│  OAuth Token Provider   | RapidRatings API Client                    │
│  Request/Response DTOs  | Response Validation                        │
│  Pagination / Retry     | Company / Period / Rating Extraction       │
│  RRID Matching Support | Mapping Input Preparation                  │
└───────────────────────────────────┬──────────────────────────────────┘
                                    │ HTTPS + Bearer token
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                  RapidRatings Data API – Sandbox                      │
│                                                                      │
│  OAuth Token → Portfolios → Companies → Periods → Ratings            │
└───────────────────────────────────┬──────────────────────────────────┘
                                    │ normalized and mapped records
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     Z2 Enterprise Data Layer                          │
│                                                                      │
│  Existing Company DataModel (`A1company.json`)                       │
│    Company                                                            │
│      └── Financial (existing NESTED group)                            │
│            └── RapidRatings (proposed NESTED group)                  │
│                  ├── Company Details                                 │
│                  └── Periods / repeatable period records             │
│                                                                      │
│  Supplier-risk views / dashboards / approved downstream workflows    │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.1 Architecture principles

- The browser must call Z2 platform APIs only. It must not call RapidRatings directly or contain client secrets. (*Verified:* the rridata host, including its token endpoint, does not return CORS headers for arbitrary origins, so direct browser calls fail regardless of policy.)
- Common integration lifecycle capabilities belong to the Z2 Data Integrations framework.
- RapidRatings endpoint details and provider-specific response parsing belong in the RapidRatings adapter.
- The existing Z2 `Company` DataModel is the target schema for this design. Do not create a separate top-level Company object.
- Company identity, reporting-period identity, and rating values must remain distinguishable.
- Manual and scheduled runs must invoke the same orchestration and mapping pipeline.
- Every data sync must record the integration configuration and mapping versions used.

## 5. RapidRatings API Reference and Source Data

The following endpoints were exercised and verified against the sandbox on 2026-10-28. Full request/response details, real examples and error bodies are in `docs/api/` (one file per endpoint) and `docs/RapidRatings_API_Reference.md`. Behaviour must still be confirmed against the contracted production API before rollout.

| Capability | Method | Sandbox endpoint | Paging | Purpose |
|---|---|---|---|---|
| OAuth token | POST | `https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` | – | Obtain an access token |
| Portfolios | GET | `/v2.0/portfolios/` and `/v2.0/portfolios/{id}/` | `page` + `next` | List portfolios visible to the account; detail by ID |
| Companies | GET | `/v2.0/companies/` | `page` + `next`, **20 per page (fixed)** | List companies; filters `portfolio`, `name` (≥ 3 chars, contains), `ticker` (exact), `externalSourceId` |
| Company detail | GET | `/v2.0/companies/{rrid}/` | – | Single company; adds `reports` and `sectorAverages` to the list fields |
| Company periods | GET | `/v2.0/companies/{rrid}/periods/` and `.../periods/{periodId}/` | `page` + `next`, **10 per page (fixed)**, newest first | Period financial statement data |
| Company ratings | GET | `/v2.0/companies/{rrid}/ratings/` and `.../ratings/{ratingId}/` | `page` + `next`, **10 per page (fixed)**, newest first | Full rating history, one rating per period |
| Outreach requests | GET/POST | `https://api.sandbox.rrilabs.co/outreach-requests` (+ `/{id}`, `/{id}/communications`) | `limit` + `cursor` | Separate outreach capability; not part of the initial data synchronization scope |

The data APIs use the `rridata.sandbox.rrilabs.co` host. Outreach lives on a separate `api.sandbox.rrilabs.co` host, publishes an OpenAPI spec (`https://api.sandbox.rrilabs.co/openapi.yaml`), and should be treated as a separately authorized capability: creating an outreach request makes RapidRatings send real emails to the counterparty. The same access token is accepted by both hosts.

### 5.0 Verified sandbox API behaviour

| Behaviour | rridata (`/v2.0/...`) | Outreach |
|---|---|---|
| Trailing slash | **Required** (`/companies/XAZPzdJ` without slash returns 404) | Optional |
| Unknown query parameter | `400 Not valid query parameter: <name>` | 400 |
| Page past the end | Companies: **404**; portfolios, periods, ratings: **500** | n/a (cursor) |
| Invalid / expired token | **403** `Not Authorized.` | **401** |
| Missing `Authorization` header | 401 | 401 |
| Error envelope | `{"Request": {"message", "info"}}` (scope key may be `Portfolio` or `Companies`) | RFC 7807 `{type, title, status, detail}` |
| Rate-limit headers | `X-Ratelimit-Limit` (sandbox 30,000,000), `X-Ratelimit-Remaining`, `X-Ratelimit-Reset`, `Retry-After` | `X-Ratelimit-Limit` 500,000 |
| Incremental / changed-since filter | **None** (`modified_since`, `updated_since`, `ordering`, `page_size`, `limit` all return 400) | n/a |
| Numbers | Integer-valued decimals are returned as `1575.0` | – |

Consequences for the adapter: always follow the `next` URL (it keeps filters) and never compute page numbers, because an out-of-range page returns a 500 that must not be retried; validate query parameters before sending, because unknown ones fail hard. Sandbox volume: 5 portfolios, 96 companies, ~26 periods and ratings per company.

### 5.1 Company response – `Companies.json`

The supplied company response contains the following fields:

| Source field | Meaning / handling |
|---|---|
| `id` | RapidRatings company identifier (RRID); preserve as the external company key |
| `name` | Source company name |
| `country` | Source country value, observed as an ISO-3 code (e.g. `USA`); normalize only using an agreed country mapping |
| `ticker` | Stock ticker, optional |
| `exchange` | Exchange, optional |
| `sector` | Industry sector, optional |
| `href` | Company resource URL |
| `financials` | Financial resource URL |
| `periods` | Periods collection URL |
| `ratings` | Ratings collection URL |
| `latestRatings` | Array containing latest-period rating summaries and period metadata |
| `reportLinks` | Links such as FHR report, financial dialogue, and peer benchmark |
| `address` | Address value; empty in the supplied sample |
| `industryClassificationSystem` | Nullable classification-system field |
| `industryClassificationCode` | Nullable classification-code field |
| `currency` | Currency value, e.g. `USD` |
| `dataScale` | Financial-data scale, e.g. `millions` |
| `dataConfidential` | Provider confidentiality marker, e.g. `PUB` |
| `externalSourceId` | Nullable external-source identifier |
| `externalSourceIds` | Array of external-source identifiers |
| `reports` | *Detail endpoint only.* API download links (`reportType` + `downloadHref`) for `fhr`, `financial_dialogue`, `peer_benchmark`; downloading requires API authentication |
| `sectorAverages` | *Detail endpoint only.* Sector average `fhr` and `chs`, e.g. `{ "fhr": 52.6, "chs": 45.8 }` |

The sample company is `Rite Aid Corporation`, with RRID `KawjbGJ`, ticker `RAD`, exchange `US`, country `USA`, and sector `Retail`. The RRID is a 7-character string.

`latestRatings` is present as an array within the company response. *Verified:* it holds the most recent ratings, **newest first, up to 5** (2–5 observed), so `latestRatings[0]` is the current rating; the full history is only available from the ratings endpoint. It includes records such as `id`, `href`, `eqyYear`, `period`, `financialDate`, `reportingPeriod`, `fhr`, `chs`, `probabilityDefault`, `termPd`, `delta`, `simulatedFhr`, profitability/efficiency indicators, and qualitative fields such as `leverage`, `liquidity`, and `earningsPerformance`. Treat it as a source-provided latest-rating view. During a full historical sync, reconcile it with the dedicated ratings endpoint rather than inserting the same period twice.

### 5.2 Period response – `PeriodById.json`

The supplied individual-period response (Tyson Foods, RRID `XAZPzdJ`, `GET /v2.0/companies/XAZPzdJ/periods/2019YE/`) contains the fields below. The list endpoint returns the same object per item. `period` takes the values `Q1`, `Q2`, `Q3` or `YE`, and the period `id` is `eqyYear` + `period`.

| Source field | Example | Suggested interpretation |
|---|---:|---|
| `id` | `2019YE` | Stable source period identifier |
| `eqyYear` | `2019` | Reporting year supplied by RapidRatings |
| `period` | `YE` | Period type supplied by RapidRatings |
| `financialDate` | `2019-09-28` | Financial statement date |
| `href` | Resource URL | Source period resource link |
| `currency` | `USD` | Currency for the financial values |
| `fixedAssets` | `7282.0` | Financial statement metric |
| `cashFromOperationsFromCashFlow` | `null` | Nullable financial metric |
| `debitOwnedWithinOneYear` | `2102.0` | Financial statement metric; preserve provider spelling unless an approved canonical target name is used |
| `abnormalItems` | `14.0` | Financial statement metric |
| `netInvestingCashFlow` | `-3464.0` | Financial statement metric |
| `termLoans` | `9830.0` | Financial statement metric |
| `financialAssets` | `null` | Nullable financial metric |
| `totalShareholderEquity` | `14226.0` | Financial statement metric |
| `salesRevenue` | `42405.0` | Financial statement metric |
| `interestReceived` | `11.0` | Financial statement metric |
| `totalCurrentLiabilities` | `5513.0` | Financial statement metric |
| `totalCurrentAssets` | `7169.0` | Financial statement metric |
| `bankCashBalances` | `484.0` | Financial statement metric |
| `totalAssets` | `33097.0` | Financial statement metric |
| `netProfitAfterTax` | `2035.0` | Financial statement metric |
| `netFundingCashFlow` | `1171.0` | Financial statement metric |
| `companyTaxExpense` | `396.0` | Financial statement metric |
| `totalLiabilities` | `18871.0` | Financial statement metric |
| `earningsBeforeInterestAndTax` | `2882.0` | Financial statement metric |
| `interestExpense` | `462.0` | Financial statement metric |

Numeric examples are decimals in the supplied payload. Every financial amount can be `null` when a figure is not reported (`cashFromOperationsFromCashFlow` was null in all sandbox samples). Null values must remain null; do not convert a missing financial value into zero. Amounts are in the company's `currency` at the company's `dataScale` (e.g. `millions`); `dataScale` is only on the company record, so it must be carried to the period records.

### 5.3 Ratings response – `GetRatingByPeriodId.json`

The supplied rating response contains:

| Source field | Example | Suggested target type (confirm with platform type registry) |
|---|---:|---|
| `id` | `2019YE` | STRING |
| `href` | Resource URL | STRING |
| `eqyYear` | `2019` | INTEGER |
| `period` | `YE` | STRING |
| `financialDate` | `2019-09-28` | DATE |
| `reportingPeriod` | `2020-05-15` | DATE |
| `fhr` | `47` | INTEGER, if the platform accepts the observed score range |
| `chs` | `50` | INTEGER, if the platform accepts the observed score range |
| `probabilityDefault` | `0.4149` | DOUBLE; preserve the source numeric value and confirm unit/scale |
| `termPd` | Object with `year2` through `year10` | Prefer scalar numeric properties in the period record unless nested-object support is confirmed |
| `delta` | `-9` | INTEGER or DOUBLE, based on full source contract |
| `simulatedFhr` | `47` | Numeric score |
| `simulatedFhrDelta` | `-9` | Numeric delta |
| `operatingProfitability` | `33` | Numeric score |
| `netProfitability` | `69` | Numeric score |
| `capitalStructureEfficiency` | `10` | Numeric score |
| `costStructureEfficiency` | `63` | Numeric score |
| `leverage` | `Average` | STRING or controlled enum only if allowed values are confirmed |
| `liquidity` | `Weak` | STRING or controlled enum only if allowed values are confirmed |
| `earningsPerformance` | `Average` | STRING or controlled enum only if allowed values are confirmed |

`termPd` contains the year-specific numeric values `year2`, `year3`, `year4`, `year5`, `year6`, `year7`, `year8`, `year9`, and `year10`. The sample values are decimal numbers. Do not collapse this object into a single number or discard its horizon labels.

Semantics recorded during the sandbox investigation (confirm against official RapidRatings definitions, Section 21):

| Field | Recorded meaning |
|---|---|
| `fhr` | Financial Health Rating, integer 0–100, short-term (12-month) default risk; higher is healthier |
| `chs` | Core Health Score, integer 0–100, medium-term (2–3 year) operating resilience |
| `probabilityDefault` | 1-year probability of default **expressed as a percentage** (`0.4149` = 0.41 %, not 41 %) |
| `termPd.yearN` | Cumulative probability of default (%) over N years |
| `delta`, `simulatedFhrDelta` | Integer change vs. the previous rating; **nullable** (null for the oldest rating) |
| Sub-scores | `operatingProfitability`, `netProfitability`, `capitalStructureEfficiency`, `costStructureEfficiency`: integers 0–100 |
| `leverage`, `liquidity`, `earningsPerformance` | Observed values `Strong`, `Average`, `Weak` only |

Display provider values as returned. Do not derive custom risk bands from these scores without business-owner approval (Section 3.2).

### 5.4 Sample identity mismatch (explained)

The three supplied examples do not represent one consistent company dataset. The verification run explained why: they come from two different sandbox companies.

| File | Source company identity | Period ID | Key observation |
|---|---|---|---|
| `Companies.json` | RRID `KawjbGJ` – Rite Aid Corporation | `2020YE`, `2020Q3`, `2020Q2`, `2020Q1`, `2019YE` in `latestRatings` | Embedded latest-rating examples for RRID `KawjbGJ` |
| `PeriodById.json` | RRID `XAZPzdJ` – Tyson Foods, Inc. (in the `href`) | `2019YE` | Period financial response |
| `GetRatingByPeriodId.json` | RRID `XAZPzdJ` – Tyson Foods, Inc. (in the `href`) | `2019YE` | Rating response for that period |

The payload difference follows from this: the `Companies.json` `latestRatings` item for `2019YE` (Rite Aid: `financialDate` `2019-03-02`, `fhr` `22`, `chs` `29`) and the standalone rating response for `2019YE` (Tyson Foods: `financialDate` `2019-09-28`, `fhr` `47`, `chs` `50`) are two companies' ratings for the same period label. They are not a data discrepancy, and they show exactly why a period ID alone is not a join key.

**Resolution:** a matched set for one RRID is documented in `docs/api/` (`GET /companies/XAZPzdJ/`, `/periods/2019YE/` and `/ratings/2019YE/` share `financialDate` `2019-09-28`). Use matched sets like this for end-to-end mapping validation.

**Mandatory join rule:** Join period and rating resources using the same company RRID plus the stable period identifier (`id`, e.g. `2019YE`). Check `href` path identity where available. Do not join by period identifier alone, company name, response order, or array index.

## 6. Integration Authentication

### 6.1 Connection configuration

A tenant administrator configures the RapidRatings integration instance with:

- Provider: `RAPIDRATINGS`.
- Environment: sandbox or production.
- Data API base URL.
- Client ID.
- Client secret (stored as a secret reference, never as ordinary configuration text).
- Optional provider-specific settings required by the contracted API.

Sandbox and production configuration and credentials must be isolated. Outreach host configuration, if required later, should be separately enabled and permission-checked.

### 6.2 OAuth 2.0 Client Credentials flow

The sandbox investigation observed the following authentication pattern:

1. The integration runtime obtains the tenant's client ID and client secret from the approved secret manager.
2. It builds the token request using the Client Credentials grant and Basic authentication for the client ID and secret, as tested in the sandbox.
3. It sends `POST /v2.0/oauth/token/` to the configured RapidRatings data host.
4. It parses the access token and expiry information from the response.
5. It stores the token only in memory or a secure short-lived cache.
6. It attaches `Authorization: Bearer <access_token>` to protected RapidRatings API calls.
7. It requests a new token before expiry (no refresh token is issued, so renewal repeats the client-credentials request) and retries an eligible failed request once with the newly obtained token.

*Verified sandbox contract:*

| Item | Observed |
|---|---|
| Request | `POST /v2.0/oauth/token/` (trailing slash required; GET returns 405), `Authorization: Basic base64(client_id:client_secret)`, body `grant_type=client_credentials` (form-urlencoded or JSON) |
| Response | `{ "access_token", "expires_in": 1800, "token_type": "Bearer", "scope": "uid introspection openid" }` |
| Refresh token | Not issued |
| Errors | 400 unsupported `grant_type`; **401 for wrong credentials, missing `Authorization` header, or missing `grant_type`**; 405 for GET |
| Host coverage | One token works for both the data host and the outreach host |

Because a missing `grant_type` returns 401, a 401 from the token endpoint does not always mean bad credentials; inspect the response body. The production contract must be confirmed. Do not hard-code secrets or tokens.

### 6.3 Token lifecycle and observed error behavior

The sandbox investigation observed:

- Token lifetime: `expires_in` = 1800 seconds (30 minutes).
- Refresh threshold used by the client: fewer than 60 seconds remaining.
- The data API returned `403 Not Authorized.` for an invalid or expired token, and `401` only when the `Authorization` header was missing.
- The outreach API returned `401` in the observed expired/unauthorized-token scenario.

These are observations, not a guarantee that every `401` or `403` indicates token expiration. Inspect the provider error response and distinguish expired/invalid authentication from insufficient permission, forbidden resources, and unrelated failures.

### 6.4 Security controls

- Encrypt credentials at rest through the approved secret-management service.
- Scope credential references to tenant and integration instance.
- Keep secrets and bearer tokens out of the browser, logs, traces, error payloads, and sync history.
- Use HTTPS for all provider traffic.
- Restrict secret access to the integration runtime.
- Support credential rotation and connection revalidation.
- Do not expose token response payloads to end users.
- Record credential-rotation events in audit history without recording the secret value.

### 6.5 Connection test

A **Test Connection** action should:

1. Resolve the tenant's secret reference.
2. Request an access token.
3. Call a low-impact authorized endpoint, such as the portfolios endpoint, to validate provider access.
4. Return a sanitized success/failure status, timestamp, and actionable error category.
5. Avoid running a full data synchronization as part of connection validation.

## 7. Source Schema Discovery and Schema Management

Schema management and data synchronization are related but separate operations.

### 7.1 Source schema discovery

The RapidRatings adapter should discover or describe the source structures for:

- Portfolios.
- Companies.
- Company `latestRatings` collection.
- Company periods.
- Period financial statement fields.
- Period ratings.

Where the API does not expose a formal machine-readable schema endpoint, schema discovery should be based on validated response samples and the provider contract. Do not claim a schema endpoint exists unless confirmed.

*Verified:* the rridata host publishes no machine-readable schema (`/openapi.json` returns 403), so company, portfolio, period and rating schemas must be **inferred** from responses. The outreach host does publish one (`https://api.sandbox.rrilabs.co/openapi.yaml`), so outreach fields can be **declared**, including required flags. The schema registry should record which of the two applies to each resource. Inference must treat integer-valued decimals such as `1575.0` as numbers and recognise that every field seen as `null` in one sample may be populated in another.

The discovered schema definition should capture:

- Provider and resource/endpoint name.
- Source field path, including nested paths such as `termPd.year2`.
- Observed JSON type and expected contract type.
- Nullable/optional status where known.
- Array/object structure.
- Description and units/scale where available.
- First-seen and last-seen timestamps.
- Source schema version or snapshot identifier.
- Environment and adapter version used during discovery.

### 7.2 Schema registry

Store versioned source schema snapshots in the platform Schema Registry. A refreshed schema must create a new version or revision rather than silently overwriting the previously approved schema.

The schema registry should support:

- Current active schema version.
- Historical schema versions.
- Field additions, removals, type changes, nullability changes, and nested-structure changes.
- Compatibility/diff report.
- Discovery timestamp and source environment.
- Review/approval status before a breaking schema revision is used by production mappings.

### 7.3 Target schema reference

Use the existing Z2 `Company` DataModel in `A1company.json` as the target. The inspected Company schema contains existing `NESTED` property groups, including `Basic Information` and `Financial`, and existing examples of `PROPS_ARRAY` with child properties.

The proposed target extension is:

```text
Company
└── Financial                         (existing NESTED group)
    ├── Moody's Credit Rating          (existing property)
    ├── D&B Rating                      (existing property)
    └── RapidRatings                    (proposed NESTED group)
        ├── Company Details             (proposed NESTED group)
        │   ├── RRID
        │   ├── Source Company Name
        │   ├── Source Country
        │   ├── Ticker
        │   ├── Exchange
        │   ├── Sector
        │   ├── Company Resource URL
        │   ├── Financials Resource URL
        │   ├── Periods Resource URL
        │   ├── Ratings Resource URL
        │   ├── Currency
        │   ├── Data Scale
        │   ├── Data Confidentiality
        │   ├── Industry Classification System
        │   ├── Industry Classification Code
        │   ├── External Source ID
        │   └── External Source IDs
        └── Periods                         (proposed NESTED group)
            └── Period Objects              (proposed PROPS_ARRAY)
                ├── Period ID
                ├── Equity Year
                ├── Period Type
                ├── Financial Date
                ├── Reporting Period Date
                ├── Period Resource URL
                ├── Currency
                ├── Period Financial Metrics
                └── Rating Metrics
                    ├── FHR
                    ├── CHS
                    ├── Probability Default
                    ├── Term PD Year 2 ... Term PD Year 10
                    ├── Delta
                    ├── Simulated FHR
                    ├── Simulated FHR Delta
                    ├── Operating Profitability
                    ├── Net Profitability
                    ├── Capital Structure Efficiency
                    ├── Cost Structure Efficiency
                    ├── Leverage
                    ├── Liquidity
                    └── Earnings Performance
```

This is a logical target hierarchy. The exact nested group(s) inside a `PROPS_ARRAY` must be validated against the deployed DataModel runtime. If nested groups inside array items are not supported, define the period's financial and rating fields as direct child properties of each `PROPS_ARRAY` item. Flatten `termPd.year2` through `termPd.year10` into separate numeric child properties unless nested-object support is confirmed. Do not use an arbitrary JSON blob as the only representation of fields that must be mapped, searched, filtered, or displayed.

### 7.4 Target schema extension rules

When generating or applying the Company schema extension:

- Follow the exact metadata structure and conventions found in `A1company.json`.
- Use `NESTED` for supported property groups and `PROPS_ARRAY` for repeatable period records.
- Use the correct existing `dataModelType` and `extDataModelId` according to platform schema rules; do not invent IDs.
- Generate new property IDs through the platform's supported mechanism; never reuse IDs.
- Keep `name`, `dbName`, `label`, `path`, `dataType`, and child property paths consistent.
- Preserve all existing Company properties and IDs.
- Avoid duplicate properties where an existing Company field can legitimately be reused.
- Preserve external source values and use explicit transformation rules for normalized values.
- Do not populate fabricated dates, IDs, or metadata values in a persisted schema artifact.
- Validate the proposed schema against the canonical DataModel schema/runtime before deployment.

## 8. Schema Mapping to Z2 Enterprise

### 8.1 Mapping design

Each source-to-target mapping should be a versioned configuration with:

- Integration/provider identifier.
- Source schema version.
- Target object type and target DataModel version.
- Source field path.
- Target property path.
- Type conversion/normalization rule.
- Required/optional handling.
- Null/default behavior.
- Validation constraints.
- Mapping version and approval state.

The platform mapping engine should transform source response DTOs into the Z2 target representation. The adapter should not hard-code tenant-specific field choices inside request handlers.

### 8.2 Company field mapping

| RapidRatings source field | Proposed Z2 target path | Rule |
|---|---|---|
| `id` | `financial.rapidratings.company_details.rrid` | Required external identity; preserve exactly |
| `name` | `financial.rapidratings.company_details.source_company_name` | Preserve source spelling; do not overwrite Z2 Company primary key/name automatically |
| `country` | `financial.rapidratings.company_details.source_country` or an existing country property when normalization is approved | Preserve source value; map to ISO code only with a verified lookup rule |
| `ticker` | `financial.rapidratings.company_details.ticker` | Optional string |
| `exchange` | `financial.rapidratings.company_details.exchange` | Optional string |
| `sector` | `financial.rapidratings.company_details.sector` | Optional string |
| `href` | `financial.rapidratings.company_details.company_resource_url` | Preserve as source resource reference |
| `financials` | `financial.rapidratings.company_details.financials_resource_url` | Preserve as source resource reference |
| `periods` | `financial.rapidratings.company_details.periods_resource_url` | Preserve as source resource reference |
| `ratings` | `financial.rapidratings.company_details.ratings_resource_url` | Preserve as source resource reference |
| `currency` | `financial.rapidratings.company_details.currency` | Preserve provider currency code |
| `dataScale` | `financial.rapidratings.company_details.data_scale` | Preserve provider scale; do not rescale silently |
| `dataConfidential` | `financial.rapidratings.company_details.data_confidentiality` | Preserve provider marker |
| `industryClassificationSystem` | `financial.rapidratings.company_details.industry_classification_system` | Nullable string unless formal schema specifies otherwise |
| `industryClassificationCode` | `financial.rapidratings.company_details.industry_classification_code` | Nullable string unless formal schema specifies otherwise |
| `address` | Existing Company address property or a provider-specific field, after target schema review | Empty in supplied sample; do not create duplicate address values without need |
| `externalSourceId` | `financial.rapidratings.company_details.external_source_id` | Nullable; preserve source value |
| `externalSourceIds` | `financial.rapidratings.company_details.external_source_ids` | Array; use platform-supported array type |
| `reportLinks.fhr` | `financial.rapidratings.company_details.report_links_fhr` or approved report-link group | Preserve URL |
| `reportLinks.financialDialog` | `financial.rapidratings.company_details.report_links_financial_dialog` or approved report-link group | Preserve URL |
| `reportLinks.peerBenchmark` | `financial.rapidratings.company_details.report_links_peer_benchmark` or approved report-link group | Preserve URL |
| `sectorAverages.fhr` / `sectorAverages.chs` | `financial.rapidratings.company_details.sector_average_fhr` / `sector_average_chs` (proposed) | Detail endpoint only; DOUBLE; benchmark values, not company scores |
| `reports[]` | Not mapped by default | Detail endpoint only; API download links that require authentication. Persist only if retention terms allow (Section 21) |

### 8.3 Period financial mapping

| RapidRatings period source field | Proposed period child property | Type / rule |
|---|---|---|
| `id` | `period_id` | STRING; stable source key |
| `eqyYear` | `eqy_year` | INTEGER |
| `period` | `period_type` | STRING; preserve source code such as `YE` or `Q3` |
| `financialDate` | `financial_date` | DATE |
| `href` | `period_resource_url` | STRING |
| `currency` | `currency` | STRING; retain currency with period financial values |
| `fixedAssets` | `fixed_assets` | Numeric; confirm precision and currency scale |
| `cashFromOperationsFromCashFlow` | `cash_from_operations_from_cash_flow` | Nullable numeric |
| `debitOwnedWithinOneYear` | `debit_owned_within_one_year` | Numeric; preserve provider spelling in source mapping |
| `abnormalItems` | `abnormal_items` | Nullable numeric |
| `netInvestingCashFlow` | `net_investing_cash_flow` | Nullable numeric |
| `termLoans` | `term_loans` | Nullable numeric |
| `financialAssets` | `financial_assets` | Nullable numeric |
| `totalShareholderEquity` | `total_shareholder_equity` | Nullable numeric |
| `salesRevenue` | `sales_revenue` | Nullable numeric |
| `interestReceived` | `interest_received` | Nullable numeric |
| `totalCurrentLiabilities` | `total_current_liabilities` | Nullable numeric |
| `totalCurrentAssets` | `total_current_assets` | Nullable numeric |
| `bankCashBalances` | `bank_cash_balances` | Nullable numeric |
| `totalAssets` | `total_assets` | Nullable numeric |
| `netProfitAfterTax` | `net_profit_after_tax` | Nullable numeric |
| `netFundingCashFlow` | `net_funding_cash_flow` | Nullable numeric |
| `companyTaxExpense` | `company_tax_expense` | Nullable numeric |
| `totalLiabilities` | `total_liabilities` | Nullable numeric |
| `earningsBeforeInterestAndTax` | `earnings_before_interest_and_tax` | Nullable numeric |
| `interestExpense` | `interest_expense` | Nullable numeric |

The provider's financial values should retain the associated `currency` and `dataScale` context. Do not convert `millions` into absolute currency values unless a documented target normalization is approved and tested.

### 8.4 Rating mapping

| RapidRatings rating source field | Proposed period child property | Type / rule |
|---|---|---|
| `id` | `period_id` | Use the same source period key as the period record |
| `eqyYear` | `eqy_year` | INTEGER |
| `period` | `period_type` | STRING |
| `financialDate` | `financial_date` | DATE; must agree with or be reconciled against period data |
| `reportingPeriod` | `reporting_period_date` | DATE |
| `href` | `rating_resource_url` | STRING |
| `fhr` | `fhr` | Numeric score; preserve observed value |
| `chs` | `chs` | Numeric score; preserve observed value |
| `probabilityDefault` | `probability_default` | DOUBLE; confirm percentage/decimal interpretation before display/conversion |
| `termPd.year2` ... `termPd.year10` | `term_pd_year_2` ... `term_pd_year_10` | DOUBLE each; keep horizon explicit |
| `delta` | `delta` | Numeric |
| `simulatedFhr` | `simulated_fhr` | Numeric |
| `simulatedFhrDelta` | `simulated_fhr_delta` | Numeric |
| `operatingProfitability` | `operating_profitability` | Numeric |
| `netProfitability` | `net_profitability` | Numeric |
| `capitalStructureEfficiency` | `capital_structure_efficiency` | Numeric |
| `costStructureEfficiency` | `cost_structure_efficiency` | Numeric |
| `leverage` | `leverage` | STRING until allowed values are formally confirmed |
| `liquidity` | `liquidity` | STRING until allowed values are formally confirmed |
| `earningsPerformance` | `earnings_performance` | STRING until allowed values are formally confirmed |

### 8.5 `latestRatings` mapping strategy

The `latestRatings` array is included in the supplied company response. It can support a latest-rating preview or provide data already returned with the company resource. The integration must decide and document one canonical strategy:

- **Recommended:** Use the dedicated ratings endpoint as the canonical source for period ratings during historical/full synchronization. Use `latestRatings` for a latest-rating summary or a cross-check only.
- If the dedicated endpoint cannot be called for a particular workflow, an explicitly configured fallback may map `latestRatings` records, but the source must be recorded and the period identity validated.
- Do not write an embedded latest rating and a separately retrieved rating as two period records.
- When values differ, do not silently choose one. Record the source discrepancy and apply the agreed precedence policy.

### 8.6 Company matching

Use the following preferred matching order:

1. Existing external identity mapping `(tenantId, provider, rrid) → z2CompanyId`.
2. A configured Z2 Company external identifier explicitly known to contain the RapidRatings RRID.
3. An approved deterministic identifier available in both systems, such as a verified registration identifier.
4. Manual review for ambiguous candidates.

Company name, country, ticker, and sector can help a user review a candidate but must not silently establish identity through fuzzy matching. Keep unmatched companies in a reviewable result set. Prevent one RRID from being associated with multiple Z2 companies in the same tenant unless the business explicitly supports that case.

## 9. Schema Mapping Validation

Before a mapping can be activated, validate:

- The source field exists in the selected source schema version.
- The target property exists in the selected Z2 Company schema version.
- Source and target types are compatible or have an explicit transformation.
- Required target properties have a mapping or an approved default.
- Nullable source fields can populate nullable targets.
- Date, number, currency, percentage, enum, and array conversions are explicit.
- `termPd` child keys are mapped individually and not lost.
- A period ID and RRID are available to form the period association key.
- No duplicate source field maps ambiguously to the same target property without an approved rule.
- Mapping version is recorded on sync runs and resulting records.
- Unknown/new source fields are reported as schema drift rather than discarded silently.

Mapping UI should support source field selection, target property selection, type compatibility indicators, transformations, required/optional behavior, preview values, validation errors, save-as-draft, approval, and activation.

## 10. Syncing Schema and Data

### 10.1 Schema synchronization workflow

Schema synchronization refreshes the provider schema reference and checks mapping compatibility. It does not itself mean that business data has been imported.

1. Resolve the active tenant integration and credential reference.
2. Authenticate with RapidRatings.
3. Retrieve representative source resources or provider schema metadata, where available.
4. Build a normalized source schema snapshot for companies, periods, and ratings.
5. Compare the new snapshot with the last approved schema version.
6. Detect added, removed, renamed, or type-changed fields and nested structure changes.
7. Revalidate current mappings against the new schema and the target Company DataModel version.
8. Produce a schema diff and mapping-impact report.
9. Save the new schema version as pending review or activate it according to platform governance.
10. Require mapping updates/approval for breaking changes before affected data syncs proceed.

### 10.2 Data synchronization workflow

1. Start a sync run with tenant, integration, selected portfolio(s), trigger type, mapping version, and target Company schema version.
2. Obtain or reuse a valid access token.
3. Retrieve selected portfolios and companies, using confirmed provider filtering and pagination behavior.
4. Validate each company RRID and resolve its Z2 Company/Supplier association.
5. Record unmatched or ambiguous companies for review; do not create a silent fuzzy match.
6. Retrieve each matched company's period resources.
7. Retrieve the corresponding ratings resources according to the provider contract.
8. Validate that company RRID and period ID match across the company, period, and rating resources.
9. Transform the data using the active mapping version.
10. Validate mapped records against the target Z2 Company schema.
11. Upsert company-level RapidRatings details and period-level records.
12. Preserve history and avoid duplicate period records.
13. Record created, updated, skipped, unmatched, and failed counts.
14. Mark the run succeeded, partially succeeded, failed, or cancelled and publish a sanitized summary.

### 10.3 Period and rating association

For every company:

```text
Company identity: (tenantId, provider, rrid)
Period identity:  (tenantId, provider, rrid, periodId)

Company RRID
   │
   ├── Periods response
   │      └── period id = 2019YE
   │
   └── Ratings response
          └── rating id = 2019YE

Join only when:
  company RRID is the same
  AND period id is the same
  AND resource/company references are consistent where available
```

*Verified in the sandbox:* the ratings endpoint returned exactly one rating per period, and each rating `id` equals the matching period `id` (e.g. both `2019YE` for the same `financialDate`). If a ratings resource contains more than one record for a period in production, or if the API introduces another rating identifier/dimension, extend the stable key based on the actual API contract. Confirm one-rating-per-period as part of the production contract.

### 10.4 Full and incremental synchronization

- **Full sync:** Retrieve the selected portfolio/company scope and all available period/rating data supported by the API. Use bounded batches and pagination.
- **Incremental sync:** Use a modified-since filter, cursor, ETag, or update marker only if the RapidRatings API explicitly supports it and the behavior is verified. Do not invent a filter parameter. *Verified:* the sandbox data API has **no** such filter; `modified_since`, `updated_since` and `ordering` are rejected with 400.
- Because no reliable incremental mechanism is available, retrieve the required resources and perform idempotent upserts using stable external keys. A safe optimisation: read the full company list each run, compare `latestRatings[0].id` (and `fhr`) per RRID with the stored state, and for changed companies read only the first page of periods/ratings (newest first, 10 per page), following `next` only while unseen period IDs appear. Run a full history load on first sync and periodically as a reconciliation.
- Define whether a full sync can mark source records as no longer present. Do not delete historical Z2 period records merely because they are absent from a partial or failed response.

### 10.5 Idempotency and upsert keys

Recommended logical keys:

- Company integration identity: `(tenantId, provider, rrid)`.
- Period record: `(tenantId, provider, rrid, periodId)`.
- If the source supplies a distinct rating record ID or additional dimension, include it as required by the verified contract.

A repeated sync of the same company and period should update the existing mapped record rather than create a duplicate. Keep source retrieval time, sync run ID, schema version, and mapping version for traceability.

### 10.6 Nulls, partial data, and data quality

- Preserve explicit `null` values where the target schema permits them.
- Do not convert null financial metrics to zero.
- Do not invent default values for missing ratings.
- Preserve provider currency and scale context with financial metrics.
- Treat missing `RRID`, period ID, or required mapping values as validation failures for the affected record.
- Quarantine or report mismatched company/period/rating references.
- Continue independent records when safe, and report partial failures accurately.

## 11. Manual and Scheduled Syncs

### 11.1 Manual sync

An authorized user can select **Sync now** from the integration detail page. The UI should allow the user to select the scope, such as all enabled portfolios or a selected portfolio/company, based on the supported API and product requirements.

Manual execution flow:

1. Validate integration status and active mapping.
2. Check that a sync is not already running for the same conflicting scope, or apply a documented concurrency policy.
3. Create a sync-run record with trigger type `MANUAL`.
4. Enqueue the standard sync job.
5. Execute authentication, retrieval, matching, mapping, validation, and upsert.
6. Update run progress and final result.
7. Display record counts and sanitized errors in the UI.

Manual sync must not implement a separate data transformation path from scheduled sync.

### 11.2 Scheduled sync

An administrator can enable a schedule for the integration. A schedule should support:

- Enabled/disabled state.
- Frequency or cron-like schedule according to the platform scheduler contract.
- Time zone.
- Start date/time, if required.
- Selected portfolio(s) or sync scope.
- Retry policy and maximum attempts.
- Optional maintenance/blackout window if supported by platform standards.
- Next scheduled execution.
- Previous execution and outcome.

At the scheduled time, the scheduler creates a run with trigger type `SCHEDULED` and enqueues the same sync job used by manual execution.

### 11.3 Scheduler behavior

- Store schedules as tenant-scoped configuration.
- Use the platform scheduler/queue rather than a timer in the browser or inside a short-lived API request.
- Prevent duplicate enqueueing for the same schedule occurrence.
- Define behavior for missed runs, overlapping runs, disabled integrations, and credential failures.
- Use a bounded retry policy; permanent errors must not retry indefinitely.
- Store time zone and evaluate schedule occurrences consistently, including daylight-saving behavior where relevant.
- Surface the next run and last run on the integration UI.

## 12. Sync Run History and Monitoring

Keep operational sync-run history separate from configuration change history.

Each sync run should contain:

| Field | Description |
|---|---|
| `runId` | Unique execution identifier |
| `tenantId` / `integrationId` | Tenant and integration scope |
| `triggerType` | `MANUAL` or `SCHEDULED` (plus approved system triggers) |
| `status` | `QUEUED`, `RUNNING`, `SUCCEEDED`, `PARTIALLY_SUCCEEDED`, `FAILED`, or `CANCELLED` |
| `startedAt` / `completedAt` | UTC timestamps |
| `scope` | Selected portfolios and/or company scope |
| `sourceSchemaVersion` | Source schema snapshot used |
| `targetSchemaVersion` | Z2 Company DataModel version used |
| `mappingVersion` | Mapping version used |
| `configVersion` | Integration configuration version used |
| `counts` | Discovered, matched, unmatched, created, updated, skipped, and failed |
| `retryCount` | Number of retries |
| `duration` | Execution duration |
| `correlationId` | Trace/log correlation identifier |
| `errorSummary` | Sanitized actionable error summary |

The UI should support filtering by time range, trigger type, status, portfolio, and integration. Run details should show per-resource or per-company outcomes where useful, while preventing exposure of credentials or sensitive raw payloads.

## 13. Sync Schedule and Change History

### 13.1 Schedule configuration history

The system must record changes to:

- Schedule enabled/disabled state.
- Frequency and cron/expression (if used).
- Time zone.
- Start date/time.
- Selected portfolio(s) and scope.
- Retry policy and maximum attempts.
- Any other scheduler options supported by the platform.

The schedule UI should show who last changed the schedule, when it changed, the current schedule version, next run, and last run outcome.

### 13.2 Integration configuration change history

Record changes to:

- Integration created, enabled, disabled, or deleted/deactivated.
- Environment and API base URL.
- Credential reference or credential rotation event (never the credential value).
- Selected portfolios.
- Source schema version selection.
- Target Company DataModel version.
- Field mappings and transformations.
- Supplier/company matching rules.
- Sync schedule and retry configuration.
- User actions such as test connection, schema refresh, mapping approval, and manual sync initiation where audit policy requires them.

### 13.3 Audit record fields

Each change-history entry should include:

- Change ID.
- Tenant and integration ID.
- Actor/user or system identity.
- Action type.
- Changed entity/setting.
- Before and after values where safe and appropriate.
- Timestamp in UTC.
- Configuration/mapping/schema version.
- Correlation ID or associated sync run ID where relevant.
- Reason/comment if required by governance.

Change history should be append-only or protected against unauthorized modification. Secret values, bearer tokens, and sensitive raw payloads must never be recorded.

## 14. Error Handling and Resilience

| Scenario | Expected behavior |
|---|---|
| Invalid credentials | Fail connection validation; show sanitized authentication error; do not retry continuously |
| Token near expiry | Refresh before expiry using the configured threshold |
| Expired token | Obtain a new token and retry an eligible request once |
| HTTP 401/403 | Inspect provider response; distinguish authentication failure from permission/authorization failure |
| HTTP 429 | Respect `Retry-After` when returned and apply bounded backoff with jitter; monitor `X-Ratelimit-Remaining` |
| Timeout / transient 5xx | Retry a limited number of times with backoff |
| 500 / 404 for a page past the end | Not transient: do not retry. Only follow `next` URLs, which prevents this case |
| HTTP 400 `Not valid query parameter` | Configuration/adapter defect: do not retry; report the rejected parameter |
| HTTP 404 on a detail URL | Check the trailing slash first (required on rridata), then treat as an unknown RRID/period |
| Invalid/unexpected JSON | Fail the affected resource/batch and report schema or parsing error |
| New source field | Record schema drift and make it available for schema review/mapping |
| Removed or changed source field | Mark affected mappings as incompatible and block unsafe sync until resolved |
| Missing optional value | Preserve null/omit according to mapping policy |
| Missing required value | Reject affected record and report validation error |
| RRID mismatch | Do not associate the records; quarantine/report data-quality failure |
| Period ID mismatch | Do not attach ratings to the period; report association failure |
| Unmatched Z2 company | Keep in unmatched review; do not silently create/link a company |
| Duplicate source record | Resolve by stable key and idempotent upsert |
| Partial company failure | Continue independent safe work and mark run partially succeeded |
| Mapping version incompatible with target schema | Block the affected sync until an approved mapping/schema combination is available |

Retries must be bounded and should apply only to transient or explicitly refreshable failures. Permanent permission, authentication, schema, and mapping errors require correction rather than repeated retry.

## 15. Suggested Platform Components and Responsibilities

| Component | Responsibility |
|---|---|
| Integration Catalogue UI | Lists supported integrations and current connection state |
| Integration Configuration API | Creates and updates tenant-level integration settings |
| Credential/Secrets Provider | Stores and resolves protected credential references |
| OAuth Token Provider | Obtains, caches, expires, and renews RapidRatings tokens |
| RapidRatings API Client | Calls provider endpoints and handles HTTP details |
| RapidRatings DTOs | Models company, period, and rating response payloads |
| Schema Discovery Service | Builds source schema snapshots from metadata/responses |
| Schema Registry | Versions source and target schema references |
| Mapping Registry/Engine | Stores and applies versioned source-to-target mappings |
| Company Matching Service | Resolves RRID to the correct Z2 Company/Supplier |
| Sync Orchestrator | Controls sync lifecycle, batching, progress, and idempotency |
| Scheduler / Queue | Triggers manual and scheduled sync jobs |
| Upsert/Persistence Layer | Writes mapped Company and period records safely |
| Sync History Service | Records run status, counts, and error summaries |
| Change History/Audit Service | Records configuration, mapping, schema, and schedule changes |
| Monitoring/Alerting | Tracks API health, failures, retries, latency, and stale data |

## 16. Suggested Platform Configuration Entities

The following names are conceptual and should be aligned with the existing Z2 Data Integrations contracts before implementation.

- **IntegrationDefinition:** provider key, display name, adapter, supported capabilities, and version.
- **TenantIntegration:** tenant, provider, environment, status, selected portfolios, and configuration version.
- **IntegrationCredentialReference:** secret-manager reference and rotation metadata; no plaintext secret.
- **SourceSchemaVersion:** source schema snapshot, provider resource, discovery time, and version.
- **TargetSchemaReference:** Company DataModel identifier and schema version.
- **FieldMapping:** source path, target property path, transformation, required flag, and mapping version.
- **MappingValidationResult:** validation status, incompatibilities, warnings, and validation timestamp.
- **SyncSchedule:** cadence, time zone, enabled state, scope, retry policy, and next execution.
- **SyncRun:** trigger, status, timestamps, counts, schema/mapping/config versions, and error summary.
- **ExternalIdentityMapping:** tenant, provider, RRID, and Z2 Company ID.
- **IntegrationChangeHistory:** actor, action, changed fields, timestamp, versions, and correlation ID.

## 17. Illustrative Platform API Contracts

The following are proposed platform routes, not confirmed existing Z2 routes. Align route naming, authorization, request conventions, and response contracts with platform standards.

| Method | Illustrative route | Purpose |
|---|---|---|
| `POST` | `/api/integrations/rapidratings/connect` | Save connection reference and validate connectivity |
| `GET` | `/api/integrations/rapidratings/status` | Get connection and integration status |
| `GET` | `/api/integrations/rapidratings/portfolios` | Discover accessible portfolios |
| `GET` | `/api/integrations/rapidratings/companies` | Discover companies for a selected scope |
| `POST` | `/api/integrations/rapidratings/schema/discover` | Start source schema discovery |
| `GET` | `/api/integrations/rapidratings/schema/versions` | List source schema versions |
| `GET` | `/api/integrations/rapidratings/schema/diff` | Compare current and previous schema versions |
| `GET` / `PUT` | `/api/integrations/rapidratings/mappings` | Read or update mapping configuration |
| `POST` | `/api/integrations/rapidratings/mappings/validate` | Validate source-to-target mapping |
| `POST` | `/api/integrations/rapidratings/sync-runs` | Start a manual sync |
| `GET` | `/api/integrations/rapidratings/sync-runs` | List sync history |
| `GET` | `/api/integrations/rapidratings/sync-runs/{runId}` | Read sync-run details |
| `GET` / `PUT` | `/api/integrations/rapidratings/schedule` | Read or update the sync schedule |
| `GET` | `/api/integrations/rapidratings/change-history` | Read configuration/audit history |

All platform routes require tenant-aware authorization. The UI calls these platform endpoints; only the server-side adapter calls RapidRatings.

## 18. ServiceNow and Downstream Workflow Integration

The normalized company and financial-risk data can be consumed by Z2 supplier-risk dashboards and approved downstream workflows. ServiceNow Integration Hub may be used as a workflow integration layer for actions such as creating or updating a risk case/task, routing an alert for review, or tracking remediation.

Keep responsibilities clear:

- Z2 Data Integrations owns the RapidRatings connection, source-to-Z2 mapping, canonical synchronization state, and integration audit trail unless the platform explicitly assigns those responsibilities elsewhere.
- ServiceNow may consume normalized Z2 information or trigger approved workflows.
- Avoid maintaining duplicate schedules, conflicting retry policies, or competing copies of the same canonical supplier-risk data.
- Outreach remains a separate feature with its own provider permissions, consent/approval, and audit requirements.

## 19. Testing Strategy

### 19.1 Unit tests

- OAuth request construction, token parsing, expiry calculation, cache, and refresh threshold.
- Secret resolution and prevention of token/secret logging.
- Company response parsing, including nullable and array fields.
- Period response parsing and financial-value null handling.
- Ratings response parsing and `termPd.year2` through `termPd.year10` mapping.
- Company RRID and period ID association validation.
- Source-to-target type conversion and required-field validation.
- Company matching and unmatched handling.
- Idempotent company and period upsert behavior.
- Mapping/schema compatibility and schema drift detection.
- Retry classification and backoff limits.

### 19.2 Sandbox integration tests

- Obtain an access token using valid credentials.
- Handle invalid credentials safely.
- Retrieve portfolios.
- Retrieve companies for a selected portfolio.
- Retrieve company periods for a known RRID.
- Retrieve ratings for the same RRID and period.
- Verify `latestRatings` handling against the dedicated ratings endpoint.
- Verify pagination and rate-limit behavior where supported.
- Verify expired-token handling and one-time refresh/retry.
- Verify permission errors are not misclassified as token expiry.

- Verify that out-of-range pages (404 / 500) are never requested and never retried.
- Verify unknown query parameters are not sent (400).

Use matched test fixtures: company RRID, period response, and ratings response must refer to the same company. The original three supplied files mix two companies (Section 5.4) and cannot alone prove a valid end-to-end join; use the matched `XAZPzdJ` set in `docs/api/`, plus a Rite Aid (`KawjbGJ`) set as a negative test that a shared period ID is not joined across companies.

### 19.3 End-to-end tests

1. Configure RapidRatings for a tenant.
2. Test connection and confirm status.
3. Discover and select a portfolio.
4. Discover source schema and save a schema version.
5. Select the Z2 Company DataModel and configure field mappings.
6. Validate and approve mappings.
7. Match a RapidRatings RRID to a Z2 Company/Supplier.
8. Run a manual sync and inspect the sync history.
9. Verify company details, period financials, and period ratings in the target Company record.
10. Run the same sync again and verify no duplicates.
11. Configure a scheduled sync and verify the scheduled trigger uses the same processing pipeline.
12. Change a mapping or schedule and verify change history.
13. Simulate partial failures, null fields, mismatched RRIDs, mismatched period IDs, and schema drift.
14. Verify tenant isolation and secret protection.

## 20. Deployment and Environment Strategy

- Keep sandbox and production base URLs and credentials separate.
- Resolve credentials through the approved secret manager per tenant/environment.
- Use the platform's standard deployment pipeline for the adapter and sync workers.
- Apply tenant-level enablement or feature flags for controlled rollout.
- Start with one pilot tenant and a limited portfolio.
- Monitor authentication failures, provider API errors, sync duration, retry volume, and data freshness.
- Define operational ownership for failed runs, mapping changes, credential rotation, and stale supplier data.

## 21. Open Questions and Decisions Required

The following points require confirmation before the design is production-final:

Status as of 2026-10-30: **Resolved** = answered by the sandbox verification; **Sandbox verified** = observed in the sandbox, production confirmation still needed; **Open** = no answer yet.

1. **Matched API fixtures** — *Resolved.* The supplied samples came from two companies (Section 5.4); a matched `XAZPzdJ` set is documented in `docs/api/`.
2. **Official API contract** — *Sandbox verified* (Section 5.0): envelopes, fixed page sizes, accepted filters, newest-first ordering, rate-limit headers, error formats. Confirm the same for production, including timeouts and whether the portfolio list returns 500 for any page past page 1 (observed in the sandbox).
3. **Authentication contract** — *Sandbox verified* (Section 6.2): request format, `expires_in` 1800, no refresh token, scope `uid introspection openid`. Confirm production scopes and lifetime.
4. **Rating semantics** — *Partially answered* (Section 5.3): ranges, percentage units and observed qualitative values are recorded. Confirm the official definitions and the allowed enum values with RapidRatings before display rules or enums are fixed.
5. **Financial units** — *Partially answered:* amounts are in the company's `currency` at the company's `dataScale` (e.g. `millions`). Confirm whether `dataScale` can differ between periods of one company and whether any conversion is required.
6. **DataModel runtime:** Confirm whether a `PROPS_ARRAY` child can include a nested group. If not, use direct child properties for the period item and flatten `termPd` by year.
7. **Target identity:** Confirm whether the existing Z2 Company primary key can be linked to supplier records and where the tenant-scoped RRID mapping is stored.
8. **`latestRatings` precedence:** Confirm whether it is a convenience summary or an authoritative source for latest-period data relative to the ratings endpoint.
9. **History policy:** Confirm how changed historical ratings are handled and whether previous values or only the latest source state must be retained.
10. **Incremental sync** — *Sandbox verified: not available.* No modified-since/cursor/ETag mechanism exists on the data API (Section 10.4). Ask RapidRatings whether production offers one.
11. **Retention:** Confirm contractual permission and retention duration for raw payloads, report links and downloaded reports.
12. **Portfolio behavior:** Confirm multiple-portfolio support and how companies shared across portfolios should be represented. The sandbox account sees 5 portfolios; whether a company can appear in more than one was not checked.
13. **Scheduling standards:** Confirm supported cadence, time-zone behavior, overlap policy, and scheduler API.
14. **ServiceNow boundary:** Confirm whether ServiceNow only consumes Z2-normalized data/triggers workflows or owns any integration activity.
15. **Outreach:** Confirm whether outreach is required in the first release; otherwise keep it out of scope. Note that creating an outreach request sends real emails to the counterparty, even from the sandbox.
16. **`financials` resource:** The company record links to `/companies/{rrid}/financials/`, which was not exercised. Confirm whether it adds data beyond `periods/` before deciding to ignore it.
17. **Private companies:** `dataConfidential` is `PUB` for public companies; confirm the values used for private companies (the sandbox has a "z2Data Sandbox-Private" portfolio) and any display or sharing restrictions they carry.

## 22. Recommended Delivery Plan

### Phase 1 – Integration foundation and authentication

- Register RapidRatings in the integration catalogue.
- Implement tenant configuration and secure credential references.
- Implement OAuth token acquisition, caching, refresh, and connection validation.
- Implement provider client configuration for sandbox and production.

### Phase 2 – Source schema discovery and target model

- Implement company, period, and rating response DTOs from verified source contracts.
- Implement source schema snapshots and schema diffing.
- Extend the existing Company DataModel under `Financial` with the approved RapidRatings property groups.
- Validate `NESTED` and `PROPS_ARRAY` support in the deployed schema runtime.

### Phase 3 – Schema mapping and company matching

- Register the Z2 Company target schema version.
- Implement source-to-target field mapping and type conversion.
- Add mapping preview, validation, approval, and versioning.
- Implement RRID-to-Z2 Company matching and unmatched review.

### Phase 4 – Data synchronization and manual runs

- Implement portfolio/company discovery.
- Implement periods and ratings retrieval.
- Validate RRID and period association.
- Implement period-preserving, idempotent upserts.
- Implement manual sync, run progress, sync history, and partial failure reporting.

### Phase 5 – Scheduling, audit, and operations

- Implement scheduled sync using the same orchestration service.
- Implement schedule configuration and schedule change history.
- Implement integration/mapping/schema change audit history.
- Add operational metrics, alerts, retry controls, and tenant-isolation tests.

### Phase 6 – Downstream consumption

- Expose normalized information to supplier-risk views and dashboards.
- Integrate approved ServiceNow workflows where required.
- Consider outreach only as a separately scoped and authorized feature.

## 23. Acceptance Criteria

The integration is ready for controlled production rollout when:

- A tenant administrator can configure and validate a RapidRatings connection without exposing credentials.
- The integration can discover portfolios and companies available to the tenant's RapidRatings account.
- Company RRID is preserved and mapped to the correct Z2 Company/Supplier through an approved matching process.
- Source schemas are versioned and schema changes can be reviewed.
- Field mappings can be configured, validated, approved, and versioned.
- The existing Company DataModel can store company details and multiple period records using supported property-group types.
- Period financial fields and period rating fields are associated using matching RRID and period ID values.
- `termPd` year-specific values are preserved.
- Null values and provider currency/scale are handled without invented defaults or silent conversion.
- Re-running the same sync does not create duplicate company or period records.
- Manual and scheduled syncs use the same orchestration pipeline.
- Sync history shows trigger, status, timestamps, scope, counts, schema/mapping versions, and sanitized errors.
- Schedule and integration configuration changes are auditable.
- Schema drift, partial failures, authentication errors, and provider throttling are handled safely.
- Tenant credentials and data remain isolated.

## 24. Reference Prototype (this repository)

A React-only prototype dashboard in this repository exercises the design against the sandbox. It is **not** the production implementation: it has no backend, no Z2 persistence and no scheduler. It shows how the platform components in Section 15 behave.

| Platform component (Section 15) | Prototype counterpart | Limitation |
|---|---|---|
| OAuth Token Provider | Dev-server proxy in `vite.config.ts`: client-credentials exchange with credentials from `.env`, in-memory token cache, renewal 60 s before expiry, one retry on 401/403 | Runs only under `npm run dev`; production needs a backend/API gateway with a secret manager |
| RapidRatings API Client | `src/services/httpClient.ts` + one service module per resource; follows `next` links only, bounded retry on 429/502/503/504 for GETs, secret-free error normalisation | Browser-side; credentials never reach it |
| Schema Discovery Service / Schema Registry | Schema Explorer: declared (outreach OpenAPI) vs inferred (rridata) fields, live discovery, snapshots and diff | Snapshots kept in browser storage, not versioned or approved |
| Mapping Registry / Engine | Schema Mapping: source → target table, type compatibility, required/unmapped/stale checks, JSON export/import | The target is an **illustrative** A1 schema, not `A1company.json` |
| Sync Orchestrator / Sync History | "Sync now" per resource with progress, no duplicate in-flight runs, partial-failure handling, retry | Records are mapped and validated in memory and **discarded**; no A1 write; history is browser-local metadata |
| Scheduler | Schedule settings and next-run calculation (time-zone aware) | Configuration only; nothing executes on it |
| Company Matching, Change History/Audit, tenant isolation | Not implemented | – |

The prototype also has a mock mode that generates fictional data following the verified response shapes and provider rules, for UI work without credentials. See the repository `README.md`.

## 25. Source Files and Design Basis

This proposal is based on the following supplied or previously prepared artifacts:

- `A1company.json` – existing Z2 Company DataModel structure and examples of `NESTED` and `PROPS_ARRAY` property groups.
- `Companies.json` – RapidRatings company response, including company identity, resource links, metadata, report links, and `latestRatings`.
- `PeriodById.json` – RapidRatings individual-period financial response.
- `GetRatingByPeriodId.json` – RapidRatings individual-period rating response, including `termPd` and additional rating metrics.
- `rapidratings_integration_solution_overview.md` – earlier integration requirements and platform design notes, including authentication, supplier matching, scheduling, sync history, and change history.
- `docs/api/01-authentication.md` … `06-outreach-requests.md` and `docs/RapidRatings_API_Reference.md` – endpoint behaviour verified against the sandbox on 2026-10-28, with real (redacted) responses and error bodies.
- `docs/postman/` – Postman collection and sandbox environment for the same endpoints.
- `https://api.sandbox.rrilabs.co/openapi.yaml` – RapidRatings OpenAPI specification for the outreach API (retrieved 2026-10-28).

**Document note:** This is a proposed solution overview, not a claim that all platform routes, Data Integrations contracts, schema-runtime capabilities, or production RapidRatings permissions are already implemented. Sandbox observations are identified as such; unresolved API and platform behavior is listed in Section 21 for confirmation.
