# RapidRatings Integration – Solution Overview

**Platform:** Z2 Enterprise – Data Integrations  
**Integration:** RapidRatings  
**Target DataModel:** Existing Company DataModel (`A1company.json`)  
**Environment covered:** RapidRatings Sandbox  
**Status:** Proposed solution based on the supplied API examples and the existing A1Company structure.

## 1. Overview

The RapidRatings integration enables a Z2 Enterprise tenant to connect a RapidRatings account, discover portfolios and companies, retrieve company reporting periods and ratings, and synchronize the information into existing Z2 Company records.

The integration uses the common Z2 Data Integrations capabilities for credentials, schema management, field mapping, synchronization, scheduling, and history. A RapidRatings adapter handles provider authentication, API requests, and response parsing. Z2 remains the owner of integration configuration, mapping, synchronization orchestration, and target Company data.

The solution covers:

- Secure connection and OAuth authentication.
- RapidRatings source schema discovery and versioning.
- Mapping RapidRatings Company, Periods, and Ratings data to `A1company.json`.
- Schema and business-data synchronization.
- Manual and scheduled synchronization.
- Run history, error reporting, and configuration change history.

## 2. End-to-End Architecture and Flow

```text
Z2 Enterprise UI
  |  Configure connection, review schema, map fields,
  |  start sync, configure schedule, view history
  v
Z2 Data Integrations APIs and Orchestration
  |-- Tenant integration configuration
  |-- Secure credential reference
  |-- Source schema registry
  |-- Mapping registry and validation
  |-- Manual/scheduled sync orchestration
  |-- Run history and audit history
  v
RapidRatings Adapter
  |-- OAuth token management
  |-- RapidRatings API client
  |-- Company / Period / Rating response handling
  |-- Validation and mapping input preparation
  v
RapidRatings APIs
  |-- Portfolios
  |-- Companies
  |-- Company Periods
  |-- Company Ratings
  v
Z2 Existing Company DataModel (A1company.json)
  |-- RapidRatings company profile
  |-- Periods collection
  |-- Ratings collection
  v
Company Details / Raw Properties / approved Z2 workflows
```

**Processing sequence:**

1. An administrator configures RapidRatings credentials in Z2.
2. The Z2 backend authenticates with RapidRatings and validates the connection.
3. Z2 discovers the source resources and captures their schema.
4. The administrator reviews and approves mappings to the Company DataModel.
5. A manual or scheduled sync retrieves company, period, and rating data.
6. The adapter validates identifiers and prepares source records for mapping.
7. The mapping engine transforms the data into the A1Company RapidRatings properties.
8. Z2 validates and upserts the RapidRatings data on the matched Company record.
9. Z2 records the synchronization result and any configuration changes.

## 3. RapidRatings APIs and Authentication

### 3.1 API endpoints

| Resource | Method | Sandbox endpoint | Purpose |
|---|---|---|---|
| OAuth token | POST | `https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` | Obtain an access token |
| Portfolios | GET | `https://rridata.sandbox.rrilabs.co/v2.0/portfolios/` | Retrieve accessible portfolios |
| Companies | GET | `https://rridata.sandbox.rrilabs.co/v2.0/companies/` | Retrieve companies, optionally by supported portfolio filter |
| Periods | GET | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/` | Retrieve a company's reporting periods |
| Ratings | GET | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/` | Retrieve a company's ratings |
| Outreach | API-specific | `https://api.sandbox.rrilabs.co/outreach-requests` | Separate capability; not included in the initial data sync |

The API pagination, filtering, rate limits, and exact response envelopes must be confirmed against the provider contract before production rollout. Outreach uses a different host; its method, permissions, and authentication requirements must be validated separately.

### 3.2 Authentication flow

RapidRatings sandbox authentication uses OAuth 2.0 Client Credentials. The observed token request uses HTTP Basic Authentication with `client_id` and `client_secret` against the token endpoint.

1. The user enters the Client ID and Client Secret in the Z2 integration UI.
2. The frontend sends the credentials securely to the Z2 backend.
3. The backend stores the secret in the approved secret manager and retains only a secure reference in integration configuration.
4. The backend obtains an access token from RapidRatings.
5. The backend uses `Authorization: Bearer {access_token}` for protected RapidRatings API calls.
6. The backend reuses the token while valid and renews it before expiry.
7. A connection test calls an authorized low-impact endpoint, such as Portfolios, and returns a sanitized status to the UI.

**Security boundary:** The frontend must not call the RapidRatings token endpoint or business APIs directly. Client secrets and access tokens must never be exposed to the browser or written to logs, run history, or error responses.

The sandbox investigation observed an approximately 30-minute token lifetime and used a 60-second renewal margin. An expired-token scenario returned HTTP 403 from the data API; this status must not be assumed to mean token expiry in every case. Inspect the provider error response and retry an eligible request only once after confirmed token renewal.

## 4. Source Schema and A1Company Target Structure

### 4.1 RapidRatings source resources

The integration consumes three primary business resources:

- **Company:** source identity and company profile, including `id` (RRID), `name`, `country`, `ticker`, `exchange`, `sector`, resource links, `currency`, `dataScale`, `dataConfidential`, report links, and `latestRatings`.
- **Periods:** reporting-period identity and financial statement values, including `id`, `eqyYear`, `period`, `financialDate`, `currency`, `href`, and financial metrics such as `salesRevenue`, `totalAssets`, `totalLiabilities`, `netProfitAfterTax`, `fixedAssets`, and `termLoans`.
- **Ratings:** period-specific rating information, including `id`, `eqyYear`, `period`, `financialDate`, `reportingPeriod`, `fhr`, `chs`, `probabilityDefault`, `termPd`, `delta`, simulated FHR values, profitability and efficiency metrics, `leverage`, `liquidity`, and `earningsPerformance`.

Schema discovery should use provider documentation/OpenAPI information where available and authenticated API response samples. No dedicated RapidRatings schema-discovery endpoint has been established in the supplied evidence.

### 4.2 Fit RapidRatings into A1Company.json

The existing A1Company model demonstrates:

- `Financial` as a root-level `NESTED` property group with Moody's and D&B properties.
- `Kharon Risk Data` as a root-level `NESTED` group containing scalar properties and a `Risks` `PROPS_ARRAY`.
- Direct leaf properties inside the `PROPS_ARRAY` items.

Use the same proven structure for RapidRatings. Add **RapidRatings as a root-level `NESTED` property group on Company**, alongside `Financial` and `Kharon Risk Data`.

```text
Company (existing)
├── Financial (existing NESTED)
│   ├── Moody's Credit Rating
│   └── D&B Rating
├── Kharon Risk Data (existing NESTED)
└── RapidRatings (new NESTED)
    ├── RRID
    ├── Source Company Name
    ├── Source Country
    ├── Ticker
    ├── Exchange
    ├── Sector
    ├── Company / Financials / Periods / Ratings URLs
    ├── Currency / Data Scale / Data Confidentiality
    ├── Report Links
    ├── Periods (PROPS_ARRAY)
    │   ├── Period ID
    │   ├── EQY Year / Period / Financial Date
    │   ├── Currency / Period URL
    │   └── Financial statement fields
    └── Ratings (PROPS_ARRAY)
        ├── Period ID / EQY Year / Period
        ├── Financial Date / Reporting Period / Rating URL
        ├── FHR / CHS / Probability Default
        ├── Term PD Year 2 through Year 10
        ├── Delta / Simulated FHR / Simulated FHR Delta
        ├── Profitability and efficiency metrics
        └── Leverage / Liquidity / Earnings Performance
```

This placement is deliberate: the existing file demonstrates a root-level `NESTED` group containing a `PROPS_ARRAY`, but does not establish support for `Financial (NESTED) → RapidRatings (NESTED)` or nested groups inside an array item. The Company Details page can visually place the section under **Financial › RapidRatings**, while the actual model paths remain `rapidratings.*`.

Keep Periods and Ratings as separate arrays because they are separate provider resources. Use direct leaf properties inside each array. Flatten `termPd.year2` through `termPd.year10` into individual numeric properties unless the runtime's typed nested-object support is confirmed.

### 4.3 Target data types and mapping rules

| Source value | Target property example | Target type / rule |
|---|---|---|
| Company `id` | `rapidratings.rrid` | STRING; preserve RRID exactly |
| Company `name` | `rapidratings.source_company_name` | STRING; do not overwrite canonical Company Name |
| Company `country`, `ticker`, `exchange`, `sector` | Corresponding `rapidratings.*` fields | STRING |
| Company resource links | `rapidratings.*_resource_url` | STRING |
| `currency`, `dataScale` | `rapidratings.currency`, `rapidratings.data_scale` | STRING; preserve provider context |
| `dataConfidential` | `rapidratings.data_confidential` | STRING; supplied value is `PUB`, not BOOLEAN |
| Period `id`, `period`, `href` | `rapidratings.periods.period_id`, `.period`, `.period_resource_url` | STRING |
| Period `eqyYear` | `rapidratings.periods.eqy_year` | INTEGER |
| Period `financialDate` | `rapidratings.periods.financial_date` | DATE |
| Period financial amounts | Corresponding `rapidratings.periods.*` fields | DOUBLE; preserve precision and nulls |
| Rating `id` / period identity | `rapidratings.ratings.period_id` | STRING; validate against Periods |
| Rating dates | `rapidratings.ratings.*_date` | DATE |
| FHR, CHS, PD, Term PD, and numeric metrics | Corresponding `rapidratings.ratings.*` fields | Numeric type supported by the platform; preserve source values |
| Leverage, Liquidity, Earnings Performance | Corresponding `rapidratings.ratings.*` fields | STRING unless provider enum values are confirmed |
| `externalSourceIds` | `rapidratings.external_source_ids` | Supported string-array type or JSON; confirm platform support |

Keep every source field traceable to its original JSON path. Map all returned period financial fields and rating fields, not only the examples above. Do not convert null financial values to zero, rescale monetary values without an approved rule, or infer rating units and thresholds from field names.

### 4.4 Example mapped Company payload

The following abbreviated example illustrates the target shape after mapping. Production sync should include all mapped fields.

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
        "net_profit_after_tax": -666.954
      }
    ],
    "ratings": [
      {
        "period_id": "2019YE",
        "eqy_year": 2019,
        "financial_date": "2019-03-02",
        "reporting_period": "2020-05-15",
        "fhr": 22,
        "chs": 29,
        "probability_default": 8.4984,
        "term_pd_year_2": 12.3,
        "term_pd_year_10": 46.57,
        "leverage": "Weak",
        "liquidity": "Weak"
      }
    ]
  }
}
```

The canonical Z2 `company_name` remains unchanged. RapidRatings' name is stored separately as source data.

## 5. Schema Mapping and Company Details Presentation

Schema discovery and schema mapping are separate responsibilities:

- **Source schema:** describes what RapidRatings returns.
- **Target schema:** describes the properties available in A1Company.
- **Mapping configuration:** links source JSON paths to target property paths and defines any approved type conversion or normalization.

The mapping configuration should be versioned and should reference the source schema version and target Company DataModel version. Validate source/target field existence, type compatibility, required fields, null handling, and collection paths before a mapping is approved or activated.

### 5.1 Mapping groups

| Source resource | Mapping destination | Main contents |
|---|---|---|
| Company | `rapidratings.*` | RRID, source company profile, resource links, currency/scale, report links |
| Periods | `rapidratings.periods.*` | Period identity, dates, currency, all period financial statement fields |
| Ratings | `rapidratings.ratings.*` | Period identity, rating dates, FHR, CHS, probability of default, Term PD horizons, and other rating metrics |

Use RRID as the external company identifier. Prefer a stored `(tenantId, provider, rrid) → z2CompanyId` mapping or an explicitly approved deterministic match. Do not automatically link companies using fuzzy name matching alone. Keep unmatched or ambiguous companies for review.

The observed rating `id` is the period identifier (`2019YE`). Validate and map it as `period_id`; if the provider contract later exposes a separate rating identifier, add it without changing the period association rule.

### 5.2 Period and rating association

Associate a rating record with a period using the same company RRID and period ID:

```text
Company RRID: KawjbGJ
Period ID:    2019YE
Rating ID:    2019YE
Join key:     (tenantId, provider, rrid, periodId)
```

Never join by array position, response order, company name, or period ID alone. A missing or mismatched association must be reported rather than silently attached to another record.

### 5.3 latestRatings handling

The Company response includes an embedded `latestRatings` collection, while a dedicated Ratings endpoint provides rating records. Use the dedicated Ratings endpoint as the preferred source for detailed historical ratings, subject to the provider contract. Use embedded `latestRatings` as a summary or comparison source and do not create duplicate historical records from both resources.

The corrected samples for RRID `KawjbGJ` and period `2019YE` align on several rating values, but the `termPd` horizon values differ between embedded `latestRatings` and the dedicated Ratings response. Do not merge or overwrite these values silently; retain the discrepancy for review and apply an explicit source-precedence rule.

### 5.4 Raw Properties and Company Details

- **Raw Properties** is driven by the A1Company data model. Once the `rapidratings` NESTED group and child properties are added, this group and its values can be exposed in the property hierarchy.
- **Company Details** is driven by a separate layout configuration. Add an explicit Details section titled **Financial › RapidRatings** and bind the summary properties, Periods collection, and Ratings collection to their A1Company `dbName` paths.
- Display company profile fields and selected rating summary values first; show Periods and Ratings as separate collection views/tables.
- Confirm that the deployed Details renderer supports `PROPS_ARRAY` table/list rendering before configuring the final layout. Use the platform's existing collection-rendering pattern; do not invent unsupported layout keys.

## 6. Schema and Data Synchronization

### 6.1 Schema synchronization

Schema sync updates the RapidRatings source schema registry; it does not import business records or automatically change the target A1Company schema.

1. Authenticate with RapidRatings.
2. Discover or inspect the Company, Periods, and Ratings resources using provider metadata and validated API responses.
3. Build a source schema snapshot with field paths, observed/contract types, object/array structure, and nullability where known.
4. Compare the snapshot with the previous version.
5. Identify added, removed, renamed, or type-changed fields.
6. Revalidate mappings affected by those changes.
7. Save the new schema version and change summary.
8. Require review and mapping approval for breaking or incompatible changes before affected data syncs continue.

### 6.2 Data synchronization

1. Start a run for the selected portfolio/company scope.
2. Obtain or reuse a valid access token.
3. Retrieve companies and resolve each RRID to an existing Z2 Company record.
4. Retrieve that company's Periods and Ratings resources.
5. Validate RRID and period ID consistency.
6. Apply the active approved mapping.
7. Validate and transform the mapped data.
8. Upsert the RapidRatings property group and its Periods/Ratings collections into the matched Company record.
9. Preserve historical periods and report successful, skipped, unmatched, and failed records.

Use idempotent keys:

- Company: `(tenantId, provider, rrid)`.
- Period and rating association: `(tenantId, provider, rrid, periodId)`; include a distinct provider rating identifier if the actual contract requires it.

A repeated sync should update an existing matching record, not create a duplicate. Do not delete historical periods merely because they are absent from a partial or failed response. Use incremental synchronization only if the provider confirms a reliable change-tracking mechanism; otherwise use bounded retrieval and idempotent upserts.

## 7. Manual and Scheduled Syncs

Manual and scheduled execution must call the **same synchronization orchestration pipeline**. Only the trigger differs.

### Manual sync

An authorized user can start a sync from the RapidRatings integration page. The user selects a supported scope: one company, selected companies, a portfolio, or the configured integration scope. The backend validates connection and mapping status, creates a run, queues the work, and returns a run ID. The UI shows progress and the final result.

### Scheduled sync

An administrator configures separate schedules for:

- **Schema refresh:** periodically checks source schema changes and mapping compatibility.
- **Data sync:** retrieves Company, Periods, and Ratings data for the selected scope.

A schedule stores its enabled state, frequency, time zone, execution time, scope, retry policy, last run, and next run. The scheduler queues the same jobs used for manual sync. Use the Z2 scheduler/queue rather than browser timers or long-running HTTP requests. Prevent duplicate scheduled occurrences and define how overlapping or missed runs are handled.

## 8. History, Security, and Error Handling

Maintain two separate histories:

**Synchronization run history** records run ID, tenant/integration, trigger (`MANUAL` or `SCHEDULED`), sync type (`SCHEMA` or `DATA`), scope, start/end time, status, company/period/rating counts, retry count, schema/mapping versions, correlation ID, and sanitized error summary.

**Configuration change history** records who changed integration settings, credential references/rotation, schema or mapping approval, company matching rules, and schedules. Never store secret values or access tokens in history.

Key operational behavior:

- Retry transient network failures, rate limits, and eligible server errors with bounded backoff; respect `Retry-After` when supplied.
- Refresh and retry once only when token expiry is confirmed.
- Do not endlessly retry invalid credentials, insufficient permissions, invalid RRIDs, or mapping/schema validation failures.
- Preserve nulls and numeric precision; keep currency and `dataScale` context with financial values.
- Continue independent records when safe and mark partial failures accurately.
- Keep all provider calls on the backend, isolate credentials and data by tenant, and redact secrets/tokens from logs.

## 9. Key Decisions and Items to Confirm

1. The corrected Company, Periods, and Ratings examples all use RRID `KawjbGJ`; the matching period ID is `2019YE`.
2. `RapidRatings` is a root-level `NESTED` group on A1Company, with independent `Periods` and `Ratings` `PROPS_ARRAY` children. Company Details may visually group it under Financial.
3. `termPd.year2` through `termPd.year10` are flattened into numeric leaf properties unless typed nested data inside arrays is confirmed as supported.
4. Confirm provider pagination, filters, rate limits, token request/response contract, and production authentication behavior.
5. Confirm rating metric definitions and units, and how currency and `dataScale` should be interpreted.
6. Confirm the Company Details renderer's support for collection tables and the platform's valid metadata/type rules for new A1Company properties.
7. Confirm the precedence/reconciliation rule for `latestRatings` versus the dedicated Ratings endpoint.
8. Confirm whether RapidRatings supports incremental change tracking. Do not assume a modified-since filter or cursor exists.
9. Keep Outreach outside the initial release until its method, payload, authorization, and business approval requirements are verified.

---

**Design basis:** Existing `A1company.json` nested/array examples and the supplied RapidRatings Company, Period, and Rating sandbox responses. This is a solution overview; proposed platform behavior and unresolved API/runtime questions must be validated before production deployment.
