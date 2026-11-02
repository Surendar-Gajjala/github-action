# Prompt: Write the Requirements for the RapidRatings Integration in Z2 Data Integrations

## 1. Your task

Write one complete requirements document that developers can build from. It covers integrating the RapidRatings API into the **Data Integrations** tab of Z2 Enterprise and showing the results on the **Company Details** page.

- Write requirements only, not application code.
- Everything you need is in this prompt. You do not need any other file.
- Return the whole document as Markdown.

## 2. Background

Z2 Enterprise has a place called **Data Integrations**, where each customer chooses which integrations to enable for their tenant. RapidRatings provides financial health ratings for companies, and it is one of those integrations. More providers will follow, so shared logic must stay separate from RapidRatings-specific logic.

Every integration has these common functions, and the document must cover all of them:

1. Integration authentication
2. Schema
3. Schema mapping to Z2 Enterprise
4. Syncing of schema and data, as separate operations
5. Manual and scheduled syncs
6. Sync schedule and change history

RapidRatings data extends the existing Z2 Company. It must not become a separate Company object type.

## 3. The flow to specify

1. A tenant administrator opens the Data Integrations tab, enables RapidRatings, and enters a Client ID and Client Secret.
2. The Z2 backend calls the authentication API. Nothing else runs until this succeeds.
3. After a successful response, the backend uses the Bearer token to read portfolios and companies.
4. For each company, the backend uses its RapidRatings ID (RRID) to read company details, periods and ratings.
5. Z2 links each RapidRatings company to an **existing** Z2 Company and maps the data into a new `rapidratings` group on that Company.
6. The Company Details tab shows the company profile, current rating, report links, and tables for Latest Ratings, Periods and Ratings.
7. An administrator can run a sync manually or on a schedule, and every run and configuration change is recorded.

```text
Authenticate -> Portfolios and Companies -> for each RRID: Details, Periods, Ratings
             -> Link to Z2 Company -> Map -> Save -> Show on Company Details
```

## 4. RapidRatings API

Sandbox base URL: `https://rridata.sandbox.rrilabs.co/v2.0`

Facts marked as measured come from checking all 96 sandbox companies on 2026-11-01. They describe the sandbox and do not prove production behaviour.

### 4.1 Endpoints

| Resource | Method | Path | Paging |
|---|---|---|---|
| Token | `POST` | `/oauth/token/` | None |
| Portfolios | `GET` | `/portfolios/` | Follow `next` |
| Companies | `GET` | `/companies/` | Follow `next`, 20 per page, fixed |
| Company details | `GET` | `/companies/{rrid}/` | None |
| Periods | `GET` | `/companies/{rrid}/periods/` | Follow `next`, 10 per page, fixed, newest first |
| Ratings | `GET` | `/companies/{rrid}/ratings/` | Follow `next`, 10 per page, fixed, newest first |

List responses have this shape:

```json
{ "count": 27, "previous": null, "next": "https://.../periods/?page=2", "results": [] }
```

**Out of scope for release 1**

- `https://api.sandbox.rrilabs.co/outreach-requests`: `POST` makes RapidRatings send real emails to the counterparty. This host also uses different paging and error formats.
- `POST /companies/`: creates a private company at RapidRatings.
- `/companies/{rrid}/financials/`: returned no rows for all 96 companies. Store the URL only.
- `/companies/{rrid}/reports/?reportType=`: returns JSON with `reportContent`, `mimeType`, `md5sum` and `reportType`. Serving reports from Z2 is a possible later release.

### 4.2 Authentication

```http
POST /v2.0/oauth/token/
Host: rridata.sandbox.rrilabs.co
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
```

```json
{ "access_token": "<token>", "expires_in": 1800, "token_type": "Bearer", "scope": "uid introspection openid" }
```

- Send `Authorization: Bearer <access_token>` on every data call.
- There is no refresh token. To renew, repeat the same request.
- The sandbox token lasts 1,800 seconds. Read `expires_in` instead of hard-coding it.

### 4.3 Behaviour

| Topic | Behaviour |
|---|---|
| Trailing slash | Required. Without it the API returns 404. |
| Companies parameters | `page`, `portfolio`, `name` (3+ characters, partial, case-insensitive), `ticker` (exact, case-sensitive), `externalSourceId` |
| Other list parameters | `page` only |
| Unknown parameter | 400 |
| Page past the end | 404 for companies; 500 for portfolios, periods and ratings. Never build page numbers. |
| Token endpoint 400 | Unsupported grant type |
| Token endpoint 401 | Wrong credentials, missing header, or missing `grant_type` |
| Data call 401 | `Authorization` header missing |
| Data call 403 | Invalid or expired token |
| Error body | `{"<Scope>": {"message": "...", "info": {}}}`. The scope key varies: `Request`, `Portfolio`, `Companies`, `Not found`. |
| Changed-since filter | None |
| Rate-limit headers | `X-Ratelimit-Limit`, `X-Ratelimit-Remaining`, `X-Ratelimit-Reset`, `Retry-After`. `Retry-After` also appears on normal responses (for example 3,464), so read it only on a 429. A 429 has not been seen in testing. |
| Browser access | The data host sends no CORS headers for browser origins |
| Machine-readable schema | None for this host. The schema must be inferred from responses. |

### 4.4 Measured volumes

| Measure | Result |
|---|---|
| Companies | 96, on 5 list pages |
| Portfolios | US COVERAGE 96, z2Data Sandbox 96, Fortune 500 37, INTERNATIONAL COVERAGE 0, z2Data Sandbox-Private 0. Portfolios overlap. |
| Rows | 1,613 periods and 1,614 ratings |
| Periods per company | 2 to 27 |
| Calls for a full read | 533 |

### 4.5 Sample responses

A company (details, shortened to one rating):

```json
{
  "id": "KawjbGJ",
  "name": "Rite Aid Corporation",
  "country": "USA",
  "ticker": "RAD",
  "exchange": "US",
  "sector": "Retail",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/",
  "financials": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/financials/",
  "periods": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/periods/",
  "ratings": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/ratings/",
  "latestRatings": [
    {
      "id": "2020YE",
      "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/ratings/2020YE/",
      "eqyYear": 2020,
      "period": "YE",
      "financialDate": "2020-02-29",
      "reportingPeriod": "2020-05-15",
      "fhr": 35,
      "chs": 37,
      "probabilityDefault": 1.5853,
      "termPd": {
        "year2": 8.24, "year3": 12.18, "year4": 15.85, "year5": 19.4, "year6": 23.04,
        "year7": 26.93, "year8": 31.16, "year9": 35.81, "year10": 40.85
      },
      "delta": 13,
      "simulatedFhr": 37,
      "simulatedFhrDelta": 9,
      "operatingProfitability": 36,
      "netProfitability": 5,
      "capitalStructureEfficiency": 82,
      "costStructureEfficiency": 50,
      "leverage": "Weak",
      "liquidity": "Weak",
      "earningsPerformance": "Weak"
    }
  ],
  "reportLinks": {
    "fhr": "https://portal.sandbox.rrilabs.co/reportsapi/31136/fhr",
    "financialDialog": "https://portal.sandbox.rrilabs.co/reportsapi/31136/financial_dialogue",
    "peerBenchmark": "https://portal.sandbox.rrilabs.co/reportsapi/31136/peer_benchmark"
  },
  "address": "",
  "reports": [
    { "reportType": "fhr", "downloadHref": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/reports/?reportType=fhr" }
  ],
  "sectorAverages": { "chs": 48.5, "fhr": 55.6 },
  "industryClassificationSystem": null,
  "industryClassificationCode": null,
  "dataScale": "millions",
  "currency": "USD",
  "dataConfidential": "PUB",
  "externalSourceId": null,
  "externalSourceIds": []
}
```

A period:

```json
{
  "id": "2020YE",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/periods/2020YE/",
  "eqyYear": 2020,
  "period": "YE",
  "financialDate": "2020-02-29",
  "currency": "USD",
  "salesRevenue": 21928.393,
  "earningsBeforeInterestAndTax": 152.271,
  "interestExpense": 229.657,
  "interestReceived": null,
  "companyTaxExpense": 387.607,
  "abnormalItems": -92.793,
  "netProfitAfterTax": -469.219,
  "bankCashBalances": 218.18,
  "totalCurrentAssets": 3700.641,
  "fixedAssets": 4119.094,
  "financialAssets": null,
  "totalAssets": 9452.369,
  "debitOwnedWithinOneYear": 8.84,
  "totalCurrentLiabilities": 2766.463,
  "termLoans": 3096.594,
  "totalLiabilities": 8777.842,
  "totalShareholderEquity": 674.527,
  "cashFromOperationsFromCashFlow": null,
  "netInvestingCashFlow": -86.542,
  "netFundingCashFlow": -326.652
}
```

A rating from `/ratings/` has the same fields as a `latestRatings` row.

### 4.6 Traps found in the data

Most of these do not show up in a single sample, so the requirements must handle them explicitly.

| Finding (measured) | What it means for the requirements |
|---|---|
| `sectorAverages` and `reports` appear on company details only, not on the list | Reading only the list won't refresh sector averages |
| `latestRatings` on the list and on details matched for all 96 companies | The list is enough to detect a new rating |
| `latestRatings` IDs always match the start of `ratings` | The newest rating is `latestRatings[0]` |
| For 9 financial-sector companies (45 rows), `latestRatings` has `null` for `simulatedFhr` and all four sub-scores while `ratings` has values for the same ID. For example, `YA5vG2D` rating `2017Q3` has `simulatedFhr` `null` in one and `58` in the other. | Keep the two collections separate, and never fill one from the other. Ask RapidRatings which is authoritative. |
| `KawjkGJ` (Kansas City Southern) has rating `2014Q3`, but its period returns 404 | A rating without a period is valid and must not fail the company |
| 234 quarters are missing across 60 companies | Gaps are valid |
| Same-ID period and rating always have the same `financialDate` | A difference should be a warning, stored as received |
| `leverage`, `liquidity` and `earningsPerformance` are `Strong`, `Average`, `Weak` or `--` (147 rows, all with `null` PD) | Use `STRING`, not `ENUM`, and keep `--` as received |
| `sector` is `---` for 3 companies | Keep as received |
| `reportLinks` varies: 87 companies have `fhr`, `financialDialog`, `peerBenchmark`; 3 have `fin_services` and `companyProfilePage`; 3 have `companyProfilePage`; 3 have none (for example `ALnGkmJ`) | Every link is optional |
| Report links redirect to `/account/login/` without a portal session, and the API token does not help | Label them as RapidRatings portal links that may need a sign-in. Ask whether Z2 users have portal accounts. |
| `href`, `financials`, `periods` and `ratings` need a Bearer token | Don't show them as clickable links |
| Rating nulls (of 1,614): `chs` 190, `probabilityDefault` 147, `simulatedFhrDelta` 56, `delta` 53 | Treat every rating value as nullable |
| Period nulls (of 1,613): `netFundingCashFlow` and `netInvestingCashFlow` 994, `cashFromOperationsFromCashFlow` 688, `interestReceived` 591, `financialAssets` 297, `abnormalItems` 126, `interestExpense` 108, `companyTaxExpense` 75, `fixedAssets` 45 | Treat every amount as nullable. A `null` must never become `0`. |
| `sectorAverages` nulls (of 96): `chs` 9, `fhr` 3 | Nullable |
| `reportingPeriod` repeats within 68 companies | Order rows by `eqyYear` then `YE`, `Q3`, `Q2`, `Q1`, which matches the API order |
| Whole-number amounts may parse as integers | An `integer` and `number` mix is `number` in schema inference |
| All 96 RRIDs have 7 characters, but an outreach example shows `Axtr4P` (6) | Validate `^[A-Za-z0-9]+$`, not the length |
| All 96 have `country` USA, `currency` USD, `dataScale` millions, `dataConfidential` PUB, empty `address`, `null` `externalSourceId`, `[]` `externalSourceIds`, `null` industry fields | These fields can't be fully proven in the sandbox, and linking by external ID can't be tested with this data. List them as open questions. |
| Variations appear in only some sectors | Schema sync must sample several sectors, not just the first company |

## 5. Existing Z2 Company DataModel

| Item | Detail |
|---|---|
| Object | `objectType` 144, `dbName: company`, `dataModelType: CORE`, `dataKind: CUSTOMER_DATA`, primary key `company_name` (required `STRING`) |
| Existing top-level properties | `company_name`, `company_tier`, `owners`, `z2data`, `basic_info`, `ownership`, `financial`, `esg`, `cyber`, `certifications`, `preferred_sourcing`, `supplier_risk`, `z2`, `kharon`, `parts_summary`, `test_group`, `traceability`. None may change. |
| Data types in use | `STRING`, `TEXT`, `INTEGER`, `LONG`, `DOUBLE`, `BOOLEAN`, `DATE`, `TIMESTAMP`, `ENUM`, `LOV`, `LOV_ARRAY`, `STRING_ARRAY`, `USER_ARRAY`, `EMAIL`, `PHONE`, `JSON`, `NESTED`, `PROPS_ARRAY` |
| URLs | No URL type. URLs are `STRING`. |
| Nesting | `PROPS_ARRAY` inside `PROPS_ARRAY` exists (`kharon.risks.topics`). No `NESTED` inside `PROPS_ARRAY` exists, so flatten `termPd`. |
| Required row key precedent | `z2.sanctions.list_key` is a required child of a `PROPS_ARRAY` |
| Integration-written group precedent | `z2data` has `canUpload: false` and `dataModelType: APPLICATION`. `kharon` has `canUpload: true` and `CORE`, `extDataModelId` 2278. |
| Filtering in lists | 8 of 9 `kharon.risks` children are `filterable: true`. Whether row filters work is an open question. Filtering and sorting companies still needs one value per company, so keep `current_*` fields. |
| Company Name | Never written by the integration |
| Country | `basic_info.country`, type `LOV`, Countries list. Each entry looks like `{"name":"United States","value":"US","alpha_2":"US","alpha_3":"USA"}`, and `value` is what gets stored. |
| Identifier candidates for linking | `basic_info.duns_number`, `basic_info.cage_code`, `basic_info.tax_id` |
| Missing from the model | No ticker, exchange or sector property, so link candidates can use only name and country |

The `kharon` group is the pattern for a provider group, with summary values plus lists:

```json
{
  "name": "Kharon Risk Data",
  "dbName": "kharon",
  "path": "kharon",
  "label": "Kharon Risk Data",
  "description": "Risk intelligence about this company sourced from Kharon.",
  "isPrimaryKey": false,
  "required": false,
  "searchable": false,
  "filterable": false,
  "extDataModelId": 2278,
  "dataType": "NESTED",
  "dataModelType": "CORE",
  "canUpload": true,
  "hidden": false,
  "properties": [],
  "isCalculated": false,
  "isAi": false,
  "config": {},
  "uiHints": {}
}
```

- `id`, `dataEnvId` and timestamps are assigned by the platform.
- A child's `path` is its parent's path + `.` + its `dbName`.
- Dates use `"uiHints": {"displayType": "DATE"}`.
- An existing external link looks like this. It templates on `{id}`, so the template for a value that is itself a URL is an open question:

```json
{ "displayType": "LINK", "link": { "href": "https://example.com/details?CompanyId={id}", "type": "EXTERNAL", "color": "blue", "newTab": true } }
```

### Company Details layout

Adding properties does not display them. Layout entries are required. The `details` tab has these sections, in order: `general`, `basic_info`, `ownership`, `financial`, `esg`, `cyber`, five `certifications_*` sections, `preferred_sourcing`, `supplier_risk`, `z2_sanctions`, `kharon`, `kharon_risks`, `parts_summary`. Add the RapidRatings sections directly after `kharon_risks`.

A fields section:

```json
{
  "id": "kharon",
  "title": "Kharon Risk Data",
  "preset": "single",
  "columns": [[{
    "id": "kharon-fields",
    "kind": "fields",
    "columns": 2,
    "bindings": [{
      "id": "kharon_kharon_id",
      "path": [],
      "label": "Kharon ID",
      "target": { "kind": "objectProperty", "dbName": "kharon.kharon_id" }
    }]
  }]]
}
```

A table section:

```json
{
  "id": "kharon_risks",
  "title": "Kharon Risk Data › Risks",
  "preset": "single",
  "columns": [[{
    "id": "kharon_risks-table",
    "kind": "fields",
    "columns": 1,
    "bindings": [{
      "id": "kharon_risks",
      "path": [],
      "label": "Kharon Risk Data › Risks",
      "target": { "kind": "objectProperty", "dbName": "kharon.risks" }
    }]
  }]]
}
```

## 6. Target structure

```text
Z2 Company (existing, unchanged)
├── basic_info, financial, kharon, ... (existing)
└── rapidratings (NEW, NESTED)
    ├── Profile        rrid, source_company_name, source_country, ticker, exchange, sector, address
    ├── Data context   currency, data_scale, data_confidentiality, industry_classification_*, external_source_id(s)
    ├── Technical URLs company_url, financials_url, periods_url, ratings_url
    ├── Report links   report_link_fhr, _financial_dialog, _peer_benchmark, _fin_services, _company_profile
    ├── Benchmark      sector_average_fhr, sector_average_chs
    ├── Current rating current_rating_id, current_fhr, current_chs, current_probability_default, current_financial_date
    ├── Sync           last_synced_at
    ├── latest_ratings (PROPS_ARRAY) from company.latestRatings; replaced each sync
    ├── periods        (PROPS_ARRAY) from the periods endpoint; full history
    └── ratings        (PROPS_ARRAY) from the ratings endpoint; full history
```

### Company field mapping (`rapidratings.*`)

| Source | Target | Label | Type | Note |
|---|---|---|---|---|
| `id` | `rrid` | RRID | STRING | Identity key; filterable |
| `name` | `source_company_name` | Source Company Name | STRING | Suggests link candidates only |
| `country` | `source_country` | Source Country | STRING | ISO-3 |
| `ticker` | `ticker` | Ticker | STRING | Filterable |
| `exchange` | `exchange` | Exchange | STRING | |
| `sector` | `sector` | Sector | STRING | Filterable |
| `address` | `address` | Source Address | TEXT | |
| `href` | `company_url` | Company URL | STRING | Technical; not in layout |
| `financials` | `financials_url` | Financials URL | STRING | Technical; not in layout |
| `periods` | `periods_url` | Periods URL | STRING | Technical; not in layout |
| `ratings` | `ratings_url` | Ratings URL | STRING | Technical; not in layout |
| `currency` | `currency` | Currency | STRING | |
| `dataScale` | `data_scale` | Data Scale | STRING | Applies to all period amounts |
| `dataConfidential` | `data_confidentiality` | Data Confidentiality | STRING | |
| `industryClassificationSystem` | `industry_classification_system` | Industry Classification System | STRING | To confirm |
| `industryClassificationCode` | `industry_classification_code` | Industry Classification Code | STRING | To confirm |
| `externalSourceId` | `external_source_id` | External Source ID | STRING | Linking input |
| `externalSourceIds` | `external_source_ids` | External Source IDs | STRING_ARRAY | To confirm |
| `reportLinks.fhr` | `report_link_fhr` | FHR Report | STRING (link) | Optional |
| `reportLinks.financialDialog` | `report_link_financial_dialog` | Financial Dialogue Report | STRING (link) | Optional |
| `reportLinks.peerBenchmark` | `report_link_peer_benchmark` | Peer Benchmark Report | STRING (link) | Optional |
| `reportLinks.fin_services` | `report_link_fin_services` | Financial Services Report | STRING (link) | Optional; confirm the label |
| `reportLinks.companyProfilePage` | `report_link_company_profile` | Company Profile Page | STRING (link) | Optional |
| `sectorAverages.fhr` | `sector_average_fhr` | Sector Average FHR | DOUBLE | Details only |
| `sectorAverages.chs` | `sector_average_chs` | Sector Average CHS | DOUBLE | Details only |
| `latestRatings[0].id` | `current_rating_id` | Current Rating Period | STRING | Filterable |
| `latestRatings[0].fhr` | `current_fhr` | Current FHR | INTEGER | Filterable |
| `latestRatings[0].chs` | `current_chs` | Current CHS | INTEGER | Filterable |
| `latestRatings[0].probabilityDefault` | `current_probability_default` | Current PD (%) | DOUBLE | |
| `latestRatings[0].financialDate` | `current_financial_date` | Current Financial Date | DATE | |
| (sync time) | `last_synced_at` | Last Synced | TIMESTAMP | |
| `reports[]` | Not mapped | | | Download needs API auth; out of scope |

### Period field mapping (`rapidratings.periods`)

| Source | Target | Label | Type |
|---|---|---|---|
| `id` | `period_id` | Period ID | STRING, required (row key) |
| `eqyYear` | `eqy_year` | EQY Year | INTEGER |
| `period` | `period` | Period | STRING |
| `financialDate` | `financial_date` | Financial Date | DATE |
| `currency` | `currency` | Currency | STRING |
| `href` | `period_url` | Period URL | STRING |
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

`debitOwnedWithinOneYear` is the provider's own spelling.

### Rating field mapping (`rapidratings.ratings` and `rapidratings.latest_ratings`)

Both lists use the same fields but are filled from different sources.

| Source | Target | Label | Type |
|---|---|---|---|
| `id` | `rating_id` | Rating Period ID | STRING, required (row key) |
| `eqyYear` | `eqy_year` | EQY Year | INTEGER |
| `period` | `period` | Period | STRING |
| `financialDate` | `financial_date` | Financial Date | DATE |
| `reportingPeriod` | `reporting_period` | Reporting Period | DATE |
| `href` | `rating_url` | Rating URL | STRING |
| `fhr` | `fhr` | FHR | INTEGER |
| `chs` | `chs` | CHS | INTEGER |
| `probabilityDefault` | `probability_default` | Probability of Default (%) | DOUBLE |
| `termPd.year2` to `termPd.year10` | `term_pd_year_2` to `term_pd_year_10` | Term PD Year 2 to 10 (%) | DOUBLE × 9 |
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

`probabilityDefault` and `termPd` values are already percentages (`1.5853` means 1.59 %). Period amounts are in the company's `currency` at its `dataScale`.

## 7. Rules the requirements must enforce

**Security**

- Only the Z2 backend calls RapidRatings. The browser calls Z2 APIs only.
- The Client Secret lives in the Z2 secret manager, and tokens live in server memory only.
- The secret and token never appear in responses, logs, errors or history.
- All data, configuration, tokens and jobs are separated by tenant. Sandbox and production credentials are separate.
- API base URLs come from server configuration. Admins choose an environment and never type a URL.
- Check that each `next` URL is on the configured host before sending the token to it.

**Authentication**

- Reuse the token, and renew it when less than 60 seconds remain.
- Workers for the same tenant share one in-flight token request.
- A data call that returns 403 renews the token and retries once. A second 403 is a permission failure.
- A token request that returns 400 or 401 is an authentication failure and is never retried.
- **Test connection** gets a token and calls `GET /portfolios/`. Only a 200 counts as connected.

**Data**

- Keep provider values exactly as received, with no rescaling, currency conversion, risk bands or derived scores.
- Do not create Z2 Companies, and never overwrite `company_name`.
- Match records only by RRID and period ID, never by name, position or response order.
- An admin must confirm any link suggested from a name. One RRID links to one Z2 Company per tenant.
- Decide what happens on unlink, because data from a wrong link must not stay on display.

**Schema**

- Version the inferred schema and have an admin approve it before the first data sync.
- A new field creates a version for review, and sync continues.
- A removed or incompatibly changed **mapped** field blocks sync until the mapping is updated.
- `null` is not a type change. A field missing from only some items is optional, not removed.
- Schema sync never changes the DataModel or an approved mapping.

**Processing**

- Schema sync never writes Company data. Data sync never changes the schema, the DataModel or a mapping.
- Manual and scheduled syncs run the same code, in a background job, using the existing Z2 scheduler.
- Re-running a sync creates no duplicates. Period and rating rows are not deleted because they are missing from one response. `latest_ratings` is replaced on each successful read.
- One failing company does not stop the others, and a run can end partially successful.
- Scheduled runs that overlap an active run are skipped and recorded. Missed runs run once at the next start.
- Retries are limited, and authentication, permission, validation and provider errors are reported separately.
- Because there is no changed-since filter, specify two modes: a full read, and a regular read that uses the company list to detect changes.
- Raw API responses are not kept after a run.

**History**

- Keep sync history, schedule versions and change history as separate records.
- Change history is append-only, and credential changes are recorded as events without values.

## 8. What the document must contain

1. **Purpose and scope:** what is in and out of release 1, including why Outreach is out.
2. **Roles:** who can do what.
3. **Architecture:** what the UI, the shared framework and the RapidRatings adapter each do and never do.
4. **Data Integrations tab:** the catalogue card and each screen (connection, scope, schema, mapping, company links, sync, schedule, history), with fields, actions and blocked states. Include the connection states and setup order.
5. **Functional requirements:** authentication, API client, schema sync, DataModel and mapping, company linking, data sync, scheduling and history.
6. **Company Details:** sections, layout, and how to show `null`, percentages, links and large tables.
7. **Z2 backend API:** the routes the UI calls.
8. **Error handling:** each situation, the system behaviour, and the message shown to the admin.
9. **Non-functional requirements:** security, performance and monitoring.
10. **Delivery plan and rollback.**
11. **Acceptance tests:** real scenarios with expected results. Use `KawjbGJ` (27 periods, 27 ratings, 5 latest; `2020YE` `interest_received` is `null` and `net_profit_after_tax` is `-469.219`), `YA5vG2D`, `KawjkGJ` and `ALnGkmJ`.
12. **Open questions:** each with an owner, when it is needed, and a working default. Say clearly if an item has no default and blocks delivery.
13. **Appendices:** API reference, complete field mappings, and the measured data findings.

## 9. Open questions to carry into the document

| Question | Owner |
|---|---|
| Does production match the sandbox: URLs, paging, errors, token lifetime, rate limits? Is there a changed-since option? | RapidRatings |
| Why do `latestRatings` and `ratings` differ for financial-sector companies, and which is authoritative? | RapidRatings |
| Can Z2 users open report links without a RapidRatings portal account? | RapidRatings, Product |
| What do `externalSourceIds`, the industry classification fields and private-company `dataConfidential` values contain? | RapidRatings |
| Is `/financials/` ever populated? | RapidRatings |
| Which Z2 property holds an ID comparable to `externalSourceId`? | Product |
| Does each tenant have its own RapidRatings credentials, or does Z2 hold one account? | Product, RapidRatings |
| Which `dataModelType` layer owns the group? Can sections be hidden for tenants that haven't enabled RapidRatings? Does `canUpload: false` block UI edits? What is the link template for a URL value? | Z2 platform |
| Which API updates a Company from a backend job, with optimistic locking? | Z2 platform |
| What are the scheduler and queue APIs, frequencies and time-zone rules? | Z2 platform |
| How does the Company Details table handle 25 to 27 columns and many rows? Can a table binding choose its columns? How is `null` shown? Do row filters work? | Z2 UI |
| Are Company records separate per tenant? | Z2 platform |
| Are there contractual limits on storing rating history and report links? What retention applies? | Legal, Product |

## 10. Decision to raise

Earlier notes disagree on whether the integration may fill an empty Z2 Country. Do not settle this on your own. Use this default, and list it in your final message for confirmation:

- Write `basic_info.country` only when it is empty, using the Countries entry whose `alpha_3` equals `country`.
- If there is no match, write nothing and log a warning.
- Record each fill in change history, and make sure the rollback section accounts for it.

## 11. How to write it

- Give every requirement an ID, such as `AUTH-01`, and make it testable. Link each test to the requirements it covers.
- Mark how each important statement is known: measured in the sandbox, read from the Z2 model, a design decision, or still to confirm.
- Do not invent Z2 platform APIs or behaviour. Label proposed routes as proposals, and list unknowns as open questions.
- Do not include credentials or tokens.
- Use plain language and tables. Write for a developer who has seen nothing else about this project.

## 12. Check before finishing

- Every field in Section 6 is mapped in the document, or excluded with a reason.
- Every trap in Section 4.6 is handled by a requirement.
- Every requirement ID, test ID and question number referred to in the document exists.
- No statement contradicts another. Check rollback against the Country fill.
- The document has no references to outside files.

## 13. Your final message

After the document, add a short note with:

- what you could not verify
- the open questions that block delivery
- the Country decision
