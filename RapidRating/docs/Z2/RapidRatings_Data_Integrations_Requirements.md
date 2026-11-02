# RapidRatings in the Z2 Data Integrations Tab: Implementation Requirements

| | |
|---|---|
| **Program** | Z2 Enterprise, Data Integrations |
| **Integration** | RapidRatings (company financial health ratings) |
| **Target DataModel** | Existing Z2 Company object (`objectType` 144, `dbName: company`) |
| **Status** | Ready for developer review. Items in Section 15 must be closed by the stated phase. |
| **Date** | 2026-11-01 |
| **Replaces** | `RapidRatings_Integration_Requirements_Developer_Review.md` and `RapidRatings_Integration_Requirements.md` (same folder) |

**Requirement wording.** "Must" is mandatory for release 1. Each requirement has an ID that the tests in Section 14 refer to.

**Evidence labels**

| Label | Meaning |
|---|---|
| **[Sandbox]** | Measured on 2026-11-01 across all 96 companies visible to the sandbox account: 1,613 period rows and 1,614 rating rows. See Appendix C. |
| **[Sandbox docs]** | Tested on 2026-10-28 and recorded in `docs/api/`. Covers error cases that the 2026-11-01 sweep did not trigger. |
| **[Z2 model]** | Read from `docs/Best/Z2company.json`. |
| **[Decision]** | Design decision made by this document. It can be challenged in review. |
| **[Confirm]** | Needs an answer from the owner named in Section 15. |

The sandbox results describe sandbox data only. They do not prove production behaviour (Q1).

---

## 1. Purpose

A tenant administrator enables RapidRatings from the Data Integrations tab and connects it with a Client ID and Client Secret. Z2 then reads company profiles, financial periods and ratings, links each RapidRatings company to an **existing** Z2 Company, stores the data in a new `rapidratings` group on that Company, and shows it on Company Details.

The integration provides the common Data Integrations functions:

| Function | Section |
|---|---|
| Integration authentication | 6, 7.1 |
| Source schema | 7.3 |
| Mapping to the Z2 Company DataModel | 7.4, Appendix B |
| Separate schema sync and data sync | 7.3, 7.6 |
| Manual and scheduled sync | 7.6, 7.7 |
| Sync, schedule and change history | 7.8 |

---

## 2. Scope

| In scope for release 1 | Out of scope |
|---|---|
| Catalogue entry, connection, credentials and connection test | Outreach API: `POST /outreach-requests` makes RapidRatings send real emails to the counterparty **[Sandbox docs]** |
| Portfolios, companies, company details, periods and ratings (read only) | `POST /companies/`, which creates a private company at RapidRatings, and any other write to RapidRatings |
| Versioned source schema with change review | Creating Z2 Companies |
| Versioned mapping into the `rapidratings` group | A separate RapidRatings object type |
| Company linking with an admin review list | Changing or removing any existing Company property or layout section |
| Manual and scheduled data sync | A RapidRatings-specific scheduler |
| Sync, schedule and change history | Risk bands, derived scores, currency conversion or rescaling |
| Company Details sections | Downloading reports into Z2, or showing them in Z2 |
| | Alerts or workflows triggered by rating changes |
| | Storing raw API responses after a run |

---

## 3. Corrections to the Earlier Documents

Checking every sandbox company and the Z2 model changed these points. Each one changes what gets built.

| # | Earlier statement | What the evidence shows | Effect on this specification |
|---|---|---|---|
| 1 | A rating ID always has a matching period ID | True for 95 of 96 companies. Kansas City Southern (`KawjkGJ`) has rating `2014Q3`, but its period returns 404. **[Sandbox]** | A rating without a period is valid. It must not fail the company (DATA-07). |
| 2 | `latestRatings` and `ratings` hold the same values | For 9 financial-sector companies (45 rows), `latestRatings` has `null` for `simulatedFhr` and all four sub-scores, while `ratings` has values for the same ID. `simulatedFhrDelta` also differs in 40 of those rows. **[Sandbox]** | Keep the two lists strictly separate and never fill one from the other (DATA-06). Which value is correct is Q3. |
| 3 | `leverage`, `liquidity` and `earningsPerformance` are `Strong`, `Average` or `Weak` | 147 rating rows hold `--`. **[Sandbox]** | Use `STRING`, not `ENUM`. Store and show `--` as received. |
| 4 | Report links are always `fhr`, `financialDialog` and `peerBenchmark` | 87 companies have those three. 3 have `fin_services` and `companyProfilePage`. 3 have only `companyProfilePage`. 3 have none. **[Sandbox]** | Every report link is optional. Two more target fields are added (Appendix B). |
| 5 | Schema sync can sample "the first company" | The variations in rows 2 to 4 appear only in some sectors, so one company would miss them. **[Sandbox]** | Schema sync must sample more widely (SCH-02). |
| 6 | `cashFromOperationsFromCashFlow` is always `null` | It has a value in 925 of 1,613 rows. **[Sandbox]** | Normal nullable `DOUBLE`. |
| 7 | API URL fields are shown as links | `href`, `financials`, `periods` and `ratings` need a Bearer token, so a user who clicks one gets an error. **[Sandbox docs]** | Store them as plain text and leave them out of the Company Details layout (CD-05). |
| 8 | Report links open for the user | Without a portal session they redirect to `/account/login/`. The API token does not help. **[Sandbox]** | Label them as RapidRatings portal links that may need a sign-in (CD-06, Q4). |
| 9 | `financials` is an extra data source to map | It returns 200 with `count: 0` for all 96 companies. **[Sandbox]** | Do not call it in release 1. Store the URL only (Q5). |
| 10 | Country needs a new ISO-3 lookup table | The Countries LOV already has `alpha_3` next to its stored `value` (`USA` gives `US`). **[Z2 model]** | Use the LOV. No new table is needed (MAP-05). |
| 11 | Candidates can be matched on ticker | The Company model has no ticker, exchange or sector property. **[Z2 model]** | Match candidates on name and country only. The RapidRatings ticker is shown to help the admin decide (LINK-03). |
| 12 | List rows can't be filtered, as shown by `kharon.risks` | `kharon.risks` children are `filterable: true` on 8 of 9 fields. **[Z2 model]** | The real reason for `current_*` fields is that filtering and sorting companies needs one value per company. Whether row filters work is Q9. |
| 13 | RRIDs are 7 characters | All 96 are 7 characters, but the outreach spec example shows `Axtr4P` (6). **[Sandbox], [Sandbox docs]** | Validate the pattern `^[A-Za-z0-9]+$`, not the length. |
| 14 | "Wait for `Retry-After`" on rate limits | Normal responses also carry `Retry-After`, with values such as 3,464 seconds. **[Sandbox docs]** | Read it on 429 only, and cap the wait (API-05). |
| 15 | A full sync of 96 companies takes about 680 calls | The measured figure is 533: 5 list pages plus 528 per-company calls. **[Sandbox]** | Use 533 for planning. |
| 16 | No Z2 data changes, so rollback restores nothing (while the integration also fills an empty Country) | These two statements conflict. | Rollback must cover Country fills (Section 13). The fill is confirmed as Q2. |

### Status of the earlier open items

| Earlier open item | Status |
|---|---|
| Token format, expiry and errors | **Resolved** for the sandbox (Appendix A) |
| Methods, paging, filters and rate limits | **Resolved** for the sandbox (Appendix A) |
| `sectorAverages`, `reports` and `latestRatings` consistency | **Resolved.** `latestRatings` is on list and detail. `sectorAverages` and `reports` are on detail only, for 96 of 96 companies. `sectorAverages` values can be `null`. |
| Periods and Ratings fields consistent across companies | **Resolved.** Every field appears on every row, and values can be `null`. |
| Type of `financialAssets` and `cashFromOperationsFromCashFlow` | **Resolved.** `number` or `null`. |
| Type of `industryClassification*` and `externalSourceIds` | **Open.** `null` or `[]` for all 96 companies, so stored as documented and tested in production (Q6). |
| Rating ID matches Period ID | **Resolved with an exception** (correction 1) |
| Matching an RRID to a Z2 Company | **Decided** (Section 7.5). The shared identifier stays open (Q7). |
| Nested structures inside `PROPS_ARRAY` | **Avoided.** The model has `PROPS_ARRAY` in `PROPS_ARRAY` but no `NESTED` in `PROPS_ARRAY` **[Z2 model]**, so term PD is flattened. |
| Scheduler rules | **Open** (Q10) |
| Outreach contract | **Documented and out of scope**, because of the real emails |
| Production URLs, credentials and retention | **Open** (Q1, Q11) |

---

## 4. Roles

Align the names with existing Z2 roles.

| Permission | Integration admin | Business user | Auditor |
|---|---|---|---|
| Enable or disable, manage credentials and environment | Yes | | |
| Set scope, approve schema, activate mapping | Yes | | |
| Link, unlink or ignore companies | Yes | | |
| Run a sync, retry or cancel a run, edit the schedule | Yes | | |
| View the RapidRatings tab pages (read only) | Yes | | Yes |
| View sync, schedule and change history | Yes | | Yes |
| View RapidRatings data on Company Details | Yes | Yes | Yes |

**ROLE-01** Each permission must be enforced by the backend. Hiding a control in the UI is not enough.

---

## 5. Architecture Rules

```text
Z2 UI (Data Integrations tab, Company Details)
   |  Z2 platform APIs only
   v
Data Integrations framework (shared by all providers)
   Config, secret references, schema registry, mapping, company links,
   sync runner and queue, scheduler, history. Writes Company records.
   |  provider adapter contract
   v
RapidRatings adapter (RapidRatings only)
   Token handling, API client, paging, response validation.
   |  HTTPS + Bearer token
   v
RapidRatings API (rridata host)
```

| ID | Requirement |
|---|---|
| ARCH-01 | Only the Z2 backend calls RapidRatings. The browser calls Z2 APIs only. Browser calls can't work anyway, because the data host sends no CORS headers for browser origins **[Sandbox docs]**. |
| ARCH-02 | The adapter must not write Z2 data or decide mapping and linking. The framework must not contain RapidRatings URLs or rules, so the next provider can reuse it. |
| ARCH-03 | Manual and scheduled syncs must run the same job and code path. |
| ARCH-04 | Schema sync must never write Company data. Data sync must never change the schema, the DataModel or a mapping. |

---

## 6. Data Integrations Tab

### 6.1 Catalogue

**UI-01** The Data Integrations tab lists the integrations available to the tenant. The RapidRatings card shows the name, a one-line description, connection status, last sync result and time, and an **Enable** or **Manage** action.

### 6.2 RapidRatings page

**UI-02** **Manage** opens a page with the sub-tabs below. Sub-tabs whose prerequisites are unmet stay visible, and each explains what is needed first.

| Sub-tab | Shows | Actions | Prerequisite |
|---|---|---|---|
| **Connection** | Environment, Client ID (last 4 characters), secret state (`Set` or `Not set`), status, last test time and result, granted scope | Save, Test connection, Replace secret, Enable or Disable | None |
| **Scope** | Scope mode, chosen portfolios or companies, company count | Save scope | `ACTIVE` |
| **Schema** | Versions per resource (companies, company detail, periods, ratings), status, added, removed and changed fields | Refresh schema, Approve, Reject | `ACTIVE` |
| **Mapping** | Source field, target path, type, version and status, validation errors, unmapped source fields | Edit draft, Validate, Activate | Approved schema |
| **Company links** | RRID, source name, country, ticker, link status, linked Z2 Company, candidates | Link, Bulk confirm, Unlink, Ignore, Restore | `ACTIVE` |
| **Sync** | Sync now, live progress, active run, run list | Sync now, Cancel, Retry | Active mapping |
| **Schedule** | Current settings, next run, last result, who changed it and when | Save, Turn on or off | Active mapping |
| **History** | Sync runs with per-company results, schedule versions, change history | Filter, open details | None |

**UI-03** The Connection page must never display or return the secret or a token. The secret field is write-only: blank means "keep the current secret".

**UI-04** Scope modes are **All companies** (the default), **Selected portfolios** and **Selected companies**.

- Portfolios come from `GET /portfolios/`.
- The portfolio object has no company count **[Sandbox docs]**. Show the `count` from `GET /companies/?portfolio={id}`, and load it on demand so opening the page doesn't trigger many calls.
- Company search uses the API filters: `name` needs 3 or more characters and matches partially, and `ticker` is exact and case-sensitive. The UI must explain both and must block a shorter `name`.

**UI-05** Actions that are blocked show the reason in plain language, for example "Approve schema version 2 before starting a sync".

**UI-06** Sync progress shows companies processed and total. The final view shows the counts and links to per-company results. Progress must come from polling or the platform's standard mechanism, and must keep working if the page is closed and reopened.

**UI-07** Every error shown in the tab uses the plain messages in Section 10 and must not show raw provider bodies.

**UI-09** **Sync now** offers the configured scope (the default), one portfolio or one company within it, and a mode of `REGULAR` or `FULL`. The first sync is always `FULL`.

### 6.3 Setup order

```text
Enable -> Enter credentials -> Test connection (ACTIVE) -> Choose scope
       -> Approve schema v1 -> Activate default mapping -> Link companies
       -> Sync now -> (optional) Set schedule
```

### 6.4 Connection states

| State | Meaning | Enters when | Syncs allowed |
|---|---|---|---|
| `NOT_CONFIGURED` | No credentials | Initial state | No |
| `CONFIGURED` | Credentials saved, not yet proven | Credentials or environment saved | No |
| `ACTIVE` | Token and data read both work | Test connection succeeds | Yes |
| `AUTH_FAILED` | Token request rejected | Token request returns 400 or 401 | No |
| `PERMISSION_FAILED` | Token works, data is refused | 403 after one token renewal | No |
| `DISABLED` | Switched off by an admin | Admin disables | No |

**UI-08** From `AUTH_FAILED` or `PERMISSION_FAILED`, a successful Test connection returns the state to `ACTIVE`. Enabling from `DISABLED` returns to `CONFIGURED`, and a test is required before syncing.

---

## 7. Functional Requirements

### 7.1 Authentication (AUTH)

The token contract is in Appendix A.

| ID | Requirement |
|---|---|
| AUTH-01 | The Client Secret must be stored in the Z2 secret manager. Z2 configuration holds only a reference. |
| AUTH-02 | Tokens must be kept in server memory only, keyed by tenant and environment, with `expiresAt = now + expires_in`. Use `expires_in` from the response and do not hard-code 1,800. |
| AUTH-03 | Reuse the token. Request a new one when less than 60 seconds remain. There is no refresh token, so renewal repeats the same request. |
| AUTH-04 | Workers for the same tenant and environment must share one in-flight token request. |
| AUTH-05 | A data call that returns 403 must drop the token, renew it and retry **once**. A second 403 sets `PERMISSION_FAILED` and stops the run. |
| AUTH-06 | A token request that returns 400 or 401 must set `AUTH_FAILED` and must not be retried. Timeouts and 502, 503 and 504 on the token request follow API-04. |
| AUTH-07 | **Test connection** gets a new token and then calls `GET /portfolios/`. Only a 200 from that call sets `ACTIVE`. |
| AUTH-08 | Rotating credentials must replace the secret, clear the cached token, set `CONFIGURED`, require a new test and record the event without values. |
| AUTH-09 | Base URLs must come from server configuration per environment (sandbox or production). Admins choose an environment and never type a URL. |
| AUTH-10 | Changing environment must clear the token, set `CONFIGURED` and require schema and mapping approval for that environment. Company links must be stored per environment, because an RRID link from one environment is not assumed valid in another (Q1). |

### 7.2 API client (API)

| ID | Requirement |
|---|---|
| API-01 | Provide one client method per endpoint in Appendix A, plus a helper that follows `next` until it is `null`. Never build page numbers. |
| API-02 | Always send the trailing slash, and only the accepted query parameters. |
| API-03 | Before following a `next` URL, check that its scheme and host match the configured base URL. On a mismatch, fail the request and don't send the token. |
| API-04 | Use a 30-second timeout. Retry timeouts, network errors and 502, 503 and 504 up to 2 times with exponential backoff. Never retry 400, 404 or 500. A 500 can mean a page past the end **[Sandbox docs]**, and following `next` prevents that case. |
| API-05 | On 429 only, wait for `Retry-After` and retry at most 2 times. If it asks for more than 60 seconds **[Decision]**, stop retrying, and mark the remaining companies `FAILED` with reason `RATE_LIMITED`. |
| API-06 | Log the `X-Ratelimit-Remaining` value per run. |
| API-07 | Parse errors from the body `{"<Scope>": {"message", "info"}}`. The scope key varies (`Request`, `Portfolio`, `Companies`, `Not found`), so don't hard-code it. |
| API-08 | Errors passed upward carry the HTTP status, a category and the sanitized API message. They never carry the token or the Basic header. |
| API-09 | Treat whole numbers and decimals as the same numeric type, because `1575.0` may parse as an integer. |
| API-10 | Process at most 4 companies in parallel **[Decision]**, configurable. |

### 7.3 Source schema and schema sync (SCH)

RapidRatings publishes no machine-readable schema for the data API (`/openapi.json` returns 403) **[Sandbox docs]**. Z2 therefore infers it from responses and versions it per tenant and environment.

| ID | Requirement |
|---|---|
| SCH-01 | Schema sync runs when the connection first becomes `ACTIVE`, when an admin clicks **Refresh schema**, and as a check at the start of each data sync. |
| SCH-02 | Sample at least: the whole first page of companies (20), plus detail, the first page of periods and the first page of ratings for up to 5 companies from **different sectors** in that page. The check before a data sync uses the same sample, which costs about 16 calls. |
| SCH-03 | Record for each field path (for example `termPd.year2`, `latestRatings[].fhr`): observed types, whether `null` was seen, and how many sampled items contained it. |
| SCH-04 | `null` must not count as a type change. An `integer` and `number` mix is `number`. A field seen only as `null` keeps its known type from Appendix B. |
| SCH-05 | A field is "removed" only if it is missing from **every** sampled item. Missing from some items means optional. |
| SCH-06 | Handle comparison results as in the table below. |
| SCH-07 | The first run saves version 1 as `PENDING_REVIEW`. Data sync is blocked until an admin approves it. Rejecting a version keeps the previous approved one in force. |
| SCH-08 | Schema sync must never change the DataModel or an approved mapping automatically. Every new version is recorded in change history. |
| SCH-09 | Version 1 starts from Appendix B as a baseline, in which every field is nullable and report links are optional. This stops known variations from triggering a review each time the sample changes. |
| SCH-10 | During data sync, a field not in the approved schema must not fail the company. Count it in the run's `unmappedFields`, ask for a schema review, and don't store its value. |

| Finding against the approved version | Result | Data sync |
|---|---|---|
| No change | Marked "checked" | Continues |
| New field | New version, `PENDING_REVIEW` | Continues |
| Field became nullable | New version, `PENDING_REVIEW` | Continues |
| Unmapped field removed or changed type | New version, `PENDING_REVIEW` | Continues |
| **Mapped** field removed or changed to an incompatible type | New version, `PENDING_REVIEW` | **Blocked** until the mapping is updated and activated |

### 7.4 DataModel extension and mapping (DM, MAP)

```text
Z2 Company (existing, unchanged)
├── basic_info, ownership, financial, kharon, ... (existing)
└── rapidratings (NEW, NESTED)
    ├── Profile        rrid, source_company_name, source_country, ticker, exchange, sector, address
    ├── Data context   currency, data_scale, data_confidentiality, industry_classification_*, external_source_id(s)
    ├── Technical URLs company_url, financials_url, periods_url, ratings_url
    ├── Report links   report_link_fhr, _financial_dialog, _peer_benchmark, _fin_services, _company_profile
    ├── Benchmark      sector_average_fhr, sector_average_chs
    ├── Current rating current_rating_id, current_fhr, current_chs, current_probability_default, current_financial_date
    ├── Sync           last_synced_at
    ├── latest_ratings (PROPS_ARRAY) snapshot from the company response; replaced each sync
    ├── periods        (PROPS_ARRAY) full history from the periods endpoint
    └── ratings        (PROPS_ARRAY) full history from the ratings endpoint
```

| ID | Requirement |
|---|---|
| DM-01 | Add only the `rapidratings` group. A before-and-after comparison must show no change to any existing property, ID, path or layout section. |
| DM-02 | Use the same property shape as `kharon` **[Z2 model]**. `name`, `label`, `dbName` and `dataType` come from Appendix B, and a child's `path` is parent path + `.` + `dbName`. `id`, `dataEnvId` and timestamps are assigned by the platform. |
| DM-03 | Set `required: true` only on `period_id` and `rating_id`. The precedent is `z2.sanctions.list_key` **[Z2 model]**. |
| DM-04 | Set `canUpload: false` on the group and its children so only the integration writes them. The precedent is `z2data` **[Z2 model]**. Whether this also blocks UI edits is Q8. |
| DM-05 | Flatten objects: `sectorAverages.fhr` to `sector_average_fhr`, `termPd.year2` to `term_pd_year_2`, and so on. |
| DM-06 | Set `searchable` and `filterable` to `true` for `rrid`, `ticker`, `sector`, `current_rating_id`, `current_fhr` and `current_chs`. Set them to `false` on list fields and all other fields in release 1 **[Decision]**. |
| DM-07 | URLs are `STRING` because the model has no URL type **[Z2 model]**. Dates use `uiHints: {"displayType": "DATE"}`. Report links use `uiHints.displayType: "LINK"`, `type: "EXTERNAL"`, `newTab: true`. Existing link templates use `{id}`, so the template for a value that is itself a URL is Q8. |
| DM-08 | `dataModelType` and `extDataModelId` are set by the platform team (Q8). The layer must not make RapidRatings fields or sections visible to tenants that haven't enabled it. |

| ID | Requirement |
|---|---|
| MAP-01 | Ship a default mapping that covers every field in Appendix B. Only `reports[]` is left out, for the reason given there. |
| MAP-02 | Every target must be inside `rapidratings`. The single exception is the Country fill in MAP-05. |
| MAP-03 | Mapping must not change values: `null` stays `null` (never `0` or `""`), `""` stays `""`, `--` and `---` stay as received, and signs, precision, currency and scale are kept. |
| MAP-04 | `company_name` must never be written. `name` is stored in `source_company_name` and used only to suggest link candidates. |
| MAP-05 | `basic_info.country` is written only when it is **empty** and `country` has a value. Find the LOV entry whose `alpha_3` equals `country`, and store its `value` (`USA` gives `US`). If there is no match, write nothing and log a warning. Each fill is recorded in change history. `source_country` always keeps the provider value. Q2 confirms this behaviour. |
| MAP-06 | One source may feed two targets, but a target path takes only one source. |
| MAP-07 | Mapping versions: every change creates a new version, old versions are kept, one is active, and each run records the version it used. |
| MAP-08 | A version can be activated only when: its schema version is approved; every source field exists in that schema; every target path exists in the deployed DataModel; types are compatible with Appendix B; `rrid`, `periods.period_id`, `ratings.rating_id` and `latest_ratings.rating_id` are mapped; all nine `term_pd_year_*` fields are mapped in both rating lists; and no target has two sources. |
| MAP-09 | Validation errors must name the field and the rule that failed. |

### 7.5 Company linking (LINK)

| ID | Requirement |
|---|---|
| LINK-01 | One RRID links to at most one Z2 Company, and one Z2 Company has at most one RRID, per tenant and environment. |
| LINK-02 | Try in this order: (1) an existing link; (2) if the tenant has configured a shared identifier, `externalSourceId` equal to that Z2 property; (3) candidates for the admin to confirm. |
| LINK-03 | Candidates are ranked on name and country only. Never link automatically on a name match. The shared identifier options are `basic_info.duns_number`, `basic_info.cage_code` and `basic_info.tax_id` **[Z2 model]**, subject to Q7. `externalSourceId` is `null` for all sandbox companies, so step 2 can't be tested against sandbox data. |
| LINK-04 | Link statuses are `LINKED`, `UNLINKED` and `IGNORED`. An `IGNORED` company stays out of the review list and out of `FAILED` counts until an admin restores it. |
| LINK-05 | Nothing is written for `UNLINKED` or `IGNORED` companies, and no Z2 Company is created. |
| LINK-06 | **Bulk confirm** lets an admin accept several suggested candidates in one action. Each accepted link is still an admin decision and is recorded individually. |
| LINK-07 | **Unlink** must ask for confirmation and then clear the `rapidratings` group on that Company, because data from a wrong link would otherwise stay on display. Any Country filled from that RRID is listed in the confirmation dialog and stays unless the admin chooses to clear it. |
| LINK-08 | If the linked Z2 Company is deleted, the link returns to `UNLINKED`, with a note in the review list. |
| LINK-09 | If a linked RRID is no longer returned by the provider or no longer in scope, keep the stored data and show "not returned since {date}" in **Company links**. It must not be counted as `FAILED`. |
| LINK-10 | Every link, unlink, ignore and restore is recorded in change history. |

### 7.6 Data sync (DATA)

```text
Create run -> token -> schema check -> list RRIDs in scope (deduplicated)
  -> per company: link? -> read -> validate -> map -> write -> save result
  -> finish run: status, counts, errors
```

| ID | Requirement |
|---|---|
| DATA-01 | Before starting, check that the status is `ACTIVE`, an approved schema and active mapping exist, and no blocking schema change is pending. |
| DATA-02 | Allow one active run per tenant and environment **[Decision]**. **Sync now** during a run shows the active run instead of starting a new one. |
| DATA-03 | Resolve scope: All uses `GET /companies/`; a portfolio uses `GET /companies/?portfolio={id}`; selected companies use `GET /companies/{rrid}/`. Portfolios overlap, so deduplicate by RRID and process each company once. A selected portfolio that returns 404 must fail the run with a clear message. |
| DATA-04 | For each linked company in a `FULL` run: read details, all periods and all ratings, following `next`. |
| DATA-05 | Writes are per company and all-or-nothing **[Decision]**. Validate everything first. If any read or validation fails, write nothing for that company and record `FAILED`. Other companies continue. |
| DATA-06 | `latest_ratings` is filled only from `latestRatings` in the company response, and `ratings` only from the ratings endpoint. `current_*` comes from `latestRatings[0]`. If `latestRatings` is empty, `current_*` fields are `null`. |
| DATA-07 | Match periods and ratings only on RRID + ID, never on name, position or order. A rating without a period, or a period without a rating, is valid. Missing quarters are also valid (234 gaps across 60 companies **[Sandbox]**). |
| DATA-08 | Validation rejects a company if: the RRID in a row's `href` differs from the company; a row has no `id`; the same ID appears twice in a list; or a mapped value can't be converted to the target type. `null` always passes. |
| DATA-09 | A period and a rating with the same ID but a different `financialDate` is stored as received and reported as a warning. No mismatch exists in the sandbox today. |
| DATA-10 | Apply the update rules in the table below. Re-running a sync must create no duplicates. |
| DATA-11 | If the mapped record is unchanged, record `UNCHANGED` and write only `last_synced_at`, so that it always shows the last successful check. |
| DATA-12 | Write through the platform's record-update API with optimistic locking. Retry a version conflict once by re-reading the Company, and then record `FAILED`. |
| DATA-13 | Store list rows newest first: `eqy_year` descending, then `YE`, `Q3`, `Q2`, `Q1`. The API uses this order today **[Sandbox]**. Don't order by `reportingPeriod`, because it repeats across periods in 68 of 96 companies **[Sandbox]**. |
| DATA-14 | Raw responses must not be kept after the run. Store only mapped data. |
| DATA-15 | **Cancel** stops new companies from starting and lets companies in progress finish. Completed writes stay. The run ends `CANCELLED`. |
| DATA-16 | **Retry** creates a new run that has `retryOf` set and covers only the companies that failed. |

| Data | Key | Rule |
|---|---|---|
| Profile, report links, `current_*` | Company record | Replace with latest values. A report link no longer returned becomes `null`. |
| `sector_average_*` | Company record | Replace whenever company detail is read (see Sync modes below) |
| `company_name` | | Never written |
| `basic_info.country` | | MAP-05 only |
| `latest_ratings[]` | Whole list | Replace on each successful read |
| `periods[]` | RRID + `period_id` | Add new, update changed, **never delete** |
| `ratings[]` | RRID + `rating_id` | Add new, update changed, **never delete** |

**Sync modes.** There is no changed-since filter **[Sandbox docs]**.

| Mode | When | Behaviour |
|---|---|---|
| `FULL` | First sync, and weekly by default | DATA-04 for every linked company. This is the only mode that picks up a restated older period. |
| `REGULAR` | Other runs | Read only the company list. Compare each `latestRatings` and profile with stored values. **Same:** record `UNCHANGED`, no further calls. **Different or never synced:** read details, then read periods and ratings from page 1, continuing while a page contains an unseen ID. |

**Sync run results**

| Company outcome | Meaning |
|---|---|
| `UPDATED` | Data written |
| `UNCHANGED` | Read or compared, nothing to write |
| `UNLINKED` | No link, nothing written, shown in the review list |
| `IGNORED` | Excluded by an admin |
| `FAILED` | Read, validation or write failed, nothing written. A reason is recorded. |

| Run status | Rule |
|---|---|
| `SUCCEEDED` | Finished with no `FAILED` company. Unlinked companies don't lower this status, but their count is shown. |
| `PARTIALLY_SUCCEEDED` | At least one company `FAILED` and at least one did not |
| `FAILED` | Stopped by an auth, permission or schema block, or every processed company failed |
| `CANCELLED` | Cancelled by an admin |
| `SKIPPED` | Scheduled run not started because the connection isn't `ACTIVE`, or setup is blocked |
| `SKIPPED_OVERLAP` | Scheduled run not started because a run is active |

### 7.7 Scheduled sync (SCHED)

| ID | Requirement |
|---|---|
| SCHED-01 | Use the existing Z2 platform scheduler and job queue, not browser timers or a RapidRatings-specific scheduler. |
| SCHED-02 | Settings: on or off, frequency (daily, weekly, monthly), time, time zone, scope, mode and retry policy. The options must match the platform (Q10). Show the next and last run. |
| SCHED-03 | Recommended default: daily `REGULAR` plus weekly `FULL`. A schedule that is left off runs nothing. |
| SCHED-04 | One schedule occurrence creates at most one run. The idempotency key is schedule ID + occurrence time. |
| SCHED-05 | A run still active at the next occurrence gives `SKIPPED_OVERLAP`. |
| SCHED-06 | Runs missed during downtime run once at the next start, not once per missed occurrence. |
| SCHED-07 | When the connection isn't `ACTIVE`, or a blocking schema change is pending, record `SKIPPED` with the reason and notify the admin. |
| SCHED-08 | Time zone and daylight saving follow the configured zone. |
| SCHED-09 | Run-level retries apply to temporary failures only. Auth, permission, schema and mapping failures are never retried. |

### 7.8 History (HIST)

Sync history, schedule versions and change history are separate records.

| ID | Requirement |
|---|---|
| HIST-01 | Each run stores the fields in the first table below, and one result per company: `runId`, `rrid`, `companyId`, `outcome`, `reason`. |
| HIST-02 | The UI filters runs by date, trigger and status, and shows per-company results. |
| HIST-03 | Each schedule change creates a new version, with who, when and the before-and-after values. |
| HIST-04 | Change history records: enable and disable, environment change, credential rotation (event only), connection test result, schema found, approved or rejected, mapping changes and activation, company link changes, Country fills, schedule changes, manual sync start, cancel and retry. |
| HIST-05 | Change history is append-only. Users and admins can't edit or delete it. |
| HIST-06 | No history record may contain a secret, token, Basic header or raw response. Credential changes appear as `"***changed***"`. |
| HIST-07 | Retention periods follow the Z2 platform standard (Q11). |

| Sync run field | Description |
|---|---|
| `runId`, `tenantId`, `integrationId`, `environment` | Identity |
| `triggerType`, `requestedBy` | `MANUAL`, `SCHEDULED` or `RETRY`, and the user or schedule |
| `status` | From Section 7.6, plus `QUEUED` and `RUNNING` while active |
| `startedAt`, `completedAt` | UTC |
| `scope`, `mode` | Companies in scope, `FULL` or `REGULAR` |
| `schemaVersion`, `mappingVersion`, `dataModelVersion` | Versions used |
| `counts` | `listed`, `linked`, `unlinked`, `ignored`, `updated`, `unchanged`, `failed` |
| `unmappedFields`, `warnings`, `apiCalls`, `retries` | Run diagnostics |
| `retryOf`, `correlationId` | Links to the original run and to logs |
| `errorSummary` | Plain language, sanitized |

| Change history field | Description |
|---|---|
| `changeId`, `tenantId`, `integrationId` | Identity |
| `actor` | User ID, or `SYSTEM` |
| `action`, `entity` | For example `MAPPING_ACTIVATED` on mapping version 3 |
| `before`, `after` | Changed values where safe |
| `timestamp`, `runId`, `comment` | Context |

---

## 8. Company Details

Adding properties doesn't display them. Layout entries are required **[Z2 model]**.

| ID | Requirement |
|---|---|
| CD-01 | Add the four sections below to the `details` tab, directly after `kharon_risks`, in the same format as the `kharon` and `kharon_risks` sections **[Z2 model]**. |
| CD-02 | Tables show the newest row first. The layout must work with 25 visible period columns and 27 visible rating columns. How the table handles that is Q9. |
| CD-03 | `null` shows as blank or the platform's standard empty marker, never `0`. `--` and `---` show as received, because they are provider values. |
| CD-04 | Show "(%)" on PD and term PD labels. PD values are already in percent (`1.5853` means 1.59 %). Show currency and data scale next to the Periods table. |
| CD-05 | `company_url`, `financials_url`, `periods_url` and `ratings_url` must not appear in the layout, and `period_url` and `rating_url` must not appear as table columns, because they all need a Bearer token. If a table binding always shows every child, set `hidden: true` on the row URLs (Q9). |
| CD-06 | Show only report links that have a value, and label them "Opens in RapidRatings. Sign-in may be required." |
| CD-07 | Show no custom score, band or colour for ratings. Values are shown as provided. |
| CD-08 | Sections must not appear for tenants that haven't enabled RapidRatings. If the layout can't be conditional, this is a blocker for Q8. |
| CD-09 | A linked Company with no data yet shows "Not synced yet", not zeros. |
| CD-10 | Integration admins get **Sync this company** on Company Details, which uses the same job as Sync now. |

| Section id | Title | Layout | Contents |
|---|---|---|---|
| `rapidratings` | RapidRatings | Fields, 2 columns | RRID, source company name, source country, ticker, exchange, sector, address, current period, FHR, CHS, PD (%), current financial date, sector average FHR and CHS, currency, data scale, data confidentiality, report links, last synced |
| `rapidratings_latest_ratings` | RapidRatings › Latest Ratings | Table | `rapidratings.latest_ratings` |
| `rapidratings_periods` | RapidRatings › Periods | Table | `rapidratings.periods` |
| `rapidratings_ratings` | RapidRatings › Ratings | Table | `rapidratings.ratings` |

Example table section, following `kharon_risks`:

```json
{
  "id": "rapidratings_periods",
  "title": "RapidRatings › Periods",
  "preset": "single",
  "columns": [[{
    "id": "rapidratings_periods-table",
    "kind": "fields",
    "columns": 1,
    "bindings": [{
      "id": "rapidratings_periods",
      "path": [],
      "label": "RapidRatings › Periods",
      "target": { "kind": "objectProperty", "dbName": "rapidratings.periods" }
    }]
  }]]
}
```

---

## 9. Z2 Backend API

Align route names and authorization with Z2 conventions. All routes are tenant-scoped.

| Method | Route (under `/api/integrations`) | Purpose | Permission |
|---|---|---|---|
| `GET` | `/` | Catalogue with tenant status | Read |
| `GET` | `/rapidratings` | Status, environment, scope, last run. Never a secret or token. | Read |
| `PATCH` | `/rapidratings` | Enable, disable, set scope | Admin |
| `PUT` | `/rapidratings/connection` | Save environment and credentials (secret write-only) | Admin |
| `POST` | `/rapidratings/connection/test` | Test connection | Admin |
| `GET` | `/rapidratings/portfolios` | Portfolios for scope | Read |
| `GET` | `/rapidratings/source-companies?name=&ticker=` | Provider company search for scope and linking | Admin |
| `POST` | `/rapidratings/schema/refresh` | Run schema sync | Admin |
| `GET` | `/rapidratings/schema/versions`, `/{id}`, `/{id}/diff` | Versions and diff | Read |
| `POST` | `/rapidratings/schema/versions/{id}/approve`, `/reject` | Review | Admin |
| `GET`, `PUT` | `/rapidratings/mappings`, `/{version}` | Read and edit a draft | Read, Admin |
| `POST` | `/rapidratings/mappings/{version}/validate`, `/activate` | Validate and activate | Admin |
| `GET` | `/rapidratings/company-links?status=` | Review list | Read |
| `PUT`, `DELETE` | `/rapidratings/company-links/{rrid}` | Link or ignore, and unlink | Admin |
| `POST` | `/rapidratings/company-links/bulk` | Bulk confirm | Admin |
| `POST` | `/rapidratings/sync-runs` | Sync now (scope and mode in body) | Admin |
| `GET` | `/rapidratings/sync-runs`, `/{runId}`, `/{runId}/companies` | History, progress and results | Read |
| `POST` | `/rapidratings/sync-runs/{runId}/cancel`, `/retry` | Cancel and retry | Admin |
| `GET`, `PUT` | `/rapidratings/schedule` | Read and update | Read, Admin |
| `GET` | `/rapidratings/schedule/versions`, `/rapidratings/change-history` | History | Read |

Company Details reads the stored `rapidratings` data through the existing Company APIs.

**Configuration entities:** `IntegrationDefinition`, `TenantIntegration` (with `credentialRef`), `SourceSchemaVersion`, `MappingVersion`, `CompanyLink`, `SyncSchedule`, `SyncRun`, `SyncRunCompanyResult`, `IntegrationChangeHistory`. Every entity is keyed by tenant.

---

## 10. Error Handling

| Situation | Behaviour | Message to the admin |
|---|---|---|
| Token request 400 or 401 | `AUTH_FAILED`, no retry, notify the admin | "RapidRatings rejected the credentials. Check the Client ID and Secret." |
| Data call 403 | Renew and retry once. Second 403: `PERMISSION_FAILED`, stop the run. | "The credentials are valid, but this account can't read the data." |
| Data call 401 | Defect (missing header). Fail the run. | "Internal request error. Contact support." |
| 400 on a data call | Defect, or invalid search input. Report the parameter. No retry. | Parameter name and reason |
| 404 on a selected portfolio | Fail the run | "Portfolio {id} no longer exists. Update the scope." |
| 404 on a company in scope | Company `FAILED` | "Company {rrid} isn't available to this account." |
| 404 or 500 past the last page | Prevented by following `next`. Never retried. | Treated as a defect if it occurs |
| Other 500 | Company `FAILED`, no retry | "RapidRatings returned an error." |
| 429 | API-05 | "Rate limited by RapidRatings." |
| Timeout, network error, 502, 503, 504 | API-04. Then company `FAILED`. | "RapidRatings didn't respond." |
| `next` URL on an unexpected host | Fail the company, don't send the token | "Unexpected response from RapidRatings." |
| Validation failure | Company `FAILED`, nothing written | Field and rule |
| Unknown field | Continue, count in `unmappedFields` | "New source fields found. Review the schema." |
| Breaking schema change | Block sync | "Review schema version {n} and update the mapping." |
| Write version conflict | Retry once, then `FAILED` | "The Company changed during sync. It will be retried on the next run." |
| Secret manager or queue unavailable | Run `FAILED`, retryable | "Z2 service unavailable." |

---

## 11. Non-Functional Requirements

| ID | Requirement |
|---|---|
| SEC-01 | Secrets are in the secret manager only, and tokens are in server memory only. |
| SEC-02 | Secrets, tokens, Basic headers and raw responses must not appear in UI responses, logs, traces, errors or history. Automated tests must search logs for test values. |
| SEC-03 | All provider traffic uses HTTPS. Sandbox and production credentials are separate. |
| SEC-04 | Everything is isolated by tenant: configuration, tokens, schema, mapping, links, runs, history and stored data (see Q12). |
| SEC-05 | Stored link values must be `https` URLs before they can display as links. Otherwise they show as plain text. |
| PERF-01 | Plan on 533 calls for a `FULL` sync of 96 companies **[Sandbox]**, or about 5.5 calls per company plus one per 20 companies listed. Sandbox limit: 30,000,000 **[Sandbox]**. The production limit is Q1. |
| PERF-02 | A sync must run as a background job, so that no HTTP request stays open for its duration. |
| PERF-03 | List history grows over time. Confirm the platform's limits for rows in a `PROPS_ARRAY` (Q9). |
| OBS-01 | Provide metrics per tenant: runs by status, API calls, retries, 429s and duration. |
| OBS-02 | Alert when a tenant enters `AUTH_FAILED`, a scheduled run fails, two runs in a row are partial, a schema change blocks sync, or `last_synced_at` is older than two schedule intervals. |
| OBS-03 | Logs carry `correlationId`, `runId`, tenant and RRID. |

---

## 12. Delivery Plan

| Phase | Deliverable | Depends on | Done when |
|---|---|---|---|
| 1. Foundation | Catalogue entry, connection page, secret storage, token handling, states, change history | Q13 | T-01 to T-05 pass |
| 2. Adapter and schema | API client, schema sync, schema sub-tab | 1 | T-06 to T-11 pass |
| 3. DataModel and layout | `rapidratings` group, Company Details sections | Q8, Q9 | T-12, T-13 pass |
| 4. Mapping and linking | Default mapping, mapping and links sub-tabs | 2, 3, Q7 | T-14 to T-20 pass |
| 5. Data sync | Sync pipeline, Sync now, progress, history | 4, Q8 (write API), Q10 (queue) | T-21 to T-33 pass |
| 6. Schedule | Schedule sub-tab and scheduler integration | 5, Q10 | T-34 to T-37 pass |
| 7. Pilot | One tenant behind a feature switch, production credentials | All, Q1, Q11 | T-38, T-39 pass |

---

## 13. Rollout and Rollback

**Rollout:** sandbox, then one production pilot tenant behind a tenant-level feature switch, then general availability.

**Rollback:** disabling the integration stops all syncs immediately, and data stays on the Company records. Removing the Company Details sections hides it. `company_name` and all non-empty values are never overwritten. Country fills are recorded in change history, so they can be reviewed and cleared if needed.

---

## 14. Acceptance Tests

Sandbox company data is as of 2026-11-01. If the sandbox changes, use an equivalent company.

| Test | Scenario | Expected | Covers |
|---|---|---|---|
| T-01 | Valid credentials | `ACTIVE`. No secret or token in any response, log or history. | AUTH-07, SEC-02 |
| T-02 | Wrong secret | `AUTH_FAILED`, exactly one token request | AUTH-06 |
| T-03 | Token with under 60 s left | New token before the next call. Concurrent workers cause one request. | AUTH-03, AUTH-04 |
| T-04 | Data call returns 403, then 403 | One renewal, one retry, then `PERMISSION_FAILED` | AUTH-05 |
| T-05 | Rotate credentials | Token cleared, `CONFIGURED`, history entry with no values | AUTH-08, HIST-06 |
| T-06 | Read a 3-page list | All rows, and only `next` URLs called | API-01 |
| T-07 | `next` points to another host | Request fails, and no token is sent | API-03 |
| T-08 | 429 with `Retry-After: 2`, and with `3464` | First: waits and retries, max 2. Second: stops, `RATE_LIMITED`. | API-05 |
| T-09 | First schema sync | Version 1 `PENDING_REVIEW`, data sync blocked | SCH-07 |
| T-10 | Sample with `reportLinks` variants and `null` in a numeric field | Optional and nullable fields, no type change | SCH-03 to SCH-05 |
| T-11 | Fixture adds a field; fixture removes a mapped field | First: review, sync continues. Second: sync blocked. | SCH-06 |
| T-12 | Compare DataModel and layout before and after | Only `rapidratings` additions | DM-01 |
| T-13 | Tenant without RapidRatings opens Company Details | No RapidRatings sections | CD-08, DM-08 |
| T-14 | Default mapping validates | Activates. Every Appendix B field is mapped, `reports[]` is excluded. | MAP-01, MAP-08 |
| T-15 | Two sources on one target; unknown target path | Activation refused, error names the field | MAP-08, MAP-09 |
| T-16 | Linked Company whose name and country differ | Values unchanged, provider values in `source_*` | MAP-04, MAP-05 |
| T-17 | Empty Country and `USA` | `basic_info.country = US`, recorded in history | MAP-05 |
| T-18 | Country code with no LOV match | Nothing written, warning | MAP-05 |
| T-19 | Unlinked company | In review list, nothing written, no Company created | LINK-05 |
| T-20 | Unlink a company | Confirmation, group cleared, recorded. Next sync writes nothing to it. | LINK-07, LINK-10 |
| T-21 | Sync `KawjbGJ` (Rite Aid) | 5 latest ratings, 27 periods, 27 ratings. `2020YE`: `interest_received` is `null`, `net_profit_after_tax` is `-469.219`. `current_fhr` is 35. | DATA-04, MAP-03 |
| T-22 | Sync `YA5vG2D` | In row `2017Q3`, `latest_ratings.simulated_fhr` is `null` and `ratings.simulated_fhr` is 58. `leverage` is `--`. Both `fin_services` and `company_profile` links stored. | DATA-06, MAP-03 |
| T-23 | Sync `KawjkGJ` | Rating `2014Q3` stored with no period. Company doesn't fail. | DATA-07 |
| T-24 | Sync `ALnGkmJ` | Sector `---`, sector averages `null`, no report links shown | MAP-03, CD-06 |
| T-25 | Re-run the same sync | No duplicates, `UNCHANGED` | DATA-10, DATA-11 |
| T-26 | `KawjbGJ` and `XAZPzdJ` both have `2019YE` | Never mixed | DATA-07 |
| T-27 | Rating `href` holds a different RRID | Company `FAILED`, nothing written | DATA-05, DATA-08 |
| T-28 | Periods read fails after ratings succeed | Nothing written for that company, others continue, `PARTIALLY_SUCCEEDED` | DATA-05 |
| T-29 | A stored period is missing from a response | Stored row kept | DATA-10 |
| T-30 | Selected portfolios overlap | Each RRID processed once | DATA-03 |
| T-31 | `REGULAR` run, nothing changed | List calls only, `UNCHANGED` | Sync modes |
| T-32 | `REGULAR` run, new rating at the top | Detail read, new rows added, existing rows kept | Sync modes |
| T-33 | Cancel and retry | Cancel keeps finished writes. Retry covers only failed companies, with `retryOf` set. | DATA-15, DATA-16 |
| T-34 | Same scope through Sync now and schedule | Same stored result | ARCH-03 |
| T-35 | Scheduled run during an active run | `SKIPPED_OVERLAP` | SCHED-05 |
| T-36 | Scheduled run while `AUTH_FAILED` | `SKIPPED`, no provider call, admin notified | SCHED-07 |
| T-37 | Edit schedule and mapping | New versions, history shows who and the before-and-after values | HIST-03, HIST-04 |
| T-38 | Tenant B requests tenant A's data, runs or tokens | Denied | SEC-04 |
| T-39 | Pilot | One week of scheduled syncs with no manual fixes | Section 13 |

---

## 15. Open Items

Each item has a working default so it doesn't block unrelated work. "Needed by" refers to the phases in Section 12.

| # | Question | Owner | Needed by | Default until answered |
|---|---|---|---|---|
| Q1 | Does production match the sandbox: URLs, paging, page sizes, errors, token lifetime and rate limits? Is there a changed-since option? Do RRIDs match across environments? | RapidRatings | Phase 7 | Behave as in the sandbox; treat environments as separate |
| Q2 | Should the integration fill an empty `basic_info.country`? The earlier documents disagree. | Product | Phase 4 | Fill when empty, as in the most recent document (MAP-05) |
| Q3 | Why do `latestRatings` and `ratings` differ for financial-sector companies, and which is authoritative? | RapidRatings | Phase 5 | Store both as received; `current_*` uses `latestRatings[0]` |
| Q4 | Can Z2 users open report links without their own RapidRatings portal account? `GET /companies/{rrid}/reports/` returns JSON with `reportContent`, `mimeType`, `md5sum` and `reportType`. Should a later release serve reports from Z2? | RapidRatings, Product | Phase 3 | Show portal links with the sign-in note |
| Q5 | Is `/companies/{rrid}/financials/` ever populated? It's empty for all 96 companies. | RapidRatings | Phase 2 | Don't call it |
| Q6 | What are the types and meanings of `externalSourceIds`, `industryClassification*`, and the private-company values of `dataConfidential`? The sandbox has only `PUB` and an empty private portfolio. | RapidRatings | Phase 4 | `STRING_ARRAY` and `STRING`; no display restrictions |
| Q7 | Which Z2 property holds an ID comparable to `externalSourceId`? Can customers populate that field at RapidRatings? | Product | Phase 4 | Link by admin confirmation only |
| Q8 | Which `dataModelType` layer owns the group (`kharon` uses `CORE`/2278)? Can sections be conditional per tenant? Does `canUpload: false` block UI edits? What is the link template for a URL value? Which API updates a Company from a backend job, with locking? | Z2 platform | Phase 3 | None. **Blocks Phases 3 and 5.** |
| Q9 | How does the Company Details table handle 25 to 27 columns, sorting and many rows? Can a binding choose its columns? What is the row limit for `PROPS_ARRAY`? How are `null` values shown? Do row-level filters work? | Z2 UI | Phase 3 | Standard table, row filters off |
| Q10 | What are the scheduler and queue APIs, the supported frequencies and the time-zone rules? | Z2 platform | Phase 5 for the queue, Phase 6 for the scheduler | None. **Blocks Phases 5 and 6.** |
| Q11 | Are there contractual limits on storing rating history and report links? What retention applies to history? | Legal, Product | Before pilot | Keep all stored rows; no raw responses |
| Q12 | Are Company records separate per tenant? `dataKind` is `CUSTOMER_DATA` **[Z2 model]**, which suggests yes. If records are shared, RapidRatings data can't be stored on them as designed. | Z2 platform | Phase 3 | Assume separate per tenant |
| Q13 | Does each tenant use its own RapidRatings credentials, or does Z2 hold one account? | Product, RapidRatings | Phase 1 | Per tenant |
| Q14 | Who owns failed runs caused by outages? | Support | Before pilot | Platform support |

---

## Appendix A: RapidRatings API Reference

Full details are in `docs/api/`.

**Base URL (sandbox):** `https://rridata.sandbox.rrilabs.co/v2.0`

| Resource | Method | Path | Paging |
|---|---|---|---|
| Token | `POST` | `/oauth/token/` | None |
| Portfolios | `GET` | `/portfolios/` | `next` |
| Companies | `GET` | `/companies/` | `next`, 20 per page, fixed |
| Company details | `GET` | `/companies/{rrid}/` | None |
| Periods | `GET` | `/companies/{rrid}/periods/` | `next`, 10 per page, fixed, newest first |
| Ratings | `GET` | `/companies/{rrid}/ratings/` | `next`, 10 per page, fixed, newest first |

List response: `{"count": n, "previous": url|null, "next": url|null, "results": [...]}`

**Token request**

| Item | Value |
|---|---|
| Client authentication | `Authorization: Basic base64(client_id:client_secret)` |
| Body | `grant_type=client_credentials`, form-urlencoded |
| Response | `access_token`, `expires_in` (1,800 in the sandbox), `token_type` (`Bearer`), `scope` |
| Refresh token | None |
| Use | `Authorization: Bearer <access_token>` |
| Errors | 400: unsupported grant type. 401: wrong credentials, missing header **or** missing `grant_type`. 405: `GET` used. |

**Behaviour**

| Topic | Behaviour |
|---|---|
| Trailing slash | Required. Without it the API returns 404. |
| Accepted parameters | Companies: `page`, `portfolio`, `name` (3+ characters, partial, case-insensitive), `ticker` (exact, case-sensitive), `externalSourceId`. Others: `page` only. Any other parameter returns 400. |
| Page past the end | Companies: 404. Portfolios, periods and ratings: 500. |
| Missing `Authorization` header | 401 |
| Invalid or expired token | 403 on this host |
| Changed-since filter | None |
| List vs detail | Detail adds `reports` and `sectorAverages`. `latestRatings` matched between list and detail for all 96 companies. |
| Rate-limit headers | `X-Ratelimit-Limit`, `X-Ratelimit-Remaining`, `X-Ratelimit-Reset`, `Retry-After` |

---

## Appendix B: Field Mapping

**Rules for all tables:** any field can be `null`, which is stored as `null`. Period amounts can be negative and are in the company's `currency` at its `dataScale`. `probabilityDefault` and `termPd` values are percentages.

**Type conversion**

| Source | Z2 type | Rule |
|---|---|---|
| string | `STRING` | As received |
| URI | `STRING` | As received; report links display as links |
| integer | `INTEGER` | |
| number | `DOUBLE` | Keep sign and precision |
| date | `DATE` | No time-zone shift |
| `termPd` object | 9 × `DOUBLE` | `termPd.yearN` to `term_pd_year_N` |
| array of objects | `PROPS_ARRAY` | One row per element |
| `externalSourceIds` | `STRING_ARRAY` | Q6 |

### Company fields (`rapidratings.*`)

| Source | Target | Label | Type | Note |
|---|---|---|---|---|
| `id` | `rrid` | RRID | STRING | Identity key; filterable |
| `name` | `source_company_name` | Source Company Name | STRING | Suggests link candidates only |
| `country` | `source_country` | Source Country | STRING | ISO-3; also used by MAP-05 |
| `ticker` | `ticker` | Ticker | STRING | Filterable |
| `exchange` | `exchange` | Exchange | STRING | |
| `sector` | `sector` | Sector | STRING | Filterable; can be `---` |
| `address` | `address` | Source Address | TEXT | Empty for all 96 sandbox companies |
| `href` | `company_url` | Company URL | STRING | Technical; not in layout |
| `financials` | `financials_url` | Financials URL | STRING | Technical; not in layout |
| `periods` | `periods_url` | Periods URL | STRING | Technical; not in layout |
| `ratings` | `ratings_url` | Ratings URL | STRING | Technical; not in layout |
| `currency` | `currency` | Currency | STRING | |
| `dataScale` | `data_scale` | Data Scale | STRING | Applies to all period amounts |
| `dataConfidential` | `data_confidentiality` | Data Confidentiality | STRING | Not converted to boolean |
| `industryClassificationSystem` | `industry_classification_system` | Industry Classification System | STRING | Q6 |
| `industryClassificationCode` | `industry_classification_code` | Industry Classification Code | STRING | Q6 |
| `externalSourceId` | `external_source_id` | External Source ID | STRING | Linking input |
| `externalSourceIds` | `external_source_ids` | External Source IDs | STRING_ARRAY | Q6 |
| `reportLinks.fhr` | `report_link_fhr` | FHR Report | STRING (link) | Optional |
| `reportLinks.financialDialog` | `report_link_financial_dialog` | Financial Dialogue Report | STRING (link) | Optional |
| `reportLinks.peerBenchmark` | `report_link_peer_benchmark` | Peer Benchmark Report | STRING (link) | Optional |
| `reportLinks.fin_services` | `report_link_fin_services` | Financial Services Report | STRING (link) | Optional; 3 companies. Confirm the label. |
| `reportLinks.companyProfilePage` | `report_link_company_profile` | Company Profile Page | STRING (link) | Optional; 6 companies |
| `sectorAverages.fhr` | `sector_average_fhr` | Sector Average FHR | DOUBLE | Detail only |
| `sectorAverages.chs` | `sector_average_chs` | Sector Average CHS | DOUBLE | Detail only |
| `latestRatings[0].id` | `current_rating_id` | Current Rating Period | STRING | Filterable |
| `latestRatings[0].fhr` | `current_fhr` | Current FHR | INTEGER | Filterable |
| `latestRatings[0].chs` | `current_chs` | Current CHS | INTEGER | Filterable |
| `latestRatings[0].probabilityDefault` | `current_probability_default` | Current PD (%) | DOUBLE | |
| `latestRatings[0].financialDate` | `current_financial_date` | Current Financial Date | DATE | |
| (sync time) | `last_synced_at` | Last Synced | TIMESTAMP | |
| `reports[]` | Not mapped | | | Detail only. Download needs API auth, and report access is out of scope. |

### Period fields (`rapidratings.periods`)

| Source | Target | Label | Type |
|---|---|---|---|
| `id` | `period_id` | Period ID | STRING, required (row key) |
| `eqyYear` | `eqy_year` | EQY Year | INTEGER |
| `period` | `period` | Period | STRING |
| `financialDate` | `financial_date` | Financial Date | DATE |
| `currency` | `currency` | Currency | STRING |
| `href` | `period_url` | Period URL | STRING |
| `salesRevenue` | `sales_revenue` | Sales Revenue | DOUBLE |
| `earningsBeforeInterestAndTax` | `earnings_before_interest_and_tax` | EBIT | DOUBLE |
| `interestExpense` | `interest_expense` | Interest Expense | DOUBLE |
| `interestReceived` | `interest_received` | Interest Received | DOUBLE |
| `companyTaxExpense` | `company_tax_expense` | Company Tax Expense | DOUBLE |
| `abnormalItems` | `abnormal_items` | Abnormal Items | DOUBLE |
| `netProfitAfterTax` | `net_profit_after_tax` | Net Profit After Tax | DOUBLE |
| `bankCashBalances` | `bank_cash_balances` | Bank Cash Balances | DOUBLE |
| `totalCurrentAssets` | `total_current_assets` | Total Current Assets | DOUBLE |
| `fixedAssets` | `fixed_assets` | Fixed Assets | DOUBLE |
| `financialAssets` | `financial_assets` | Financial Assets | DOUBLE |
| `totalAssets` | `total_assets` | Total Assets | DOUBLE |
| `debitOwnedWithinOneYear` | `debit_owned_within_one_year` | Debt Due Within One Year | DOUBLE |
| `totalCurrentLiabilities` | `total_current_liabilities` | Total Current Liabilities | DOUBLE |
| `termLoans` | `term_loans` | Term Loans | DOUBLE |
| `totalLiabilities` | `total_liabilities` | Total Liabilities | DOUBLE |
| `totalShareholderEquity` | `total_shareholder_equity` | Total Shareholder Equity | DOUBLE |
| `cashFromOperationsFromCashFlow` | `cash_from_operations_from_cash_flow` | Cash From Operations | DOUBLE |
| `netInvestingCashFlow` | `net_investing_cash_flow` | Net Investing Cash Flow | DOUBLE |
| `netFundingCashFlow` | `net_funding_cash_flow` | Net Funding Cash Flow | DOUBLE |

`debitOwnedWithinOneYear` is the provider's own spelling.

### Rating fields (`rapidratings.ratings` and `rapidratings.latest_ratings`)

Both lists use the same fields. Remember that they are filled from different sources (DATA-06).

| Source | Target | Label | Type |
|---|---|---|---|
| `id` | `rating_id` | Rating Period ID | STRING, required (row key) |
| `eqyYear` | `eqy_year` | EQY Year | INTEGER |
| `period` | `period` | Period | STRING |
| `financialDate` | `financial_date` | Financial Date | DATE |
| `reportingPeriod` | `reporting_period` | Reporting Period | DATE |
| `href` | `rating_url` | Rating URL | STRING |
| `fhr` | `fhr` | FHR | INTEGER |
| `chs` | `chs` | CHS | INTEGER |
| `probabilityDefault` | `probability_default` | Probability of Default (%) | DOUBLE |
| `termPd.year2` | `term_pd_year_2` | Term PD Year 2 (%) | DOUBLE |
| `termPd.year3` | `term_pd_year_3` | Term PD Year 3 (%) | DOUBLE |
| `termPd.year4` | `term_pd_year_4` | Term PD Year 4 (%) | DOUBLE |
| `termPd.year5` | `term_pd_year_5` | Term PD Year 5 (%) | DOUBLE |
| `termPd.year6` | `term_pd_year_6` | Term PD Year 6 (%) | DOUBLE |
| `termPd.year7` | `term_pd_year_7` | Term PD Year 7 (%) | DOUBLE |
| `termPd.year8` | `term_pd_year_8` | Term PD Year 8 (%) | DOUBLE |
| `termPd.year9` | `term_pd_year_9` | Term PD Year 9 (%) | DOUBLE |
| `termPd.year10` | `term_pd_year_10` | Term PD Year 10 (%) | DOUBLE |
| `delta` | `delta` | FHR Delta | INTEGER |
| `simulatedFhr` | `simulated_fhr` | Simulated FHR | INTEGER |
| `simulatedFhrDelta` | `simulated_fhr_delta` | Simulated FHR Delta | INTEGER |
| `operatingProfitability` | `operating_profitability` | Operating Profitability | INTEGER |
| `netProfitability` | `net_profitability` | Net Profitability | INTEGER |
| `capitalStructureEfficiency` | `capital_structure_efficiency` | Capital Structure Efficiency | INTEGER |
| `costStructureEfficiency` | `cost_structure_efficiency` | Cost Structure Efficiency | INTEGER |
| `leverage` | `leverage` | Leverage | STRING |
| `liquidity` | `liquidity` | Liquidity | STRING |
| `earningsPerformance` | `earnings_performance` | Earnings Performance | STRING |

---

## Appendix C: Sandbox Measurements (2026-11-01)

All 546 read-only requests in the main sweep returned 200.

| Measure | Result |
|---|---|
| Companies | 96 unique RRIDs over 5 pages; all 7 characters |
| Portfolios | US COVERAGE 96, z2Data Sandbox 96, Fortune 500 37, INTERNATIONAL COVERAGE 0, z2Data Sandbox-Private 0 |
| Period rows and rating rows | 1,613 and 1,614 |
| Periods per company | 2 to 27 |
| `latestRatings` length | 5 for 69 companies, 3 for 26, 2 for 1 |
| Calls for a full read | 5 list pages + 528 per-company calls = 533 |
| Duplicate IDs, `href` RRID mismatches, ID format exceptions | 0 |
| Same-ID period and rating with different `financialDate` | 0 |
| Rows out of newest-first order | 0 |
| `latestRatings` IDs not matching the start of `ratings` | 0 |
| Rating without a period | 1 (`KawjkGJ`, `2014Q3`) |
| Missing quarters | 234 across 60 companies |
| Companies with repeated `reportingPeriod` | 68 |
| `latestRatings` rows differing from `ratings` | 45 rows in 9 companies (Banking, Financial Diversified, `---` sector) |
| Rating nulls of 1,614 | `chs` 190, `probabilityDefault` 147, `simulatedFhrDelta` 56, `delta` 53, `fhr` 0 |
| Period nulls of 1,613 | `netFundingCashFlow` and `netInvestingCashFlow` 994, `cashFromOperationsFromCashFlow` 688, `interestReceived` 591, `financialAssets` 297, `abnormalItems` 126, `interestExpense` 108, `companyTaxExpense` 75, `fixedAssets` 45, `termLoans`, `bankCashBalances` and `debitOwnedWithinOneYear` 1 each |
| `sectorAverages` nulls of 96 | `chs` 9, `fhr` 3 |
| `leverage`, `liquidity`, `earningsPerformance` values | `Strong`, `Average`, `Weak`, and `--` (147 rows each, all with `null` PD) |
| Single-value fields across all 96 | `country` USA, `exchange` US, `currency` USD, `dataScale` millions, `dataConfidential` PUB |
| Always empty | `address` `""`, `industryClassification*` `null`, `externalSourceId` `null`, `externalSourceIds` `[]`, `financials` list `count: 0` |

**Not exercised in this sweep:** 403 and 429 responses, token expiry, and production. The 403 behaviour comes from `docs/api/`. 429 handling is based on the documented headers and has not been seen in practice.
