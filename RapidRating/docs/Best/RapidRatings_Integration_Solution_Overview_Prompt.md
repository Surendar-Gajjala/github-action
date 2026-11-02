# Prompt: RapidRatings Integration – Solution Overview

Use this prompt to prepare a professional solution overview for integrating RapidRatings with Z2 Enterprise Data Integrations. The resulting document must be based on the API details, raw property schemas, and target DataModel structure provided below.

---

## 1. Role and Objective

Act as a Senior Solution Architect and Data Integration Architect experienced in enterprise integrations, OAuth 2.0, REST APIs, schema discovery, data mapping, configurable DataModels, and data synchronization.

Prepare a clear, technically accurate **RapidRatings Integration – Solution Overview** for the Z2 Enterprise Data Integrations platform.

The document must explain how a Z2 customer enables the RapidRatings integration, how Z2 authenticates with RapidRatings, how Company, Periods, and Ratings data is retrieved, how the source properties are mapped into the existing Z2 Company DataModel, and how schema and data synchronization work.

Focus on the solution architecture, authentication, source-to-target schema mapping, Company Details presentation, and manual and scheduled synchronization. Avoid unnecessary implementation code, generic integration theory, and unrelated technical detail.

## 2. Project Context and Document Information

Use the following document information:

- **Document title:** RapidRatings Integration – Solution Overview
- **Program:** Z2 Enterprise – Data Integrations
- **Integration:** RapidRatings
- **Target DataModel:** Existing Z2 Company DataModel
- **Source environment investigated:** RapidRatings sandbox
- **Document status:** Proposed solution design based on the supplied API response samples and existing Company DataModel. Confirm open API-contract and platform-runtime questions before production rollout.

### Business context

Z2 Enterprise provides a **Data Integrations** area where customers can select and enable integrations for their tenants. RapidRatings is one of the external integrations available to a customer.

Describe the integration lifecycle at a solution level:

1. A customer selects and enables RapidRatings for a tenant.
2. The customer or an authorized administrator configures the RapidRatings credentials and required settings.
3. Z2 validates the integration authentication.
4. Z2 retrieves the available RapidRatings data.
5. Z2 maps the source data into the existing Company DataModel.
6. Z2 displays the RapidRatings company information, reporting periods, and ratings in the Company Details experience.
7. The customer can run synchronization manually or configure scheduled synchronization, subject to the platform's supported scheduling capabilities.

Do not assume that every customer must use a portfolio. Explain portfolio-based filtering as an available scoping mechanism if supported by the API and customer configuration.

## 3. Integration Authentication

Explain how Z2 authenticates to RapidRatings using the supplied Client ID and Client Secret and obtains a Bearer access token.

### 3.1 Authentication endpoint

- **Method:** `POST`
- **URL:** `https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/`
- **Authentication model:** OAuth 2.0 Client Credentials grant
- **Client authentication:** HTTP Basic Authentication using the Client ID and Client Secret
- **API authentication:** Bearer access token
- **Observed token lifetime:** Approximately 30 minutes; validate the actual token response and expiry behavior.
- **Suggested renewal margin:** 60 seconds before expiry.

Explain the token request, including the expected grant type, authentication header, and form-encoded request body. Show a representative request, but do not invent unverified token response field names. Clearly identify which response fields must be confirmed against the actual sandbox response.

Cover these authentication requirements:

- Store the Client ID and Client Secret securely in the approved Z2 credential store or secret manager.
- Never expose credentials or the RapidRatings access token to the browser or frontend.
- Perform token acquisition and all RapidRatings API calls through the Z2 backend.
- Cache and reuse a valid token instead of requesting a new token for every API call.
- Track token expiry and renew the token before it expires.
- On an authentication failure, inspect the provider's error response before deciding whether to refresh the token.
- Retry a request at most once after a confirmed token-expiry condition; do not treat every `401` or `403` as token expiry and do not create an unlimited retry loop.
- Redact credentials and tokens from application logs and synchronization history.

Include a simple authentication sequence or flow diagram showing:

`Z2 Integration Configuration → Z2 Backend → RapidRatings Token Endpoint → Access Token → RapidRatings APIs`

## 4. RapidRatings API Inventory

Use the following supplied sandbox endpoints. Identify the purpose of each endpoint and explain where it fits in the integration flow.

| API | Method | Endpoint | Purpose |
|---|---|---|---|
| Authentication | POST | `https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` | Obtain an access token |
| Companies | GET | `https://rridata.sandbox.rrilabs.co/v2.0/companies/` | Retrieve RapidRatings company records |
| Portfolios | GET | `https://rridata.sandbox.rrilabs.co/v2.0/portfolios/` | Retrieve available portfolios and identify portfolio scope |
| Periods | GET | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/` | Retrieve financial reporting periods for a company |
| Ratings | GET | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/` | Retrieve rating records for a company |
| Outreach requests | Confirm method | `https://api.sandbox.rrilabs.co/outreach-requests` | Outreach-related capability; include only after its contract and authentication are verified |

For the Companies, Portfolios, Periods, and Ratings endpoints, explain the Bearer-token requirement. Do not assume the Outreach endpoint uses the same host, authentication configuration, HTTP method, permissions, or request format as the `rridata` endpoints. Explicitly flag Outreach as a separate API host whose contract must be confirmed before including it in the initial production scope.

Explain the relationship between the calls:

1. Optionally retrieve portfolios to identify the portfolio or portfolios in scope.
2. Retrieve companies, applying a portfolio filter only if supported and configured.
3. Read the company's RapidRatings identifier (`rrid`).
4. Use the `rrid` to retrieve that company's Periods.
5. Use the same `rrid` to retrieve that company's Ratings.

Do not invent pagination, filtering, rate limits, or incremental-sync parameters. List them as items to verify if not demonstrated by the supplied API contract or responses.

## 5. Source Schema: RapidRatings Company

Treat the following as the supplied Company raw-property inventory. Preserve the original source field names and casing in the source schema section. Use snake_case for proposed Z2 target property names.

| Source property path | Supplied data type |
|---|---|
| `id` | string |
| `name` | string |
| `country` | string |
| `ticker` | string |
| `exchange` | string |
| `sector` | string |
| `href` | uri |
| `financials` | uri |
| `periods` | uri |
| `ratings` | uri |
| `latestRatings` | array |
| `latestRatings[]` | object |
| `latestRatings[].id` | string |
| `latestRatings[].href` | uri |
| `latestRatings[].eqyYear` | integer |
| `latestRatings[].period` | string |
| `latestRatings[].financialDate` | date |
| `latestRatings[].reportingPeriod` | date |
| `latestRatings[].fhr` | integer |
| `latestRatings[].chs` | integer |
| `latestRatings[].probabilityDefault` | number |
| `latestRatings[].termPd` | object |
| `latestRatings[].termPd.year2` through `latestRatings[].termPd.year10` | number for each year |
| `latestRatings[].delta` | integer |
| `latestRatings[].simulatedFhr` | integer |
| `latestRatings[].simulatedFhrDelta` | integer |
| `latestRatings[].operatingProfitability` | integer |
| `latestRatings[].netProfitability` | integer |
| `latestRatings[].capitalStructureEfficiency` | integer |
| `latestRatings[].costStructureEfficiency` | integer |
| `latestRatings[].leverage` | string |
| `latestRatings[].liquidity` | string |
| `latestRatings[].earningsPerformance` | string |
| `reportLinks` | object |
| `reportLinks.fhr` | uri |
| `reportLinks.financialDialog` | uri |
| `reportLinks.peerBenchmark` | uri |
| `address` | string |
| `industryClassificationSystem` | null in supplied schema sample |
| `industryClassificationCode` | null in supplied schema sample |
| `currency` | string |
| `dataScale` | string |
| `dataConfidential` | string |
| `externalSourceId` | null in supplied schema sample |
| `externalSourceIds` | array |

### Required Company mapping analysis

Create a source-to-target mapping table with these columns:

- Source property path
- Source data type / observed nullability
- Proposed Z2 Company target property path
- Z2 property type
- Transformation or mapping rule
- Mapping notes / verification required

Cover the complete supplied Company property inventory. Do not silently omit `reportLinks`, `latestRatings`, `externalSourceIds`, or the URI fields.

Discuss these mapping rules:

- Use the RapidRatings `id` as the provider company identifier, mapped to a property such as `rapidratings.rrid`.
- Preserve the RapidRatings company name separately from the canonical Z2 Company name unless an approved company-matching rule explicitly updates the canonical name.
- Preserve the source URI values as source references where the Z2 DataModel supports them; otherwise document the approved URI representation.
- Do not infer a permanent schema type from a field that was only observed as `null`. Mark `industryClassificationSystem`, `industryClassificationCode`, and `externalSourceId` as requiring further response samples or provider documentation.
- Preserve `dataConfidential` as a string because the supplied schema identifies it as a string; do not convert values such as `PUB` to Boolean.
- Preserve `currency` and `dataScale` as source metadata. Do not silently convert or rescale financial values.
- Confirm the supported Z2 representation for `externalSourceIds` before mapping it.
- Treat `latestRatings` as an embedded summary/preview collection unless the design explicitly requires a separate mapping. Compare it with the dedicated Ratings endpoint and do not silently merge inconsistent values.

## 6. Source Schema: RapidRatings Periods

Treat the following as the supplied Periods raw-property inventory. Periods are retrieved for a company using its `rrid`.

Endpoint:

`GET https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/`

| Source property path | Supplied data type |
|---|---|
| `id` | string |
| `href` | uri |
| `eqyYear` | integer |
| `period` | string |
| `financialDate` | date |
| `currency` | string |
| `salesRevenue` | number |
| `earningsBeforeInterestAndTax` | number |
| `interestExpense` | number |
| `interestReceived` | number |
| `companyTaxExpense` | number |
| `abnormalItems` | number |
| `netProfitAfterTax` | number |
| `bankCashBalances` | number |
| `totalCurrentAssets` | number |
| `fixedAssets` | number |
| `financialAssets` | null in supplied schema sample |
| `totalAssets` | number |
| `debitOwnedWithinOneYear` | number |
| `totalCurrentLiabilities` | number |
| `termLoans` | number |
| `totalLiabilities` | number |
| `totalShareholderEquity` | number |
| `cashFromOperationsFromCashFlow` | null in supplied schema sample |
| `netInvestingCashFlow` | number |
| `netFundingCashFlow` | number |

### Required Periods mapping analysis

Recommend representing financial periods as a collection beneath the RapidRatings nested group in the Z2 Company DataModel:

`Company → RapidRatings (NESTED) → Periods (PROPS_ARRAY)`

Create a complete source-to-target mapping table for every supplied Periods property. Use consistent target names such as `rapidratings.periods.period_id`, `rapidratings.periods.eqy_year`, `rapidratings.periods.financial_date`, and `rapidratings.periods.sales_revenue`.

Explain:

- How each period belongs to the RapidRatings company identified by `rrid`.
- How the source `id` is used as the period identifier, subject to provider-contract confirmation.
- How multiple periods are represented as separate array entries.
- How date, integer, numeric, URI, and null values should be handled.
- Why negative financial values must be preserved.
- Why null must not be replaced with zero.
- How currency and data scale must be retained so values are not misinterpreted.
- Why `financialAssets` and `cashFromOperationsFromCashFlow`, observed as null in the supplied schema, require further samples or documentation before assigning a definitive target type.

Do not invent financial fields, values, units, or business definitions that are not in the supplied schema or verified response samples.

## 7. Source Schema: RapidRatings Ratings

Treat the following as the supplied Ratings raw-property inventory. Ratings are retrieved for a company using its `rrid`.

Endpoint:

`GET https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/`

| Source property path | Supplied data type |
|---|---|
| `id` | string |
| `href` | uri |
| `eqyYear` | integer |
| `period` | string |
| `financialDate` | date |
| `reportingPeriod` | date |
| `fhr` | integer |
| `chs` | integer |
| `probabilityDefault` | number |
| `termPd` | object |
| `termPd.year2` through `termPd.year10` | number for each year |
| `delta` | integer |
| `simulatedFhr` | integer |
| `simulatedFhrDelta` | integer |
| `operatingProfitability` | integer |
| `netProfitability` | integer |
| `capitalStructureEfficiency` | integer |
| `costStructureEfficiency` | integer |
| `leverage` | string |
| `liquidity` | string |
| `earningsPerformance` | string |

### Required Ratings mapping analysis

Recommend representing ratings as a collection beneath the RapidRatings nested group:

`Company → RapidRatings (NESTED) → Ratings (PROPS_ARRAY)`

Create a complete source-to-target mapping table for every supplied Ratings property. Use consistent snake_case target names such as:

- `rapidratings.ratings.period_id`
- `rapidratings.ratings.eqy_year`
- `rapidratings.ratings.reporting_period`
- `rapidratings.ratings.probability_default`
- `rapidratings.ratings.term_pd_year_2` through `rapidratings.ratings.term_pd_year_10`
- `rapidratings.ratings.simulated_fhr_delta`
- `rapidratings.ratings.operating_profitability`

Explain:

- The role of FHR, CHS, probability of default, Term PD and the other rating attributes only to the extent supported by supplied definitions or provider documentation.
- How rating records relate to the company and its reporting periods.
- How historical rating records are retained.
- Why `termPd.year2` through `termPd.year10` may be flattened to leaf properties in each Ratings array entry if nested objects inside `PROPS_ARRAY` are not confirmed as supported by the Z2 DataModel runtime.
- How numeric and categorical values are preserved.
- Why provider units, scales, ranges, and metric meanings must not be guessed.

Do not infer missing Term PD values or create metrics that are not in the supplied schema.

## 8. Target Z2 Company DataModel Structure

The integration extends the **existing Z2 Company objectType/DataModel**. Use the following proposed target structure as the design starting point:

```text
Z2 Company
 ├── Basic Information (NESTED)
 ├── Financial (NESTED)
 │    ├── Moody's Credit Rating
 │    └── D&B Rating
 │
 └── RapidRatings (NESTED)  ← NEW
      ├── Company profile properties
      ├── Periods (PROPS_ARRAY)
      └── Ratings (PROPS_ARRAY)
```

Explain how the RapidRatings group is added to the existing Z2 Company schema/objectType rather than creating an unrelated top-level objectType by default. Treat `RapidRatings` as a nested property group unless the actual Z2 platform conventions require a separate objectType. Do not confuse the provider name, nested group name, and Z2 objectType.

Describe the proposed target property paths and types. Explain that the actual DataModel configuration must use the exact property-definition format and supported data types required by Z2. Do not invent JSON configuration keys or claim unsupported nested/array capabilities.

If the existing Company DataModel contains additional groups not shown above, preserve them and propose RapidRatings as an additive extension rather than replacing existing structures.

## 9. Company Details and Raw Properties Presentation

Explain how RapidRatings data should appear in the Z2 user interface.

### Company Details

The Company Details page should display:

1. RapidRatings company profile details, such as provider company name, RRID, country, ticker, exchange, sector, currency, and data scale where available.
2. A **Periods** section or tab showing the financial reporting periods as a table/list from the `Periods (PROPS_ARRAY)`.
3. A **Ratings** section or tab showing rating-period records as a table/list from the `Ratings (PROPS_ARRAY)`.

Explain the distinction between storing properties in the DataModel and explicitly configuring their display in a layout-driven Company Details page. Identify any table/array rendering capability that must be verified in the current Z2 UI runtime.

### Raw Properties

Explain that Raw Properties should expose the mapped RapidRatings properties according to the Company DataModel, including nested Company properties and the Periods and Ratings property arrays, subject to the current Raw Properties renderer's supported types.

Do not assume that adding the properties to the DataModel automatically creates the desired Company Details layout. Do not invent UI configuration keys; identify the required layout bindings as a platform-specific implementation detail to verify.

## 10. Schema Synchronization

Describe schema synchronization separately from business-data synchronization.

Explain how the integration discovers or refreshes the RapidRatings source schema, compares the returned properties with the currently registered source schema, and identifies added, removed, changed, or type-changed fields.

Important constraints:

- Do not invent a dedicated RapidRatings schema-discovery endpoint. The supplied endpoint list does not identify one.
- Explain that schema discovery may rely on provider documentation/OpenAPI, observed API responses, or an approved schema-sampling process, subject to confirmation.
- Explain how schema changes should be reviewed and mapped to the Z2 Company DataModel.
- Do not automatically overwrite the approved Company DataModel or production mappings without review/approval.
- Explain how schema version, mapping version, sync time, and schema-diff status can be recorded if supported by Z2.

## 11. Data Synchronization: Manual and Scheduled

Explain both manual and scheduled synchronization using the same core backend integration pipeline.

### Manual synchronization

Describe how an authorized user initiates a sync from the RapidRatings integration configuration or Company experience, how the backend retrieves the required data, maps it, validates it, upserts the result, and reports success, partial success, or failure.

### Scheduled synchronization

Describe how an authorized user configures an available schedule and how the scheduler invokes the same synchronization pipeline. Do not invent supported schedule frequencies or scheduler APIs; mark those as platform capabilities to confirm.

### Data synchronization sequence

Use this logical sequence:

1. Confirm the tenant's RapidRatings integration is enabled.
2. Load the tenant's secure credentials and integration settings.
3. Obtain or reuse a valid access token.
4. Retrieve portfolios if portfolio scoping is enabled.
5. Retrieve the in-scope Companies.
6. Extract each company's `rrid`.
7. Retrieve Periods for each in-scope company.
8. Retrieve Ratings for each in-scope company.
9. Validate required identifiers and response structures.
10. Map Company, Periods, and Ratings into the Z2 Company DataModel.
11. Upsert the mapped data without duplicating existing records or deleting historical periods/ratings unintentionally.
12. Record synchronization results and report errors or partial completion.

Explain the recommended record identity keys:

- Company: `tenantId + provider + rrid`
- Period: `tenantId + provider + rrid + periodId`
- Rating: `tenantId + provider + rrid + periodId`, adding a separate rating identifier if the provider contract confirms one is distinct.

Do not associate records by company name, array index, or response order. Clearly identify the assumption that a rating's `id` corresponds to the reporting-period ID and state that the provider contract should confirm it.

Explain how to handle a company with multiple periods and ratings and how the sync avoids duplicate array entries.

## 12. Security, Validation, and Error Handling

Cover the solution-level requirements for:

- Tenant isolation and tenant-specific integration configuration.
- Secure storage and rotation of Client ID and Client Secret.
- Server-side access-token handling and log redaction.
- Invalid credentials and token acquisition failures.
- Confirmed expired-token responses and one-time retry.
- Permission errors, including `401` and `403` where returned by the provider.
- Provider timeouts and transient failures.
- Empty Companies, Periods, or Ratings responses.
- Missing or invalid `rrid` and period identifiers.
- Null values and numeric precision.
- Duplicate records and repeated sync execution.
- Partial failures when one company's Periods or Ratings cannot be retrieved.
- Provider schema changes.
- Pagination, rate limits, and incremental sync, where supported and verified.

Separate provider errors from Z2 mapping or persistence errors. Do not claim that a status code always has one meaning unless the provider contract confirms it.

## 13. Key Design Decisions

Summarize the recommended decisions, including:

- RapidRatings authentication is performed by the Z2 backend using OAuth2 Client Credentials.
- The access token is cached and renewed before expiry.
- RapidRatings is added as a `NESTED` group to the existing Z2 Company DataModel.
- Company profile properties are stored under the RapidRatings group.
- Periods and Ratings are separate `PROPS_ARRAY` collections under RapidRatings.
- The dedicated Periods and Ratings endpoints are used to retrieve their respective detailed records.
- Company, Periods, and Ratings are associated using stable provider identifiers.
- `latestRatings` is handled as a summary/source field and is not silently merged with the dedicated Ratings response.
- Manual and scheduled data sync use the same orchestration and mapping logic.
- Schema synchronization is a separate process from data synchronization.

Clearly label recommendations separately from confirmed facts.

## 14. Open Questions and Production Readiness Checks

End the document with a concise table of items that must be confirmed before production rollout. Include at least:

| Area | Question to confirm |
|---|---|
| Authentication | Exact token response fields, expiry representation, and token error contract |
| Companies | Pagination, portfolio filtering, and supported query parameters |
| Periods | Complete field list, nullability, units, currency, and numeric precision |
| Ratings | Complete field list, metric definitions, units, ranges, and period identifiers |
| `latestRatings` | Precedence and handling when values differ from the dedicated Ratings API |
| Outreach | HTTP method, authentication, permissions, request/response schema, and scope |
| Z2 DataModel | Supported property types, URI representation, array element definitions, and null handling |
| Z2 UI | Company Details bindings and table/list rendering for `PROPS_ARRAY` |
| Synchronization | Supported schedules, pagination, rate limits, incremental retrieval, and deletion/update semantics |

Do not present an unverified item as settled.

## 15. Required Document Structure and Writing Style

Use these headings in the final solution overview:

1. Executive Summary
2. Integration Context and Scope
3. Integration Architecture
4. RapidRatings API Inventory
5. Authentication and Token Management
6. RapidRatings Source Schema
   - Company Schema
   - Periods Schema
   - Ratings Schema
7. Z2 Company DataModel Extension
8. Company, Periods, and Ratings Schema Mapping
9. Company Details and Raw Properties Presentation
10. Schema Synchronization
11. Manual and Scheduled Data Synchronization
12. Security, Validation, and Error Handling
13. Key Design Decisions
14. Open Questions and Production Readiness

Use clear professional language suitable for a solution design review and developer handover. Include concise tables, readable JSON examples, and simple architecture or sequence diagrams where they add value.

Keep the document focused on the RapidRatings integration. Do not add lengthy source-code listings, speculative API details, generic microservices explanations, or unrelated project information.

## 16. Accuracy and Source-Grounding Rules

These rules are mandatory:

- Treat the endpoint list and raw property inventories in this prompt as supplied source material, not as proof of every runtime behavior.
- Preserve all supplied property names and data types in the source-schema inventory.
- Use snake_case consistently for proposed Z2 target property names.
- Distinguish **verified from supplied samples**, **proposed design decisions**, and **items requiring confirmation**.
- A field reported as `null` in a sample does not establish its permanent schema type. Mark its type as unresolved until more evidence is available.
- Do not invent request parameters, response properties, pagination formats, token response fields, API capabilities, or Z2 DataModel configuration syntax.
- Do not silently drop source properties. If a property is not mapped, explain why and identify the follow-up action.
- Do not silently merge conflicting values from `latestRatings` and the Ratings endpoint.
- Do not silently convert currencies, financial scales, nulls, or numeric values.
- Do not claim that the Company Details page automatically renders a property merely because it exists in the DataModel.
- Do not assume nested objects inside `PROPS_ARRAY` are supported unless verified in the actual Z2 runtime.
- Keep the final result a **solution overview**, not a low-level implementation specification.

The final document should allow a reviewer to understand the integration architecture, authentication approach, complete Company/Periods/Ratings data mapping, target Z2 Company structure, user-facing presentation, synchronization approach, and remaining decisions before implementation and production rollout.
