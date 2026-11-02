// The anchor and graph payloads (src/queries).
import anchorsQuery from '../queries/item-hierarchy-anchors.json';
import graphQuery from '../queries/item-hierarchy-graph.json';
import type { PageResponse } from '../types/page';
import { EnterpriseQueryError, enterpriseGet, enterprisePost, executeQuery } from './enterpriseQuery';
import { num, text } from './enterpriseValues';
import type { GraphEdge, GraphNode, GraphResponse, HierarchyAnchor } from './hierarchyTree';

const CONFIG_PATH = '/core/data-configuration/table-configs/item-hierarchy';
const GRAPH_PATH = '/core/graph/match/execute';

/** Anchors per graph request: one request for a default 25-row page; larger pages run batches in parallel. */
export const GRAPH_BATCH_SIZE = 25;
/** The graph response is large (≈2 MB for 12 products) and slow to stream (~40 s measured). */
const GRAPH_TIMEOUT_MS = 180_000;
/** Most item numbers resolved per lookup query (Enterprise page size). */
const RESOLVE_PAGE_SIZE = 100;

/** Traversal depth requested from Enterprise, also the tree builder's depth limit. */
export const GRAPH_MAX_DEPTH: number = graphQuery.traversal.maxDepth;

/** The parts of the Enterprise `item-hierarchy` table config the hierarchy uses. */
export interface HierarchyTableConfig {
  /** Configured anchor query, if the table config carries one (currently null upstream). */
  queryConfigObject: Record<string, unknown> | null;
}

type Json = Record<string, unknown>;
const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null && !Array.isArray(value);

/** Fetches the Enterprise Item Hierarchy table config (API 1). */
export async function fetchHierarchyConfig(): Promise<HierarchyTableConfig> {
  const data = await stage('the Item Hierarchy configuration', () => enterpriseGet<unknown>(CONFIG_PATH));
  if (!isObject(data)) {
    throw new EnterpriseQueryError('Unexpected Item Hierarchy configuration response from the Enterprise API.');
  }
  return { queryConfigObject: isObject(data.queryConfigObject) ? data.queryConfigObject : null };
}

/**
 * The anchor query to run: the table config's own query when it has one, otherwise the
 * existing `item-hierarchy-anchors.json` (top of item_bom, scoped, distinct, ascending).
 */
export function anchorQueryOf(config: HierarchyTableConfig): Json {
  return config.queryConfigObject ?? anchorsQuery;
}

/** The `item.scope.id` filter value of an anchor query, for query keys; null when unscoped. */
export function scopeOf(query: Json): unknown {
  return scopeFilters(query).map((filter) => filter.value)[0] ?? null;
}

/**
 * Executes one page of the anchor query (API 2) and returns the anchors with their
 * internal item ids. Rows that carry `item__id` (or `id`) are used directly; rows with
 * only an item number are resolved to ids with one extra lookup query. Anchors without a
 * valid id are dropped, so the graph never receives item numbers as `id` anchors.
 */
export async function fetchHierarchyAnchors(query: Json, page: number, size: number): Promise<PageResponse<HierarchyAnchor>> {
  const rows = await stage('the top-level items', () => executeQuery(query, page, size, (row) => row));
  const raw = rows.objects.map((row) => ({
    id: validId(row.item__id ?? row.id),
    itemNumber: text(row.anchor ?? row.item_number),
  }));

  const unresolved = raw.filter((a) => a.id === null && a.itemNumber !== null).map((a) => a.itemNumber!);
  const resolved = unresolved.length ? await resolveItemIds(unresolved, query) : new Map<string, number>();

  const seen = new Set<number>();
  const anchors: HierarchyAnchor[] = [];
  for (const a of raw) {
    const id = a.id ?? (a.itemNumber !== null ? (resolved.get(a.itemNumber) ?? null) : null);
    if (id !== null && !seen.has(id)) {
      seen.add(id);
      anchors.push({ id, itemNumber: a.itemNumber });
    }
  }
  return { ...rows, objects: anchors, count: anchors.length };
}

/**
 * Executes the graph match (API 3) for the given anchor item ids: the existing
 * `item-hierarchy-graph.json` with `anchorProperty: "id"` and the ids as `anchorValues`.
 * Batches of {@link GRAPH_BATCH_SIZE} run in parallel and are merged by node / edge id.
 */
export async function fetchHierarchyGraph(anchorIds: number[]): Promise<GraphResponse> {
  if (anchorIds.length === 0) return { nodes: [], edges: [] };
  const batches: number[][] = [];
  for (let i = 0; i < anchorIds.length; i += GRAPH_BATCH_SIZE) batches.push(anchorIds.slice(i, i + GRAPH_BATCH_SIZE));

  const responses = await Promise.all(
    batches.map((batch) =>
      stage('the BOM hierarchy', () => enterprisePost<unknown>(GRAPH_PATH, graphPayload(batch), { timeout: GRAPH_TIMEOUT_MS })),
    ),
  );
  return mergeGraphs(responses.map(toGraph));
}

function graphPayload(anchorIds: number[]): Json {
  return {
    ...graphQuery,
    traversal: { ...graphQuery.traversal, anchorProperty: 'id', anchorValues: anchorIds.map(String) },
  };
}

/** Validates one graph response: nodes and edges must be arrays; malformed entries are dropped. */
function toGraph(data: unknown): GraphResponse {
  if (!isObject(data) || !Array.isArray(data.nodes) || !Array.isArray(data.edges)) {
    throw new EnterpriseQueryError('Unexpected BOM hierarchy response from the Enterprise API.');
  }
  const nodes = data.nodes.filter(
    (n): n is GraphNode => isObject(n) && typeof n.id === 'number' && typeof n.type === 'string',
  );
  const edges = data.edges.filter(
    (e): e is GraphEdge =>
      isObject(e) && typeof e.fromNodeId === 'number' && typeof e.toNodeId === 'number' && typeof e.type === 'string',
  );
  return { nodes, edges };
}

/** Combines batch responses without losing node identity: nodes by id, edges by id (or endpoints). */
function mergeGraphs(graphs: GraphResponse[]): GraphResponse {
  if (graphs.length === 1) return graphs[0];
  const nodes = new Map<number, GraphNode>();
  const edges = new Map<string, GraphEdge>();
  for (const graph of graphs) {
    for (const node of graph.nodes) if (!nodes.has(node.id)) nodes.set(node.id, node);
    for (const edge of graph.edges) {
      const key = edge.id != null ? `id:${edge.id}` : `${edge.type}:${edge.fromNodeId}->${edge.toNodeId}`;
      if (!edges.has(key)) edges.set(key, edge);
    }
  }
  return { nodes: [...nodes.values()], edges: [...edges.values()] };
}

/**
 * Resolves item numbers to internal ids with one `item_number IN (...)` query, keeping
 * the anchor query's scope filter so numbers resolve within the same scope.
 */
async function resolveItemIds(itemNumbers: string[], anchorQuery: Json): Promise<Map<string, number>> {
  const lookup = {
    from: { entity: 'item', alias: 'item' },
    select: [
      { selectType: 'property', property: 'item.item_number', alias: 'item_number' },
      { selectType: 'property', property: 'item.id', alias: 'item__id' },
    ],
    where: {
      and: [
        { filterType: 'property', property: 'item.item_number', operator: 'in', value: itemNumbers },
        ...scopeFilters(anchorQuery),
      ],
    },
    orderBy: [{ property: 'item.id', direction: 'asc' }],
  };
  const rows = await stage('the top-level item ids', () => executeQuery(lookup, 0, RESOLVE_PAGE_SIZE, (row) => row));
  const ids = new Map<string, number>();
  for (const row of rows.objects) {
    const number = text(row.item_number);
    const id = validId(row.item__id);
    if (number !== null && id !== null && !ids.has(number)) ids.set(number, id);
  }
  return ids;
}

function scopeFilters(query: Json): Json[] {
  const where = isObject(query.where) ? query.where : null;
  const and = where && Array.isArray(where.and) ? where.and : [];
  return and.filter((f): f is Json => isObject(f) && f.property === 'item.scope.id');
}

/** A positive integer item id, else null. */
function validId(value: unknown): number | null {
  const id = num(value);
  return id !== null && Number.isSafeInteger(id) && id > 0 ? id : null;
}

/** Prefixes an Enterprise failure with the step that failed, keeping its safe message. */
async function stage<T>(what: string, request: () => Promise<T>): Promise<T> {
  try {
    return await request();
  } catch (error) {
    const message = error instanceof Error ? error.message : 'The Enterprise API request failed.';
    throw new EnterpriseQueryError(
      `Could not load ${what}: ${message}`,
      error instanceof EnterpriseQueryError ? error.status : null,
    );
  }
}
