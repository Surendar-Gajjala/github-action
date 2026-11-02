# RapidRatings API Documentation (Sandbox)

All endpoints were called and verified against the sandbox on 2026-10-28. Configuration lives in `.env` (template: `.env.example`).

| # | Doc | Env variable | Endpoint |
|---|---|---|---|
| 1 | [Authentication](01-authentication.md) | `RR_TOKEN_URL` | `POST /v2.0/oauth/token/` |
| 2 | [Companies](02-companies.md) | `RR_COMPANIES_URL` | `GET /v2.0/companies/` |
| 3 | [Portfolios](03-portfolios.md) | `RR_PORTFOLIOS_URL` | `GET /v2.0/portfolios/` |
| 4 | [Periods (financials)](04-periods.md) | `RR_PERIODS_URL` | `GET /v2.0/companies/{rrid}/periods/` |
| 5 | [Ratings](05-ratings.md) | `RR_RATINGS_URL` | `GET /v2.0/companies/{rrid}/ratings/` |
| 6 | [Outreach Requests](06-outreach-requests.md) | `RR_OUTREACH_URL` | `GET/POST /outreach-requests` (different host) |

## How the endpoints connect

```
oauth/token  ->  Bearer token (30 min, works on both hosts)
     |
portfolios (27070) --> companies?portfolio=27070 --> companies/{rrid}/ --+--> periods/   (financials)
                                                                      +--> ratings/   (rating history)
outreach-requests --(is_rated=true, rrid)--> companies/{rrid}/
```

## Behaviour summary

| | rridata (`/v2.0/...`) | Outreach (`api.sandbox...`) |
|---|---|---|
| Paging | `page` + `next` URL; companies 20/page, periods & ratings 10/page | `limit` + `cursor` |
| Page past end | companies: 404, portfolios/periods/ratings: **500** | n/a |
| Unknown query param | 400 | 400 |
| Invalid/expired token | **403** | **401** |
| Error format | `{"Request": {"message", "info"}}` | RFC 7807 `{type,title,status,detail}` |
| Trailing slash | **Required** | Optional |
| Writes | POST companies (not used) | POST outreach (sends real emails) |
