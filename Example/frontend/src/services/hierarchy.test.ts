import { http, HttpResponse } from 'msw';

import { server } from '../test/server';
import { GRAPH_BATCH_SIZE, fetchHierarchyGraph } from './hierarchy';

const GRAPH = '/enterprise/core/graph/match/execute';

describe('fetchHierarchyGraph', () => {
  it('batches anchor ids and merges batches without duplicating shared nodes or edges', async () => {
    const batches: string[][] = [];
    server.use(
      http.post(GRAPH, async ({ request }) => {
        const body = (await request.json()) as { traversal: { anchorProperty: string; anchorValues: string[] } };
        expect(body.traversal.anchorProperty).toBe('id');
        batches.push(body.traversal.anchorValues);
        // Every batch returns the same shared sub-assembly (node 9000, edge 1).
        return HttpResponse.json({
          nodes: [
            ...body.traversal.anchorValues.map((id) => ({ id: Number(id), type: 'item', properties: { item_number: `P-${id}` } })),
            { id: 9000, type: 'item', properties: { item_number: 'SHARED' } },
          ],
          edges: [{ id: 1, type: 'item_bom', fromNodeId: 9000, toNodeId: 9000, properties: {} }],
        });
      }),
    );
    const ids = Array.from({ length: GRAPH_BATCH_SIZE + 5 }, (_, i) => i + 1);

    const graph = await fetchHierarchyGraph(ids);

    expect(batches.map((b) => b.length).sort((a, b) => b - a)).toEqual([GRAPH_BATCH_SIZE, 5]);
    expect(graph.nodes).toHaveLength(ids.length + 1);
    expect(graph.edges).toHaveLength(1);
  });

  it('makes no request for an empty id list', async () => {
    let called = false;
    server.use(http.post(GRAPH, () => {
      called = true;
      return HttpResponse.json({ nodes: [], edges: [] });
    }));

    expect(await fetchHierarchyGraph([])).toEqual({ nodes: [], edges: [] });
    expect(called).toBe(false);
  });
});
