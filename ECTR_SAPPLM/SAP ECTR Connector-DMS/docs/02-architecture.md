# 2. Architecture: CAD application → ECTR connector → Spring Boot → SAP

> **Verified vs illustrative.** Everything from the REST layer down to `RfcClient` is verified by tests. The "ECTR connector" box on the left is the caller. We assume it speaks HTTP/JSON, and we do not use or invent any ECTR-Connector framework API. The JCo box is compiled only with `-Pjco`.

```
┌─────────────────────┐  HTTP/JSON  ┌──────────────────────── ectr-dms-connector (Spring Boot) ─────────────────────────┐
│ External CAD app /  │ ──────────► │ api/        DocumentController, TransactionController                           │
│ custom ECTR         │ ◄────────── │             validation (@Valid) · problem+json errors · X-Correlation-Id        │
│ connector           │             │                    │                                                            │
└─────────────────────┘             │ service/    DocumentService  ── timeout, idempotency, bounded worker pool       │
                                    │                    │          TransactionMonitor (state history)                 │
┌─────────────────────┐   HTTP GET  │                    ▼                                                            │
│ Downstream viewer / │ ◄────────── │ files/      OriginalFileService · FileTypeDetector · LocalFileStore (PDF/DXF)   │
│ MES / supplier      │  (stream)   │ sap/        SapDocumentGateway: CREATE2 → check RETURN → COMMIT | ROLLBACK      │
└─────────────────────┘             │                    │  DocumentBapiMapper → RfcCall (neutral, testable)           │
                                    │                    ▼                                                            │
                                    │             RfcClient ─┬─ MockRfcClient   (default profile)                       │
                                    │                        └─ JcoRfcClient    (profile "jco") ──── RFC ──────────────┼──► SAP DMS
                                    └──────────────────────────────────────────────────────────────────────────────────┘
```

## Request flow: create a document

1. `POST /api/v1/documents` arrives. `CorrelationIdFilter` puts the correlation id into the log MDC.
2. Bean Validation checks lengths and formats (document type of 1–3 characters, short text of up to 40, …). An invalid request gets **400** and never reaches SAP.
3. `DocumentService` registers a transaction (`RECEIVED`) and hands the work to the bounded `sap-*` thread pool. If the pool is full, the response is **503**.
4. `SapDocumentGateway` opens **one stateful RFC session** and then:
   - calls `BAPI_DOCUMENT_CREATE2` → `SAP_CALL_STARTED`
   - on RETURN E/A: calls `BAPI_TRANSACTION_ROLLBACK`; the result is **422** with the SAP messages
   - otherwise: `SAP_DOCUMENT_CREATED`, then `BAPI_TRANSACTION_COMMIT` with `WAIT='X'`, whose RETURN is checked too → `COMMITTED`
5. The caller receives **201** with a `Location` header, the transaction id, and the key SAP created.
6. If SAP does not answer within `app.sap.call-timeout`, the response is **504** with the transaction id. The SAP call keeps running, and its final state is still written to the transaction.

## Why an `RfcCall` layer instead of calling JCo directly?

- The BAPI logic (what to call, in which order, how to read RETURN) is **unit-tested** without JCo or SAP.
- The JCo adapter becomes a mechanical copy of maps into JCo parameter lists, about 150 lines.
- The mock and the real client cannot drift apart, because both run the *same* gateway code.

## Next

[Chapter 3](03-why-spring-boot.md) explains why Spring Boot is the integration layer.
