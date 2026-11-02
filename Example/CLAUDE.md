# External Item Data Integration — Requirements & Instructions

This file has two parts:

- **Project guide**: how the repository is built today, how to run and test it, and the decisions made while building it. Keep it current when the code changes.
- **Requirements** (sections 1–32): the original specification, written for a Spring Boot backend. **The backend has since been removed**: the React frontend now calls the hosted server (Enterprise) directly through a dev proxy. Where the requirements mention the backend, its local API or "the frontend never calls the hosted server", the guide below takes precedence; the data, mapping, paging, error-handling and security rules still apply to the frontend.

---

# Project guide

## Layout

```text
frontend/   React 19, TypeScript, Tailwind 4, Vite 6, TanStack Query, React Router
README.md   overview, configuration, run and test instructions
```

Frontend:
- `src/queries`: the Enterprise request payloads (query-config and graph-match JSON), one file per entity or traversal.
- `src/services`: Enterprise calls and mapping.
  - `enterpriseQuery.ts` is the only HTTP client (axios). It provides `executeQuery`, `enterpriseGet`/`enterprisePost`, `EnterpriseQueryError` and `getErrorMessage`.
  - `enterpriseValues.ts` holds the shared value normalisation and code labels.
  - Per-entity mappers: `itemMapper`, `partMapper`, `siteMapper`, `companyMapper`.
  - Hierarchy: `hierarchy.ts` and `hierarchyTree.ts`.
  - Details pages: `objectOverview.ts` plus `itemDetails.ts`, `partDetails.ts` and `companyDetails.ts`.
  - RapidRatings: `rapidRatings.ts` (second axios client, base `/rapidratings`; name search, `next` paging, `RapidRatingsError`) and `rapidRatingsMapper.ts`. `userFacingError.ts` is the shared base of both error classes and holds `getErrorMessage`.
- `proxy/rapidRatings.ts` (outside `src/`, web APIs only): the `/rapidratings` handler shared by the Vite dev/preview servers and the Netlify Edge Function.
- `src/hooks`: TanStack Query hooks.
- `src/types`: the internal models.
- `src/pages`: one per tab.
- `src/components/table`: shared `DataTable`, `PagedEntityPage`, `PaginationFooter`, `SearchBox`, `LoadError` and cell helpers.
- `src/components/<feature>`: column definitions and feature components.

## Commands

```bash
# Node 22.3+
cd frontend && npm run dev                      # :3000; /enterprise is proxied to the hosted server with the token
cd frontend && npm run typecheck && npm test
cd frontend && npm run build && npm run preview  # dist/ on :4173, same /enterprise proxy
```

## Configuration and secrets

`frontend/.env` (git-ignored; copy `frontend/.env.example`):

| Variable | Used for |
|---|---|
| `VITE_ENTERPRISE_API_BASE_URL` | Base path the browser calls (default `/enterprise`) |
| `ENTERPRISE_API_TOKEN` | Bearer token. Read only by the Vite dev proxy (Node side) in `vite.config.ts` and set as `Authorization`. Not `VITE_`-prefixed, so it never enters the bundle |
| `ENTERPRISE_API_HOST` | Enterprise origin (https, no path). Vite proxy target (falls back to the host in `vite.config.ts` when empty); also read by the Netlify Edge Function |
| `RAPIDRATINGS_CLIENT_ID`, `RAPIDRATINGS_CLIENT_SECRET` | RapidRatings OAuth client credentials. Read only by `proxy/rapidRatings.ts` (Vite middleware / Netlify Edge Function); never in the bundle |
| `RAPIDRATINGS_API_BASE_URL` | Optional; default `https://rridata.sandbox.rrilabs.co/v2.0` |

- `frontend/.env.example` is committed and must keep `ENTERPRISE_API_TOKEN=` empty.
- Never print the token. To check it, decode only its `exp` claim.
- Hosted tokens expire after about 24 hours. The symptom is "The Enterprise API token is missing or expired (401)" on every tab. Restart `npm run dev` after editing `.env`.
- Error messages come only from `EnterpriseQueryError`, which never includes headers or the token.
- **Host name in docs:** write the Enterprise server as `https://<enterprise-host>/api/v1/platform/...` (for example `https://<enterprise-host>/api/v1/platform/core/data-configuration/table-configs/item-hierarchy`). The real host lives only in `frontend/vite.config.ts`; do not copy it into docs.
- **Production (Netlify):** `netlify.toml` plus the Edge Functions `frontend/netlify/edge-functions/enterprise.ts` and `rapidratings.ts` replace the Vite proxy. The notes below are for `enterprise.ts`; `rapidratings.ts` reads `RAPIDRATINGS_CLIENT_ID` / `RAPIDRATINGS_CLIENT_SECRET` (and optionally `RAPIDRATINGS_API_BASE_URL`) and delegates to `proxy/rapidRatings.ts`.
  - The function reads `ENTERPRISE_API_HOST` (https origin) and `ENTERPRISE_API_TOKEN` from the Netlify environment. Redeploy after changing them.
  - It only calls `<host>/api/v1/platform/*` with GET/POST.
  - It strips cookies.
  - It times out at 38 s waiting for headers, but never while the body streams.
  - Its own errors carry `X-Enterprise-Proxy-Error` and a `{ code, message }` body, which `enterpriseQuery.ts` shows verbatim. Enterprise responses pass through unchanged.
  - The function is outside `src/`, so `tsc` does not check it. It runs on Deno, using web APIs only.
- **Other hosting:** `npm run dev` / `npm run preview` (port 4173, which serves `dist/`) use the Vite proxy. Any other deployment needs an equivalent reverse proxy that forwards `/enterprise` to `https://<enterprise-host>/api/v1/platform` and adds `Authorization`. Enterprise answers CORS preflights with `Access-Control-Allow-Origin: *`, so the proxy only exists to keep the token out of the browser; the app has no user login that could supply one.

## Enterprise calls (all through `/enterprise`)

| View | Calls per page | Payload / notes |
|---|---|---|
| Items, Parts, Sites, Companies lists | 1 `POST core/query-config-v2/execute?page&size` | `item-query.json`, `part-query.json`, `site-query.json`, `company-query.json`; `results` mapped by `*Mapper.ts` |
| Dashboard tiles | the same queries with `size=1`; the BOM tile runs config + anchors only (never the graph) | reads `totalElements` |
| Item Hierarchy | 3: `GET core/data-configuration/table-configs/item-hierarchy` → anchor query → `POST core/graph/match/execute` | see below |
| Item details → Overview | 1 `GET core/objects/partial/{id}` | sections in `itemDetails.ts` |
| Item details → Sources | 1 graph match (`item-sources-graph.json`, `item_sources`, depth 1, anchored on the id) | part nodes mapped with `mapPart` rules |
| Part details → Overview | 1 `GET core/objects/partial/{id}` | sections in `partDetails.ts` |
| Company details → Overview | 1 `GET core/objects/partial/{id}` | sections in `companyDetails.ts` |

## RapidRatings (Company details → RapidRatings tab, through `/rapidratings`)

- **Read-only and live.** Nothing is mapped into or written to the Z2 Company; RapidRatings is only called when the tab is opened. The integration spec in `docs/Z2/` (backend sync into a `rapidratings` group) is not implemented; only its display layout and field labels are used.
- **Why a proxy:** RapidRatings sends no `Access-Control-Allow-Origin`, so the browser cannot call it, and the client secret must stay out of the bundle. `proxy/rapidRatings.ts` gets the token (`POST /oauth/token/`, Basic auth, `grant_type=client_credentials`), caches it in memory, renews it 60 s before expiry, and on a 403 renews and retries once. It forwards only GET on `/companies/…` and `/portfolios/…`, passes rate-limit headers, drops cookies, and its own errors carry `X-RapidRatings-Proxy-Error` + `{ code, message }`.
- **Calls:** `GET companies/?name=<name without legal suffix>` (≤ 5 pages) → `GET companies/{rrid}/` + all pages of `periods/` and `ratings/` in parallel. Paging only follows `next` (rewritten to the proxy path by `toProxyPath`); trailing slashes are required.
- **Demo override (current):** `FIXED_RAPIDRATINGS_COMPANY` in `RapidRatingsTab.tsx` makes every company show RRID `KawjbGJ` (Rite Aid Corporation, sandbox), with a "Demo data" notice, and skips the name search. Only Sealed Air (`694400`) matches a sandbox company by name. Set it to `null` to restore matching.
- **Matching:** a candidate whose name equals the Z2 `company_name` ignoring case, punctuation and trailing legal suffixes (`normaliseCompanyName`) is shown directly; otherwise the user picks one from the candidates. Never matched on anything looser automatically.
- **Values:** nulls stay null (shown "—"), signs and precision kept, nothing rescaled; the periods table notes the currency and data scale. `debitOwnedWithinOneYear` (provider spelling) is `debtDueWithinOneYear` internally.

**Item Hierarchy:**
1. The table config currently returns `queryConfigObject: null`, so the anchor query is `item-hierarchy-anchors.json`. A query configured in the table config is used if one appears.
2. The anchor query (top of `item_bom`, scope `516`, distinct, ascending) selects `item.item_number AS anchor` and `item.id AS item__id`. Rows with only a number are resolved with one `item.item_number IN (...)` lookup in the same scope. Only ids reach the graph.
3. The graph match is `item-hierarchy-graph.json` with `anchorProperty: "id"` and the ids (as strings) set at runtime. Batches of 25 anchors run in parallel and are merged by node/edge id.
4. The graph is slow (≈30–40 s and ≈2 MB for 12 products), so its timeout is 180 s.
5. `hierarchyTree.ts` builds the tree from the edges.

Scope `516` replaced `4332013722`, which returns 0 anchors in the current data environment.

Pages use `types/page.ts` `PageResponse`: `count, objects, page (0-based), size, totalObjects, totalPages, hasMore`. `totalPages` is null when there is no exact total, and the footer then uses `hasMore`.

## Decisions

- **One hosted call per page, never one per row.** Lists page on the server; the frontend never loads the whole dataset.
- **Item Hierarchy:** `item_bom` is many-to-many, so a shared item is repeated under each parent with its own qty. Each occurrence has a unique `key` (path of node ids). Parts are shown inside their item's row (Part Number / Manufacturer columns), not as tree rows. Cycles are cut, and depth is limited to the traversal's `maxDepth` (6).
- **Display labels:** status-like codes become readable labels.
  - `codeLabel` in `enterpriseValues.ts` handles known codes, e.g. `PRODN_APPROVED` → Production Approved, `N_A` → N/A.
  - Other codes are title-cased by `humanizeCode` in `objectOverview.ts`, e.g. `NON_COMPLIANT` → Non Compliant. Codes of 3 characters or fewer (US, EXW, Z2) stay as-is.
  - Entity enums (`SourcingType`, `SupplyChainRisk`, `LifecycleStatus`, `SiteType`, `AvailabilityRisk`) keep their spec labels.
- **The object endpoint returns an empty 200 for an unknown id.** `fetchObject` turns that, or an object of the wrong type, into an `EnterpriseQueryError` with status 404 ("No item exists with this id."). The details hooks do not retry 404s.
- **Overview sections follow the live schema.** The item and part field tables (`itemDetails.ts`, `partDetails.ts`) name the properties the objects actually return, for example:
  - `item_risk.*` / `part_risk.*` (band + score)
  - `env_compliance.*.status`
  - `multi_sourcing.*`, `geographic_concentration.*`
  - `lifecycle.*`, `obsolescence.*`, `origin.*`, `trade_codes.*`, `qualifications.*`, `parametric.*`

  The original spec's fields (`item_status`, `revision`, `availability_risk`, `environmental_compliance.*`, `lifecycle_metadata.*`) do not exist in this environment. In the Risk section a band shows as a badge, with "Score N · Driven by: …" as its reason line.
- **Item Sources** selects only part properties that exist (`part.z2_properties_comparison` does not, and made the query fail with 400). Sourcing type and supply chain risk show their defaults (Unknown / Not Assessed).
- **IDs are numbers** (hosted ids exceed 32-bit int but are safe JS integers).
- **Removed on request:** the backend, the DigiKey integration, the item details "Where Used" tab, the Add Item button, the Actions and ID columns, and the "Go to page" box (Previous/Next and page numbers remain).

## How to add an entity or tab

1. Add the payload JSON under `src/queries/`.
2. Add the internal model in `src/types`, a mapper in `src/services`, and a service built on `executeQuery` (or `enterprisePost` for a graph match).
   - Reuse `enterpriseValues.ts` and the existing enum mappers.
   - External names live only in the mapper and the query file.
3. Add a hook, a column list and a page built on `PagedEntityPage`. Then add the route in `App.tsx` and the link in `Sidebar.tsx`.
4. Add MSW tests that use a real response shape.

Before writing a mapper, look at a real hosted response with a read-only call. Several shapes differed from the samples and from the original spec, for example:
- ODM values arrive as strings like `"[\"true\"]"`.
- Object properties are wrapped as `{ value, … }`.
- Graph nodes key nested properties by their dotted name.

## Known gaps

- The Item Hierarchy table config's `statisticProperties` (counts by status and type) are not shown.
- Search filters the current page only, on every tab.
- The token proxy only exists in `npm run dev` / `npm run preview` and the Netlify Edge Function (see Configuration).

---

# Requirements

## 1. Objective

Build a Spring Boot application that retrieves Item data from an external hosted server and exposes the data through a local REST API.

The application acts as an integration layer between the external hosted data environment and the local frontend.

---

## 2. Core Requirements

- Item data comes from an external hosted server.
- The local application must not use a database for Item data.
- Item data must not be persisted locally.
- The application should retrieve the required Item data from the hosted server through one API call.
- The hosted server requires Bearer Token authentication.
- The external API URL must be configurable.
- The Bearer Token must be configurable.
- API URL and Bearer Token must be provided through environment variables.
- The Bearer Token must never be hard-coded.
- The Bearer Token must never be exposed to the frontend.
- The external API response must be mapped into the application's internal Item data model.
- The frontend should communicate only with the local application API.
- The frontend should not directly call the hosted server.
- External API-specific field names should be isolated from the internal application model.

---

# 3. Application Context

The application has its own Item data model.

The actual Item data, however, is maintained by an external hosted data environment.

The local application does not own or persist this data.

Instead, whenever Item data is requested:

1. The frontend calls the local Item API.
2. The local application calls the hosted server.
3. The hosted server returns the Item data.
4. The local application maps the external response.
5. The local application returns the mapped Item data to the frontend.

The application therefore behaves as a **read-only data integration layer**.

---

# 4. Data Ownership

### External Hosted Server

The hosted server is the source of truth for Item data.

It owns:

- Item information
- Item identifiers
- Item descriptions
- Item revisions
- Business units
- Product information
- Structure roles
- ODM information
- Availability risk
- Product usage
- Product family impact
- Item status

### Local Application

The local application is responsible for:

- Calling the hosted API
- Authenticating with the hosted API
- Receiving the external response
- Mapping external fields
- Providing a clean local API
- Returning data to the frontend

The local application does **not** own the underlying Item data.

---

# 5. No Database Requirement

There must be no database dependency for retrieving Item data.

The application must not:

- Save Items into PostgreSQL
- Save Items into MySQL
- Create Item database tables
- Insert Item records
- Update Item records
- Delete Item records
- Use a repository for Item persistence
- Use JPA/Hibernate for Item persistence

The data should remain in the hosted environment.

---

# 6. High-Level Architecture

```text
Frontend
    |
    | GET /api/items
    |
    v
Item Controller
    |
    v
Item Service
    |
    v
External API Client
    |
    | One authenticated API call
    |
    v
Hosted Server
    |
    | Item data
    |
    v
External Response
    |
    v
Mapper
    |
    v
Internal Item Model
    |
    v
Item Response
    |
    v
Frontend
```

The architecture must remain simple:

**Frontend → Local API → External API → Mapping → Local API Response**

---

# 7. Central External API Configuration

Create one centralized configuration area for the hosted server connection.

The configuration must contain:

- External API URL
- Bearer Token
- Any required external API settings

The configuration should be used by the external API client.

The application must not spread the external URL or authentication token across multiple classes.

---

# 8. Environment Configuration

The following information must come from environment variables:

### External API URL

The URL of the hosted Item data API.

### Bearer Token

The authentication token required to access the hosted server.

The token must be treated as a secret.

### Security instructions

- Never hard-code the token.
- Never commit the token to Git.
- Never return the token in an API response.
- Never display the token in application logs.
- Never send the token to the frontend.
- Keep environment-specific configuration outside application source code.

---

# 9. Required Application Components

The application should contain the following logical components:

### Item Model

Represents the application's internal Item structure.

### Item DTO

Represents the data exposed by the local application API.

### External Response DTO

Represents the response received from the hosted server.

This DTO should contain the external API response structure.

### Item Mapper

Responsible for converting external Item data into the application's internal Item representation.

### External API Client

Responsible only for communicating with the hosted server.

### Item Service

Responsible for coordinating the external data retrieval and mapping process.

### Item Controller

Responsible for exposing the local Item REST API.

### External API Configuration

Responsible for managing the hosted server URL and authentication configuration.

---

# 10. Item Data Model

The internal Item model must contain the following business fields:

| Field | Type | Description |
|---|---|---|
| itemNumber | Text | Item number |
| description | Text | Item description |
| revision | Text | Item revision |
| businessUnit | Text | Business unit |
| isProduct | Boolean | Indicates whether the Item is a product |
| structureRole | List of Values | ITEM or BOM |
| odmName | Text/List | ODM name information |
| odmActive | Boolean/List | ODM active information |
| availabilityRisk | List of Values | LOW, MEDIUM, HIGH, NOT ASSESSED |
| usedInProducts | Text | Products where the Item is used |
| productFamiliesImpacted | Number | Number of impacted product families |
| itemStatusName | Text | Item status |
| id | Integer/Long | Item identifier |

---

# 11. Structure Role

The Item structure role has two possible values:

- ITEM
- BOM

This represents the role of the Item within the product structure.

The external value must be converted into the application's internal representation.

---

# 12. Availability Risk

Availability risk has the following allowed values:

- LOW
- MEDIUM
- HIGH
- NOT ASSESSED

The external API may provide the value in uppercase format.

The application must normalize the external value into the internal representation.

---

# 13. External Data Fields

The hosted API provides the following fields:

| External Field | Internal Field |
|---|---|
| item_number | itemNumber |
| description | description |
| item__revision | revision |
| item__business_unit | businessUnit |
| item__is_product | isProduct |
| item__structure_role | structureRole |
| item__odm__name | odmName |
| item__odm__active | odmActive |
| item__availability_risk | availabilityRisk |
| item__used_in_products | usedInProducts |
| item__product_families_impacted | productFamiliesImpacted |
| item__item_status__name | itemStatusName |
| item__id | id |

The external field names must not become the application's internal business naming convention.

For example:

**External:**

`item__business_unit`

**Internal:**

`businessUnit`

---

# 14. External Response Context

The hosted server response contains multiple sections.

### Query Information

The response may contain the ZQL query used by the hosted server.

This is external-system metadata.

The local Item model does not need to expose the ZQL query.

### Properties

The response may contain the list of returned property names.

This describes the external response structure.

The local application should use it only as required for external response processing.

### Objects

The response may contain metadata describing the external object.

This is external-system metadata and does not need to become part of the internal Item model.

### Results

The results section contains the actual Item records.

The application must map the records from this section into the internal Item model.

---

# 15. Single API Call Requirement

The application must retrieve the Item dataset using one external API request.

The expected behavior is:

```text
Local Item Request
       |
       v
One External API Call
       |
       v
External Response
       |
       v
Multiple Item Results
       |
       v
Local Mapping
```

The application must not make a separate external API call for every Item.

For example, if the hosted server returns 10,000 Items:

- External API calls = 1
- Items received = 10,000
- Mapping operations = 10,000 locally

---

# 16. Mapping Responsibility

The mapper is an important boundary between the external system and the local application.

Its responsibility is to:

- Read external field names.
- Convert external values.
- Normalize enums.
- Normalize empty values.
- Convert external identifiers.
- Convert ODM information.
- Create the internal Item representation.
- Create the local API response representation if required.

The controller must not contain mapping logic.

The service must not contain detailed field-by-field mapping logic.

Mapping should be centralized in the mapper.

---

# 17. ODM Data Handling

The external API currently provides ODM values such as:

- ODM name as an array-like value.
- ODM active as an array-like value.

Example external values:

`[]`

These values need to be normalized before being exposed through the local API.

The implementation must confirm the complete ODM response format from the real hosted API.

The mapping design must support:

- Empty ODM values
- One ODM value
- Multiple ODM values
- Missing ODM values

The final internal type should be selected based on the actual external API contract.

---

# 18. Empty and Null Values

The external system may return:

- Null
- Empty string
- Empty array
- Missing field

The application must handle these values safely.

A missing or empty value must not cause the entire Item retrieval operation to fail.

The normalization rules should be consistent across all Item records.

---

# 19. Service Responsibility

The Item service coordinates the complete business flow.

The service should:

1. Request the Item data from the external API client.
2. Receive the external response.
3. Extract the Item result records.
4. Pass each record to the mapper.
5. Build the local response.
6. Return the result to the controller.

The service must not:

- Directly construct HTTP requests.
- Contain the Bearer Token.
- Contain the external URL.
- Contain large amounts of field mapping logic.
- Save Items to a database.

---

# 20. External API Client Responsibility

The external API client is responsible for communication with the hosted server.

It should:

- Read the centralized external configuration.
- Build the external request.
- Add the Bearer Token.
- Call the hosted API.
- Receive the response.
- Convert the response into the external response DTO.
- Handle external communication errors.

The client should not contain business mapping logic.

---

# 21. Controller Responsibility

The Item controller exposes the local REST endpoint.

The frontend should call the local endpoint to retrieve Items.

Example logical endpoint:

```text
GET /api/items
```

The controller should:

1. Receive the frontend request.
2. Call the Item service.
3. Receive the mapped Item response.
4. Return the response to the frontend.

The controller should not know:

- The external server URL
- The Bearer Token
- The ZQL query
- External field names
- External response structure

---

# 22. Frontend Responsibility

The frontend communicates only with the local application.

The frontend should not directly communicate with the hosted server.

Expected flow:

```text
Frontend
    |
    | GET /api/items
    v
Local Spring Boot Application
    |
    v
Hosted Server
```

The frontend receives a clean Item structure using internal field names.

---

# 23. API Response Contract

The local API should expose clean application-level fields.

The response should contain:

- id
- itemNumber
- description
- revision
- businessUnit
- isProduct
- structureRole
- odmName
- odmActive
- availabilityRisk
- usedInProducts
- productFamiliesImpacted
- itemStatusName

The local API should not expose unnecessary external-system metadata such as:

- ZQL
- External object metadata
- External property metadata

unless specifically required by the frontend.

---

# 24. Error Handling Requirements

The application must handle external API failures.

### Unauthorized

If the Bearer Token is invalid or expired, the application must handle the unauthorized response appropriately.

### Forbidden

If the hosted server rejects access, return an appropriate error to the local client.

### Not Found

If the configured external endpoint is unavailable, return an appropriate error.

### Rate Limiting

If the hosted server returns a rate-limit response, the application must handle it appropriately.

### Server Error

External server failures must be handled without exposing internal implementation details.

### Timeout

Network timeout and connection failures must be handled gracefully.

The Bearer Token must never appear in error messages.

---

# 25. Security Requirements

The hosted server authentication information is sensitive.

The application must:

- Store the token only in environment configuration.
- Protect the token from source control.
- Protect the token from logs.
- Protect the token from API responses.
- Protect the token from frontend access.
- Avoid exposing the external authentication mechanism unnecessarily.

---

# 26. Performance Requirements

The application should optimize for the following flow:

```text
One external request
        ↓
Complete external response
        ↓
Local in-memory mapping
        ↓
Local API response
```

No repeated external calls should be generated during mapping.

The mapper must work on the already retrieved response.

---

# 27. Separation of Responsibilities

The design should maintain clear separation:

### Configuration

Knows:

- External URL
- Authentication configuration

### Client

Knows:

- How to call the external API

### External DTO

Knows:

- External response structure

### Mapper

Knows:

- How to translate external fields into internal fields

### Model

Knows:

- Internal Item business structure

### Service

Knows:

- Application flow

### Controller

Knows:

- Local API contract

### Frontend

Knows:

- Local API response

---

# 28. Complete Data Flow

```text
                 ENVIRONMENT
                     |
          External API URL
          Bearer Token
                     |
                     v
          Central Configuration
                     |
                     v
          External API Client
                     |
                     | ONE API CALL
                     v
           Hosted Data Environment
                     |
                     | Item Response
                     v
         External Response Structure
                     |
                     v
             External DTO
                     |
                     v
                Mapper
                     |
                     v
              Item Model
                     |
                     v
             Item Service
                     |
                     v
            Item Controller
                     |
                     v
              Local REST API
                     |
                     v
                 Frontend
```

---

# 29. Main Implementation Principle

The most important design principle is:

**The hosted server is the source of truth, while the local application is a read-only integration and presentation layer.**

The local application retrieves the data, transforms it into its own model, and exposes it through its own API.

No Item data is persisted locally.

---

# 30. Expected Result

After implementation, the application should provide the following behavior:

1. Application starts.
2. External API configuration is loaded from environment variables.
3. Frontend requests Item data.
4. Local Item controller receives the request.
5. Item service starts the retrieval process.
6. External API client sends one authenticated request.
7. Hosted server returns the Item dataset.
8. External response is converted into the external DTO.
9. Mapper converts external fields into the internal Item model.
10. Service prepares the local response.
11. Controller returns the clean Item data.
12. Frontend displays the Item information.
13. No Item data is saved in a local database.

---

# 31. Future Extensibility

The same architecture should be reusable for other external entities such as:

- Parts
- Sites
- BOM
- Suppliers
- Relationships
- Compliance data
- Risk data

For example:

```text
External Hosted Server
        |
        +---- Item API
        |
        +---- Part API
        |
        +---- Site API
        |
        +---- BOM API
        |
        +---- Supplier API
```

Each domain can have its own:

- External DTO
- Model
- Mapper
- Service
- Controller

while sharing the centralized external API configuration and client infrastructure where appropriate.

---

# 32. Final Requirement

Build the Item integration as a **database-free, read-only external data integration service**.

The final architecture must follow:

**Hosted Data → One API Call → External DTO → Mapper → Internal Item Model → Service → Controller → Frontend**

The implementation must keep external API details isolated, use environment-based authentication, avoid local persistence, and provide a clean internal Item API contract.