# RapidRatings API: Authentication (OAuth Token)

Every RapidRatings API call needs a **Bearer access token**. You get the token from this endpoint using the client ID and secret, then send it on all other requests.

> Verified against the sandbox on 2026-10-28. All examples below are real responses, with token values redacted.

---

## 1. Endpoint details

| Item | Value |
|---|---|
| **Env variable** | `RR_TOKEN_URL` |
| **URL (sandbox)** | `https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` |
| **Method** | `POST` (GET returns `405`) |
| **Auth type** | OAuth 2.0, **Client Credentials** grant |
| **Client authentication** | HTTP Basic: `Authorization: Basic base64(client_id:client_secret)` |
| **Request body** | `grant_type=client_credentials` (form-urlencoded or JSON) |
| **Token type returned** | `Bearer` |
| **Token lifetime** | `1800` seconds (**30 minutes**) |
| **Refresh token** | Not issued. Request a new token when the current one expires. |

> Note: the trailing slash in the URL (`/token/`) is part of the endpoint.

### Related `.env` variables

| Variable | Purpose |
|---|---|
| `RR_TOKEN_URL` | Token endpoint URL |
| `RR_CLIENT_ID` | OAuth client ID (key) |
| `RR_CLIENT_SECRET` | OAuth client secret |
| `RR_BASIC_AUTH` | Pre-encoded `base64(RR_CLIENT_ID:RR_CLIENT_SECRET)` |

---

## 2. Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Basic <RR_BASIC_AUTH>` | Yes |
| `Content-Type` | `application/x-www-form-urlencoded` (or `application/json`) | Yes |

### Body

Form-urlencoded (recommended):

```
grant_type=client_credentials
```

JSON (also accepted):

```json
{ "grant_type": "client_credentials" }
```

### Raw HTTP

```http
POST /v2.0/oauth/token/ HTTP/1.1
Host: rridata.sandbox.rrilabs.co
Authorization: Basic <RR_BASIC_AUTH>
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
```

### cURL (Git Bash)

```bash
set -a; . ./.env; set +a

curl -X POST "$RR_TOKEN_URL" \
  -H "Authorization: Basic $RR_BASIC_AUTH" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials"
```

Alternative: let curl build the Basic header from the ID and secret:

```bash
curl -X POST "$RR_TOKEN_URL" -u "$RR_CLIENT_ID:$RR_CLIENT_SECRET" -d "grant_type=client_credentials"
```

### PowerShell

```powershell
$headers = @{ Authorization = "Basic $env:RR_BASIC_AUTH" }
$body    = @{ grant_type = "client_credentials" }

$resp = Invoke-RestMethod -Method Post -Uri $env:RR_TOKEN_URL `
          -Headers $headers -Body $body `
          -ContentType "application/x-www-form-urlencoded"
$resp.access_token
```

---

## 3. Response

### Success: `200 OK`

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
```

```json
{
  "access_token": "Lf4A**************************",
  "expires_in": 1800,
  "token_type": "Bearer",
  "scope": "uid introspection openid"
}
```

| Field | Type | Description |
|---|---|---|
| `access_token` | string | Token to send as `Authorization: Bearer <access_token>` on all API calls |
| `expires_in` | integer | Seconds until the token expires (1800 = 30 min) |
| `token_type` | string | Always `Bearer` |
| `scope` | string | Scopes granted to the client (space-separated) |

### Error responses

All errors use the same JSON envelope:

```json
{ "Request": { "message": "<description>", "info": {} } }
```

| HTTP status | When it happens (tested) | Response `message` |
|---|---|---|
| `400 Bad Request` | Unsupported `grant_type` (e.g. `password`) | `Bad Request.` |
| `401 Unauthorized` | Wrong client ID/secret, missing `Authorization` header, **or missing `grant_type`** | `Valid authentication credentials were not provided.` |
| `405 Method Not Allowed` | Using `GET` instead of `POST` | `Method not allowed.` |

> A missing `grant_type` returns **401, not 400**, so a 401 does not always mean bad credentials. Check the body first.

---

## 4. Using the token

Send the token on every data endpoint request:

```http
GET /v2.0/portfolios/ HTTP/1.1
Host: rridata.sandbox.rrilabs.co
Authorization: Bearer <access_token>
```

```bash
curl "$RR_PORTFOLIOS_URL" -H "Authorization: Bearer <access_token>"
```

Verified: `GET /v2.0/portfolios/` with a fresh token returned `200` with `{"count": 5, "previous": null, "next": null, "results": [...]}`.

### Bearer token errors on data endpoints

| HTTP status | When | Response `message` |
|---|---|---|
| `401 Unauthorized` | No / empty `Authorization` header | `Valid authentication credentials were not provided.` |
| `403 Forbidden` | Invalid or expired Bearer token | `Not Authorized.` |

> An expired token returns **403**, not 401. The client should treat 403 on a data call as "token may be expired": get a new token and retry once.

---

## 5. Implementation notes (for the integration client)

1. **Cache the token** in memory and reuse it until it is close to expiry. Don't request a new token for every call.
2. **Refresh early:** request a new token when fewer than ~60 seconds are left (`issued_at + expires_in - 60`).
3. **No refresh token** is issued. Renewing means repeating the same client-credentials request.
4. **Retry once on 401/403** from a data endpoint: clear the cached token, get a new one, and repeat the call. If it fails again, raise an error.
5. **Never log** `RR_CLIENT_SECRET`, `RR_BASIC_AUTH`, or `access_token` values.
6. **Timeouts:** use a connect/read timeout (e.g. 30s) and retry on network errors and `5xx` with backoff.
