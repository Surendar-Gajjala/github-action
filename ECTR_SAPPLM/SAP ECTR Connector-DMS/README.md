# ECTR Connector: SAP DMS Integration Service

A Spring Boot 3.3 / Java 17 REST service that sits between external CAD or modelling applications (or a custom ECTR connector) and **SAP Document Management (DMS)**. It:

- creates document info records with **BAPI_DOCUMENT_CREATE2**, followed by **BAPI_TRANSACTION_COMMIT** in the same stateful SAP session
- reads them back with **BAPI_DOCUMENT_GETDETAIL2**
- accepts **PDF/DXF** originals, checks what they really contain, stores them with a SHA-256 checksum, and streams them to downstream applications
- tracks every request as a **transaction** (`RECEIVED → SAP_CALL_STARTED → SAP_DOCUMENT_CREATED → COMMITTED`, or a failure state), with timeouts, idempotency keys and correlation ids
- returns structured **RFC 9457 problem+json** errors, including the SAP RETURN messages

It runs **without SAP**. A mock SAP backend is active by default. The real connection goes through SAP JCo under the Maven/Spring profile `jco`.

> **Verified vs illustrative.**
> - **Verified by the build:** Spring Boot, REST, validation, the BAPI orchestration logic, file handling and the error model (38 tests, plus a curl smoke test).
> - **Illustrative:** the mock SAP backend (`sap/mock`, invented messages and numbers).
> - **Not compiled here:** the JCo adapter (`src/jco/java`), because `sapjco3.jar` is licensed by SAP.
> - **Must be checked in SE37 on your system:** the BAPI parameter and field names (`BapiNames`).

## Quick start (mock SAP)

```powershell
.\mvnw.cmd verify
java -jar target\ectr-dms-connector-1.0.0-SNAPSHOT.jar
```

```bash
curl -X POST localhost:8080/api/v1/documents -H "Content-Type: application/json" \
     -d '{"documentType":"DRW","description":"Brake caliper drawing"}'
# -> 201 {"transactionId":"…","state":"COMMITTED","document":{"documentType":"DRW","documentNumber":"10000001",…}}
```

More requests are in [`examples/requests.http`](examples/requests.http). VS Code REST Client and IntelliJ can run that file directly.

## API

| Method & path | Purpose | Success | Typical errors |
|---|---|---|---|
| `POST /api/v1/documents` | Create document metadata (DIR) in SAP | 201 (200 on idempotent replay) | 400 validation, 422 SAP rejected, 503 SAP down, 504 timeout |
| `GET /api/v1/documents/{type}/{number}/{part}/{version}` | Read metadata | 200 | 404 |
| `POST …/{key}/originals` (multipart `file`) | Upload a PDF/DXF original | 201 | 404 document, 413 too large, 415 wrong type |
| `GET …/{key}/originals` | List originals | 200 | |
| `GET …/{key}/originals/{fileId}` | Download (streamed, Range, ETag) | 200 / 206 | 404 |
| `GET /api/v1/transactions/{id}` | Transaction state and history | 200 | 404 |
| `GET /actuator/health` | Includes `sap` UP/DOWN | 200 / 503 | |

## Layout

```
src/main/java/com/example/ectr/dms/
  api/        REST controllers, DTOs, problem+json handler, correlation-id filter
  service/    DocumentService (timeouts, idempotency), TransactionMonitor
  sap/        RfcClient port, RfcCall/RfcResult, BapiNames, DocumentBapiMapper, SapDocumentGateway
  sap/mock/   MockRfcClient - simulated SAP (default profile)       [ILLUSTRATIVE]
  files/      FileTypeDetector, LocalFileStore, OriginalFileService
  config/     IntegrationProperties, ExecutorConfig, SapHealthIndicator
src/jco/java/ JcoRfcClient, SpringDestinationDataProvider, JcoConfiguration (profile "jco")
src/main/resources/application.yml, application-jco.yml
docs/         step-by-step guide        examples/  HTTP requests        implemation.txt  step list
```

## Guide

1. [SAP ECTR, DMS, JCo, RFC and BAPI](docs/01-concepts.md)
2. [Architecture](docs/02-architecture.md)
3. [Why Spring Boot as the integration layer](docs/03-why-spring-boot.md)
4. [Project structure and secure JCo configuration](docs/04-jco-setup.md)
5. [REST controller, DTOs, service layer, exception handling](docs/05-rest-api.md)
6. [Calling BAPI_DOCUMENT_CREATE2 through JCo](docs/06-bapi-document-create2.md)
7. [Document key, metadata, original file and version](docs/07-metadata-vs-originals.md)
8. [PDF/DXF file handling and streaming](docs/08-file-handling.md)
9. [Validation, timeouts, logging, errors and transaction monitoring](docs/09-errors-timeouts-monitoring.md)
10. [Testing, deployment and troubleshooting](docs/10-testing-deployment-troubleshooting.md)
