# 5. REST controller, request DTO, service layer and exception handling

> **Verified.** All examples were produced by the running service with the mock backend.

## Request DTO and validation

`api/dto/CreateDocumentRequest.java`:

```java
public record CreateDocumentRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9]{1,3}") String documentType,      // DOKAR, CHAR 3
        @Size(max = 25) @Pattern(regexp = "[A-Z0-9_-]*") String documentNumber, // empty = SAP numbering
        @Pattern(regexp = "[A-Z0-9]{0,3}") String documentPart,
        @Pattern(regexp = "[A-Z0-9]{0,2}") String documentVersion,
        @NotBlank @Size(max = 40) String description,
        @Size(max = 3) String laboratory,
        @Valid @Size(max = 20) List<DescriptionDto> descriptions,
        @Valid @Size(max = 50) List<ObjectLinkDto> objectLinks) { … }
```

The lengths follow the usual SAP DMS field lengths; check them for your system. Validating them here means SAP never truncates a value silently.

**Example request:**

```http
POST /api/v1/documents
Content-Type: application/json
X-Correlation-Id: cad-job-4711
Idempotency-Key: cad-job-4711

{
  "documentType": "DRW",
  "documentPart": "000",
  "documentVersion": "00",
  "description": "Brake caliper drawing",
  "descriptions": [ { "languageIso": "DE", "description": "Bremssattel Zeichnung" } ],
  "objectLinks":  [ { "objectType": "MARA", "objectKey": "MAT-4711" } ]
}
```

**201 Created:**

```json
{
  "transactionId": "cff54d59-3756-46ff-95ad-087c604f20ef",
  "state": "COMMITTED",
  "document": { "documentType": "DRW", "documentNumber": "10000001", "documentPart": "000", "documentVersion": "00" },
  "replayed": false
}
```

The `Location` header points to `/api/v1/documents/DRW/10000001/000/00`.

## Controller → service → gateway

- **`DocumentController`** stays thin. It converts the DTO to `DocumentMetadata`, calls the service, and builds the `ResponseEntity`: 201, 200 for a replay, or 202 for a replay of an unfinished transaction.
- **`DocumentService`** registers the transaction, applies idempotency, runs the gateway on the `sap-*` pool, enforces the timeout, and translates exceptions into `IntegrationException(kind, transactionId, …)`.
- **`SapDocumentGateway`** holds the BAPI logic (chapter 6).

## Exception handling: one format for every error

`GlobalExceptionHandler` (a `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`) returns `application/problem+json`:

| Cause | Status | `type` |
|---|---|---|
| Invalid body (`@Valid`) | 400 | `urn:problem:ectr-dms:validation`, plus an `errors[]` list of `{field, message}` |
| Invalid header (e.g. Idempotency-Key) | 400 | `…:validation` |
| BAPI RETURN E/A | 422 | `…:sap-rejected`, plus `sapMessages[]` and `transactionId` |
| SAP unreachable / pool full | 503 | `…:sap-unavailable` |
| SAP timeout | 504 | `…:sap-timeout`, plus `transactionId` |
| Unknown document / file / transaction | 404 | `…:not-found` |
| File not PDF/DXF, or the name does not match the content | 415 | `…:invalid-file` |
| Upload larger than `spring.servlet.multipart.max-file-size` | 413 | (Spring default) |
| Anything else | 500 | `…:internal`. The message only names the correlation id; the details are in the log. |

**422 example (real output, mock backend):**

```json
{
  "type": "urn:problem:ectr-dms:sap-rejected",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "SAP rejected the request",
  "instance": "/api/v1/documents",
  "correlationId": "158d1577-b2b8-4044-a361-d13fb81635a2",
  "transactionId": "d309509f-a62d-486d-b736-58aff0d1e19b",
  "sapMessages": [ { "type": "E", "id": "MOCK", "number": "001", "message": "Mock: document type ZZZ is not defined" } ]
}
```

**What the caller should do, by status:**

| Status | Action |
|---|---|
| 400 / 415 / 422 | Fix the data. **Do not retry unchanged.** |
| 503 | Retry later with backoff, and use the same Idempotency-Key. |
| 504 | **Do not resend blindly.** Poll `GET /api/v1/transactions/{id}` until `finished=true`, or resend with the same Idempotency-Key. |

## Next

[Chapter 6](06-bapi-document-create2.md) covers the call to BAPI_DOCUMENT_CREATE2.
