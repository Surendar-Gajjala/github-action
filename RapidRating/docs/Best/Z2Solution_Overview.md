# RapidRatings Integration – Solution Overview

| | |
|---|---|
| **Program** | Z2 Enterprise – Data Integrations |
| **Integration** | RapidRatings |
| **Target DataModel** | Existing Z2 `Company` DataModel |
| **Source environment investigated** | RapidRatings 
| **Document status** | Proposed solution design for review. Open questions (Section 15) must be confirmed before production rollout. |
| **Version** | 1.1 – 2026-10-30 |
| **Detailed design** | `RapidRatings_Integration_Implementation.md` (same folder): full field mappings, property definitions, layout JSON, platform APIs, acceptance tests |

**How to read this document.** Statements are marked where it matters:

| Mark | Meaning |
|---|---|
| ✅ **Verified** | Observed against the RapidRatings sandbox or read from the existing Z2 Company DataModel |
| 💡 **Proposed** | Design decision recommended by this document |
| ❓ **To confirm** | Needs an answer from RapidRatings or the Z2 platform team (Section 15) |

---

## 1. Summary

Z2 Enterprise is adding a **Data Integrations** area where each customer chooses which external data sources to enable for their tenant. **RapidRatings**, a provider of financial health ratings for companies, is one of them.

With RapidRatings enabled, users see on each linked Z2 company:

- the company's RapidRatings profile (RRID, sector, ticker, currency, report links);
- its **current financial health**: FHR, CHS and probability of default;
- its **financial statements per reporting period** (Periods);
- its **full rating history** (Ratings) and the five most recent ratings (Latest Ratings).

Data is refreshed on demand or on a schedule, and every sync and configuration change is recorded.

The integration reuses a pattern Z2 already has for another external provider, **Kharon Risk Data** ✅: a provider-owned property group on the Company record, with list collections shown as tables on Company Details. That keeps the design familiar to the platform team and lowers delivery risk.

### Requirement coverage

| Common integration function | How RapidRatings meets it | Section |
|---|---|---|
| Integration Authentication | OAuth 2.0 Client Credentials, performed by the Z2 backend; secret held in the secret manager | 6 |
| Schema | Source schema inferred from real API responses and versioned in Z2 | 7 |
| Schema Mapping to Z2 Enterprise | New `RapidRatings` group on the existing Company DataModel; every source field mapped | 8 |
| Syncing of Schema and Data | Separate schema sync (detects changes) and data sync (updates records without duplicates) | 9 |
| Manual and Scheduled Syncs | "Sync now" and schedules run the same pipeline | 10 |
| Sync Schedule and Change History | Run history, schedule versions, append-only audit log | 11 |

---

## 2. Scope

| In scope (release 1) | Out of scope |
|---|---|
| Tenant enablement, credentials, connection test | **Outreach requests** – creating one sends real emails to the counterparty ✅; needs its own approval workflow |
| Portfolios (for scoping), companies, company details, periods, ratings | Writing anything back to RapidRatings |
| Versioned source schema and change detection | Creating Z2 companies automatically from RapidRatings data |
| Mapping into the existing Company DataModel | Overwriting Z2's own company name, country or other canonical fields |
| Linking RapidRatings companies to Z2 companies (with review) | Custom risk scores or bands derived from RapidRatings values |
| Company Details display | Downloading RapidRatings PDF reports into Z2 |
| Manual and scheduled syncs; sync, schedule and change history | Alerts or workflows triggered by rating changes (candidate for a later release) |

### Assumptions

- Each tenant has its own RapidRatings Client ID and Secret.
- The Z2 platform provides a secret manager, a background job queue and a scheduler ❓.
- Companies to enrich already exist in Z2 (RapidRatings data enriches, it does not create).
- Production RapidRatings behaves like the sandbox ❓ (Section 15, Q5).

---

## 3. Glossary

| Term | Meaning |
|---|---|
| **RRID** | RapidRatings company ID, 7 characters (e.g. `KawjbGJ`). Key for every company-level API call. |
| **FHR** | Financial Health Rating, 0–100: short-term (12-month) default risk; higher is healthier ✅ |
| **CHS** | Core Health Score, 0–100: medium-term (2–3 year) operating resilience ✅ |
| **PD** | Probability of default, a **percentage** (`1.5853` = 1.59 %) ✅. *Term PD* = cumulative PD over 2 to 10 years. |
| **Period** | One reporting period, identified as year + period type, e.g. `2020YE` (year end) or `2020Q3` ✅ |
| **Data scale** | Unit of the financial amounts, e.g. `millions` ✅ |
| **Portfolio** | A named group of companies in a RapidRatings account; used to limit the sync scope |
| **NESTED / PROPS_ARRAY** | Z2 DataModel types: a property group, and a list of repeated records inside a record ✅ |

---

## 4. Customer Journey

1. **Enable.** A tenant administrator opens Data Integrations and enables RapidRatings.
2. **Connect.** They enter the Client ID and Client Secret and click **Test connection**. Z2 confirms access.
3. **Choose scope.** All companies visible to the RapidRatings account, selected portfolios, or a list of companies.
4. **Review mapping.** Z2 provides a default mapping to the Company DataModel; the admin reviews and activates it.
5. **Link companies.** Each RapidRatings company is linked to an existing Z2 company. Companies Z2 cannot link with certainty are listed for the admin to resolve.
6. **Sync.** The admin runs **Sync now** and/or sets a schedule (e.g. daily).
7. **Use.** Users open a company and see the RapidRatings section on Company Details; they can filter companies by current FHR or CHS.
8. **Monitor.** The admin reviews sync history and change history.

### Roles and permissions 💡

| Role | Can |
|---|---|
| Integration administrator | Enable/disable, manage credentials, set scope, approve schema and mapping, link companies, run syncs, edit schedule |
| Business user | View RapidRatings data on Company Details; filter by current FHR/CHS |
| Auditor / read-only admin | View sync history and change history |

---

## 5. Solution Architecture

```text
 Z2 UI (browser)
   Data Integrations screens · Company Details
        │  Z2 platform APIs only
        ▼
 Z2 Data Integrations backend  (shared by all providers)
   Tenant configuration · Secret references · Schema registry · Mapping
   Company linking · Sync orchestrator + queue · Scheduler · History & audit
        │  provider adapter
        ▼
 RapidRatings adapter  (provider-specific)
   Token handling · API client · Paging · Response validation
        │  HTTPS + Bearer token
        ▼
 RapidRatings API  (rridata.sandbox.rrilabs.co/v2.0)
   oauth/token · portfolios · companies · companies/{rrid} · periods · ratings
        │
        ▼
 Z2 Company record  →  RapidRatings group  (profile · Latest Ratings · Periods · Ratings)
```

**Key principle: the browser never calls RapidRatings.** All RapidRatings calls, including sign-in, are made by the Z2 backend, because:

- the Client Secret must never reach the browser;
- RapidRatings' data API blocks requests from browsers (no CORS support) ✅, so a frontend call cannot work.

The **framework** layer is built once and reused by future integrations; only the **adapter** is RapidRatings-specific.

### Key design decisions 💡

| Decision | Alternatives considered | Why |
|---|---|---|
| Backend-only access to RapidRatings | Frontend calls with a token | Secret protection; browser calls are blocked anyway ✅ |
| New `RapidRatings` group on the existing Company DataModel | Separate RapidRatings object type; fields inside the existing `Financial` group | Data stays on the company users already open; provider data is clearly owned and removable; same pattern as Kharon ✅ |
| Separate **Periods** and **Ratings** lists | One list with rating values inside each period | Matches the two API resources one-to-one, so each can be synced and validated on its own |
| Keep **Latest Ratings** as its own list | Derive it from Ratings | Requested explicitly; it is a snapshot of the newest 5 and is replaced on each sync, never merged with Ratings |
| Store **current FHR/CHS** as single values too | Lists only | List contents cannot be filtered in Z2 ✅; single values allow filtering and sorting |
| Flatten term PD into 9 columns | Keep as a nested object | The DataModel has lists inside lists, but no NESTED group inside a list ✅; flat columns are safe and display directly in the table |
| Infer and version the schema | Hard-code the fields | RapidRatings publishes no machine-readable schema for this API ✅; versioning detects provider changes |
| One pipeline for manual and scheduled syncs | Separate implementations | Same results either way; half the code to test |

---

## 6. Integration Authentication

| Item | Value ✅ |
|---|---|
| Endpoint | `POST https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/` |
| Method | OAuth 2.0 Client Credentials; Client ID and Secret sent as HTTP Basic authentication |
| Result | Bearer access token, valid **30 minutes** (`expires_in: 1800`); **no refresh token** |
| Use | `Authorization: Bearer <token>` on every API call |

How it works:

1. The Client Secret is stored in Z2's secret manager. Z2 keeps only a reference to it.
2. When a sync needs data, the backend requests a token and keeps it **in server memory only**.
3. The token is reused for every call and renewed 60 seconds before it expires.
4. If RapidRatings rejects a token (HTTP 403 on the data API ✅), the backend gets a new token and retries **once**. A second rejection is treated as a permission problem and reported, not retried.
5. **Test connection** requests a token and reads the portfolio list, proving both the credentials and data access.
6. Secrets and tokens never appear in logs, sync history or any screen. Credential rotation is recorded in the audit log without the values.

Connection states shown to the admin: **Not configured → Configured → Active**, plus **Authentication failed**, **Permission failed** and **Disabled**. Syncs run only when **Active**.

---

## 7. Schema

### 7.1 RapidRatings data used

| Resource | Endpoint | What it provides |
|---|---|---|
| Portfolios | `/v2.0/portfolios/` | Named company groups, used to choose the sync scope (optional) |
| Companies | `/v2.0/companies/` | Company profile and the 5 latest ratings; filter by portfolio |
| Company details | `/v2.0/companies/{rrid}/` | Same, plus sector average FHR/CHS |
| Periods | `/v2.0/companies/{rrid}/periods/` | Financial statements per period: revenue, profit, assets, liabilities, cash flows |
| Ratings | `/v2.0/companies/{rrid}/ratings/` | One rating per period: FHR, CHS, PD, term PD (2–10 years), sub-scores, leverage/liquidity/earnings assessments |

### 7.2 Key facts about the data ✅

- **Financial amounts** are in the company's currency and **data scale** (e.g. millions). They can be negative or empty (`null`).
- **Periods and ratings** share IDs (`2020YE`); each rating ID matches its period ID.
- **Paging:** 20 companies or 10 periods/ratings per page, newest first. Z2 always follows the "next page" link the API returns; requesting a page beyond the end returns an error.
- **No "changed since" filter**, so Z2 detects changes itself (Section 9.3).
- **Volume in the sandbox:** 5 portfolios, 96 companies, about 26–27 periods and ratings per company.

### 7.3 Schema management

RapidRatings publishes no machine-readable schema for this API ✅. Z2 therefore **infers** the schema from real responses, stores it as a **versioned snapshot**, and compares each new snapshot with the approved one. Changes are reviewed before they affect mappings (Section 9.1).

---

## 8. Schema Mapping to Z2 Enterprise

### 8.1 Target structure 💡

RapidRatings becomes a new group on the **existing** Company DataModel. No existing property or group changes.

```text
Z2 Company (existing)
├── Basic Information, Financial, Kharon Risk Data, … (existing, unchanged)
└── RapidRatings (NEW group)
    ├── Profile        RRID, source company name, country, ticker, exchange, sector, address
    ├── Data context   currency, data scale, data confidentiality, industry classification, external IDs
    ├── Links          company/periods/ratings URLs, FHR / Financial Dialogue / Peer Benchmark reports
    ├── Benchmark      sector average FHR and CHS
    ├── Current rating current period, current FHR, current CHS, current probability of default
    ├── Last synced
    ├── Latest Ratings (list) – newest 5 ratings, replaced on each sync
    ├── Periods        (list) – full financial history, one row per period
    └── Ratings        (list) – full rating history, one row per period
```

### 8.2 Mapping principles

| Principle | Effect |
|---|---|
| **Every source field is mapped** | All Company, Periods and Ratings fields in the requirement are mapped. The only exclusion is the API report-download list, which needs API credentials to open; the user-facing report links are mapped. |
| **Provider values kept as received** | RapidRatings' company name and country are stored in the RapidRatings group; they never overwrite the Z2 company name or Basic Information. |
| **No silent conversion** | Empty values stay empty (never 0); amounts keep their sign, currency and scale; nothing is rescaled or converted. |
| **Z2 naming and types** | snake_case property names; text, whole numbers, decimals and dates use the matching Z2 types; URLs are text shown as links ✅ (Z2 has no URL type). |
| **Versioned and validated** | A mapping is validated against the source schema and the Company DataModel before activation; each sync records the mapping version it used. |

**Worked example (real sandbox data ✅).** Rite Aid Corporation (`KawjbGJ`) maps to `rrid = KawjbGJ`, `sector = Retail`, `current_fhr = 35`, `current_chs = 37`, `current_probability_default = 1.5853`, 5 latest ratings, 27 periods and 27 ratings. Its 2020 year-end net profit is stored as `-469.219` (millions USD), and its empty interest-received value stays empty.

### 8.3 Linking RapidRatings companies to Z2 companies 💡

1. Use an existing link (the RRID already stored on a Z2 company).
2. Use a customer identifier both systems share, if the tenant configures one ❓.
3. Otherwise suggest candidates by name, country and ticker **for the admin to confirm**. Z2 never links on a name match alone.
4. Unlinked companies are listed for review.

One RRID links to at most one Z2 company per tenant, and every manual link is audited.

### 8.4 Company Details

The Company Details page gets a **RapidRatings** section with the profile and current-rating fields, followed by three tables: **Latest Ratings**, **Periods** and **Ratings**. These are layout entries added next to the existing Kharon sections ✅ (Company Details is layout-driven). Adding properties to the DataModel does not display them by itself.

---

## 9. Syncing of Schema and Data

Schema sync and data sync are **separate**: a schema sync never writes company data, and a data sync never changes the schema.

### 9.1 Schema sync

Runs when the connection is activated, on demand, and as a quick check at the start of each data sync.

1. Sample current API responses and build a schema snapshot.
2. Compare it with the approved version: fields added, removed or changed type.
3. **No change:** recorded as checked.
4. **New fields:** saved for review; data sync continues.
5. **A mapped field removed or changed:** data sync for that resource is **blocked** until the mapping is updated and approved.

### 9.2 Data sync

```text
Start run ─▶ token ─▶ list companies in scope ─▶ for each company:
      details ─▶ link to Z2 company ─▶ all periods ─▶ all ratings
      ─▶ validate ─▶ map ─▶ update Z2 record ─▶ record result
─▶ finish run: status + counts ─▶ sync history
```

Update rules that prevent duplicates and protect history:

- A company is identified by **tenant + RRID**; a period or rating row by **RRID + period ID**.
- New periods and ratings are added, changed ones updated, and **nothing is deleted** because it is missing from one response.
- A rating is attached to a period only when **both RRID and period ID match**; records are never matched by name or list position.
- Running the same sync twice changes nothing and creates no duplicates.
- If one company fails, the others continue and the run is marked **partially succeeded**.

### 9.3 Keeping syncs light 💡

RapidRatings has no "changed since" filter ✅, so regular syncs compare each company's latest rating with what Z2 already has, and re-read periods and ratings **only for companies that changed**. A full re-read runs on the first sync and weekly as a check. A full sync of the 96 sandbox companies needs about 680 API calls, well within the sandbox rate limit ✅.

---

## 10. Manual and Scheduled Syncs

Both triggers run the **same job and the same pipeline**.

| | Manual | Scheduled |
|---|---|---|
| Started by | Admin clicks **Sync now** (all, one portfolio, or one company) | The Z2 platform scheduler ❓ |
| Options | Scope | On/off, frequency (daily/weekly/monthly), time and time zone, scope, full or regular, retry policy |
| Recommended 💡 | When needed | **Daily** regular sync plus a **weekly** full sync |
| Progress | Live progress and results on screen | Results in sync history |

Safeguards:

- One run at a time per scope. A scheduled run that would overlap a running one is skipped and recorded.
- Missed scheduled runs (e.g. during downtime) run once at the next start, not once per missed slot.
- Temporary failures (timeouts, rate limits) are retried a limited number of times. Permanent failures (bad credentials, blocked schema, invalid mapping) are not retried; the admin is told what to fix.
- A failed run can be retried manually.

---

## 11. Sync Schedule and Change History

### 11.1 Sync history

Every run records:

- trigger (manual, scheduled or retry) and who started it;
- status: queued, running, succeeded, partially succeeded, failed, cancelled or skipped;
- start and end time, and scope;
- the schema and mapping versions used;
- counts: companies listed, linked, unlinked, updated, unchanged and failed;
- a plain-language error summary.

Results per company are kept too. Admins can filter by date, trigger and status, and retry a failed run.

### 11.2 Schedule history

Each schedule change creates a new schedule version. The schedule screen shows the current settings, who changed them and when, the next run and the last result.

### 11.3 Change history (audit)

Every configuration action is logged: enabling or disabling, credential rotation (the event only, never the values), connection tests, schema approvals, mapping changes, company links, schedule changes and manual syncs. Each entry records who did it, when, and what changed (before and after). The log is **append-only**: it can be read but not edited or deleted.

---

## 12. Security, Errors and Operations

### 12.1 Security

- Client Secret in the secret manager only; tokens in server memory only.
- No RapidRatings calls from the browser.
- Secrets, tokens and raw provider responses never appear in logs, history or screens.
- Every configuration, job and record is isolated per tenant; sandbox and production credentials are separate.
- Only integration administrators can change settings or start syncs; all changes are audited.

### 12.2 What users see when something goes wrong

| Situation | Behaviour |
|---|---|
| Wrong credentials | Connection shows **Authentication failed**; scheduled syncs pause until fixed |
| Account lacks permission | **Permission failed**; no retries |
| RapidRatings slow or rate-limiting | Automatic limited retries; the run completes or is marked failed with the reason |
| One company fails | Others continue; run **partially succeeded**; the company is listed with the reason |
| Company cannot be linked | Listed as **unlinked** for the admin; no data written |
| RapidRatings changes a mapped field | Schema change flagged; affected syncs **blocked** until the mapping is approved |
| Data does not belong to the requested company | Record rejected and reported; nothing stored |

### 12.3 Operations and monitoring 💡

| Signal | Alert when |
|---|---|
| Authentication failures | Any tenant enters **Authentication failed** |
| Failed or partially failed runs | A scheduled run fails, or two in a row are partial |
| Data freshness | A linked company's **Last synced** is older than 2 schedule intervals |
| Schema changes | A breaking change blocks a sync |
| API usage | Retries or rate-limit responses exceed normal levels |

Ownership: the integration admin fixes credentials, mapping and linking; the platform support team owns failed runs caused by Z2 or RapidRatings outages ❓.

---

## 13. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Production API differs from the sandbox | Sync errors at go-live | Confirm with RapidRatings (Q5); pilot tenant first; schema sync detects field changes |
| Wrong company linked | RapidRatings data shown on the wrong company | No automatic name matching; admin confirmation; links audited and reversible |
| Provider adds, removes or changes fields | Missing or wrong data | Versioned schema with diff; breaking changes block sync until approved |
| Users misread PD values | 1.59 % read as 159 % or as a fraction | Labels show "(%)"; values stored exactly as provided |
| Large tables on Company Details | Poor usability | Confirm table rendering early (Q2); current values summarised at the top |
| Contract limits on storing rating history | Compliance issue | Retention confirmed before go-live (Q9); raw responses not stored |

---

## 14. Delivery and Rollout

| Phase | Outcome |
|---|---|
| 1. Foundation and authentication | Admins can connect RapidRatings and test the connection securely |
| 2. Adapter and schema | Z2 reads RapidRatings data and keeps versioned schemas with change detection |
| 3. DataModel extension | RapidRatings group and Company Details sections in place |
| 4. Mapping and company linking | Default mapping active; companies linked or listed for review |
| 5. Data sync and manual runs | Sync now works end to end; repeat runs create no duplicates |
| 6. Scheduling and audit | Scheduled syncs, schedule history and change history live |
| 7. Pilot | One pilot tenant runs scheduled syncs for a week without manual fixes |

**Rollout:** sandbox credentials first, then production credentials for one pilot tenant behind a tenant-level feature switch, then general availability.
**Rollback:** disabling the integration stops all syncs immediately; synced data stays on the company records and can be hidden by removing the Company Details sections. No existing Z2 data is changed by the integration, so there is nothing to restore.

**Testing approach:** unit tests for token handling, mapping and update rules; integration tests against the sandbox (including Rite Aid and Tyson Foods, which share period IDs and must never be mixed); end-to-end tests of manual and scheduled runs, history and tenant isolation. The full test list is in the detailed design.

### Success criteria

- An admin connects RapidRatings without credentials ever being exposed.
- Linked companies show the profile, current rating, latest ratings, and full period and rating history on Company Details.
- Every source field is mapped or explicitly excluded with a reason.
- Re-running a sync creates no duplicates and never deletes history.
- Manual and scheduled syncs give the same results.
- Every run and every configuration change is visible in history.
- Schema changes are detected and reviewed before they affect data.
- Tenants are fully isolated.

---

## 15. Open Questions

| # | Area | To confirm | Owner | Needed by |
|---|---|---|---|---|
| 1 | Z2 DataModel | Which model layer owns the RapidRatings group; whether it can be read-only for users | Z2 platform | Phase 3 |
| 2 | Z2 UI | How the Periods table (~30 columns, ~27 rows) displays; how empty values appear | Z2 UI | Phase 3 |
| 3 | Z2 platform | API for a backend job to update Company records; scheduler and job-queue capabilities | Z2 platform | Phase 5 |
| 4 | Company linking | Which Z2 property holds a customer ID comparable to RapidRatings' external ID | Product | Phase 4 |
| 5 | RapidRatings production | Same behaviour as the sandbox (paging, token lifetime, errors)? Any "changed since" option? | RapidRatings | Phase 2 |
| 6 | Rating definitions | Official meaning of FHR, CHS and PD; allowed values for leverage, liquidity, earnings performance | RapidRatings | Phase 4 |
| 7 | Data scale | Can the scale differ between a company's periods? | RapidRatings | Phase 4 |
| 8 | Private companies | Confidentiality values and any display restrictions | RapidRatings | Phase 4 |
| 9 | Retention | Contractual limits on storing rating history and report links | Legal / Product | Before pilot |
| 10 | Operations | Who owns failed runs caused by outages | Support | Before pilot |

---

## 16. Approval

| Role | Name | Decision | Date |
|---|---|---|---|
| Product owner | | | |
| Solution architect | | | |
| Z2 platform lead | | | |
| Security | | | |
