# RapidRatings API: Outreach Requests

Outreach requests ask RapidRatings to **contact a (usually private) counterparty and collect its financials** so it can be rated. You can list your requests and their status, create new ones, and see the communication attempts. Once a request is rated, it carries the company's `rrid`, which links into the Companies/Ratings APIs.

> Verified against the sandbox on 2026-10-28. The sandbox account has **0 outreach requests**, so list examples with data come from RapidRatings' OpenAPI spec (`https://api.sandbox.rrilabs.co/openapi.yaml`). All error responses below are real.
> Uses the **same Bearer token** as the other APIs. See [01-authentication.md](01-authentication.md).

---

## 1. Endpoint details

This is a **different API host and style** from the `rridata` endpoints.

| Item | Value |
|---|---|
| **Env variable** | `RR_OUTREACH_URL` (base: `RR_OUTREACH_BASE_URL`) |
| **URL (sandbox)** | `https://api.sandbox.rrilabs.co/outreach-requests` |
| **Auth** | `Authorization: Bearer <access_token>` (OAuth scope `uid`). Basic auth is **not** accepted |
| **Trailing slash** | Optional (`/outreach-requests` and `/outreach-requests/` both work) |
| **Pagination** | **Cursor-based** (`limit` + `cursor`), not page numbers |
| **Error format** | RFC 7807 `application/problem+json` (different from rridata) |
| **Rate limit** | `X-Ratelimit-Limit: 500000` (vs 30,000,000 on rridata) |
| **API spec** | `https://api.sandbox.rrilabs.co/openapi.yaml` |

### Operations

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/outreach-requests` | List your organization's outreach requests (with status) |
| `POST` | `/outreach-requests` | Create a new outreach request |
| `GET` | `/outreach-requests/{outreach_request_id}` | Get one request |
| `GET` | `/outreach-requests/{outreach_request_id}/communications` | Communication attempts (emails sent) for one request |

---

## 2. List: `GET /outreach-requests`

### Query parameters

| Parameter | Type | Description (tested) |
|---|---|---|
| `limit` | integer | Results per page. Default `20` (`1000` was accepted). Non-integer returns 400 |
| `cursor` | string | Value of `next_cursor` / `prev_cursor` from the previous response. An invalid value returns `400 Invalid Cursor` |
| `sort` | string | `id`, `counterparty_company_name`, `reference_id`, `created_date`. Prefix `-` = descending, `+` or none = ascending. Default `-id`. Invalid values return `400 Not a valid sort` |
| `q` | string (URL-encoded) | Filter: `counterparty_company_name=…`, `reference_id=…`, `is_rated=true\|false`. Partial, case-insensitive, combined with AND |
| `embed` | string | Include embedded resources, e.g. `(surveys(results(href)))` |

Any other parameter returns `400 Extra query parameter(s) <name> not in spec`.

### Example request

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -X POST "$RR_TOKEN_URL" -H "Authorization: Basic $RR_BASIC_AUTH" \
          -d "grant_type=client_credentials" | sed -E 's/.*"access_token": *"([^"]+)".*/\1/')

curl "$RR_OUTREACH_URL?limit=50&sort=-created_date" -H "Authorization: Bearer $TOKEN"
curl "$RR_OUTREACH_URL?q=is_rated%3Dtrue"           -H "Authorization: Bearer $TOKEN"   # q must be URL-encoded
```

```powershell
Invoke-RestMethod -Uri "$($env:RR_OUTREACH_URL)?limit=50" -Headers @{ Authorization = "Bearer $token" }
```

### Response `200 OK`: real sandbox (empty)

```json
{
  "cursors": {
    "query": { "limit": 20, "sort": "-id" },
    "total": 0
  },
  "items": []
}
```

### Response `200 OK`: with data (from the OpenAPI spec)

```json
{
  "cursors": {
    "first_cursor": "22FERSFFGE2324",
    "last_cursor": "WEFAGEWH35236EWGDS",
    "next_cursor": "WWDASFAGE325623ETTYWE",
    "prev_cursor": "112425WFASBDEW",
    "query": { "limit": 10 },
    "total": 23115
  },
  "items": [
    {
      "outreach_request_id": "1234",
      "counterparty": {
        "company_name": "Bob's Burgers",
        "contact": {
          "country": "USA",
          "email": "joe.bloggs@example.com",
          "first_name": "Joe",
          "last_name": "Bloggs",
          "tel": "353851234567"
        },
        "reference_id": "your-ref-1234"
      },
      "relationship_manager": { "email": "joe.bloggs@example.com", "name": "Joe Bloggs" },
      "created_at": "2018-12-10T13:45:00Z",
      "modified_at": "2018-12-10T13:45:00Z",
      "financial_submissions": [ { "created_at": "2019-08-24T14:15:22Z" } ],
      "is_rated": true,
      "rrid": "Axtr4P"
    }
  ]
}
```

### `cursors` fields

| Field | Type | Description |
|---|---|---|
| `total` | integer | Total matching requests |
| `query` | object | Echo of the parameters applied (`limit`, `sort`, `q`) |
| `next_cursor` | string \| null | Pass as `cursor` to get the next page. Missing or `null` = last page |
| `prev_cursor` | string \| null | Previous page |
| `first_cursor` / `last_cursor` | string | First / last page |

When there's no data, only `query` and `total` are returned.

### Outreach request (item) fields

| Field | Type | Description |
|---|---|---|
| `outreach_request_id` | string | Unique ID of the request |
| `counterparty.company_name` | string | Company to be contacted |
| `counterparty.reference_id` | string \| null | **Your** free-text reference (e.g. ERP supplier ID) |
| `counterparty.contact.first_name` / `last_name` | string | Primary contact name |
| `counterparty.contact.email` | string | Primary contact email |
| `counterparty.contact.country` | string | ISO-3166 alpha-3 country code |
| `counterparty.contact.tel` | string \| null | Phone |
| `counterparty.additional_contacts[]` | array | Extra contacts (`email` required; `first_name`, `last_name`, `tel`, read-only `contact_ref`). Intercom-delivered orgs only |
| `relationship_manager.name` / `email` | string | Your internal owner of the relationship |
| `created_at` / `modified_at` | date-time | Created / last modified (UTC) |
| `financial_submissions[].created_at` | date-time | When the counterparty uploaded financials (newest first) |
| `is_rated` | boolean | `true` once a rating exists |
| `rrid` | string \| null | RRID of the rated company. Use it with `/v2.0/companies/{rrid}/` |
| `status` | string \| null | Current status (e.g. `Request Created`). **Display only; values may change without notice** |
| `status_history[]` | array | `status`, `status_detail`, `status_comment`, `status_reasons[]`, `status_updated_at` |
| `_embedded` | object | Present when `embed` is used (e.g. survey result links) |

---

## 3. Create: `POST /outreach-requests`

> Creating a request makes RapidRatings **send real emails to the counterparty contact**. It was **not executed** during testing. Only the validation errors were checked.

### Request

```http
POST /outreach-requests HTTP/1.1
Host: api.sandbox.rrilabs.co
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "counterparty": {
    "company_name": "Bob's Burgers",
    "reference_id": "your-ref-1234",
    "contact": {
      "first_name": "Joe",
      "last_name": "Bloggs",
      "email": "joe.bloggs@example.com",
      "country": "USA",
      "tel": "353851234567"
    }
  },
  "relationship_manager": {
    "name": "Joe Bloggs",
    "email": "joe.bloggs@example.com"
  }
}
```

### Body fields

| Field | Required | Rules |
|---|---|---|
| `counterparty` | **Yes** | object |
| `counterparty.company_name` | **Yes** | 1–255 chars |
| `counterparty.contact` | **Yes** | object |
| `counterparty.contact.first_name` | **Yes** | 1–255 chars |
| `counterparty.contact.last_name` | **Yes** | 1–255 chars |
| `counterparty.contact.email` | **Yes** | email, 1–255 chars |
| `counterparty.contact.country` | **Yes** | ISO-3166 alpha-3 (e.g. `USA`) |
| `counterparty.contact.tel` | No | string |
| `counterparty.reference_id` | No | free text: put your supplier/vendor ID here |
| `counterparty.additional_contacts` | No | **max 1** on create (more returns 400); returns **409** if your org isn't on Intercom delivery |
| `relationship_manager` | **Yes** | object |
| `relationship_manager.name` | **Yes** | 1–255 chars |
| `relationship_manager.email` | **Yes** | email, 1–255 chars |

### Response

`201 Created` returns the created outreach request object (same fields as a list item, with the new `outreach_request_id`).

---

## 4. Get one: `GET /outreach-requests/{id}`

`200 OK` returns a single outreach request object (same fields as a list item).

## 5. Communications: `GET /outreach-requests/{id}/communications`

`200 OK` (from spec):

```json
{
  "communications": {
    "introductions": [
      { "channel": "email", "sent_at": "2019-08-24T14:15:22Z" },
      { "channel": "email", "sent_at": "2019-09-24T14:15:22Z" }
    ]
  }
}
```

| Field | Type | Description |
|---|---|---|
| `communications.introductions[]` | array | Each outreach attempt RapidRatings made (may be empty) |
| `.channel` | string | Currently always `email` |
| `.sent_at` | date-time | When it was sent |

---

## 6. Error responses (all real, from the sandbox)

Format (RFC 7807):

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Not a valid sort" }
```

Validation errors add an `errors` array.

| HTTP | When (tested) | `detail` |
|---|---|---|
| `400` | Invalid `sort` | `Not a valid sort` |
| `400` | Non-integer `limit` | `["invalid literal for int() with base 10: 'abc'"]` |
| `400` | Unknown query parameter | `Extra query parameter(s) foo not in spec` |
| `400` | Invalid `cursor` | `Invalid Cursor` |
| `400` | POST with `{}` | `Input errors`, `errors: ["'counterparty' is a required property", "'relationship_manager' is a required property"]` |
| `400` | POST with no body | `Input errors`, `errors: ["None is not of type 'object'"]` |
| `401` | No `Authorization` header, or Basic auth | `No authorization token provided` |
| `401` | Invalid / expired Bearer token | `The server could not verify that you are authorized to access the URL requested. …` |
| `403` | GET an ID that doesn't exist / isn't yours | `Sorry, you do not have permission to access this resource` (title `Permission denied.`) |
| `404` | Communications for an unknown ID | `Solicitation matching query does not exist.` |

> An invalid token returns **401** here, while rridata returns **403**. The client's "refresh token and retry" logic must handle both.

---

## 7. Implementation notes

1. **Same token, different host.** Use `RR_OUTREACH_BASE_URL`, and parse errors as problem+json (`detail`), not `{"Request": {...}}`.
2. **Paging:** loop while `cursors.next_cursor` is set, passing it as `cursor`. Use a large `limit` (e.g. 100) to cut down on calls.
3. **Status sync:** poll the list with `sort=-created_date` (or `q=is_rated=false` for open requests). When `is_rated` becomes `true`, take `rrid` and pull the company and ratings from the rridata API.
4. **Map your supplier ID into `counterparty.reference_id`** on create, so results can be matched back without fuzzy name matching.
5. **Don't build logic on `status` text.** RapidRatings says its values may change. Use `is_rated`, `rrid` and `financial_submissions` instead.
6. **POST has real-world side effects** (emails to suppliers). Gate it behind an explicit flag or approval, and never call it automatically in tests.
