# Item Integration (backend)

Read-only, database-free Spring Boot service. It calls the hosted data platform with a Bearer token, maps each response into its own models and serves Items, Parts, Sites, the Item Hierarchy and item details to the [frontend](../frontend). Each local request makes one hosted call (two for an Item Hierarchy page); nothing is persisted. Requirements and project guide: [CLAUDE.md](../CLAUDE.md).

## Run

Requires JDK 17+ and Maven. From this `backend/` folder, either put the values in `backend/.env` (git-ignored, see [.env.example](.env.example)) or export them:

```bash
export EXTERNAL_API_URL="https://<host>/api/v1/platform/core/query-config-v2/execute"
export EXTERNAL_API_GRAPH_URL="https://<host>/api/v1/platform/core/graph/match/execute"
export EXTERNAL_API_OBJECT_URL="https://<host>/api/v1/platform/core/objects/partial"
export EXTERNAL_API_TOKEN="<bearer token>"      # secret: never commit, expires after ~24h
mvn spring-boot:run
```

Call `GET http://localhost:8080/api/items`, or run the [frontend](../frontend) for the UI. Restart after changing `.env`. `.env` is read from the working directory, so start the app from `backend/`.

`EXTERNAL_API_GRAPH_URL` (required) is the hosted graph-match endpoint (Item Hierarchy and item Sources); `EXTERNAL_API_OBJECT_URL` (required) is the single-object endpoint (item Overview). Both use the same token.

Optional: `EXTERNAL_API_CONNECT_TIMEOUT` (default `5s`), `EXTERNAL_API_READ_TIMEOUT` (default `30s`), `SERVER_PORT` (default `8080`). The app will not start if the URL or token is missing.

## Layout

Every entity uses the same `EXTERNAL_API_URL` and token; only the query-config payload differs (`src/main/resources/external-queries/<entity>-query.json`, registered in `external.ExternalQuery`).

| Layer | Shared | Items (`GET /api/items`) | Parts (`GET /api/parts`) | Sites (`GET /api/sites`) |
|---|---|---|---|---|
| Configuration | `config.ExternalApiProperties`, `config.ExternalApiClientConfig` | | | |
| External client | `external.ExternalApiClient`, `external.ExternalQuery` | `item-query.json` | `part-query.json` | `site-query.json` |
| External DTOs | `external.dto.ExternalQueryResponse` | `ExternalItemRecord` | `ExternalPartRecord` | `ExternalSiteRecord` |
| Mapper | `common.mapping.ExternalValues` | `item.mapper.ItemMapper` | `part.mapper.PartMapper` | `site.mapper.SiteMapper` |
| Model / DTO | `common.dto.PageResponse` | `item.model.Item`, `item.dto.ItemDto` | `part.model.Part`, `part.dto.PartDto` | `site.model.Site`, `site.dto.SiteDto` |
| Service | | `item.service.ItemService` | `part.service.PartService` | `site.service.SiteService` |
| Controller | | `item.controller.ItemController` | `part.controller.PartController` | `site.controller.SiteController` |
| Errors | `common.error.GlobalExceptionHandler` | | | |

## Item Hierarchy (`GET /api/item-hierarchy?page=&size=`)

One page of top-level products (`item_bom` roots), each with its full tree: BOM child items (`item_bom`, many-to-many, `qty` on the edge) and sourced parts (`item_sources`, one-to-many). Each page makes **two** hosted calls: the anchors query (`item-hierarchy-anchors.json`, which holds the scope id) and one graph traversal for all anchors (`item-hierarchy-graph.json`, max depth 6). `hierarchy.mapper.HierarchyTreeBuilder` turns the flat nodes/edges into nested `HierarchyNodeDto`s; a shared item is repeated under every parent with its own qty, and each occurrence has a unique `key` (path of node ids). Status-like codes become readable labels via `common.mapping.CodeLabels` (unknown codes pass through). Responses are gzip-compressed.

`GET /api/item-hierarchy/products?page=&size=` returns only the top-level product numbers (one anchors query, no traversal); the dashboard uses it for the BOM count.

## Item details (`GET /api/items/{id}/overview | sources`)

One hosted call per tab, made only when that tab is opened:

| Tab | Endpoint | Hosted call |
|---|---|---|
| Overview | `/api/items/{id}/overview` | `GET ${EXTERNAL_API_OBJECT_URL}/{id}`: all properties, grouped into sections by `itemdetail.mapper.ItemOverviewMapper` (risk fields carry their reason) |
| Sources | `/api/items/{id}/sources` | graph match `item-sources-graph.json` (item_sources, descendants, anchored on the id), returned as `PartDto`s |

The hosted object endpoint answers an unknown id with an empty 200; that, or an id belonging to another entity, returns 404 `ITEM_NOT_FOUND`.

To add another entity: add `<entity>-query.json` and an `ExternalQuery` constant, then an external record DTO, model, DTO, mapper, service and controller following Parts.

## Response

`GET /api/items?page=0&size=25` returns one page. `page` is zero-based (default `0`); `size` is 1–100 (default `25`). Each request makes exactly one paged call to the hosted server; `page`/`size` in `EXTERNAL_API_URL` are overridden.

```json
{
  "page": 0, "size": 25, "totalObjects": 5631, "totalPages": 226, "hasMore": true,
  "count": 1,
  "objects": [{
    "id": 42, "itemNumber": "ITM-001", "description": "Widget", "revision": "B",
    "businessUnit": "Hardware", "isProduct": true, "structureRole": "BOM",
    "odmName": ["Acme"], "odmActive": [true], "availabilityRisk": "NOT ASSESSED",
    "usedInProducts": "P1, P2", "productFamiliesImpacted": 3, "itemStatusName": "Released"
  }]
}
```

## Upstream errors

| Hosted server | Local response |
|---|---|
| 401 / 403 | 502 `ITEM_SOURCE_ACCESS_DENIED` |
| 404 | 502 `ITEM_SOURCE_NOT_FOUND` |
| 429 | 429 `ITEM_SOURCE_RATE_LIMITED` (+ `Retry-After`) |
| 5xx / other 4xx | 502 `ITEM_SOURCE_ERROR` |
| Unreadable body | 502 `ITEM_SOURCE_INVALID_RESPONSE` |
| Timeout | 504 `ITEM_SOURCE_TIMEOUT` |
| Connection failure | 503 `ITEM_SOURCE_UNAVAILABLE` |

## Hosted data notes (confirmed against the real API)

- **Requests:** query-config and graph calls are `POST`s with the payload file as the body; the object call is a `GET`. Each payload's `select[].alias` (or graph `select` property) must match a `@JsonProperty` in its external record DTO.
- **Rows:** the hosted server returns object rows keyed by alias; positional rows named by `properties` are also supported.
- **ODM fields** arrive as JSON-encoded strings (`"[]"`, `"[\"true\"]"`) and are exposed as lists (`odmName: string[]`, `odmActive: boolean[]`).
- **Unknown object id:** the object endpoint answers with an empty 200, which the API turns into 404 `ITEM_NOT_FOUND`.
- **Expired token:** every hosted call returns 401, shown as 502 `ITEM_SOURCE_ACCESS_DENIED`.
