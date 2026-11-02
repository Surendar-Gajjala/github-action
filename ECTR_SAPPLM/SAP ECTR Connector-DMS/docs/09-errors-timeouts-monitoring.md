# 9. Timeouts, validation, logging, error handling and transaction-state monitoring

> **Verified.** Timeout, late completion, idempotency, communication failure and rejection are each covered by `DocumentServiceTest`. The logging output shown here is real.

## Validation layers

| Layer | Catches | Result |
|---|---|---|
| Bean Validation on the DTO and headers | Wrong lengths or formats, missing fields | 400 with every invalid field listed |
| Configuration validation (`@Validated` properties) | Missing `SAP_*` variables, bad timeouts | The service does not start |
| File checks | Wrong type, name/content mismatch, empty, too large | 415 / 413 |
| SAP itself (RETURN) | Business rules: unknown type, number range, mandatory fields from customizing | 422 with the SAP messages |

## Timeouts

```yaml
app:
  sap:
    call-timeout: 30s      # REST caller waits at most this long
    worker-threads: 4      # parallel SAP calls (<= JCo peak-limit)
    queue-capacity: 50     # then 503 instead of piling up
```

An RFC call that is already running cannot be cancelled safely from Java. So `DocumentService`:

1. returns **504** with the `transactionId` once the timeout expires, setting the state to `TIMEOUT_OUTCOME_UNKNOWN`;
2. lets the SAP call finish in the background;
3. writes the real outcome (`COMMITTED`, `REJECTED_BY_SAP`, …) to the same transaction when it arrives.

```
GET /api/v1/transactions/{id}
{ "state": "COMMITTED", "finished": true, "document": {…},
  "history": [ {"state":"RECEIVED",…}, {"state":"SAP_CALL_STARTED",…},
               {"state":"TIMEOUT_OUTCOME_UNKNOWN",…}, {"state":"SAP_DOCUMENT_CREATED",…}, {"state":"COMMITTED",…} ] }
```

JCo also has its own network-level timeouts, configured as destination properties. Set them consistently with `call-timeout`, after checking the property names for your JCo release.

## Idempotency: safe retries

Send `Idempotency-Key: <unique id of the CAD job>`. A second request with the same key returns the **first** transaction (`replayed: true`, HTTP 200, or 202 if it is still running) and does **not** call SAP again. This prevents duplicate documents after timeouts or network errors.

## Transaction states

| State | Terminal | Meaning |
|---|---|---|
| `RECEIVED` | | validated, waiting for a worker |
| `SAP_CALL_STARTED` | | BAPI running |
| `SAP_DOCUMENT_CREATED` | | created in the LUW, commit pending |
| `COMMITTED` | ✔ | document exists in SAP |
| `REJECTED_BY_SAP` | ✔ | RETURN E/A, rolled back |
| `COMMUNICATION_FAILED` | ✔ | SAP unreachable, nothing committed |
| `TIMEOUT_OUTCOME_UNKNOWN` | | caller timed out; real outcome will follow |
| `FAILED` | ✔ | unexpected middleware error |

Terminal states are never overwritten. `TIMEOUT_OUTCOME_UNKNOWN` is not terminal, so the real result replaces it.

**Production:** `TransactionMonitor` is in memory, so it is lost on restart and not shared between instances. Move it to a database table with the same fields, and add a reconciliation job that, for transactions still unknown after N minutes, looks the document up in SAP (by the caller's reference, if you store one in a DIR field).

## Logging (safe)

Pattern: `%5p [%X{correlationId:-}]`. The id comes from `X-Correlation-Id` (it is validated, so callers cannot inject log lines) or is generated. It is copied to the `sap-*` worker threads by a `TaskDecorator`.

```
INFO [smoke-1] … DocumentService     : Transaction cff54d59…: create document type=DRW number=<internal>
INFO [smoke-1] … TransactionMonitor  : Transaction cff54d59… -> SAP_CALL_STARTED
INFO [smoke-1] … SapDocumentGateway  : BAPI_DOCUMENT_CREATE2 created DRW/10000001/000/00 (not yet committed), messages=[S / …]
INFO [smoke-1] … SapDocumentGateway  : Committed DRW/10000001/000/00
```

| Log | Never log |
|---|---|
| transaction id, correlation id, document key, BAPI name, RETURN type/id/number/message, state changes | passwords (`JcoProperties.toString()` masks them), full request bodies, file contents, SAP hostnames in API responses |

`SapHealthIndicator` reports only the exception class name, never connection details.

## Monitoring

- `GET /actuator/health` gives `{"components":{"sap":{"status":"UP"}}}`. With JCo, it uses `destination.ping()`.
- Alert on: health DOWN; a rising share of `COMMUNICATION_FAILED` or `TIMEOUT_OUTCOME_UNKNOWN`; 503s caused by a full pool.
- To export metrics, add Micrometer with your registry (Prometheus, …) and count transitions in `TransactionMonitor.update`.

## Next

[Chapter 10](10-testing-deployment-troubleshooting.md) covers testing, deployment and troubleshooting.
