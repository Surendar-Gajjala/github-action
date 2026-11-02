# RapidRatings API: Company Ratings

Returns a company's **full rating history**, one rating per financial period: FHR, CHS, probability of default, term PDs and sub-scores. The `latestRatings` array on the company record holds only the newest 5; this endpoint returns all of them.

> Verified against the sandbox on 2026-10-28 using Tyson Foods (`XAZPzdJ`). Examples are real responses.
> Requires a Bearer token. See [01-authentication.md](01-authentication.md).

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_RATINGS_URL` (replace `{rrid}` at runtime) |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/` |
| **List** | `GET /v2.0/companies/{rrid}/ratings/` |
| **Detail** | `GET /v2.0/companies/{rrid}/ratings/{ratingId}/` |
| **Auth** | `Authorization: Bearer <access_token>` |
| **Methods** | `GET` only (POST returns `405`) |
| **Pagination** | Page-number based, **10 per page, fixed** |
| **Order** | Newest rating first |
| **Sandbox volume** | 26 ratings for Tyson Foods (2014Q1 → 2020Q2), i.e. 3 pages |

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <access_token>` | Yes |

### Path parameters

| Parameter | Type | Description |
|---|---|---|
| `rrid` | string | RapidRatings company ID (e.g. `XAZPzdJ`) |
| `ratingId` | string | `eqyYear` + `period`, e.g. `2019YE` (detail only) |

### Query parameters

| Parameter | Type | Notes |
|---|---|---|
| `page` | int ≥ 1 | Page number. **A page past the end returns `500`** |

Nothing else is accepted (`eqyYear`, `period` and `page_size` return `400 Not valid query parameter`).

### Examples

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')
URL=${RR_RATINGS_URL/\{rrid\}/XAZPzdJ}

curl "$URL" -H "Authorization: Bearer $TOKEN"            # latest 10 ratings
curl "$URL?page=2" -H "Authorization: Bearer $TOKEN"     # older ratings
curl "${URL}2019YE/" -H "Authorization: Bearer $TOKEN"   # one rating
```

```powershell
$url = $env:RR_RATINGS_URL.Replace("{rrid}", "XAZPzdJ")
Invoke-RestMethod -Uri $url -Headers @{ Authorization = "Bearer $token" }
```

---

## 3. Response

### List: `200 OK`

```json
{
  "count": 26,
  "previous": null,
  "next": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/ratings/?page=2",
  "results": [ { ...rating... }, "... 10 per page ..." ]
}
```

### Rating object (real, first item of page 1)

```json
{
  "id": "2020Q2",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/companies/XAZPzdJ/ratings/2020Q2/",
  "eqyYear": 2020,
  "period": "Q2",
  "financialDate": "2020-03-28",
  "reportingPeriod": "2020-05-15",
  "fhr": 49,
  "chs": 48,
  "probabilityDefault": 0.3343,
  "termPd": {
    "year2": 3.83, "year3": 6.52, "year4": 9.35, "year5": 12.29, "year6": 15.39,
    "year7": 18.73, "year8": 22.42, "year9": 26.54, "year10": 31.12
  },
  "delta": 0,
  "simulatedFhr": 49,
  "simulatedFhrDelta": -1,
  "operatingProfitability": 30,
  "netProfitability": 66,
  "capitalStructureEfficiency": 12,
  "costStructureEfficiency": 63,
  "leverage": "Average",
  "liquidity": "Average",
  "earningsPerformance": "Average"
}
```

### Detail: `200 OK`

`GET .../XAZPzdJ/ratings/2019YE/` returns the same object shape without the envelope:

```json
{"id": "2019YE", "eqyYear": 2019, "period": "YE", "financialDate": "2019-09-28", "reportingPeriod": "2020-05-15",
 "fhr": 47, "chs": 50, "probabilityDefault": 0.4149, "delta": -9, "simulatedFhr": 47, "simulatedFhrDelta": -9,
 "leverage": "Average", "liquidity": "Weak", "earningsPerformance": "Average", "...": "..."}
```

### Fields

| Field | Type | Description |
|---|---|---|
| `id` | string | Rating ID = `eqyYear` + `period` (same as the matching period ID) |
| `href` | string | URL of this rating |
| `eqyYear` | integer | Fiscal/equity year |
| `period` | string | `Q1`, `Q2`, `Q3`, `YE` |
| `financialDate` | date | Statement date the rating is based on |
| `reportingPeriod` | date | Date the rating was produced/published |
| `fhr` | integer 0–100 | **Financial Health Rating**: 12-month default risk. Higher = healthier |
| `chs` | integer 0–100 | **Core Health Score**: 2–3 year operating efficiency/resilience |
| `probabilityDefault` | number (%) | 1-year probability of default (`0.3343` = 0.33%) |
| `termPd.year2` … `termPd.year10` | number (%) | Cumulative probability of default over 2–10 years |
| `delta` | integer \| null | FHR change vs. the previous rating (`null` for the oldest) |
| `simulatedFhr` | integer | Simulated FHR |
| `simulatedFhrDelta` | integer \| null | Change in simulated FHR |
| `operatingProfitability` | integer 0–100 | Sub-score |
| `netProfitability` | integer 0–100 | Sub-score |
| `capitalStructureEfficiency` | integer 0–100 | Sub-score |
| `costStructureEfficiency` | integer 0–100 | Sub-score |
| `leverage` | string | `Strong` / `Average` / `Weak` |
| `liquidity` | string | `Strong` / `Average` / `Weak` |
| `earningsPerformance` | string | `Strong` / `Average` / `Weak` |

### FHR risk bands (RapidRatings standard scale)

| FHR | Risk level |
|---|---|
| 80–100 | Very Low Risk |
| 60–79 | Low Risk |
| 40–59 | Medium Risk |
| 20–39 | High Risk |
| 0–19 | Very High Risk |

> These bands follow RapidRatings' published methodology; the API doesn't return them. Confirm them with RapidRatings before relying on them in business rules.

---

## 4. Error responses

| HTTP | When (tested) | Response body |
|---|---|---|
| `400` | Unknown query parameter | `{"Request":{"message":"Not valid query parameter: period.","info":{}}}` |
| `401` | No `Authorization` header | `{"Request":{"message":"Valid authentication credentials were not provided.","info":{}}}` |
| `403` | Invalid / expired token | `{"Request":{"message":"Not Authorized.","info":{}}}` |
| `404` | Unknown RRID | `{"Request":{"message":"Not Found.","info":{}}}` |
| `404` | Unknown rating ID (e.g. `2030YE`) | `{"Not found":{"message":"Not Found.","info":{}}}` (note the different scope key) |
| `405` | POST | `{"Request":{"message":"Method not allowed.","info":{}}}` |
| `500` | Page past the end (`?page=4`) | `{"Request":{"message":"Internal Server Error.","info":{}}}` |

---

## 5. Implementation notes

1. **Key = (`rrid`, `id`)**. Join to periods on the same key.
2. **Current rating** = `results[0]` on page 1, the same as `companies[].latestRatings[0]`. For a daily sync, the company list alone is enough to spot new ratings. Call this endpoint only for companies whose `latestRatings[0].id` changed, or for the initial history load.
3. Follow `next` until `null`. Never ask for a page past it (that returns 500).
4. Store `probabilityDefault` and `termPd` as decimals in **percent units**, not fractions.
5. `reportingPeriod` can be the same date for many periods (back-filled history), so order by `eqyYear` + `period`, not `reportingPeriod`.
