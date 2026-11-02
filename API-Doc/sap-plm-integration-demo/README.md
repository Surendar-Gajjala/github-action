# SAP PLM Integration: Learning Demo

A sample project that explains **two different** SAP PLM integration architectures. They are not the same pattern and should not be treated as one.

| | Architecture A | Architecture B |
|---|---|---|
| Path | **CAD → SAP ECTR → SAP back end** (ECC / S/4HANA) | **External PLM → integration layer → SAP ERP / S/4HANA** |
| Backend code | `backend/.../plmdemo/cad/`: **simulated** ECTR steps writing to a mock SAP backend | `backend/.../plmdemo/erp/`: integration layer with mock PLM and mock SAP gateway |
| React tab | **CAD to PLM** (`#cad-to-plm`) | **ERP to PLM** (`#erp-to-plm`) |
| Also | Illustrative Java **pseudocode** for the real ECTR Connector | Static one-page demo at `http://localhost:8080/` |
| Docs | [docs/architecture-a-cad-ectr.md](docs/architecture-a-cad-ectr.md) | [docs/architecture-b-plm-erp.md](docs/architecture-b-plm-erp.md) |

> ⚠️ **This is a learning/sample project, not a production SAP integration.**
> - Everything runs against **in-memory MOCK adapters**. No SAP or PLM system is called.
> - References starting with `MOCK-BOM-` are not SAP records. The demo never creates real SAP data.
> - The REST API in `backend/` is this demo's own API. It is **not** a standard SAP endpoint.
> - The `SapBomRequest` shape is the demo's own neutral contract, **not** an SAP payload schema.

## Business scenario

Assembly **MOTOR-100**, revision **B**, plant **1000**:

| Component | Quantity |
|---|---|
| SHAFT-101 | 1 |
| BEARING-201 | 2 |

The mock PLM also contains two "bad" BOMs for demos: **MOTOR-100 rev C** (still `IN_WORK`) and **MOTOR-900 rev A** (quantities 0 and -2).

## Project structure

```
sap-plm-integration-demo/
├── README.md                     ← this file
├── PROMPT.md                     ← the original prompt, reusable with a coding assistant
├── .env.example                  ← placeholder variable names only (no secrets)
├── docs/
│   ├── architecture-a-cad-ectr.md   ← CAD → ECTR → SAP: diagrams, prerequisites, failures/logs
│   ├── architecture-b-plm-erp.md    ← External PLM → SAP: diagrams, sync vs async, error rules
│   └── api-examples.md              ← request/response JSON for every case
├── TOTAL_FLOW.txt                ← both flows end to end in one text file
├── ectr-pseudocode/
│   └── EctrMaterialAndBomExample.java  ← ILLUSTRATIVE: materialCreate / cdeskBomWizard (not compiled)
├── frontend/                     ← React 18 + Vite, one page with a tab per architecture
│   └── src/ App.jsx, api.js, cad-to-plm/CadToPlmFlow.jsx, erp-to-plm/ErpToPlmFlow.jsx, components/DataFlow.jsx, components/shared.jsx
└── backend/                      ← one Spring Boot 3.3 / Java 17 app, code split by architecture
    ├── pom.xml, mvnw, mvnw.cmd
    └── src/main/java/com/example/plmdemo/
        ├── cad/                  ← CAD → SAP ECTR → SAP backend
        │   ├── model/            CadAssembly, CadStructure, CadStructureMapper, CadValidationException
        │   ├── ectr/             EctrMockService (simulated ECTR steps), EctrSessionView, step/state exceptions
        │   ├── sapbackend/       MockSapPlmBackend: DIR, originals, BoD, materials, BOMs (in memory)
        │   └── api/              EctrController (/api/v1/ectr/sessions), dto/CadExportDto
        ├── erp/                  ← External PLM → Integration layer → SAP ERP/S/4HANA
        │   ├── api/              BomTransferController, PlmBomController, PlmImportController, dto/
        │   ├── config/           IntegrationProperties
        │   ├── domain/           EngineeringBom, BomItem, ReleaseState, TransferRecord, TransferStatus
        │   ├── source/           PlmBomSource, PlmBomStore, MockPlmBomSource, PlmBomService
        │   ├── validation/       BomValidator, BomValidationException
        │   ├── mapping/          BomMapper
        │   ├── sap/              SapBomGateway, MockSapBomGateway, RealSapBomGatewayPlaceholder, SapBomRequest/Response, exceptions
        │   └── transfer/         BomTransferService, TransferRepository, InMemoryTransferRepository
        └── common/               CorrelationIdFilter, GlobalExceptionHandler, ErrorResponseDto, InfoController
```

The test folders mirror this layout (`src/test/java/.../cad/`, `.../erp/`). `erp` reuses the CAD JSON model from `cad/model` only for its "CAD data enters the external PLM" import. Nothing in `cad` depends on `erp`.

In `erp`, the business logic (`validation`, `mapping`, `transfer`, `domain`) has **no SAP transport code**. Everything SAP-specific sits behind the `SapBomGateway` interface.

## Run in mock mode

Requirements: **Java 17+**. You don't need to install Maven; the wrapper downloads it on first use.

```bash
cd backend
./mvnw test              # Windows: mvnw.cmd test
./mvnw spring-boot:run   # Windows: mvnw.cmd spring-boot:run
```

Then, in another terminal:

```bash
curl -i -X POST http://localhost:8080/api/v1/bom-transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-1" \
  -d '{"materialNumber":"MOTOR-100","revision":"B","plant":"1000"}'
```

You get `201 Created` with `"status":"SUCCEEDED"`, `"gatewayMode":"MOCK"` and `"targetReference":"MOCK-BOM-000001"`. Send it again and you get `200` with `"duplicate":true`. See [docs/api-examples.md](docs/api-examples.md) for all cases.

### React page: both architectures

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 (forwards /api to the backend on :8080)
```

Both tabs start from the same sample CAD assembly JSON (`frontend/public/samples/`):

| Tab | Endpoints | Steps |
|---|---|---|
| **CAD to PLM** (`#cad-to-plm`): CAD → SAP ECTR → SAP backend, backend package `cad` | `POST /api/v1/ectr/sessions`, then `/{id}/checkin`, `/{id}/materials`, `/{id}/bom` | createDocuments → save(SAVECHECKIN) → materialCreate → cdeskBomWizard, writing DIRs, originals, BoD, materials and the BOM into the mock SAP backend |
| **ERP to PLM** (`#erp-to-plm`): External PLM → Integration layer → SAP ERP, backend package `erp` | `POST /api/v1/plm/imports`, `POST /api/v1/plm/boms/{m}/{r}/release`, `POST /api/v1/bom-transfers`, `GET /api/v1/bom-transfers/{id}` | CAD data into the external PLM (IN_WORK) → release → validate, map and send to the mock SAP gateway → status |

Each tab starts with a **data-flow view**: the systems as boxes, with arrows labelled with the data moving between them (CAD files and metadata, RFC steps, released BOM, mapped request). Boxes and arrows fill with live values and turn green or red as the steps run. Every API call is shown on the page. Both flows, the sample JSON, every request/response and the error cases are described in one text file: **[TOTAL_FLOW.txt](TOTAL_FLOW.txt)**.

### Demo page

Open **http://localhost:8080/** while the app is running. The single page (`backend/src/main/resources/static/index.html`, plain HTML/JS with no build step) shows:
- the **transfer flow** (PLM source → Validate → Map → SAP gateway → Status), with the steps that ran or failed for the last request highlighted
- the **source BOM** from the mock PLM for the entered material, revision and plant (release state, change number, items; invalid quantities in red)
- a **request form** with presets (MOTOR-100 rev B, rev C not released, MOTOR-900 bad quantities, unknown BOM, invalid input), plus *Send again (same key)* to demonstrate duplicate protection
- the **result** (HTTP code, status, reference, attempts, messages, correlation ID, raw JSON)
- a **history** of transfers in this session. Click a transfer ID to reload its status from the server.

The page uses two small helper endpoints: `GET /api/v1/info` (active gateway mode) and `GET /api/v1/plm/boms/{material}/{revision}?plant=…` (source BOM from `PlmBomSource`).

### Simulate SAP failures

```bash
SAP_MOCK_FAILURE_MODE=TRANSIENT_ONCE   ./mvnw spring-boot:run   # 1st call fails, retry succeeds
SAP_MOCK_FAILURE_MODE=ALWAYS_TRANSIENT ./mvnw spring-boot:run   # 503 after max attempts
SAP_MOCK_FAILURE_MODE=PERMANENT        ./mvnw spring-boot:run   # 502, no retry
```

PowerShell: `$env:SAP_MOCK_FAILURE_MODE='PERMANENT'; .\mvnw.cmd spring-boot:run`

### Configuration

All settings are in [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) and read from environment variables (names in [.env.example](.env.example)). **Never** put credentials in source code, frontend code or Git; use environment variables or a secrets manager.

## What the service does

| Concern | Implementation |
|---|---|
| Input validation | Bean Validation on `TransferRequestDto` → `400 INVALID_INPUT` |
| Business validation | `BomValidator`: released only, quantity > 0 with at most 3 decimals, no duplicate or self-referencing components, items present → `422` |
| Mapping | `BomMapper`: item numbers 0010/0020, unit EA→PC, configurable BOM usage (demo values) |
| Duplicate protection | `Idempotency-Key` header, or a business key `BOM:<material>:<rev>:<plant>`. Atomic registration in `TransferRepository`. |
| Retry | Only `SapTransientException`, up to `TRANSFER_MAX_ATTEMPTS` with linear backoff. `SapPermanentException` is never retried. |
| Status tracking | `TransferRecord`: RECEIVED → IN_PROGRESS → SUCCEEDED / FAILED_TRANSIENT / FAILED_PERMANENT / VALIDATION_FAILED, plus attempts, messages, target reference, timestamps. `GET /api/v1/bom-transfers/{id}` |
| Correlation IDs | `X-Correlation-ID` is accepted or generated, returned in the header and error body, stored on the record, and printed in every log line |

## Tests

`./mvnw test` runs 42 tests. `cad/`: `CadStructureMapperTest` and the end-to-end `EctrFlowTest`. `erp/`: the tests below plus `PlmImportFlowTest` and `DemoPageTest`.

| Required case | Tests |
|---|---|
| Release validation | `BomValidatorTest.nonReleasedBomIsRejected`, `BomTransferServiceTest.unreleasedBomIsNotSentToSap`, `BomTransferControllerTest.unreleasedBomReturns422` |
| Invalid quantities | `BomValidatorTest.zeroOrNegativeQuantityIsRejected` / `missingQuantityIsRejected` / `tooManyDecimalsIsRejected`, `BomTransferServiceTest.invalidQuantityIsRecordedAsValidationFailed`, `BomTransferControllerTest.invalidQuantitiesReturn422` |
| SAP failure | `BomTransferServiceTest.transientSapFailureIsRetriedThenSucceeds` / `persistentTransientFailureStopsAfterMaxAttempts` / `permanentSapRejectionIsNotRetried` |
| Duplicate submissions | `BomTransferServiceTest.duplicateSubmissionReturnsFirstTransferWithoutCallingSapAgain` / `withoutHeaderKeyTheBusinessKeyPreventsDuplicates` / `failedTransferCanBeResubmittedWithSameKey`, `BomTransferControllerTest.transferSucceedsThenDuplicateReturnsSameTransfer` |

## Replacing the mock adapters

| Mock | Replace with | Notes |
|---|---|---|
| `MockPlmBomSource` | An adapter for your PLM product's supported API | Implement `PlmBomSource`. Return only data the PLM considers released, or let `BomValidator` reject it. |
| `MockSapBomGateway` | Your implementation of `RealSapBomGatewayPlaceholder` | Set `SAP_MODE=real`. First confirm **which interface your SAP and PLM versions actually support** (OData/REST, IDoc via middleware, BAPI/RFC via JCo, an SAP PLM integration add-on, a vendor connector). Implement it against that interface's **documented** payload schema. Map timeouts/5xx/locks to `SapTransientException` and business rejections to `SapPermanentException`. Pass the idempotency key to SAP if supported. |
| `InMemoryTransferRepository` | A database-backed `TransferRepository` | Keep `register(...)` atomic, for example with a unique constraint on the idempotency key. |

Until a real adapter is implemented **and verified against your actual SAP system**, this project does not create SAP records.

## Architecture A in this project

Real SAP ECTR runs on the designer's desktop through a **client-side** API, not REST. The `cad` package therefore **simulates** the four documented ECTR Connector steps on the server and writes to `MockSapPlmBackend`, so the flow can be watched in the browser. It shows what lands in SAP, not how a real ECTR installation is called. The real integration is covered by:
- [docs/architecture-a-cad-ectr.md](docs/architecture-a-cad-ectr.md): components, diagrams, why CAD document, material, BoD and manufacturing BOM are **separate** operations, prerequisites (PAM, toolkit/Javadoc, application type, license, document types, SAP configuration), and typical failures with where to find the logs.
- [ectr-pseudocode/EctrMaterialAndBomExample.java](ectr-pseudocode/EctrMaterialAndBomExample.java): `materialCreate(...)` and `cdeskBomWizard(...)` as documented by SAP, marked as illustrative.

## Not included

- The React page is plain JavaScript (JSX), not TypeScript.

## Sources

- SAP Help Portal, SAP ECTR: https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363507057.html
- SAP ECTR Connector API documentation: https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363506386.html
- See also `../SAP_ECTR_Integration_Details.md` and `../CAD.txt` in the parent folder.
