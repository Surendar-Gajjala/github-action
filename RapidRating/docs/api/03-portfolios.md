# RapidRatings API: Portfolios

Lists the portfolios (named groups of monitored companies) your account can see. Use this to find the portfolio ID, then pull its companies with `/companies/?portfolio={id}`.

> Verified against the sandbox on 2026-10-28. All examples below are real responses.
> Requires a Bearer token. See [01-authentication.md](01-authentication.md).

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_PORTFOLIOS_URL` (and `RR_PORTFOLIO_ID` = `27070`) |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/portfolios/` |
| **List** | `GET /v2.0/portfolios/` |
| **Detail** | `GET /v2.0/portfolios/{portfolioId}/` |
| **Auth** | `Authorization: Bearer <access_token>` |
| **Methods** | `GET` only (POST/DELETE return `405`): read-only |
| **Pagination** | Same envelope as companies; all 5 sandbox portfolios fit on page 1 |

> The **trailing slash is required** (`/portfolios/27070` without it returns `404`).

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <access_token>` | Yes |

### Query parameters (list)

| Parameter | Type | Notes |
|---|---|---|
| `page` | int ≥ 1 | `page=1` works. **`page=2` returns `500 Internal Server Error`** in the sandbox (out-of-range page bug), so rely on `next` instead |

No other parameters are accepted (`?name=` and anything else return `400 Not valid query parameter`).

### Path parameter (detail)

| Parameter | Type | Description |
|---|---|---|
| `portfolioId` | integer | Portfolio ID from `results[].id` (e.g. `27070`) |

### Examples

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')

curl "$RR_PORTFOLIOS_URL" -H "Authorization: Bearer $TOKEN"                       # list
curl "${RR_PORTFOLIOS_URL}${RR_PORTFOLIO_ID}/" -H "Authorization: Bearer $TOKEN"  # detail
```

```powershell
Invoke-RestMethod -Uri "$($env:RR_PORTFOLIOS_URL)$($env:RR_PORTFOLIO_ID)/" -Headers @{ Authorization = "Bearer $token" }
```

---

## 3. Response

### List: `200 OK`

```json
{
  "count": 5,
  "previous": null,
  "next": null,
  "results": [
    { "id": 631,   "name": "Fortune 500",            "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/631/",   "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=631" },
    { "id": 137,   "name": "INTERNATIONAL COVERAGE", "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/137/",   "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=137" },
    { "id": 36,    "name": "US COVERAGE",            "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/36/",    "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=36" },
    { "id": 27070, "name": "z2Data Sandbox",         "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/27070/", "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=27070" },
    { "id": 27072, "name": "z2Data Sandbox-Private", "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/27072/", "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=27072" }
  ]
}
```

### Detail: `200 OK`

`GET /v2.0/portfolios/27070/` returns a single object (no envelope):

```json
{
  "companies": "https://rridata.sandbox.rrilabs.co/v2.0/companies/?portfolio=27070",
  "href": "https://rridata.sandbox.rrilabs.co/v2.0/portfolios/27070/",
  "id": 27070,
  "name": "z2Data Sandbox"
}
```

### Fields

| Field | Type | Description |
|---|---|---|
| `id` | integer | Portfolio ID, used as the `portfolio` filter on `/companies/` |
| `name` | string | Portfolio display name |
| `href` | string | URL of this portfolio's detail endpoint |
| `companies` | string | Ready-made URL listing this portfolio's companies |

The portfolio object has **no company count**. Call the `companies` URL and read `count`.

### Sandbox portfolios (company counts from `/companies/?portfolio=`)

| ID | Name | Companies |
|---|---|---|
| 36 | US COVERAGE | 96 |
| 137 | INTERNATIONAL COVERAGE | 0 |
| 631 | Fortune 500 | 37 |
| **27070** | **z2Data Sandbox** (our `RR_PORTFOLIO_ID`) | **96** |
| 27072 | z2Data Sandbox-Private | 0 |

---

## 4. Error responses

| HTTP | When (tested) | Response body |
|---|---|---|
| `400` | Unknown query parameter | `{"Request":{"message":"Not valid query parameter: name.","info":{}}}` |
| `401` | No `Authorization` header | `{"Request":{"message":"Valid authentication credentials were not provided.","info":{}}}` |
| `403` | Invalid / expired token | `{"Request":{"message":"Not Authorized.","info":{}}}` |
| `404` | Unknown portfolio ID | `{"Portfolio":{"message":"Portfolio 999999 not Found.","info":{}}}` |
| `404` | Non-numeric ID / missing trailing slash | `{"Request":{"message":"Not Found.","info":{}}}` |
| `405` | POST / DELETE | `{"Request":{"message":"Method not allowed.","info":{}}}` |
| `500` | Page past the end (`?page=2`) | `{"Request":{"message":"Internal Server Error.","info":{}}}` |

---

## 5. Implementation notes

1. Call this once at the start of a sync to **check that `RR_PORTFOLIO_ID` still exists** (404 means stop and alert).
2. Use the returned `companies` URL (or `/companies/?portfolio=27070`) to get the company list.
3. Follow `next` for paging and never request a page number past it, because that returns **500**, not 404.
4. Portfolios are read-only through this API. Membership changes happen in the RapidRatings portal.
