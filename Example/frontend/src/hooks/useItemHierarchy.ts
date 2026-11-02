import { type QueryClient, keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  GRAPH_MAX_DEPTH,
  anchorQueryOf,
  fetchHierarchyAnchors,
  fetchHierarchyConfig,
  fetchHierarchyGraph,
  scopeOf,
} from '../services/hierarchy';
import { buildHierarchy } from '../services/hierarchyTree';
import type { HierarchyPageResponse } from '../types/hierarchy';
import type { PageResponse } from '../types/page';

export const hierarchyKeys = {
  all: ['item-hierarchy'] as const,
  config: () => [...hierarchyKeys.all, 'config'] as const,
  anchors: (scope: unknown, page: number, size: number) => [...hierarchyKeys.all, 'anchors', { scope, page, size }] as const,
  list: (page: number, size: number) => [...hierarchyKeys.all, 'list', { page, size }] as const,
  products: (page: number, size: number) => [...hierarchyKeys.all, 'products', { page, size }] as const,
};

const STALE_MS = 60_000;

/**
 * Stages 1–2: the Enterprise table config, then one page of the anchor query. Each stage
 * is cached under its own key, so the Item Hierarchy page and the dashboard tile share
 * the config and no stage is re-requested while fresh.
 */
async function loadAnchors(client: QueryClient, page: number, size: number) {
  const config = await client.fetchQuery({ queryKey: hierarchyKeys.config(), queryFn: fetchHierarchyConfig, staleTime: STALE_MS });
  const query = anchorQueryOf(config);
  return client.fetchQuery({
    queryKey: hierarchyKeys.anchors(scopeOf(query), page, size),
    queryFn: () => fetchHierarchyAnchors(query, page, size),
    staleTime: STALE_MS,
  });
}

/** Stages 1–3 for one page: config → anchors → one batched graph match, built into trees in memory. */
async function loadHierarchy(client: QueryClient, page: number, size: number): Promise<HierarchyPageResponse> {
  const anchors = await loadAnchors(client, page, size);
  // The graph request waits for valid anchor ids and is skipped when there are none.
  const ids = anchors.objects.map((anchor) => anchor.id);
  const roots = ids.length ? buildHierarchy(anchors.objects, await fetchHierarchyGraph(ids), GRAPH_MAX_DEPTH) : [];
  return { ...anchors, objects: roots, count: roots.length };
}

/**
 * One page of top-level products with their whole BOM and sourced-part trees, read
 * directly from the Enterprise APIs; `page` is zero-based. Expanding rows needs no
 * further requests.
 */
export function useItemHierarchy(page: number, size: number) {
  const client = useQueryClient();
  return useQuery({
    queryKey: hierarchyKeys.list(page, size),
    queryFn: () => loadHierarchy(client, page, size),
    placeholderData: keepPreviousData,
    staleTime: STALE_MS,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}

/** Top-level product item numbers only (config + anchors, no graph traversal); `page` is zero-based. */
export function useHierarchyProducts(page: number, size: number) {
  const client = useQueryClient();
  return useQuery({
    queryKey: hierarchyKeys.products(page, size),
    queryFn: async (): Promise<PageResponse<string>> => {
      const anchors = await loadAnchors(client, page, size);
      const numbers = anchors.objects.map((anchor) => anchor.itemNumber ?? String(anchor.id));
      return { ...anchors, objects: numbers, count: numbers.length };
    },
    staleTime: STALE_MS,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}
