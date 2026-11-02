import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';

import type { GraphEdge, GraphNode } from '../services/hierarchyTree';
import { server } from '../test/server';
import { ItemHierarchyPage } from './ItemHierarchyPage';

const CONFIG = '/enterprise/core/data-configuration/table-configs/item-hierarchy';
const EXECUTE = '/enterprise/core/query-config-v2/execute';
const GRAPH = '/enterprise/core/graph/match/execute';

/** The real table config shape: no anchor query of its own (`queryConfigObject: null`). */
const TABLE_CONFIG = {
  id: 'item-hierarchy',
  columnProperties: [],
  statisticProperties: [{ path: 'item.item_lifecycle_status', label: 'Item / Item Lifecycle Status' }],
  queryConfigObject: null,
};

const itemNode = (id: number, itemNumber: string, extra: Record<string, unknown> = {}): GraphNode => ({
  id,
  type: 'item',
  alias: null,
  properties: { item_number: itemNumber, description: `Description of ${itemNumber}`, business_unit: 'NPO', item_status: 'PRODN_APPROVED', ...extra },
});
const partNode = (id: number, partNumber: string, manufacturer: string): GraphNode => ({
  id, type: 'part', alias: 'leaf', properties: { part_number: partNumber, manufacturer },
});
const bom = (id: number, from: number, to: number, qty: string): GraphEdge => ({
  id, type: 'item_bom', label: 'Child Items', fromNodeId: from, toNodeId: to, properties: { 'qty_per_bom.qty': qty },
});
const sources = (id: number, from: number, to: number): GraphEdge => ({
  id, type: 'item_sources', label: 'Sources', fromNodeId: from, toNodeId: to, properties: { 'qty_per_bom.qty': null },
});

/** 903239 → E70293-013 (qty 1) → [K33608-001 (qty 0.1), part FTLX8574D3BCV-IT]; 903240 shares K33608-001 (qty 2). */
const NODES: GraphNode[] = [
  itemNode(1, '903239'),
  itemNode(2, 'E70293-013'),
  itemNode(3, 'K33608-001'),
  itemNode(4, '903240'),
  partNode(9, 'FTLX8574D3BCV-IT', 'FINISAR CORPORATION'),
];
const EDGES: GraphEdge[] = [bom(11, 1, 2, '1'), bom(12, 2, 3, '0.1'), sources(13, 2, 9), bom(14, 4, 3, '2')];
const ANCHORS = [{ anchor: '903239', item__id: 1 }, { anchor: '903240', item__id: 4 }];

interface Calls {
  config: number;
  anchors: string[];
  anchorBodies: Record<string, unknown>[];
  lookups: Record<string, unknown>[];
  graphBodies: { traversal: Record<string, unknown> }[];
}

interface ServeOptions {
  anchors?: Record<string, unknown>[];
  totalElements?: number;
  nodes?: GraphNode[];
  edges?: GraphEdge[];
  config?: Record<string, unknown>;
  lookupRows?: Record<string, unknown>[];
}

function serve({
  anchors = ANCHORS,
  totalElements,
  nodes = NODES,
  edges = EDGES,
  config = TABLE_CONFIG,
  lookupRows = [],
}: ServeOptions = {}): Calls {
  const total = totalElements ?? anchors.length;
  const calls: Calls = { config: 0, anchors: [], anchorBodies: [], lookups: [], graphBodies: [] };
  server.use(
    http.get(CONFIG, () => {
      calls.config += 1;
      return HttpResponse.json(config);
    }),
    http.post(EXECUTE, async ({ request }) => {
      const body = (await request.json()) as { where?: { and?: { operator?: string }[] } };
      if (body.where?.and?.[0]?.operator === 'in') {
        calls.lookups.push(body as Record<string, unknown>);
        return HttpResponse.json({ results: lookupRows, totalElements: lookupRows.length, hasMore: false });
      }
      const params = new URL(request.url).searchParams;
      const page = Number(params.get('page') ?? 0);
      const size = Number(params.get('size') ?? 25);
      calls.anchors.push(`${page}/${size}`);
      calls.anchorBodies.push(body as Record<string, unknown>);
      return HttpResponse.json({
        properties: ['anchor', 'item__id'],
        results: anchors,
        pageNumber: page,
        pageSize: size,
        totalElements: total,
        exactTotal: true,
        hasMore: (page + 1) * size < total,
      });
    }),
    http.post(GRAPH, async ({ request }) => {
      calls.graphBodies.push((await request.json()) as { traversal: Record<string, unknown> });
      return HttpResponse.json({ nodes, edges, zql: 'MATCH ...', queryInfo: { executionTimeMs: 5 } });
    }),
  );
  return calls;
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ItemHierarchyPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const bodyRows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1);
const rowOf = (text: string) => screen.getByText(text).closest('tr')!;

describe('ItemHierarchyPage', () => {
  it('reads config, then anchors, then one graph match with the anchor item ids', async () => {
    const calls = serve();

    renderPage();

    expect(await screen.findByText('903239')).toBeInTheDocument();
    expect(calls.config).toBe(1);
    expect(calls.anchors).toEqual(['0/25']);
    expect(calls.anchorBodies[0]).toMatchObject({
      distinct: true,
      where: { and: [{ topOfRelationship: 'item_bom' }, { property: 'item.scope.id', value: 516 }] },
    });
    expect(calls.graphBodies).toHaveLength(1);
    expect(calls.graphBodies[0].traversal).toMatchObject({
      anchorProperty: 'id',
      anchorValues: ['1', '4'],
      relationshipTypes: ['item_bom', 'item_sources'],
      direction: 'DESCENDANTS',
      edgeProperties: ['qty_per_bom.qty'],
      maxDepth: 6,
    });
    expect(calls.lookups).toHaveLength(0);
  });

  it('shows products collapsed with the table-config columns', async () => {
    serve({ totalElements: 292 });

    renderPage();

    expect(await screen.findByText('903239')).toBeInTheDocument();
    expect(bodyRows()).toHaveLength(2);
    expect(screen.queryByText('E70293-013')).not.toBeInTheDocument();
    for (const header of ['Item Number', 'Description', 'Revision', 'Qty', 'Item Type', 'Item Status', 'Make / Buy',
      'Design Group', 'Restricted', 'Exemption Status', 'Project', 'Source Notes', 'EU RoHS', 'PNR Lock',
      'Business Unit', 'Product', 'Structure', 'Part Number', 'Manufacturer']) {
      expect(screen.getByRole('columnheader', { name: header })).toBeInTheDocument();
    }
    expect(within(rowOf('903239')).getByText('Production Approved')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Item Hierarchy' })).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent('1–2 of 292');
  });

  it('expands BOM items with qty and lists sourced parts in the item row', async () => {
    serve();
    renderPage();
    await screen.findByText('903239');

    await userEvent.click(screen.getByRole('button', { name: 'Expand 903239' }));
    expect(screen.getByRole('button', { name: 'Collapse 903239' })).toHaveAttribute('aria-expanded', 'true');
    expect(within(rowOf('E70293-013')).getByText('1')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Expand E70293-013' }));
    expect(within(rowOf('K33608-001')).getByText('0.1')).toBeInTheDocument();
    // The sourced part sits in E70293-013's own row, not in a separate tree row.
    const itemRow = rowOf('E70293-013');
    expect(within(itemRow).getByText('FTLX8574D3BCV-IT')).toBeInTheDocument();
    expect(within(itemRow).getByText('FINISAR CORPORATION')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /FTLX8574D3BCV-IT/ })).not.toBeInTheDocument();
    expect(bodyRows()).toHaveLength(4);

    await userEvent.click(screen.getByRole('button', { name: 'Collapse 903239' }));
    expect(bodyRows()).toHaveLength(2);
  });

  it('links item numbers in the tree to their details page', async () => {
    serve();
    renderPage();

    expect(await screen.findByRole('link', { name: '903239' })).toHaveAttribute('href', '/items/1');
  });

  it('links part numbers in the tree to their part details page', async () => {
    serve({ anchors: [ANCHORS[0]], nodes: [itemNode(1, '903239'), NODES[4]], edges: [sources(13, 1, 9)] });
    renderPage();

    expect(await screen.findByRole('link', { name: 'FTLX8574D3BCV-IT' })).toHaveAttribute('href', '/parts/9');
  });

  it('shows a shared item under every product that uses it', async () => {
    serve();
    renderPage();
    await screen.findByText('903239');

    await userEvent.click(screen.getByRole('button', { name: 'Expand all' }));

    expect(screen.getAllByText('K33608-001')).toHaveLength(2);
    expect(bodyRows()).toHaveLength(5);

    await userEvent.click(screen.getByRole('button', { name: 'Collapse all' }));
    expect(bodyRows()).toHaveLength(2);
  });

  it('stacks several sourced parts inside one item row', async () => {
    serve({
      anchors: [ANCHORS[0]],
      nodes: [
        itemNode(1, '903239'),
        itemNode(5, 'E65689-006'),
        partNode(7, 'FTLX8574D3BCV-I3', 'FINISAR CORPORATION'),
        partNode(8, 'FTLX8574D3BCV-I5', 'FINISAR CORPORATION'),
        partNode(9, 'FTLX8574D3BCV-IT', 'FINISAR CORPORATION'),
      ],
      edges: [bom(21, 1, 5, '1'), sources(22, 1, 9), sources(23, 1, 7), sources(24, 1, 8)],
    });
    renderPage();
    await screen.findByText('903239');

    // One item with three parts: three lines, the item cells spanning all of them.
    expect(bodyRows()).toHaveLength(3);
    expect(screen.getByText('903239').closest('td')).toHaveAttribute('rowspan', '3');
    expect(screen.getByText('FTLX8574D3BCV-I3')).toBeInTheDocument();
    expect(screen.getByText('FTLX8574D3BCV-IT')).toBeInTheDocument();
    expect(screen.getAllByText('FINISAR CORPORATION')).toHaveLength(3);
    // Parts are not expandable; only the BOM child item is.
    expect(screen.getByRole('button', { name: 'Expand 903239' })).toBeInTheDocument();
  });

  it('search keeps matching products and opens the path to the match', async () => {
    serve();
    renderPage();
    await screen.findByText('903239');

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search products' }), 'finisar');

    await waitFor(() => expect(screen.getByText('FTLX8574D3BCV-IT')).toBeInTheDocument());
    expect(screen.queryByText('903240')).not.toBeInTheDocument();
  });

  it('requests the next anchor page and reuses the cached config', async () => {
    const calls = serve({ totalElements: 292 });
    renderPage();
    await screen.findByText('903239');

    expect(screen.queryByRole('spinbutton', { name: 'Go to page' })).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Rows' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Page 2' }));

    await waitFor(() => expect(calls.anchors.at(-1)).toBe('1/25'));
    await waitFor(() => expect(calls.graphBodies).toHaveLength(2));
    expect(calls.config).toBe(1);
  });

  it('resolves item-number-only anchors to ids before the graph match', async () => {
    const configuredQuery = {
      from: { entity: 'item', alias: 'item' },
      select: [{ selectType: 'property', property: 'item.item_number', alias: 'anchor' }],
      distinct: true,
      where: { and: [{ topOfRelationship: 'item_bom' }, { filterType: 'property', property: 'item.scope.id', operator: '=', value: 516 }] },
    };
    const calls = serve({
      config: { ...TABLE_CONFIG, queryConfigObject: configuredQuery },
      anchors: [{ anchor: '903239' }, { anchor: '903240' }],
      lookupRows: [{ item_number: '903240', item__id: 4 }, { item_number: '903239', item__id: 1 }],
    });

    renderPage();

    expect(await screen.findByText('903240')).toBeInTheDocument();
    expect(calls.anchorBodies[0]).toMatchObject({ select: configuredQuery.select });
    expect(calls.lookups).toHaveLength(1);
    expect(calls.lookups[0]).toMatchObject({
      where: { and: [{ operator: 'in', value: ['903239', '903240'] }, { property: 'item.scope.id', value: 516 }] },
    });
    // Anchor order is kept, and only ids reach anchorProperty "id".
    expect(calls.graphBodies[0].traversal.anchorValues).toEqual(['1', '4']);
    expect(bodyRows().map((row) => within(row).getAllByRole('link')[0].textContent)).toEqual(['903239', '903240']);
  });

  it('shows which stage failed when the graph match fails', async () => {
    serve();
    server.use(http.post(GRAPH, () => HttpResponse.json({ message: 'timeout' }, { status: 504 })));

    renderPage();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('Item hierarchy could not be loaded');
    expect(alert).toHaveTextContent('Could not load the BOM hierarchy: The Enterprise API is unavailable (504).');
  });

  it('shows an authentication error when the config request is rejected', async () => {
    const calls = serve();
    server.use(http.get(CONFIG, () => HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })));

    renderPage();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('Could not load the Item Hierarchy configuration');
    expect(alert).toHaveTextContent('token is missing or expired (401)');
    expect(calls.anchors).toHaveLength(0);
    expect(calls.graphBodies).toHaveLength(0);
  });

  it('reports a malformed graph response instead of rendering it', async () => {
    serve();
    server.use(http.post(GRAPH, () => HttpResponse.json({ nodes: 'nope' })));

    renderPage();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('Unexpected BOM hierarchy response');
  });

  it('shows the empty state and skips the graph match when there are no anchors', async () => {
    const calls = serve({ anchors: [] });

    renderPage();

    expect(await screen.findByText('No products available.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Expand all' })).toBeDisabled();
    expect(calls.graphBodies).toHaveLength(0);
  });
});
