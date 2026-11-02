# RapidRatings API: Company Periods (Financials)

Returns the **financial statement figures** for each reporting period of a company (quarterly and year-end): revenue, profit, assets, liabilities, cash flows and so on. These are the inputs behind each rating.

> Verified against the sandbox on 2026-10-28 using Tyson Foods (`XAZPzdJ`) and Rite Aid (`KawjbGJ`). Examples are real responses.
> Requires a Bearer token. See [01-authentication.md](01-authentication.md).

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_PERIODS_URL` (replace `{rrid}` at runtime) |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/` |
| **List** | `GET /v2.0/companies/{rrid}/periods/` |
| **Detail** | `GET /v2.0/companies/{rrid}/periods/{periodId}/` |
| **Auth** | `Authorization: Bearer <access_token>` |
| **Methods** | `GET` only (POST returns `405`) |
| **Pagination** | Page-number based, **10 per page, fixed** |
| **Order** | Newest period first |
| **Sandbox volume** | ~26–27 periods per company (2014 → 2020), i.e. 3 pages |

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <access_token>` | Yes |

### Path parameters

| Parameter | Type | Description |
|---|---|---|
| `rrid` | string | RapidRatings company ID (`companies[].id`, e.g. `XAZPzdJ`) |
| `periodId` | string | Period ID = `eqyYear` + `period`, e.g. `2019YE`, `2020Q2` (detail only) |

### Query parameters

| Parameter | Type | Notes |
|---|---|---|
| `page` | int ≥ 1 | Page number. **A page past the end returns `500`** (not 404) |

Nothing else is accepted. `eqyYear`, `year`, `period`, `from`, `financialDate`, `page_size` and `limit` all return `400 Not valid query parameter`. To get a single period, use the detail URL.

### Examples

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')
RRID=XAZPzdJ
URL=${RR_PERIODS_URL/\{rrid\}/$RRID}

curl "$URL" -H "Authorization: Bearer $TOKEN"            # page 1 (latest 10 periods)
curl "$URL?page=2" -H "Authorization: Bearer $TOKEN"     # next page
curl "${URL}2019YE/" -H "Authorization: Bearer $TOKEN"   # one period
```

```powershell
$url = $env:RR_PERIODS_URL.Replace("{rrid}", "XAZPzdJ")
Invoke-RestMethod -Uri $url -Headers @{ Authorization = "Bearer $token" }
```

---

## 3. Response

### List: `200 OK`

```json
{
  "count": 26,
  "previous": null,
  "next": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/periods/?page=2",
  "results": [ { ...period... }, "... 10 per page ..." ]
}
```

Page IDs observed for Tyson Foods: page 1 = `2020Q2 … 2018Q1`, page 2 = next 10, page 3 = `2015Q2 … 2014Q1` (6 items, `next: null`).

### Period object (real, `GET .../XAZPzdJ/periods/2019YE/`)

```json
{
  "id": "2019YE",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/periods/2019YE/",
  "eqyYear": 2019,
  "period": "YE",
  "financialDate": "2019-09-28",
  "currency": "USD",
  "salesRevenue": 42405.0,
  "earningsBeforeInterestAndTax": 2882.0,
  "interestExpense": 462.0,
  "interestReceived": 11.0,
  "companyTaxExpense": 396.0,
  "abnormalItems": 14.0,
  "netProfitAfterTax": 2035.0,
  "bankCashBalances": 484.0,
  "totalCurrentAssets": 7169.0,
  "fixedAssets": 7282.0,
  "financialAssets": null,
  "totalAssets": 33097.0,
  "debitOwnedWithinOneYear": 2102.0,
  "totalCurrentLiabilities": 5513.0,
  "termLoans": 9830.0,
  "totalLiabilities": 18871.0,
  "totalShareholderEquity": 14226.0,
  "cashFromOperationsFromCashFlow": null,
  "netInvestingCashFlow": -3464.0,
  "netFundingCashFlow": 1171.0
}
```

> The API returns these keys in no fixed order; they're grouped here for readability. The detail response is the same object as a list item.

### Fields

Amounts are in the company's `currency`, at the company's `dataScale` (e.g. **millions**, see the company record).

| Field | Type | Description |
|---|---|---|
| `id` | string | Period ID (`eqyYear` + `period`), e.g. `2019YE` |
| `href` | string | URL of this period |
| `eqyYear` | integer | Fiscal/equity year |
| `period` | string | `Q1`, `Q2`, `Q3`, `YE` |
| `financialDate` | date | Statement date for the period |
| `currency` | string | Currency code |
| **Income statement** | | |
| `salesRevenue` | number | Revenue / sales |
| `earningsBeforeInterestAndTax` | number | EBIT |
| `interestExpense` | number | Interest paid |
| `interestReceived` | number | Interest income |
| `companyTaxExpense` | number | Income tax expense |
| `abnormalItems` | number \| null | Exceptional / one-off items |
| `netProfitAfterTax` | number | Net income |
| **Balance sheet** | | |
| `bankCashBalances` | number | Cash and equivalents |
| `totalCurrentAssets` | number | Current assets |
| `fixedAssets` | number | Fixed assets (PP&E) |
| `financialAssets` | number \| null | Financial / investment assets |
| `totalAssets` | number | Total assets |
| `debitOwnedWithinOneYear` | number | Short-term debt (due within 1 year). *Spelling is the API's own.* |
| `totalCurrentLiabilities` | number | Current liabilities |
| `termLoans` | number | Long-term debt |
| `totalLiabilities` | number | Total liabilities |
| `totalShareholderEquity` | number | Shareholders' equity |
| **Cash flow** | | |
| `cashFromOperationsFromCashFlow` | number \| null | Operating cash flow (null in all sandbox samples) |
| `netInvestingCashFlow` | number | Investing cash flow |
| `netFundingCashFlow` | number | Financing cash flow |

All numeric fields can be `null` when a figure isn't reported, so treat every amount as nullable.

---

## 4. Error responses

| HTTP | When (tested) | Response body |
|---|---|---|
| `400` | Unknown query parameter | `{"Request":{"message":"Not valid query parameter: eqyYear.","info":{}}}` |
| `401` | No `Authorization` header | `{"Request":{"message":"Valid authentication credentials were not provided.","info":{}}}` |
| `403` | Invalid / expired token | `{"Request":{"message":"Not Authorized.","info":{}}}` |
| `404` | Unknown RRID | `{"Request":{"message":"Not Found.","info":{}}}` |
| `404` | Unknown period ID (e.g. `2030YE`) | `{"Request":{"message":"Not Found.","info":{}}}` |
| `405` | POST | `{"Request":{"message":"Method not allowed.","info":{}}}` |
| `500` | Page past the end (`?page=4` when there are 3) | `{"Request":{"message":"Internal Server Error.","info":{}}}` |

---

## 5. Implementation notes

1. **Key = (`rrid`, `id`)**, e.g. (`XAZPzdJ`, `2019YE`). Upsert on that pair.
2. **Initial load:** follow `next` until `null` to get the full history. **Incremental:** page 1 (latest 10) is usually enough. Load only IDs you don't have yet.
3. Don't retry a `500` that comes from asking for a page past the end. Only follow `next`.
4. Get `dataScale` (e.g. `millions`) from the company record and store it with the figures. Amounts aren't in base units.
5. Period IDs match rating IDs one-to-one (see [05-ratings.md](05-ratings.md)), so a period can be joined to its rating on `id`.
