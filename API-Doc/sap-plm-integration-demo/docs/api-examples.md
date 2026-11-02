# API examples (demo integration layer, MOCK mode)

These are the endpoints of **this demo service**. They are **not SAP endpoints**. All `targetReference` values starting with `MOCK-` come from the in-memory mock. No SAP record exists.

Base URL: `http://localhost:8080`

## 1. Successful transfer: MOTOR-100 rev B, plant 1000

```http
POST /api/v1/bom-transfers
Content-Type: application/json
Idempotency-Key: plm-wf-task-000123
X-Correlation-ID: demo-corr-001

{
  "materialNumber": "MOTOR-100",
  "revision": "B",
  "plant": "1000"
}
```

`201 Created`

```json
{
  "transferId": "7f1d5c0e-3c1e-4b8e-9a51-2f0a0d6f9b11",
  "status": "SUCCEEDED",
  "duplicate": false,
  "gatewayMode": "MOCK",
  "materialNumber": "MOTOR-100",
  "revision": "B",
  "plant": "1000",
  "targetReference": "MOCK-BOM-000001",
  "attempts": 1,
  "messages": ["[MOCK] BOM stored in memory, no SAP system was called"],
  "idempotencyKey": "plm-wf-task-000123",
  "correlationId": "demo-corr-001",
  "createdAt": "2026-10-22T09:15:02.114Z",
  "updatedAt": "2026-10-22T09:15:02.131Z"
}
```

What the mock gateway received (the demo's **own illustrative** `SapBomRequest`, not an SAP schema):

```json
{
  "idempotencyKey": "plm-wf-task-000123",
  "correlationId": "demo-corr-001",
  "headerMaterial": "MOTOR-100",
  "plant": "1000",
  "revision": "B",
  "changeNumber": "ECN-4711",
  "bomUsage": "1",
  "items": [
    { "itemNumber": "0010", "componentMaterial": "SHAFT-101",   "quantity": 1, "unit": "PC" },
    { "itemNumber": "0020", "componentMaterial": "BEARING-201", "quantity": 2, "unit": "PC" }
  ]
}
```

## 2. Duplicate submission (same Idempotency-Key)

Same request again, so `200 OK`, the same `transferId` and `targetReference`, and `"duplicate": true`. SAP is not called again.

## 3. Read transfer status

```http
GET /api/v1/bom-transfers/7f1d5c0e-3c1e-4b8e-9a51-2f0a0d6f9b11
```

`200 OK` with the same body shape as above (`duplicate` is always `false` here).

## 4. BOM not released in PLM (MOTOR-100 rev C is IN_WORK)

```json
{ "materialNumber": "MOTOR-100", "revision": "C", "plant": "1000" }
```

`422 Unprocessable Entity`

```json
{
  "code": "BOM_VALIDATION_FAILED",
  "message": "BOM cannot be transferred",
  "details": ["BOM MOTOR-100 rev C is IN_WORK in PLM; only RELEASED BOMs can be transferred"],
  "correlationId": "c0b7..."
}
```

## 5. Invalid quantities (MOTOR-900 rev A has 0 and -2)

`422 Unprocessable Entity`

```json
{
  "code": "BOM_VALIDATION_FAILED",
  "message": "BOM cannot be transferred",
  "details": [
    "Component SHAFT-101 has invalid quantity 0 (must be > 0)",
    "Component BEARING-201 has invalid quantity -2 (must be > 0)"
  ],
  "correlationId": "4e2a..."
}
```

## 6. Invalid request input

```json
{ "materialNumber": "motor 100", "revision": "B", "plant": "10" }
```

`400 Bad Request`

```json
{
  "code": "INVALID_INPUT",
  "message": "Request body is invalid",
  "details": [
    "materialNumber: must contain only upper-case letters, digits, '-' or '_'",
    "plant: must be exactly 4 upper-case letters or digits"
  ],
  "correlationId": "9d11..."
}
```

## 7. SAP failure (simulate with `SAP_MOCK_FAILURE_MODE`)

| `SAP_MOCK_FAILURE_MODE` | Result |
|---|---|
| `TRANSIENT_ONCE` | First call fails, retry succeeds. `201`, `"attempts": 2` |
| `ALWAYS_TRANSIENT` | `503`, `"status": "FAILED_TRANSIENT"`, one message per attempt |
| `PERMANENT` | `502`, `"status": "FAILED_PERMANENT"`, no retry |

Example `503` body:

```json
{
  "transferId": "b9a0...",
  "status": "FAILED_TRANSIENT",
  "duplicate": false,
  "gatewayMode": "MOCK",
  "targetReference": null,
  "attempts": 3,
  "messages": [
    "Attempt 1: transient error: [MOCK] SAP system temporarily unavailable",
    "Attempt 2: transient error: [MOCK] SAP system temporarily unavailable",
    "Attempt 3: transient error: [MOCK] SAP system temporarily unavailable"
  ]
}
```

## curl

```bash
curl -i -X POST http://localhost:8080/api/v1/bom-transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: plm-wf-task-000123" \
  -H "X-Correlation-ID: demo-corr-001" \
  -d '{"materialNumber":"MOTOR-100","revision":"B","plant":"1000"}'
```

PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/bom-transfers `
  -ContentType 'application/json' `
  -Headers @{ 'Idempotency-Key' = 'plm-wf-task-000123' } `
  -Body '{"materialNumber":"MOTOR-100","revision":"B","plant":"1000"}'
```
