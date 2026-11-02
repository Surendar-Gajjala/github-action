# RapidRatings Integration – Solution Overview

**Program:** Z2 Enterprise – Data Integrations  
**Integration:** RapidRatings  
**Target DataModel:** Existing Z2 `Company` DataModel (`A1company.json`)  
**Source environment investigated:** RapidRatings sandbox  
**Document status:** Proposed solution design based on the supplied API response samples and existing Company DataModel. Confirm open API-contract and platform-runtime questions before production rollout.

---

## 1. Executive Summary

This document describes the proposed end-to-end integration of RapidRatings with the Data Integrations capability in Z2 Enterprise. It covers connection authentication, source-schema discovery, mapping to the Z2 Company DataModel, schema and data synchronization, manual and scheduled execution, and synchronization/change history.

The integration allows a tenant administrator to connect a RapidRatings account, select the portfolios to monitor, discover companies, associate RapidRatings companies with existing Z2 companies/suppliers, retrieve reporting-period financial data and ratings, and synchronize the normalized information into Z2 Enterprise.

The design uses a reusable integration framework for common capabilities and a provider-specific RapidRatings adapter for API communication and source-specific transformation. Z2 remains responsible for tenant configuration, credential references, schema registry, mapping configuration, sync orchestration, scheduling, run history, and audit history. The RapidRatings adapter is responsible for authentication with RapidRatings, calling its APIs, parsing provider responses, and preparing source records for the platform mapping engine.

The target is the existing `Company` DataModel in `A1company.json`. Add `RapidRatings` as a new root-level `NESTED` property group on Company, following the existing `Kharon Risk Data` pattern. Inside it, add separate `Periods` and `Ratings` `PROPS_ARRAY` collections. This avoids assuming that the deployed runtime supports a `NESTED` group inside the existing `Financial` group or inside a `PROPS_ARRAY` item. The Company Details page may visually place the section under a heading such as `Financial › RapidRatings`, while the actual Raw Properties path remains `rapidratings.*`.

> **Fixture identity clarification:** The corrected company, period, and rating examples supplied for this design all refer to RRID `KawjbGJ` (Rite Aid Corporation). The period and rating resources are for period `2019YE`, using `/companies/KawjbGJ/periods/2019YE/` and `/companies/KawjbGJ/ratings/2019YE/`. Join these records using both RRID and period ID. Earlier example payloads with a different RRID should be treated as separate test fixtures, not merged into this company.

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
- OAuth 2.0 Client Credentials flow as observed in the sandbox investigation, with backend-only token acquisition and secret storage.
- RapidRatings data API host configuration.
- Portfolio and company discovery.
- Company details, company reporting periods, and period-specific ratings retrieval.
- Source schema discovery, versioning, schema-diff review, and mapping compatibility validation.
- Mapping to the existing Z2 `Company` DataModel.
- RRID-to-Z2 Company/Supplier matching and unmatched review.
- Schema and data synchronization; manual and scheduled execution through one orchestration pipeline.
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
│      ├── Financial (existing NESTED group)                            │
│      │     ├── Moody's Credit Rating                                 │
│      │     └── D&B Rating                                            │
│      └── RapidRatings (new root-level NESTED group)                  │
│            ├── Company profile properties                            │
│            ├── Periods (PROPS_ARRAY)                                  │
│            └── Ratings (PROPS_ARRAY)                                  │
│                                                                      │
│  Supplier-risk views / dashboards / approved downstream workflows    │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.1 Architecture principles

- The browser must call Z2 platform APIs only. It must not call RapidRatings directly or contain client secrets.
- Common integration lifecycle capabilities belong to the Z2 Data Integrations framework.
- RapidRatings endpoint details and provider-specific response parsing belong in the RapidRatings adapter.
- The existing Z2 `Company` DataModel is the target schema for this design. Do not create a separate top-level Company object.
- Add `RapidRatings` as a root-level `NESTED` group on Company, alongside existing root groups such as `Financial` and `Kharon Risk Data`.
- Put `Periods` and `Ratings` directly under `RapidRatings` as separate `PROPS_ARRAY` properties.
- Do not assume a nested `NESTED`-inside-`NESTED` or `NESTED`-inside-`PROPS_ARRAY` structure until runtime support is proven.
- Company identity, reporting-period identity, and rating values must remain distinguishable.
- Manual and scheduled runs must invoke the same orchestration and mapping pipeline.
- Every data sync must record the integration configuration and mapping versions used.

## 5. RapidRatings API Reference and Source Data

The following endpoints were identified and exercised during the sandbox investigation. Exact pagination, filters, ordering, rate limits, and response-envelope behavior must be confirmed against the contracted API specification before production implementation.

| Capability | Method | Sandbox endpoint | Purpose |
|---|---|---|---|
| OAuth token | POST | `https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` | Obtain an access token |
| Portfolios | GET | `https://rridata.sandbox.rrilabs.co/v2.0/portfolios/` | List portfolios visible to the account |
| Companies | GET | `https://rridata.sandbox.rrilabs.co/v2.0/companies/` | List companies; portfolio filtering was used during investigation |
| Company periods | GET | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/` | Retrieve period financial data for a company |
| Company ratings | GET | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/` | Retrieve ratings for a company |
| Individual period / rating resources | GET | Resource links returned by company/period/rating responses | Retrieve a specific period or rating where supported by the returned `href` |
| Outreach requests | API-specific | `https://api.sandbox.rrilabs.co/outreach-requests` | Separate outreach capability; not part of the initial data synchronization scope |

The data APIs use the `rridata.sandbox.rrilabs.co` host. Outreach was investigated on a separate `api.sandbox.rrilabs.co` host and should be treated as a separately authorized capability. Its method, request body, scopes/audience, token acceptance, and permission model must be confirmed independently; do not assume that the data API token is automatically valid for Outreach.

### 5.1 Company response – `Companies.json`

The supplied company response contains the following fields:

| Source field | Meaning / handling |
|---|---|
| `id` | RapidRatings company identifier (RRID); preserve as the external company key |
| `name` | Source company name |
| `country` | Source country value; normalize only using an agreed country mapping |
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

The sample company is `Rite Aid Corporation`, with RRID `KawjbGJ`, ticker `RAD`, exchange `US`, country `USA`, and sector `Retail`.

`latestRatings` is present as an array within the company response. It includes records such as `id`, `href`, `eqyYear`, `period`, `financialDate`, `reportingPeriod`, `fhr`, `chs`, `probabilityDefault`, `termPd`, `delta`, `simulatedFhr`, profitability/efficiency indicators, and qualitative fields such as `leverage`, `liquidity`, and `earningsPerformance`. Treat it as a source-provided latest-rating view. During a full historical sync, reconcile it with the dedicated ratings endpoint rather than inserting the same period twice.

### 5.2 Period response – `PeriodById.json`

The corrected individual-period response is for RRID `KawjbGJ` and period `2019YE`. Its resource URL is `https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/periods/2019YE/`. It contains:

| Source field | Example | Suggested interpretation |
|---|---:|---|
| `id` | `2019YE` | Stable source period identifier |
| `eqyYear` | `2019` | Reporting year supplied by RapidRatings |
| `period` | `YE` | Period type supplied by RapidRatings |
| `financialDate` | `2019-03-02` | Financial statement date |
| `href` | Resource URL | Source period resource link |
| `currency` | `USD` | Currency for the financial values |
| `fixedAssets` | `1308.514` | Financial statement metric |
| `cashFromOperationsFromCashFlow` | `null` | Nullable financial metric |
| `debitOwnedWithinOneYear` | `16.111` | Financial statement metric; preserve provider spelling unless an approved canonical target name is used |
| `abnormalItems` | `-483.738` | Financial statement metric |
| `netInvestingCashFlow` | `466.188` | Financial statement metric |
| `termLoans` | `3478.649` | Financial statement metric |
| `financialAssets` | `null` | Nullable financial metric |
| `totalShareholderEquity` | `1186.69` | Financial statement metric |
| `salesRevenue` | `21639.557` | Financial statement metric |
| `interestReceived` | `null` | Nullable financial metric |
| `totalCurrentLiabilities` | `2443.135` | Financial statement metric |
| `totalCurrentAssets` | `4101.719` | Financial statement metric |
| `bankCashBalances` | `144.353` | Financial statement metric |
| `totalAssets` | `7591.367` | Financial statement metric |
| `netProfitAfterTax` | `-666.954` | Financial statement metric |
| `netFundingCashFlow` | `-540.504` | Financial statement metric |
| `companyTaxExpense` | `77.477` | Financial statement metric |
| `totalLiabilities` | `6404.677` | Financial statement metric |
| `earningsBeforeInterestAndTax` | `-399.761` | Financial statement metric |
| `interestExpense` | `227.728` | Financial statement metric |

Numeric examples are decimals in the supplied payload. Null values must remain null; do not convert a missing financial value into zero.

### 5.3 Ratings response – `GetRatingByPeriodId.json`

The corrected rating response is for the same company RRID `KawjbGJ` and period `2019YE`. Its resource URL is `https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/ratings/2019YE/`. The supplied response contains:

| Source field | Example | Suggested target type (confirm with platform type registry) |
|---|---:|---|
| `id` | `2019YE` | STRING |
| `href` | Resource URL | STRING |
| `eqyYear` | `2019` | INTEGER |
| `period` | `YE` | STRING |
| `financialDate` | `2019-03-02` | DATE |
| `reportingPeriod` | `2020-05-15` | DATE |
| `fhr` | `22` | INTEGER, if the platform accepts the observed score range |
| `chs` | `29` | INTEGER, if the platform accepts the observed score range |
| `probabilityDefault` | `8.4984` | DOUBLE; preserve the source numeric value and confirm unit/scale |
| `termPd` | Object with `year2` through `year10` | Prefer scalar numeric properties in the period record unless nested-object support is confirmed |
| `delta` | `-11` | INTEGER or DOUBLE, based on full source contract |
| `simulatedFhr` | `28` | Numeric score |
| `simulatedFhrDelta` | `-5` | Numeric delta |
| `operatingProfitability` | `17` | Numeric score |
| `netProfitability` | `5` | Numeric score |
| `capitalStructureEfficiency` | `73` | Numeric score |
| `costStructureEfficiency` | `43` | Numeric score |
| `leverage` | `Weak` | STRING or controlled enum only if allowed values are confirmed |
| `liquidity` | `Weak` | STRING or controlled enum only if allowed values are confirmed |
| `earningsPerformance` | `Weak` | STRING or controlled enum only if allowed values are confirmed |

`termPd` contains the year-specific numeric values `year2`, `year3`, `year4`, `year5`, `year6`, `year7`, `year8`, `year9`, and `year10`. The sample values are decimal numbers. Do not collapse this object into a single number or discard its horizon labels.

### 5.4 Company, period, and ratings association

The corrected examples form a matched company dataset:

| Resource | Company RRID | Period ID | Resource path |
|---|---|---|---|
| Company | `KawjbGJ` | — | `/companies/KawjbGJ/` |
| Period financials | `KawjbGJ` | `2019YE` | `/companies/KawjbGJ/periods/2019YE/` |
| Period ratings | `KawjbGJ` | `2019YE` | `/companies/KawjbGJ/ratings/2019YE/` |

The company response's `latestRatings` array also contains a `2019YE` record for RRID `KawjbGJ`. In the corrected example, its `financialDate`, `fhr`, `chs`, `probabilityDefault`, `delta`, simulated FHR, and qualitative indicators align with the standalone rating response. However, the `termPd` year values differ between the embedded `latestRatings` item and the separately supplied rating response. Do not silently merge or overwrite these values. Treat the dedicated period ratings endpoint as the canonical detailed record for that period unless the provider contract specifies otherwise; retain the embedded `latestRatings` values as a separately identified source view or use them for comparison/diagnostics. Record retrieval source and timestamp if both are retained.

**Mandatory join rule:** Associate data using `(company RRID, period ID)`. For this example the join key is `(KawjbGJ, 2019YE)`. Validate the RRID in each resource URL and the period identifier in each payload. Never join by period ID alone, company name, response order, or array index.

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
7. It refreshes the token before expiry and retries an eligible failed request once with the newly obtained token.

The exact token request body, response fields, and grant parameters must follow the contracted API documentation and the validated sandbox response. Do not hard-code secrets or tokens.

**Frontend boundary:** The React UI sends the configured Client ID/Client Secret to the Z2 backend over HTTPS only for secure credential registration. The backend stores the secret in the approved secret manager and performs the token request. The browser must never call the RapidRatings token endpoint directly, store the client secret, receive the access token, or call the provider's business endpoints directly.

### 6.3 Token lifecycle and observed error behavior

The sandbox investigation observed:

- Approximate token lifetime: 30 minutes.
- Refresh threshold used by the client: fewer than 60 seconds remaining.
- The data API returned `403` in the observed expired-token scenario.
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

### 7.3 Target schema reference and A1Company fit

The inspected `A1company.json` contains concrete schema precedents:

- `Financial` is a root-level `NESTED` group containing scalar properties `moodys_credit_rating` and `dnb_rating`.
- `Kharon Risk Data` is a root-level `NESTED` group containing scalar children and a `Risks` property with `dataType: PROPS_ARRAY`.
- The `Risks` array contains direct leaf child properties with paths such as `kharon.risks.risk_id`.
- The uploaded `Z2 Data` example is another `NESTED` group with direct child fields.

The safest schema extension that matches demonstrated conventions is therefore:

```text
Company (existing root)
├── Financial (existing NESTED)
│   ├── Moody's Credit Rating
│   └── D&B Rating
├── Kharon Risk Data (existing NESTED)
└── RapidRatings (NEW root-level NESTED)
    ├── RRID
    ├── Source Company Name
    ├── Source Country
    ├── Ticker
    ├── Exchange
    ├── Sector
    ├── Company / Financials / Periods / Ratings resource URLs
    ├── Currency
    ├── Data Scale
    ├── Data Confidentiality
    ├── Report Links
    ├── Periods (PROPS_ARRAY)
    │   ├── Period ID
    │   ├── EQY Year
    │   ├── Period
    │   ├── Financial Date
    │   ├── Currency
    │   ├── Period URL
    │   └── All period financial fields (direct leaf children)
    └── Ratings (PROPS_ARRAY)
        ├── Period ID (the source rating resource id in the observed API)
        ├── EQY Year
        ├── Period
        ├── Financial Date
        ├── Reporting Period
        ├── Rating URL
        ├── FHR / CHS / Probability Default
        ├── Term PD Year 2 through Year 10 (flattened direct children)
        ├── Delta / Simulated FHR / Simulated FHR Delta
        ├── Profitability and efficiency metrics
        └── Leverage / Liquidity / Earnings Performance
```

**Placement decision:** `RapidRatings` is a root-level sibling of `Financial`, not structurally nested inside it. This is deliberate: the existing file demonstrates a root-level `NESTED` group containing a `PROPS_ARRAY`, but does not prove support for `Financial (NESTED) → RapidRatings (NESTED)`. The Company Details layout can still label its visible section `Financial › RapidRatings` so users find it beside the other financial providers. If the platform later confirms nested-group-in-nested-group support, the property group may be moved under `financial` in a controlled DataModel version migration.

**Collection decision:** Periods and Ratings remain separate arrays because the provider exposes separate collections. Their association is enforced by the integration using RRID plus period ID, not by array position. This preserves independent source records and avoids pretending that every period must have a rating.

### 7.4 Target schema extension rules

When generating or applying the Company schema extension:

- Follow the exact metadata structure and conventions found in `A1company.json`.
- Use `NESTED` for the root-level RapidRatings group and `PROPS_ARRAY` for repeatable Periods and Ratings records.
- Use direct leaf properties within each array item. Flatten `termPd.year2` through `termPd.year10` into separate numeric child properties unless the runtime supports structured child groups in array items.
- Use the correct existing `dataModelType` and `extDataModelId` according to platform schema rules; do not invent IDs.
- Generate new property IDs and timestamps through the platform's supported mechanism; never reuse IDs.
- Keep `name`, `dbName`, `label`, `path`, `dataType`, and child paths consistent.
- Preserve all existing Company properties and IDs.
- Avoid duplicate properties where an existing Company field can legitimately be reused.
- Preserve external source values and use explicit transformation rules for normalized values.
- `dataConfidential` is a STRING in the supplied company example (value `PUB`), not a BOOLEAN. Preserve its source representation unless the provider contract specifies otherwise.
- `externalSourceIds` is an array in the supplied response. Use a platform-supported string-array type if available; otherwise use a supported structured `JSON` property and retain the array as an array.
- Do not populate fabricated dates, IDs, or metadata values in a persisted schema artifact.
- Validate the proposed schema against the canonical DataModel schema/runtime before deployment.

### 7.5 Illustrative A1Company property fragment

The fragment below shows the intended shape and path conventions. It is deliberately abbreviated; every API field in Sections 8.2–8.4 must be added before activation. Platform-generated fields such as `id`, `dataEnvId`, timestamps, and the correct `extDataModelId` must be supplied by the schema-management process. Use the exact full metadata shape required by the platform when saving.

```json
{
  "name": "RapidRatings",
  "dbName": "rapidratings",
  "path": "rapidratings",
  "label": "RapidRatings",
  "description": "Company profile, reporting-period financial data, and risk ratings supplied by RapidRatings.",
  "isPrimaryKey": false,
  "required": false,
  "searchable": false,
  "filterable": false,
  "dataType": "NESTED",
  "dataModelType": "CORE",
  "canUpload": true,
  "hidden": false,
  "properties": [
    {
      "name": "RRID",
      "dbName": "rrid",
      "path": "rapidratings.rrid",
      "label": "RapidRatings ID",
      "dataType": "STRING",
      "properties": [],
      "config": {},
      "uiHints": {}
    },
    {
      "name": "Source Company Name",
      "dbName": "source_company_name",
      "path": "rapidratings.source_company_name",
      "label": "Source Company Name",
      "dataType": "STRING",
      "properties": [],
      "config": {},
      "uiHints": {}
    },
    {
      "name": "Ticker",
      "dbName": "ticker",
      "path": "rapidratings.ticker",
      "label": "Ticker",
      "dataType": "STRING",
      "properties": [],
      "config": {},
      "uiHints": {}
    },
    {
      "name": "Periods",
      "dbName": "periods",
      "path": "rapidratings.periods",
      "label": "Periods",
      "dataType": "PROPS_ARRAY",
      "properties": [
        {
          "name": "Period ID",
          "dbName": "period_id",
          "path": "rapidratings.periods.period_id",
          "label": "Period ID",
          "dataType": "STRING",
          "properties": [],
          "config": {},
          "uiHints": {}
        },
        {
          "name": "EQY Year",
          "dbName": "eqy_year",
          "path": "rapidratings.periods.eqy_year",
          "label": "EQY Year",
          "dataType": "INTEGER",
          "properties": [],
          "config": {},
          "uiHints": {}
        },
        {
          "name": "Financial Date",
          "dbName": "financial_date",
          "path": "rapidratings.periods.financial_date",
          "label": "Financial Date",
          "dataType": "DATE",
          "properties": [],
          "config": {},
          "uiHints": {"displayType": "DATE"}
        },
        {
          "name": "Sales Revenue",
          "dbName": "sales_revenue",
          "path": "rapidratings.periods.sales_revenue",
          "label": "Sales Revenue",
          "dataType": "DOUBLE",
          "properties": [],
          "config": {},
          "uiHints": {}
        }
      ],
      "config": {},
      "uiHints": {}
    },
    {
      "name": "Ratings",
      "dbName": "ratings",
      "path": "rapidratings.ratings",
      "label": "Ratings",
      "dataType": "PROPS_ARRAY",
      "properties": [
        {
          "name": "Period ID",
          "dbName": "period_id",
          "path": "rapidratings.ratings.period_id",
          "label": "Period ID",
          "dataType": "STRING",
          "properties": [],
          "config": {},
          "uiHints": {}
        },
        {
          "name": "FHR",
          "dbName": "fhr",
          "path": "rapidratings.ratings.fhr",
          "label": "FHR",
          "dataType": "DOUBLE",
          "properties": [],
          "config": {},
          "uiHints": {}
        },
        {
          "name": "CHS",
          "dbName": "chs",
          "path": "rapidratings.ratings.chs",
          "label": "CHS",
          "dataType": "DOUBLE",
          "properties": [],
          "config": {},
          "uiHints": {}
        },
        {
          "name": "Term PD Year 2",
          "dbName": "term_pd_year_2",
          "path": "rapidratings.ratings.term_pd_year_2",
          "label": "Term PD Year 2",
          "dataType": "DOUBLE",
          "properties": [],
          "config": {},
          "uiHints": {}
        }
      ],
      "config": {},
      "uiHints": {}
    }
  ],
  "config": {},
  "uiHints": {}
}
```

Replicate the leaf-property pattern for all remaining fields. In particular, add the full Period financial-field list and all rating fields plus `term_pd_year_3` through `term_pd_year_10`. The example is not intended to be imported unchanged because it omits platform-generated metadata and many properties.

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

The following paths use the recommended root-level group (`rapidratings.*`). Company profile values must not overwrite canonical Z2 Company identity fields without an approved rule.

| RapidRatings source field | Proposed A1Company target path | Type / rule |
|---|---|---|
| `id` | `rapidratings.rrid` | STRING; required external identity; preserve exactly |
| `name` | `rapidratings.source_company_name` | STRING; source name only; do not replace Company Name automatically |
| `country` | `rapidratings.source_country` | STRING; preserve provider value such as `USA`; normalize only with approved country mapping |
| `ticker` | `rapidratings.ticker` | STRING; optional |
| `exchange` | `rapidratings.exchange` | STRING; optional |
| `sector` | `rapidratings.sector` | STRING; optional |
| `href` | `rapidratings.company_resource_url` | STRING; source link |
| `financials` | `rapidratings.financials_resource_url` | STRING; source link |
| `periods` | `rapidratings.periods_resource_url` | STRING; collection link |
| `ratings` | `rapidratings.ratings_resource_url` | STRING; collection link |
| `address` | `rapidratings.source_address` | STRING; empty in supplied sample; preserve null/empty distinction per mapping policy |
| `industryClassificationSystem` | `rapidratings.industry_classification_system` | STRING/nullable; verify contract |
| `industryClassificationCode` | `rapidratings.industry_classification_code` | STRING/nullable; preserve code formatting |
| `currency` | `rapidratings.currency` | STRING; preserve ISO/provider value |
| `dataScale` | `rapidratings.data_scale` | STRING; sample is `millions`; do not rescale silently |
| `dataConfidential` | `rapidratings.data_confidential` | STRING; sample is `PUB`; do not model as BOOLEAN |
| `externalSourceId` | `rapidratings.external_source_id` | STRING/nullable |
| `externalSourceIds` | `rapidratings.external_source_ids` | Array of strings; use supported string-array type or `JSON` if no native array type exists |
| `reportLinks.fhr` | `rapidratings.report_fhr_url` | STRING; report link |
| `reportLinks.financialDialog` | `rapidratings.report_financial_dialog_url` | STRING; report link |
| `reportLinks.peerBenchmark` | `rapidratings.report_peer_benchmark_url` | STRING; report link |

`latestRatings` is discovered as a source collection, but should not automatically be mapped into a second historical Ratings array. See Section 8.5 for the reconciliation policy.

### 8.3 Period financial mapping

All fields below are direct child properties of the `rapidratings.periods` `PROPS_ARRAY`; do not add a `Financials` nested group inside each array item unless the runtime explicitly supports it.

| RapidRatings period source field | A1Company target path | Type / rule |
|---|---|---|
| `id` | `rapidratings.periods.period_id` | STRING; stable period key |
| `eqyYear` | `rapidratings.periods.eqy_year` | INTEGER |
| `period` | `rapidratings.periods.period` | STRING; preserve codes such as `YE` |
| `financialDate` | `rapidratings.periods.financial_date` | DATE |
| `href` | `rapidratings.periods.period_resource_url` | STRING |
| `currency` | `rapidratings.periods.currency` | STRING; currency of the period values |
| `fixedAssets` | `rapidratings.periods.fixed_assets` | DOUBLE; preserve precision |
| `cashFromOperationsFromCashFlow` | `rapidratings.periods.cash_from_operations_from_cash_flow` | DOUBLE; nullable |
| `debitOwnedWithinOneYear` | `rapidratings.periods.debit_owned_within_one_year` | DOUBLE; source spelling retained in source mapping |
| `abnormalItems` | `rapidratings.periods.abnormal_items` | DOUBLE; nullable |
| `netInvestingCashFlow` | `rapidratings.periods.net_investing_cash_flow` | DOUBLE; nullable |
| `termLoans` | `rapidratings.periods.term_loans` | DOUBLE; nullable |
| `financialAssets` | `rapidratings.periods.financial_assets` | DOUBLE; nullable |
| `totalShareholderEquity` | `rapidratings.periods.total_shareholder_equity` | DOUBLE; nullable |
| `salesRevenue` | `rapidratings.periods.sales_revenue` | DOUBLE; nullable |
| `interestReceived` | `rapidratings.periods.interest_received` | DOUBLE; nullable |
| `totalCurrentLiabilities` | `rapidratings.periods.total_current_liabilities` | DOUBLE; nullable |
| `totalCurrentAssets` | `rapidratings.periods.total_current_assets` | DOUBLE; nullable |
| `bankCashBalances` | `rapidratings.periods.bank_cash_balances` | DOUBLE; nullable |
| `totalAssets` | `rapidratings.periods.total_assets` | DOUBLE; nullable |
| `netProfitAfterTax` | `rapidratings.periods.net_profit_after_tax` | DOUBLE; nullable |
| `netFundingCashFlow` | `rapidratings.periods.net_funding_cash_flow` | DOUBLE; nullable |
| `companyTaxExpense` | `rapidratings.periods.company_tax_expense` | DOUBLE; nullable |
| `totalLiabilities` | `rapidratings.periods.total_liabilities` | DOUBLE; nullable |
| `earningsBeforeInterestAndTax` | `rapidratings.periods.earnings_before_interest_and_tax` | DOUBLE; nullable |
| `interestExpense` | `rapidratings.periods.interest_expense` | DOUBLE; nullable |

The corrected `2019YE` example includes values such as sales revenue `21639.557`, total assets `7591.367`, total liabilities `6404.677`, net profit after tax `-666.954`, and nulls for selected metrics. Keep the associated currency (`USD`) and company-level data scale (`millions`) available with the record. Do not convert amounts without an approved, documented scale-normalization rule.

### 8.4 Rating mapping

All fields below are direct child properties of the `rapidratings.ratings` `PROPS_ARRAY`. The source rating `id` is the period identifier in the observed API response; map it to `period_id` and validate it against the matching Periods record.

| RapidRatings rating source field | A1Company target path | Type / rule |
|---|---|---|
| `id` | `rapidratings.ratings.period_id` | STRING; observed value `2019YE`; join key with Periods |
| `href` | `rapidratings.ratings.rating_resource_url` | STRING |
| `eqyYear` | `rapidratings.ratings.eqy_year` | INTEGER |
| `period` | `rapidratings.ratings.period` | STRING |
| `financialDate` | `rapidratings.ratings.financial_date` | DATE |
| `reportingPeriod` | `rapidratings.ratings.reporting_period` | DATE |
| `fhr` | `rapidratings.ratings.fhr` | DOUBLE; preserve source numeric value |
| `chs` | `rapidratings.ratings.chs` | DOUBLE; preserve source numeric value |
| `probabilityDefault` | `rapidratings.ratings.probability_default` | DOUBLE; confirm unit/scale before formatting or conversion |
| `termPd.year2` | `rapidratings.ratings.term_pd_year_2` | DOUBLE |
| `termPd.year3` | `rapidratings.ratings.term_pd_year_3` | DOUBLE |
| `termPd.year4` | `rapidratings.ratings.term_pd_year_4` | DOUBLE |
| `termPd.year5` | `rapidratings.ratings.term_pd_year_5` | DOUBLE |
| `termPd.year6` | `rapidratings.ratings.term_pd_year_6` | DOUBLE |
| `termPd.year7` | `rapidratings.ratings.term_pd_year_7` | DOUBLE |
| `termPd.year8` | `rapidratings.ratings.term_pd_year_8` | DOUBLE |
| `termPd.year9` | `rapidratings.ratings.term_pd_year_9` | DOUBLE |
| `termPd.year10` | `rapidratings.ratings.term_pd_year_10` | DOUBLE |
| `delta` | `rapidratings.ratings.delta` | DOUBLE; preserve sign |
| `simulatedFhr` | `rapidratings.ratings.simulated_fhr` | DOUBLE |
| `simulatedFhrDelta` | `rapidratings.ratings.simulated_fhr_delta` | DOUBLE; preserve sign |
| `operatingProfitability` | `rapidratings.ratings.operating_profitability` | DOUBLE |
| `netProfitability` | `rapidratings.ratings.net_profitability` | DOUBLE |
| `capitalStructureEfficiency` | `rapidratings.ratings.capital_structure_efficiency` | DOUBLE |
| `costStructureEfficiency` | `rapidratings.ratings.cost_structure_efficiency` | DOUBLE |
| `leverage` | `rapidratings.ratings.leverage` | STRING until provider allowed values/semantics are confirmed |
| `liquidity` | `rapidratings.ratings.liquidity` | STRING until provider allowed values/semantics are confirmed |
| `earningsPerformance` | `rapidratings.ratings.earnings_performance` | STRING until provider allowed values/semantics are confirmed |

Use `DOUBLE` for observed numeric rating metrics unless the platform's type registry or the complete provider contract requires another numeric type. Do not infer percentages, rating thresholds, or score meanings from field names alone.

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

### 8.7 Mapping configuration record

A field mapping is independent of the A1Company property definition. Store it as versioned integration metadata. A mapping row should contain:

| Mapping attribute | Purpose |
|---|---|
| `mappingId` | Platform-generated mapping identifier |
| `integrationId` / `tenantId` | Integration and tenant scope |
| `sourceSchemaVersion` | Exact source schema snapshot |
| `sourceResource` | Company, Periods, or Ratings |
| `sourcePath` | Exact JSON path, e.g. `termPd.year2` |
| `targetDataModel` | Existing Company DataModel |
| `targetSchemaVersion` | Target Company schema version |
| `targetPropertyPath` | Exact path such as `rapidratings.ratings.term_pd_year_2` |
| `sourceType` / `targetType` | Type compatibility metadata |
| `transformation` | Explicit conversion rule, or none |
| `nullPolicy` | Preserve null, omit, or reject according to property contract |
| `validationRules` | Type, range, date, and identity validation |
| `status` | Draft, Valid, Warning, Invalid, Approved, Active |
| `mappingVersion` | Version used by sync runs |
| `createdBy` / `modifiedBy` | Audit information |

### 8.8 Company Details presentation

Raw Properties and Company Details use different mechanisms:

- **Raw Properties:** driven by the Company DataModel hierarchy. Once the `rapidratings` `NESTED` property and its child properties exist, the group and its values should be discoverable in this view.
- **Company Details:** driven by a layout configuration that explicitly binds properties. Adding fields to `A1company.json` alone does not guarantee that the fields appear in the Details page.

Recommended visual organization:

```text
Company Details
└── Financial › RapidRatings      (visual section title)
    ├── Company Profile
    │   ├── RapidRatings ID
    │   ├── Source Company Name
    │   ├── Ticker / Exchange
    │   ├── Country / Sector
    │   └── Currency / Data Scale
    ├── Rating Summary             (selected latest period, if selection rules approved)
    │   ├── Period ID
    │   ├── FHR
    │   ├── CHS
    │   └── Probability Default
    ├── Periods                    (collection view/table)
    │   ├── Period ID
    │   ├── EQY Year
    │   ├── Financial Date
    │   ├── Currency
    │   ├── Sales Revenue
    │   ├── Total Assets
    │   └── Total Liabilities
    └── Ratings                    (collection view/table)
        ├── Period ID
        ├── FHR / CHS
        ├── Probability Default
        ├── Reporting Period
        ├── Leverage / Liquidity
        └── Other rating metrics
```

The existing Company Details configuration uses sections with `preset`, `columns`, `kind: fields`, and `bindings` whose targets refer to `objectProperty` `dbName` values. Existing array properties such as `z2.sanctions` and `kharon.risks` are also bound into their own layout sections. Follow those existing patterns; do not invent new renderer keys.

For example, the scalar summary section can use bindings to:

```json
{
  "kind": "objectProperty",
  "dbName": "rapidratings.rrid"
}
```

and:

```json
{
  "kind": "objectProperty",
  "dbName": "rapidratings.ticker"
}
```

Collection bindings should target the array property itself:

```json
{
  "kind": "objectProperty",
  "dbName": "rapidratings.periods"
}
```

```json
{
  "kind": "objectProperty",
  "dbName": "rapidratings.ratings"
}
```

Validate that the deployed Company Details renderer displays these `PROPS_ARRAY` bindings as the desired expandable table/list. If it does not, use the platform's supported collection renderer rather than embedding a custom unsupported layout property.

For a latest-rating summary, do not assume that the first element of `latestRatings` is the latest unless the provider contract guarantees ordering. Define a selection rule based on provider semantics and dates, or show the collection without a derived summary. Avoid persisting a duplicate summary if it can be rendered from the collection reliably.

### 8.9 Example mapped Company payload

The following logical example shows the shape after mapping the corrected `KawjbGJ` company, period, and rating resources. It is intentionally abbreviated; production mapping must include all available fields.

```json
{
  "company_name": "Rite Aid Corporation",
  "rapidratings": {
    "rrid": "KawjbGJ",
    "source_company_name": "Rite Aid Corporation",
    "source_country": "USA",
    "ticker": "RAD",
    "exchange": "US",
    "sector": "Retail",
    "currency": "USD",
    "data_scale": "millions",
    "data_confidential": "PUB",
    "periods": [
      {
        "period_id": "2019YE",
        "eqy_year": 2019,
        "period": "YE",
        "financial_date": "2019-03-02",
        "currency": "USD",
        "sales_revenue": 21639.557,
        "total_assets": 7591.367,
        "total_liabilities": 6404.677,
        "net_profit_after_tax": -666.954,
        "fixed_assets": 1308.514,
        "term_loans": 3478.649,
        "cash_from_operations_from_cash_flow": null,
        "financial_assets": null,
        "interest_received": null
      }
    ],
    "ratings": [
      {
        "period_id": "2019YE",
        "eqy_year": 2019,
        "period": "YE",
        "financial_date": "2019-03-02",
        "reporting_period": "2020-05-15",
        "fhr": 22,
        "chs": 29,
        "probability_default": 8.4984,
        "term_pd_year_2": 12.3,
        "term_pd_year_3": 16.82,
        "term_pd_year_4": 20.74,
        "term_pd_year_5": 24.45,
        "term_pd_year_6": 28.23,
        "term_pd_year_7": 32.27,
        "term_pd_year_8": 36.66,
        "term_pd_year_9": 41.44,
        "term_pd_year_10": 46.57,
        "delta": -11,
        "simulated_fhr": 28,
        "simulated_fhr_delta": -5,
        "operating_profitability": 17,
        "net_profitability": 5,
        "capital_structure_efficiency": 73,
        "cost_structure_efficiency": 43,
        "leverage": "Weak",
        "liquidity": "Weak",
        "earnings_performance": "Weak"
      }
    ]
  }
}
```

This example maintains the canonical Z2 `company_name`, keeps the RapidRatings company name source-specific, preserves nulls and decimal precision, and keeps Periods and Ratings as independent collections linked by `period_id` within the same RRID.

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

If a ratings resource contains more than one record for a period, or if the API introduces another rating identifier/dimension, extend the stable key based on the actual API contract. Do not assume one rating per period until this is confirmed.

### 10.4 Full and incremental synchronization

- **Full sync:** Retrieve the selected portfolio/company scope and all available period/rating data supported by the API. Use bounded batches and pagination.
- **Incremental sync:** Use a modified-since filter, cursor, ETag, or update marker only if the RapidRatings API explicitly supports it and the behavior is verified. Do not invent a filter parameter.
- If a reliable incremental mechanism is unavailable, retrieve the required resources and perform idempotent upserts using stable external keys.
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

### 11.4 Separate schema and data schedules

The platform may configure schema checks and business-data synchronization independently:

| Schedule | Typical purpose | Safe behavior |
|---|---|---|
| Schema refresh | Check provider structure for drift | Save a new schema version/diff; flag impacted mappings; do not auto-publish mapping changes |
| Data sync | Refresh company, Periods, and Ratings data | Run only with a compatible approved mapping and target schema |

A tenant can enable one schedule without enabling the other. Suggested default operating practice is to run schema refresh less frequently than business-data sync, subject to provider release practices and tenant needs.

### 11.5 Schedule configuration and execution rules

A schedule should store:

- Schedule ID, tenant ID, integration ID, enabled flag, and schedule type (`SCHEMA` or `DATA`).
- Frequency or platform-supported cron expression.
- Time zone and intended local execution time.
- Start date and optional end date.
- Portfolio/company scope and resource selection for data sync.
- Full/incremental mode only when supported by the API.
- Retry policy, maximum attempts, and overlap policy.
- Last scheduled occurrence, last run ID, last result, and next execution time.
- Created/modified actor and timestamps.

Example operational configuration (illustrative):

- Schema refresh: weekly, Sunday at 02:00, `Asia/Kolkata`.
- Data refresh: daily at 02:30, `Asia/Kolkata`, selected portfolio, Companies + Periods + Ratings.

The scheduler must use the platform scheduler/queue, not browser timers or an HTTP request that remains open for the whole sync. Prevent duplicate enqueueing for the same schedule occurrence. Explicitly define whether an overlap is skipped, queued, or coalesced. Persist the selected policy and result in run history.

### 11.6 Manual sync scope and result

Manual sync should allow an authorized user to choose among supported scopes:

- One RapidRatings company (RRID).
- Selected companies.
- One selected portfolio.
- All configured/in-scope portfolios.
- Selected resources (Company profile, Periods, Ratings).

Before queuing the job, validate the connection, approved schema/mapping versions, target Company schema version, and scope. Return a run ID immediately and let the UI poll or subscribe to run status.

On completion show at minimum: companies discovered, matched, unmatched, processed, created/updated; periods read/upserted; ratings read/upserted; skipped records; validation failures; provider errors; retry count; duration; schema and mapping versions; and correlation ID.

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
| HTTP 429 | Respect `Retry-After` when returned and apply bounded backoff with jitter |
| Timeout / transient 5xx | Retry a limited number of times with backoff |
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
| `POST` | `/api/integrations/rapidratings/schema/sync` | Start a schema refresh and diff run |
| `GET` | `/api/integrations/rapidratings/schema/changes` | Read detected source schema changes |
| `GET` | `/api/integrations/rapidratings/schema/versions` | List source schema versions |
| `GET` | `/api/integrations/rapidratings/schema/diff` | Compare current and previous schema versions |
| `GET` / `PUT` | `/api/integrations/rapidratings/mappings` | Read or update mapping configuration |
| `POST` | `/api/integrations/rapidratings/mappings/validate` | Validate source-to-target mapping |
| `POST` | `/api/integrations/rapidratings/sync-runs` | Start a manual sync |
| `GET` | `/api/integrations/rapidratings/sync-runs` | List sync history |
| `GET` | `/api/integrations/rapidratings/sync-runs/{runId}` | Read sync-run details |
| `GET` / `PUT` | `/api/integrations/rapidratings/schedule` | Read or update the data-sync schedule |
| `POST` | `/api/integrations/rapidratings/schedules` | Create a schema or data schedule |
| `PUT` | `/api/integrations/rapidratings/schedules/{scheduleId}` | Update a schedule |
| `POST` | `/api/integrations/rapidratings/schedules/{scheduleId}/enable` | Enable a schedule |
| `POST` | `/api/integrations/rapidratings/schedules/{scheduleId}/disable` | Disable a schedule |
| `DELETE` | `/api/integrations/rapidratings/schedules/{scheduleId}` | Delete/deactivate a schedule |
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

Use matched test fixtures: company, period, and ratings responses must refer to the same company RRID and period ID. The corrected fixture set uses RRID `KawjbGJ` and period `2019YE`; include a separate negative test with a deliberately mismatched RRID to verify that the integration rejects invalid joins.

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

1. **Matched API fixtures:** The corrected example set is aligned on RRID `KawjbGJ` and period `2019YE`. Confirm the same identity alignment with additional periods and production-contract fixtures.
2. **Official API contract:** Confirm response envelopes, pagination, filtering parameters, sorting, rate limits, and timeouts for each endpoint.
3. **Authentication contract:** Confirm exact token request parameters, response fields, token expiry behavior, and production scopes.
4. **Rating semantics:** Confirm definitions, scale, units, and display interpretation for FHR, CHS, probability of default, term PD, and other rating metrics.
5. **Financial units:** Confirm how currency and `dataScale` apply to period values and whether any conversion is required.
6. **DataModel runtime:** The current target proposal uses only demonstrated structures: root-level `RapidRatings (NESTED)` with direct `Periods (PROPS_ARRAY)` and `Ratings (PROPS_ARRAY)` children. Confirm any future need for `NESTED` inside a group or array item before using it.
7. **Target identity:** Confirm whether the existing Z2 Company primary key can be linked to supplier records and where the tenant-scoped RRID mapping is stored.
8. **`latestRatings` precedence:** Confirm whether it is a convenience summary or an authoritative source for latest-period data relative to the ratings endpoint.
9. **History policy:** Confirm how changed historical ratings are handled and whether previous values or only the latest source state must be retained.
10. **Incremental sync:** Confirm whether the provider offers a reliable modified-since/cursor/ETag mechanism.
11. **Retention:** Confirm contractual permission and retention duration for raw payloads and report links.
12. **Portfolio behavior:** Confirm multiple-portfolio support and how companies shared across portfolios should be represented.
13. **Scheduling standards:** Confirm supported cadence, time-zone behavior, overlap policy, and scheduler API.
14. **ServiceNow boundary:** Confirm whether ServiceNow only consumes Z2-normalized data/triggers workflows or owns any integration activity.
15. **Outreach:** Confirm whether outreach is required in the first release; otherwise keep it out of scope.

## 22. Recommended Delivery Plan

### Phase 1 – Integration foundation and authentication

- Register RapidRatings in the integration catalogue.
- Implement tenant configuration and secure credential references.
- Implement OAuth token acquisition, caching, refresh, and connection validation.
- Implement provider client configuration for sandbox and production.

### Phase 2 – Source schema discovery and target model

- Implement company, period, and rating response DTOs from verified source contracts.
- Implement source schema snapshots and schema diffing.
- Extend the existing Company DataModel with a root-level `RapidRatings` `NESTED` group, following the `Kharon Risk Data` precedent.
- Add `Periods` and `Ratings` as `PROPS_ARRAY` children with direct leaf fields.
- Validate the full schema against the deployed DataModel runtime; do not assume deeper nested groups inside arrays.

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
- Field mappings can be configured, previewed, validated, approved, and versioned.
- The existing Company DataModel stores RapidRatings under a root-level `RapidRatings` `NESTED` group with `Periods` and `Ratings` `PROPS_ARRAY` children, using the demonstrated Kharon pattern.
- Period financial fields and period rating fields are associated using matching RRID and period ID values.
- `termPd` year-specific values are preserved.
- Null values and provider currency/scale are handled without invented defaults or silent conversion.
- Re-running the same sync does not create duplicate company or period records.
- Manual and scheduled syncs use the same orchestration pipeline.
- Sync history shows trigger, status, timestamps, scope, counts, schema/mapping versions, and sanitized errors.
- Schedule and integration configuration changes are auditable.
- Schema drift, partial failures, authentication errors, and provider throttling are handled safely.
- Tenant credentials and data remain isolated.

## 24. Source Files and Design Basis

This proposal is based on the following supplied or previously prepared artifacts:

- `A1company.json` – existing Z2 Company DataModel structure and examples of `NESTED` and `PROPS_ARRAY` property groups.
- `Companies.json` – RapidRatings company response, including company identity, resource links, metadata, report links, and `latestRatings`.
- `PeriodById.json` – RapidRatings individual-period financial response; use the corrected matched example documented in Section 5.2.
- `GetRatingByPeriodId.json` – RapidRatings individual-period rating response, including `termPd` and additional rating metrics; use the corrected matched example documented in Section 5.3.
- Corrected fixture note from the design discussion: the company, period, and rating records being joined all use RRID `KawjbGJ`; any older payload with RRID `XAZPzdJ` must remain a separate negative or historical test fixture.
- `rapidratings_integration_solution_overview.md` – earlier integration requirements and platform design notes, including authentication, supplier matching, scheduling, sync history, and change history.

**Document note:** This is a proposed solution overview, not a claim that all platform routes, Data Integrations contracts, schema-runtime capabilities, or production RapidRatings permissions are already implemented. Sandbox observations are identified as such; unresolved API and platform behavior is listed in Section 21 for confirmation.
