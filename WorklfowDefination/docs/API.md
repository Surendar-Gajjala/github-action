# REST API

Base URL: `http://localhost:8085` (or `http://localhost:5173` through the Vite proxy).

- **Authentication.** HTTP Basic with the demo users `author`, `engineer`, `manager` and `admin` (password = username).
- **Format.** All bodies are JSON. The API returns DTOs, never JPA entities.
- **Lists.** List endpoints accept `page` (0-based), `size` (1–200), `sort=property,asc|desc` and `q` (search). Unknown
  sort properties are rejected with 400.

## Errors

Every error uses the same structure:

```json
{
  "timestamp": "2026-10-27T04:55:43.090Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/items",
  "fieldErrors": [{ "field": "itemNumber", "message": "must not be blank" }],
  "details": ["only for 422 business-rule errors, e.g. BPMN validation messages"]
}
```

| Status | When |
|---|---|
| 400 | Bean validation, malformed JSON, bad paging or sort, rejection without reason |
| 401 | Not authenticated. No `WWW-Authenticate` header, so browsers don't show a login popup. |
| 403 | Role not allowed; task for another candidate group; self-approval blocked |
| 404 | Unknown id or key |
| 409 | Duplicate number or key, stale `version`, item in review, workflow already started, task already decided |
| 422 | Business rule violation: transition not permitted, no published workflow, invalid BPMN on publish (`details`), nothing to submit |
| 503 | Workflow engine unreachable or rejected the command (the operation is rolled back) |

## Items

| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/items?q=&status=&page=&size=&sort=` | any | Paged list. Sort: `itemNumber,name,revision,lifecycleStatus,updatedAt,createdAt` |
| GET | `/api/items/{id}` | any | Details: parts, current change request, last approved revision, latest workflow |
| POST | `/api/items` | AUTHOR, ADMIN | Create (starts as `DRAFT`, revision A, opens a change request) |
| PUT | `/api/items/{id}` | AUTHOR, ADMIN | Update name, description and (optionally) `partIds`. Requires `version`. |
| GET | `/api/items/{id}/parts` | any | Parts of the item |
| PUT | `/api/items/{id}/parts` | AUTHOR, ADMIN | Replace the part set: `{ partIds, version }` |
| POST | `/api/items/{id}/submit-for-approval` | AUTHOR, ADMIN | Start the workflow for the open change request: `{ workflowKey?, version? }` → 202 |
| GET | `/api/items/{id}/workflow-history` | any | Audit trail of the item |
| GET | `/api/items/{id}/revisions` | any | Approved revision snapshots (newest first) |
| GET | `/api/items/{id}/changes` | any | Net diff of the working revision vs the last approved revision |

```bash
# Create ITEM-1001 with three parts (ids from GET /api/parts)
curl -u author:author -H 'Content-Type: application/json' -X POST localhost:8085/api/items \
  -d '{"itemNumber":"ITEM-1001","name":"Hydraulic Pump Assembly","description":"Gear pump","partIds":[1,2,3]}'

# Remove PART-103 (id 3) from an approved item → CHANGE_PENDING, revision B
curl -u author:author -H 'Content-Type: application/json' -X PUT localhost:8085/api/items/2/parts \
  -d '{"partIds":[1,2],"version":5}'

# Submit for approval (automatic workflow selection)
curl -u author:author -H 'Content-Type: application/json' -X POST localhost:8085/api/items/2/submit-for-approval -d '{}'
```

Example response of `submit-for-approval`:

```json
{
  "id": "6f0c2b8e-…", "engine": "DEV_ADAPTER", "engineInstanceKey": 3,
  "definitionKey": "ITEM_LIFECYCLE_APPROVAL", "definitionVersion": 1,
  "businessKey": "ITEM-2-CR-2", "itemId": 2, "itemNumber": "ITEM-1001", "changeRequestId": 2,
  "currentStage": "Starting", "status": "RUNNING", "managerApprovalRequired": true,
  "startedBy": "author", "startedAt": "2026-10-27T10:25:01"
}
```

## Parts

| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/parts?q=&partType=&page=&size=&sort=` | any | Paged list with `associatedItemsCount` |
| GET | `/api/parts/{id}` | any | Details plus associated items |
| POST | `/api/parts` | AUTHOR, ADMIN | Create: `{ partNumber, name, description?, partType?, itemIds? }` → `{ part, affectedItems }` |
| PUT | `/api/parts/{id}` | AUTHOR, ADMIN | Update attributes (doesn't affect item lifecycles). Requires `version`. |
| GET | `/api/parts/{id}/items` | any | Items that use the part |
| PUT | `/api/parts/{id}/items` | AUTHOR, ADMIN | Set the items using this part: `{ itemIds }`. Only items whose membership changes get a change request. |

```bash
curl -u author:author -H 'Content-Type: application/json' -X PUT localhost:8085/api/parts/6/items -d '{"itemIds":[2,4]}'
```

## Workflow definitions

| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/workflows/definitions` | any | Definitions with latest and published version |
| GET | `/api/workflows/definitions/{key}` | any | Definition, current version (draft, else published) with BPMN XML and rules, and all versions |
| POST | `/api/workflows/definitions` | ADMIN | Create (draft v1; BPMN optional, defaults to a template) |
| PUT | `/api/workflows/definitions/{key}` | ADMIN | Save the draft: `{ bpmnXml, rules, name?, description?, type? }`. Creates a new draft version if the latest is published. |
| POST | `/api/workflows/definitions/{key}/publish` | ADMIN | Validate, deploy to the engine, mark the version PUBLISHED |
| POST | `/api/workflows/definitions/{key}/activate` · `/deactivate` | ADMIN | Allow or stop new instances |
| GET | `/api/workflows/definitions/{key}/versions` | any | Version history |
| GET | `/api/workflows/definitions/{key}/versions/{n}` | any | One version with BPMN XML and rules |
| POST | `/api/workflows/definitions/validate` | ADMIN | `{ bpmnXml, key }` → `{ valid, errors[], warnings[] }` |
| GET | `/api/workflows/definitions/workers` | any | Registered worker job types |

```bash
curl -u admin:admin -X POST localhost:8085/api/workflows/definitions/ITEM_LIFECYCLE_APPROVAL/publish

curl -u admin:admin -H 'Content-Type: application/json' -X POST localhost:8085/api/workflows/definitions \
  -d '{"key":"ITEM_FAST_TRACK","name":"Fast track","type":"ITEM_LIFECYCLE",
       "rules":{"managerApproval":"NEVER","partChangeThreshold":0,"requiredRoles":["ENGINEERING"],"dueInDays":1,"preventSelfApproval":true}}'
```

## Workflow instances and tasks

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/workflows/instances` | AUTHOR, ADMIN | `{ itemId, workflowKey?, version? }`. Same as submit-for-approval. |
| GET | `/api/workflows/instances?activeOnly=&status=&definitionKey=&q=` | any | Paged instances |
| GET | `/api/workflows/instances/{id}` | any | Instance, its tasks and its history |
| GET | `/api/workflows/instances/{id}/history` | any | Execution audit trail |
| POST | `/api/workflows/instances/{id}/retry` | ADMIN | Resolve the incident and retry the failed job |
| GET | `/api/workflows/tasks?status=OPEN&mine=true&q=` | any | Approval inbox. `mine=true` limits it to the caller's roles. |
| GET | `/api/workflows/tasks/{taskId}/changes` | any | Item and parts changes under review |
| POST | `/api/workflows/tasks/{taskId}/approve` | ENGINEERING, MANAGER (must match the task's candidate group) | `{ comment? }` |
| POST | `/api/workflows/tasks/{taskId}/reject` | ENGINEERING, MANAGER (must match) | `{ reason }`, required, 5–2000 characters |

```bash
curl -u engineer:engineer 'localhost:8085/api/workflows/tasks?status=OPEN'
curl -u engineer:engineer -H 'Content-Type: application/json' -X POST localhost:8085/api/workflows/tasks/1/approve -d '{"comment":"BOM checked"}'
curl -u manager:manager  -H 'Content-Type: application/json' -X POST localhost:8085/api/workflows/tasks/2/reject  -d '{"reason":"Housing still required for variant B"}'
```

## Lifecycle, history, misc

| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/workflows/lifecycle-transitions` | any | Configured transitions |
| PUT | `/api/workflows/lifecycle-transitions` | ADMIN | Upsert `{ fromStatus, toStatus, enabled, description? }` |
| DELETE | `/api/workflows/lifecycle-transitions/{id}` | ADMIN | Delete a non-system transition |
| GET | `/api/workflows/history?q=&action=` | any | Global audit trail (paged) |
| GET | `/api/workflows/engine` | any | Active engine: `DEV_ADAPTER` (not Camunda) or `CAMUNDA_8` and connectivity |
| GET | `/api/me` | any | Current user and roles |
