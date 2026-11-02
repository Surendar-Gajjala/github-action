# Employee Onboarding Workflow Automation

An HR user submits a new employee once. The system then automatically:

1. validates and saves the employee,
2. generates a personalised welcome email, onboarding checklist and GitHub issue content with OpenAI,
3. sends the welcome email over SMTP,
4. creates a GitHub onboarding issue,
5. tracks every step, and shows the results and any errors in a single tabbed React page.

| Layer    | Stack |
|----------|-------|
| Backend  | Java 17, Spring Boot 3.3 (Web, Data JPA, Validation, Security, Actuator, Mail), Flyway, springdoc-openapi, MapStruct |
| Database | **H2** (default: file DB in PostgreSQL mode) or PostgreSQL (`postgres` profile) |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, React Hook Form, Zod, TanStack Query |

---

## Quick start (no credentials needed)

The defaults run in **mock mode**: no real emails are sent, no real GitHub issues are created, and OpenAI is not called.

```bash
# 1. Backend: http://localhost:8080
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 2. Frontend: http://localhost:5173 (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

Sign in with **`hr` / `hr-password`** (HR role) or **`admin` / `admin-password`** (HR and ADMIN roles).
Change these passwords with `APP_HR_PASSWORD` and `APP_ADMIN_PASSWORD` in any shared environment.

On first start, Flyway creates the schema in `backend/data/onboarding.mv.db` and loads six sample employees:

- four completed onboardings,
- one workflow where the GitHub step failed and can be retried,
- one workflow waiting for email approval.

| URL | What it is |
|-----|------------|
| http://localhost:5173 | The single-page dashboard |
| http://localhost:8080/swagger-ui.html | OpenAPI / Swagger UI |
| http://localhost:8080/api/health | Health check (public) |
| http://localhost:8080/h2-console | H2 console (JDBC URL `jdbc:h2:file:./data/onboarding;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE`, user `sa`, empty password) |

To reset the database, stop the backend and delete `backend/data/`.

### Running the tests

```bash
cd backend  && ./mvnw verify   # 38 tests: unit, HTTP-client (MockRestServiceServer), SMTP (GreenMail), end-to-end on in-memory H2
cd frontend && npm test        # form validation and step tracker tests
```

---

## Configuration

All configuration comes from environment variables; see [.env.example](.env.example). Secrets are read only by the backend and are **never** put in `VITE_*` variables or the React bundle. Spring Boot does not read `.env` by itself, so export the variables in your shell or IDE run configuration. Docker Compose loads `.env` automatically.

| Variable | Purpose |
|----------|---------|
| `OPENAI_MODE`, `EMAIL_MODE`, `GITHUB_MODE` | `mock` (default) or `live`, set per integration |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | OpenAI credentials and model (default `gpt-4o-mini`) |
| `OPENAI_FALLBACK_ENABLED` | Use the fallback template when OpenAI fails (default `true`) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP settings |
| `GITHUB_TOKEN` | Fine-grained personal access token with only **Issues: Read and write** on the onboarding repository |
| `GITHUB_DEFAULT_OWNER`, `GITHUB_DEFAULT_REPOSITORY` | Repository where onboarding issues are created |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Database connection (H2 by default) |
| `APP_WORKFLOW_EMAIL_APPROVAL_REQUIRED` | `true` holds each email for HR review before sending |
| `APP_WORKFLOW_GITHUB_AFTER_EMAIL` | `true` (default) creates the issue only after the email has been sent |
| `APP_MOCK_OPENAI_FAIL`, `APP_MOCK_EMAIL_FAIL`, `APP_MOCK_GITHUB_FAIL` | Make a mock integration fail, for testing retries |

Example, with real OpenAI but mock email and GitHub:

```bash
export OPENAI_MODE=live OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

### PostgreSQL

```bash
docker compose up --build        # Postgres, backend and frontend at http://localhost:8081
# or, against an existing PostgreSQL:
SPRING_PROFILES_ACTIVE=postgres DATABASE_URL=jdbc:postgresql://localhost:5432/onboarding \
DATABASE_USERNAME=onboarding DATABASE_PASSWORD=... ./mvnw spring-boot:run
```

The Flyway scripts use portable SQL, so the same migrations run on H2 and PostgreSQL. Set `FLYWAY_LOCATIONS=classpath:db/migration` to skip loading the sample data on PostgreSQL.

---

## How the workflow works

```
POST /api/employees ──► one transaction: employee + workflow + 6 step rows ──► 202 Accepted { workflowId }
                                                        │ (after commit)
                                                        ▼
            WorkflowJobRunner  (immediate trigger, plus a poller every 2 s that sweeps pending steps)
                claim step (PENDING → IN_PROGRESS, compare-and-set)    ── short transaction
                call OpenAI / SMTP / GitHub                            ── no transaction open
                record outcome, recompute workflow status              ── short transaction
```

| Step | Notes |
|------|-------|
| Employee details validated | Bean Validation on the request; the React form applies the same rules with Zod |
| Employee record saved | Employee ID such as `EMP-001001` comes from a database sequence |
| AI email generated | OpenAI structured output (JSON schema), then validation and fallback (see below) |
| Welcome email sent | Waits in `AWAITING_APPROVAL` when approval is enabled |
| GitHub onboarding issue created | The issue body includes the employee details, the checklist and task checkboxes |
| Workflow completed | Runs only after every other step succeeded |

**Durability and safe retries**

- Workflow state is stored in `workflow_steps`. A failure in one step never erases the employee or the results of earlier steps.
- **Employees:** an `Idempotency-Key` header, plus unique constraints on email, employee number and idempotency key. Re-sending a submission returns the original result with `200`.
- **Email:** the email record moves to `SENDING` *before* SMTP is called, and to `SENT` afterwards. A step already marked `SENT` is never resent. If the process stops while an email is `SENDING`, the step becomes `FAILED_UNCERTAIN`, and only an **ADMIN** can force a retry (`?force=true`), because that retry may send a duplicate.
- **GitHub:** the issue body contains a hidden marker, `<!-- onboarding-workflow:{id} -->`. Before creating an issue, the step searches recent issues for that marker and reuses a match, so a lost response cannot produce a second issue.
- Retry re-queues only `FAILED` steps, up to `APP_WORKFLOW_MAX_ATTEMPTS`.
- Steps left `IN_PROGRESS` by a crash are recovered on the next start.
- The runner is designed for a **single backend instance**. Running several instances needs lease-based claiming or an external job queue.

**AI output is untrusted.** It must match the JSON schema and pass Bean Validation (lengths, single-line subjects, 3–12 checklist items). It is rejected if it contains links, markup or unresolved placeholders, or doesn't address the employee by first name. The email body is plain text and is HTML-escaped when rendered. In the issue text, `@mentions` and HTML comments are neutralised. OpenAI receives only the first name, job title, department, joining date and access level. It never receives the email address, last name or GitHub username. If OpenAI fails or its output is rejected, the step records why and uses [the fallback template](backend/src/main/resources/templates/fallback-welcome.txt).

**GitHub permissions.** Creating the issue does **not** grant repository access or change any permissions. The requested access is recorded as a checklist task. Actual provisioning must be a separate process with explicit authorization and audit logging.

**Security.** HTTP Basic with stateless sessions. Two roles: `HR` (all endpoints) and `ADMIN` (adds forced retries). Credentials are kept in browser memory only. Every request carries an `X-Correlation-Id` that appears in the logs and error responses. Create, approve, retry and step events are written to `audit_events` and shown in the Workflow Status tab. Logs mask email addresses and never contain API keys, passwords or email bodies.

---

## REST API

All endpoints except `/api/health` need HTTP Basic auth. Errors use RFC 7807 problem responses with `code` and `correlationId`.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/employees` | Validate and create an onboarding request (`202`; `200` for an idempotent replay) |
| GET | `/api/employees?q=&page=&size=` | Search (name, email, ID, department) and paginate |
| GET | `/api/employees/{id}` | Employee details and workflow summary |
| GET | `/api/employees/{id}/workflow` | Step statuses, timestamps, errors and execution history |
| POST | `/api/employees/{id}/workflow/retry[?force=true]` | Retry failed steps (`force` needs ADMIN) |
| GET | `/api/employees/{id}/email-preview` | Generated email: recipient, subject, body, checklist |
| POST | `/api/employees/{id}/email/approve` | Approve (optionally edit) and send, when approval is enabled |
| GET | `/api/employees/{id}/github-issue` | Issue title, body, number, URL and status |
| GET | `/api/health` | Health check and integration modes |

### Sample requests

```bash
# Create an employee (safe to repeat with the same Idempotency-Key)
curl -u hr:hr-password -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 6f1c2a52-6a0e-4f43-9d8e-1b2f0d7c9a11" \
  -d '{
        "firstName": "Priya", "lastName": "Sharma",
        "workEmail": "priya.sharma@example.com",
        "jobTitle": "Backend Engineer", "department": "Engineering",
        "joiningDate": "2026-11-16", "reportingManager": "Daniel Okafor",
        "githubUsername": "priya-sharma", "githubRepository": "acme-corp/payments-service",
        "accessLevel": "WRITE"
      }'
# → 202 {"employeeId":7,"employeeNumber":"EMP-001001","workflowId":7,"workflowStatus":"IN_PROGRESS","duplicateRequest":false}

# Poll workflow status
curl -u hr:hr-password http://localhost:8080/api/employees/7/workflow

# Search the employee history
curl -u hr:hr-password "http://localhost:8080/api/employees?q=engineering&page=0&size=10"

# Retry the failed GitHub step of sample employee 4
curl -u hr:hr-password -X POST http://localhost:8080/api/employees/4/workflow/retry

# Approve sample employee 5's email, with an edited subject
curl -u hr:hr-password -X POST http://localhost:8080/api/employees/5/email/approve \
  -H "Content-Type: application/json" -d '{"subject":"Welcome aboard, Yuki!"}'
```

---

## Project layout

```
backend/
  src/main/java/com/gsr/onboarding/
    config/        AppProperties, SecurityConfig, CorrelationIdFilter, RestClientConfig, OpenApiConfig
    controller/    EmployeeController, HealthController
    domain/        entities (Employee, OnboardingWorkflow, WorkflowStep, OnboardingArtifact, AuditEvent) and enums
    dto/ mapper/   request/response records and MapStruct mappers
    exception/     GlobalExceptionHandler (ProblemDetail) and domain exceptions
    integration/   openai/ (OpenAIClient), email/ (EmailService, SmtpEmailService), github/ (GitHubService), each with a mock
    repository/    Spring Data repositories
    service/       EmployeeService, EmailContentService, AuditService
    workflow/      EmployeeOnboardingWorkflowService, WorkflowJobRunner, WorkflowStepTxService, WorkflowPolicy, handler/
  src/main/resources/db/migration   V1__schema.sql (schema)
  src/main/resources/db/sample      V2__sample_data.sql (sample data)
frontend/
  src/api/         typed fetch client and endpoints
  src/components/  Tabs, StepTracker, StatusBadge, LoginGate, UI primitives
  src/features/    employee/, workflow/, email/, github/, history/  (one per tab)
```
