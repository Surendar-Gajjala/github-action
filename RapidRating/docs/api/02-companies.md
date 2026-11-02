# RapidRatings API: Companies

Returns the companies your account can see, each with its identifiers, the latest Financial Health Ratings (FHR) and links to its periods, ratings, financials and reports. This is the main entry point of the integration: the company `id` (the **RRID**) is the key for every other company endpoint.

> Verified against the sandbox on 2026-10-28. All examples below are real responses (trimmed where marked).
> Requires a Bearer token. See [01-authentication.md](01-authentication.md).

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_COMPANIES_URL` |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/companies/` |
| **List** | `GET /v2.0/companies/` |
| **Detail** | `GET /v2.0/companies/{rrid}/` |
| **Auth** | `Authorization: Bearer <access_token>` |
| **Response format** | JSON (`application/json; charset=utf-8`) |
| **Pagination** | Page-number based, **20 per page, fixed** (page size cannot be changed) |
| **Sandbox volume** | 96 companies (5 pages) |

> Note: the **trailing slash is required**. `/companies/XAZPzdJ` (no slash) returns `404`.

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <access_token>` | Yes |
| `Accept` | `application/json` | Optional |

### Query parameters (list endpoint)

Only the parameters below are accepted. **Any other parameter returns `400 Not valid query parameter: <name>`.**

| Parameter | Type | Behaviour (tested) | Example |
|---|---|---|---|
| `page` | int ≥ 1 | Page number. `0` returns 400; a page past the end returns 404 | `?page=2` |
| `portfolio` | int | Only companies in that portfolio. Must be an integer; an unknown ID returns 404 | `?portfolio=27070` |
| `name` | string (min 3 chars) | **Case-insensitive, partial** ("contains") match on the company name | `?name=foods` |
| `ticker` | string | **Exact, case-sensitive** match (`TSN` matches, `tsn` returns 0) | `?ticker=TSN` |
| `externalSourceId` | string | Matches your own external/ERP ID if one is mapped to the company | `?externalSourceId=V1001` |

Parameters can be combined, e.g. `?portfolio=631&name=inc`.

**Not supported** (all tested, all return 400): `page_size`, `limit`, `search`, `country`, `sector`, `exchange`, `id`, `rrid`, `duns`, `ordering`, `modified_since`, `updated_since`.
There is **no incremental/"changed since" filter**, so a sync has to read the full list.

### Path parameter (detail endpoint)

| Parameter | Type | Description |
|---|---|---|
| `rrid` | string (7 chars, e.g. `XAZPzdJ`) | RapidRatings company ID, from `results[].id` in the list |

### Examples

```http
GET /v2.0/companies/?portfolio=27070&page=1 HTTP/1.1
Host: rridata.sandbox.rrilabs.co
Authorization: Bearer <access_token>
```

cURL (Git Bash):

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')

# List (first page) of the configured portfolio
curl "$RR_COMPANIES_URL?portfolio=$RR_PORTFOLIO_ID" -H "Authorization: Bearer $TOKEN"

# Search by name
curl "$RR_COMPANIES_URL?name=tyson" -H "Authorization: Bearer $TOKEN"

# Single company
curl "${RR_COMPANIES_URL}XAZPzdJ/" -H "Authorization: Bearer $TOKEN"
```

PowerShell:

```powershell
$h = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Uri "$($env:RR_COMPANIES_URL)?portfolio=$($env:RR_PORTFOLIO_ID)" -Headers $h
```

---

## 3. Response: List `200 OK`

### Envelope

```json
{
  "count": 96,
  "previous": null,
  "next": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?page=2",
  "results": [ { ...company... }, ... ]
}
```

| Field | Type | Description |
|---|---|---|
| `count` | integer | Total matching companies across all pages |
| `previous` | string \| null | Full URL of the previous page; `null` on page 1 |
| `next` | string \| null | Full URL of the next page; `null` on the last page. Filters are kept (e.g. `?portfolio=27070&page=2`) |
| `results` | array | Up to 20 company objects |

### Company object (real example, `latestRatings` trimmed to 1 of 5)

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
  "industryClassificationSystem": null,
  "industryClassificationCode": null,
  "currency": "USD",
  "dataScale": "millions",
  "dataConfidential": "PUB",
  "externalSourceId": null,
  "externalSourceIds": []
}
```

### Company fields

| Field | Type | Description |
|---|---|---|
| `id` | string | **RRID**, the RapidRatings company ID (primary key) |
| `name` | string | Company legal name |
| `country` | string | ISO-3 country code (e.g. `USA`) |
| `ticker` | string | Stock ticker (public companies) |
| `exchange` | string | Exchange code (e.g. `US`) |
| `sector` | string | RapidRatings sector (e.g. `Retail`, `Media`) |
| `href` | string | URL of this company's detail endpoint |
| `financials` | string | URL of the company's financial statements |
| `periods` | string | URL of the company's financial periods (`RR_PERIODS_URL`) |
| `ratings` | string | URL of the company's full rating history (`RR_RATINGS_URL`) |
| `latestRatings` | array | Most recent ratings, **newest first, up to 5** (observed 2–5) |
| `reportLinks` | object | Portal links to PDF reports: `fhr`, `financialDialog`, `peerBenchmark` |
| `address` | string | Address; may be empty `""` |
| `industryClassificationSystem` | string \| null | e.g. NAICS/SIC system name, if set |
| `industryClassificationCode` | string \| null | Industry code, if set |
| `currency` | string | Currency of the financials (e.g. `USD`) |
| `dataScale` | string | Scale of financial figures (e.g. `millions`) |
| `dataConfidential` | string | `PUB` = public company data (private companies use other values) |
| `externalSourceId` | string \| null | Your own system's ID for this company, if mapped |
| `externalSourceIds` | array | All external IDs mapped to this company |

### `latestRatings[]` fields

| Field | Type | Description |
|---|---|---|
| `id` | string | Rating ID = `eqyYear` + `period`, e.g. `2020YE`, `2020Q3` |
| `href` | string | URL of this rating's detail |
| `eqyYear` | integer | Equity/fiscal year of the financials |
| `period` | string | `Q1`, `Q2`, `Q3` or `YE` (year end) |
| `financialDate` | date | Date of the financial statements used |
| `reportingPeriod` | date | Date the rating was produced/published |
| `fhr` | integer 0–100 | **Financial Health Rating**: short-term (12-month) default risk. Higher = healthier |
| `chs` | integer 0–100 | **Core Health Score**: medium-term (2–3 year) operating resilience |
| `probabilityDefault` | number (%) | 1-year probability of default |
| `termPd` | object | Cumulative probability of default (%) for years 2–10 (`year2` … `year10`) |
| `delta` | integer \| null | FHR change vs. the previous period (`null` if there is none) |
| `simulatedFhr` | integer | Simulated/forward-looking FHR |
| `simulatedFhrDelta` | integer \| null | Change in simulated FHR |
| `operatingProfitability` | integer 0–100 | Sub-score |
| `netProfitability` | integer 0–100 | Sub-score |
| `capitalStructureEfficiency` | integer 0–100 | Sub-score |
| `costStructureEfficiency` | integer 0–100 | Sub-score |
| `leverage` | string | `Strong` / `Average` / `Weak` |
| `liquidity` | string | `Strong` / `Average` / `Weak` |
| `earningsPerformance` | string | `Strong` / `Average` / `Weak` |

---

## 4. Response: Detail `200 OK`

`GET /v2.0/companies/XAZPzdJ/` returns a **single company object (no `count`/`results` envelope)** with every list field **plus**:

```json
{
  "id": "XAZPzdJ",
  "name": "Tyson Foods, Inc.",
  "...": "... all list fields ...",
  "reports": [
    { "reportType": "fhr",                "downloadHref": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/reports/?reportType=fhr" },
    { "reportType": "financial_dialogue", "downloadHref": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/reports/?reportType=financial_dialogue" },
    { "reportType": "peer_benchmark",     "downloadHref": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/reports/?reportType=peer_benchmark" }
  ],
  "sectorAverages": { "fhr": 52.6, "chs": 45.8 }
}
```

| Extra field | Type | Description |
|---|---|---|
| `reports` | array | API download links for the PDF reports (`fhr`, `financial_dialogue`, `peer_benchmark`) |
| `sectorAverages` | object | Average `fhr` and `chs` for the company's sector (useful for benchmarking) |

---

## 5. Error responses

Errors use the envelope `{ "<Scope>": { "message": "...", "info": {} } }`. The scope key is usually `Request`, but can be `Portfolio` or `Companies`.

| HTTP | When (tested) | Response body |
|---|---|---|
| `400` | Unknown query parameter | `{"Request":{"message":"Not valid query parameter: sector.","info":{}}}` |
| `400` | `portfolio` not an integer | `{"Request":{"message":"Bad type for parameter: portfolio (int).","info":{}}}` |
| `400` | `name` shorter than 3 chars | `{"Request":{"message":"Invalid parameter: name must be at least 3 characters.","info":{}}}` |
| `400` | `page=0` | `{"Request":{"message":"Invalid parameter: page must to be a positive integer.","info":{}}}` |
| `401` | No `Authorization` header | `{"Request":{"message":"Valid authentication credentials were not provided.","info":{}}}` |
| `403` | Invalid / expired token | `{"Request":{"message":"Not Authorized.","info":{}}}` |
| `404` | Page past the end (`?page=999`) | `{"Companies":{"message":"Not Found.","info":{}}}` |
| `404` | Unknown portfolio | `{"Portfolio":{"message":"Portfolio 999999 not Found.","info":{}}}` |
| `404` | Unknown RRID / missing trailing slash | `{"Request":{"message":"Not Found.","info":{}}}` |

> **POST is also supported** on `/companies/` (creates a private company). Sending an empty body returns `400` listing the required fields: `name`, `country`, `currency`, `dataConfidential`, `dataScale`. This was **not executed** (to avoid creating data) and is out of scope for the read integration.

---

## 6. Rate limiting

Every response includes rate-limit headers:

```
X-Ratelimit-Limit: 30000000
X-Ratelimit-Remaining: 29999998
X-Ratelimit-Reset: 1793168282      (Unix epoch seconds)
Retry-After: 3464                  (seconds until the window resets)
```

The sandbox limit is very high, but the client should still check `X-Ratelimit-Remaining` and, on `429`, wait `Retry-After` seconds before retrying.

---

## 7. Implementation notes (for the integration client)

1. **Paginate by following `next`** until it is `null`. Don't build page numbers yourself; `next` already keeps the filters.
2. **Scope by portfolio**: use `?portfolio=$RR_PORTFOLIO_ID` (27070) so you only pull the companies you monitor.
3. **There is no delta filter.** Do a full read each run and detect changes on your side (e.g. compare `latestRatings[0].id` / `fhr` per RRID).
4. **Use `id` (RRID) as the primary key** and map it to your supplier/vendor ID. `externalSourceId` can hold that mapping on the RapidRatings side.
5. `latestRatings[0]` is the **current rating**. For the full history use the `ratings` URL (see the Ratings doc).
6. Expect `null` in `delta`, `simulatedFhrDelta`, `industryClassification*` and `externalSourceId`, and `""` in `address`.
7. Unknown parameters fail hard (400), so validate filters before sending.
8. On `403`, get a new token and retry once (tokens last 30 min).
