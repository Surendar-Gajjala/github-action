# SCM — Item & Part Lifecycle Workflow Engine

A full-stack Supply Chain Management application that manages **Items**, their **Parts**, and a configurable
**BPMN lifecycle approval workflow**. An Item becomes `APPROVED` only after every required stage of a published workflow
has completed. Editing an approved Item opens a change request, and the last approved revision stays preserved until the
new revision is released.

| Layer | Stack |
|---|---|
| Backend | Java 17+ (21 recommended), Spring Boot 3.5, Spring Web / Data JPA / Validation / Security, H2, Maven |
| Workflow | BPMN 2.0 with Zeebe extensions. **Camunda 8** via `camunda-client-java` 8.8, or the clearly labelled **development adapter** |
| Frontend | React 18, TypeScript, Vite 5, React Router 6, TanStack Query 5, React Hook Form, Zod, Tailwind CSS 3, bpmn-js 17 |

The UI has exactly three top-level tabs: **Items**, **Parts** and **Workflow**. The Workflow tab contains Definitions,
Designer, Lifecycle States, Approval Tasks, Running Instances and History.

More documentation:

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): architecture diagram, data model, end-to-end execution, design decisions.
- [docs/API.md](docs/API.md): REST API reference with example requests.

---

## 1. Runtime compatibility (read this first)

Before writing any code, I checked the requested runtime against what Camunda 8 actually needs.

| Constraint | Finding | Decision |
|---|---|---|
| Camunda 8 inside Spring Boot | Camunda 8 (Zeebe) is **not embeddable**. Adding the Java client or Spring SDK starts **no** engine; it only connects to a running Orchestration Cluster. | The backend talks to an external engine through `CamundaWorkflowAdapter`. The client is configured explicitly (`CamundaClientConfig`). |
| No Docker | The supported Docker-free local runtime is **Camunda 8 Run** (C8 Run), a downloadable distribution started from a shell. | The README documents C8 Run (section 4). |
| Java version | **C8 Run requires JDK 21+.** The application itself only needs JDK 17. The machine this was built on has JDK 17 only. | The app compiles for Java 17, so it runs on 17 or 21. Use JDK 21 for C8 Run. |
| H2 only | Camunda stores execution state in its own engine and secondary storage. It **cannot** use the application's H2 database. | H2 holds business data only: items, parts, change requests, revisions, audit, task inbox. |
| Camunda client vs Java/Boot | `camunda-client-java` 8.8.44 targets Java 8 bytecode and works with Spring Boot 3.5. I verified this against the jar. | Pinned in `backend/pom.xml`. |

Because Camunda 8 can't run in-process or without extra infrastructure, the application ships two adapters behind one
interface (`WorkflowEngineAdapter`):

- **`CamundaWorkflowAdapter`** (`scm.workflow.engine=camunda`) runs on real Camunda 8. It deploys BPMN, starts instances
  and runs job workers through the Camunda Java client.
- **`DevWorkflowAdapter`** (`scm.workflow.engine=dev`, **the default**) is **for development and tests only, and it is not
  Camunda.** It is an in-process interpreter for the BPMN subset the workflows use:
  - none start and end events
  - service tasks
  - job-worker user tasks
  - exclusive gateways with simple FEEL conditions
  - async execution after commit, retries, incidents

  The UI header, the startup log and `GET /api/workflows/engine` all say it is not Camunda.

Both adapters run **the same workers and business code**. Only the engine plumbing differs.

---

## 2. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 or 21 | `JAVA_HOME` must point to it. Use 21 if you also run Camunda 8 Run. |
| Node.js | 18.18+ (tested with 24) | npm is included |
| Maven | not required | Use the bundled wrapper (`mvnw` / `mvnw.cmd`) |

No Docker and no database server are needed. H2 runs in memory inside the backend.

---

## 3. Quick start (development adapter)

Use two terminals.

**Terminal 1: backend** (http://localhost:8085)

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local        # macOS / Linux / Git Bash
```

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"   # Windows PowerShell
```

**Terminal 2: frontend** (http://localhost:5173)

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173** and sign in with one of the demo users (the password equals the username):

| User | Roles | Can |
|---|---|---|
| `author` | AUTHOR | Create and edit items and parts, submit changes for approval |
| `engineer` | ENGINEERING | Approve or reject ENGINEERING tasks |
| `manager` | MANAGER | Approve or reject MANAGER tasks |
| `admin` | ADMIN, AUTHOR | Design, publish and activate workflows, configure lifecycle transitions, retry incidents |

The `local` profile enables the H2 console at http://localhost:8085/h2-console. Use JDBC URL `jdbc:h2:mem:scmdb`,
user `sa` and an empty password.

### Seed data

On startup the backend seeds:

- the 7 lifecycle transitions
- parts `PART-101` … `PART-108`
- one draft item `ITEM-0900`
- three workflow definitions as **unpublished drafts**:
  - `ITEM_LIFECYCLE_APPROVAL`
  - `ITEM_PART_CHANGE_APPROVAL`
  - `ITEM_REVISION_RELEASE`

No item is seeded as `APPROVED`, because the only way to approve one is through the workflow.

### End-to-end walkthrough (the spec's scenario)

1. **author** → Items → **+ Create Item**: `ITEM-1001`, "Hydraulic Pump Assembly". Pick `PART-101`, `PART-102` and
   `PART-103` in the searchable dropdown and save. The item is `DRAFT`, revision A.
2. **admin** → Workflow → Definitions → **Publish** `ITEM_LIFECYCLE_APPROVAL`. Optionally open the Designer first to look
   at or change the BPMN and the approval rules.
3. **author** → open ITEM-1001 → **Submit for approval**. The item becomes `IN_REVIEW` and an instance appears in
   Workflow → Running Instances.
4. The workers validate the item and its part relationships.
5. **engineer** → Workflow → Approval Tasks → **Approve** "Engineering Approval".
6. **manager** → **Approve** "Manager Approval". It is required here because 3 part changes exceed the threshold of 2.
7. The `release-revision` worker re-checks all approvals and releases revision A. The item is now `APPROVED`.
8. **author** → edit ITEM-1001 and remove `PART-103`. The item becomes `CHANGE_PENDING` with working revision B.
   Revision A stays the approved revision (see the "Approved revision" tab).
9. **admin** publishes `ITEM_PART_CHANGE_APPROVAL`. **author** submits again; this definition is chosen automatically
   for changes to approved items.
10. **engineer** approves, then **manager** approves (removing a part requires a manager). Revision B is released.
11. Each item's **Workflow history** tab and the global **Workflow → History** show every step: actor, timestamp,
    decision and business key.

The same scenario is automated in `WorkflowIntegrationTest.endToEndApprovalChangeAndReapproval`.

---

## 4. Running against real Camunda 8 (Camunda 8 Run, no Docker)

1. Install **JDK 21+** (required by C8 Run) and set `JAVA_HOME` to it.
2. Download **Camunda 8 Run** for your OS from the Camunda download page (*Camunda 8 Run*, version 8.8.x), then unpack
   it.
3. Start it:
   ```bash
   ./start.sh                 # macOS / Linux
   ```
   ```powershell
   .\c8run.exe start          # Windows
   ```
   C8 Run starts the Orchestration Cluster:
   - gRPC: `localhost:26500`
   - REST, Operate and Tasklist: `http://localhost:8080`
   - default web login: `demo` / `demo`

   It brings its own secondary storage, so you don't need Docker.
4. Start the backend in Camunda mode (the frontend is unchanged):
   ```bash
   cd backend
   SCM_WORKFLOW_ENGINE=camunda ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
   ```powershell
   cd backend
   $env:SCM_WORKFLOW_ENGINE="camunda"; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
   ```
   If your cluster's API requires authentication, also set `CAMUNDA_USERNAME` and `CAMUNDA_PASSWORD`. For non-default
   addresses, set `CAMUNDA_GRPC_ADDRESS` and `CAMUNDA_REST_ADDRESS`.
5. The header badge shows **"Camunda 8 · connected"**. Publishing a definition now deploys it to Camunda, and you can
   watch instances in Camunda Operate at http://localhost:8080/operate.

Notes for Camunda mode:

- **Approval tasks** are BPMN user tasks with the **job-worker implementation**: no `<zeebe:userTask/>`, and the
  candidate group comes from `zeebe:assignmentDefinition`.
  - The backend's worker for `io.camunda.zeebe:userTask` turns each activation into an inbox task. The job stays
    activated until a user decides, and the decision completes the job.
  - The validator rejects Camunda-managed user tasks, because the application must control authorization and the audit
    trail.
  - Camunda has deprecated job-worker user tasks in favour of Camunda user tasks with task listeners. Migrating means
    replacing the job worker with a `creating` task listener plus the user-task completion API. The rest of the code
    goes through the adapter and doesn't need to change.
- **Incidents.** A service job that fails is retried with backoff until its BPMN `retries` run out; then Camunda raises
  an incident. The app marks the instance `INCIDENT`, and **Resolve & retry** in the instance drawer updates the job's
  retries and resolves the incident.
- **Separate state.** The app's H2 database is in memory, so restarting the backend resets the business data while C8 Run
  keeps its process instances. Restart C8 Run with a clean state (or ignore the orphaned instances) when you restart the
  backend.

---

## 5. Tests

```bash
cd backend && ./mvnw test          # 42 JUnit tests: unit, integration (dev adapter), REST/MockMvc, Camunda wiring
cd frontend && npm test            # 15 Vitest + Testing Library component and workflow UI tests
cd frontend && npm run build       # type-check + production build
```

| Requirement | Test |
|---|---|
| Item / part creation and validation | `ItemLifecycleIntegrationTest`, `RestApiIntegrationTest`, `items.test.tsx` |
| Item–part relationship updates, shared parts | `ItemLifecycleIntegrationTest.partsCanBeAdded…`, `…sharedPartAssociationOnlyAffectsChangedItems` |
| Approved item change detection, approved revision preservation | `…changingAnApprovedItemCreatesChangeRequest…`, `WorkflowIntegrationTest.rejectionOfChangeKeepsPreviouslyApprovedRevision` |
| Lifecycle transition restrictions | `…lifecycleTransitionsAreEnforcedFromConfiguration` |
| Workflow start and duplicate prevention (including concurrent) | `WorkflowIntegrationTest.duplicateSubmissionIsPrevented`, `…concurrentSubmissionsStartExactlyOneWorkflow` |
| Approval and rejection paths, rejection reason | `WorkflowIntegrationTest`, `workflow.test.tsx` |
| Authorized task completion, segregation of duties | `…onlyCandidateRoleCanCompleteTask…`, `RestApiIntegrationTest.rolesAreEnforced` |
| Failed worker retry, incident, manual retry | `WorkerRetryIntegrationTest` |
| Concurrent lifecycle updates (optimistic locking) | `ItemLifecycleIntegrationTest.concurrentUpdatesWithSameVersionOnlyOneWins` |
| BPMN definition validation (including XXE) | `UnitTests.BpmnDefinitionValidation`, `WorkflowIntegrationTest.invalidDraftCannotBePublished` |
| Camunda adapter wiring | `CamundaWiringTest` (no cluster: verifies selection and failure handling) |

`CamundaWiringTest` doesn't execute processes on a real cluster. Running the walkthrough above against C8 Run is the
manual check for that.

---

## 6. Configuration

| Property / env var | Default | Meaning |
|---|---|---|
| `scm.workflow.engine` / `SCM_WORKFLOW_ENGINE` | `dev` | `dev` = development adapter (not Camunda), `camunda` = Camunda 8 |
| `scm.workflow.camunda.grpc-address` / `CAMUNDA_GRPC_ADDRESS` | `http://localhost:26500` | Zeebe gateway (gRPC) |
| `scm.workflow.camunda.rest-address` / `CAMUNDA_REST_ADDRESS` | `http://localhost:8080` | Orchestration Cluster REST API |
| `CAMUNDA_USERNAME` / `CAMUNDA_PASSWORD` | empty | Basic auth for protected clusters |
| `scm.workflow.camunda.user-task-job-timeout` | `P30D` | How long an approval job stays activated before Camunda re-delivers it |
| `scm.workflow.dev.retry-backoff-ms` | `1000` | Dev adapter retry backoff |
| `scm.seed.enabled` | `true` | Seed reference and demo data |
| `server.port` | `8085` | Backend port. Not 8080, which C8 Run uses. |

---

## 7. Project layout

```
backend/   Spring Boot application (com.gsr.scm)
  item/            Item, ItemPart (join entity), ChangeRequest, ItemRevision snapshot, ItemService
  part/            Part, PartService (association changes → per-item change requests)
  lifecycle/       LifecycleTransition config, LifecycleService (sole authority for status changes)
  audit/           WorkflowEvent audit trail
  workflow/        WorkflowService (submission), definition/, instance/, task/, worker/, engine/
    engine/camunda CamundaClientConfig, CamundaWorkflowAdapter, CamundaJobWorkers
    engine/dev     DevWorkflowAdapter (development/test only, not Camunda), FeelLite
  resources/bpmn   Seed BPMN definitions and the template for new definitions
frontend/  React + TypeScript (Vite)
  src/api          Typed API client and DTO types
  src/features     items/, parts/, workflow/ (definitions, designer (bpmn-js), lifecycle, tasks, instances, history)
docs/      ARCHITECTURE.md, API.md, imp.txt (original specification)
```

## 8. Security notes

- The demo uses HTTP Basic with in-memory users, for local development only. For a shared environment, replace
  `SecurityConfig.users()` with your identity provider (for example an OAuth2 resource server).
- The browser never talks to the workflow engine. Camunda credentials and engine APIs stay on the backend.
- BPMN uploads are parsed with DOCTYPE and external entities disabled (XXE-safe).
