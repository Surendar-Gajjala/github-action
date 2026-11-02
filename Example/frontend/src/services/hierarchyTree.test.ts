import type { HierarchyItemNode, HierarchyNode } from '../types/hierarchy';
import { buildHierarchy, type GraphEdge, type GraphNode } from './hierarchyTree';

const item = (id: number, itemNumber: string, extra: Record<string, unknown> = {}): GraphNode => ({
  id, type: 'item', properties: { item_number: itemNumber, ...extra },
});
const part = (id: number, partNumber: string): GraphNode => ({ id, type: 'part', properties: { part_number: partNumber, manufacturer: 'M' } });
const bom = (from: number, to: number, qty: unknown = '1'): GraphEdge => ({
  type: 'item_bom', fromNodeId: from, toNodeId: to, properties: { 'qty_per_bom.qty': qty },
});
const src = (from: number, to: number): GraphEdge => ({ type: 'item_sources', fromNodeId: from, toNodeId: to, properties: { 'qty_per_bom.qty': null } });

const asItem = (node: HierarchyNode) => node as HierarchyItemNode;
const numbers = (nodes: HierarchyNode[]) => nodes.map((n) => (n.kind === 'ITEM' ? n.item.itemNumber : n.part.partNumber));

describe('buildHierarchy', () => {
  it('builds roots in anchor order from edges, with qty and path keys', () => {
    const roots = buildHierarchy(
      [{ id: 20, itemNumber: 'P-2' }, { id: 10, itemNumber: 'P-1' }],
      { nodes: [item(10, 'P-1'), item(20, 'P-2'), item(30, 'C-1'), part(40, 'X-1')], edges: [bom(10, 30, '2.50'), src(30, 40)] },
      6,
    );

    expect(numbers(roots)).toEqual(['P-2', 'P-1']);
    const child = asItem(roots[1].children[0]);
    expect(child).toMatchObject({ key: '10/30', id: 30, kind: 'ITEM', qty: 2.5 });
    expect(child.children[0]).toMatchObject({ key: '10/30/40', kind: 'PART', qty: undefined, part: { partNumber: 'X-1' } });
    expect(roots[0].qty).toBeUndefined();
  });

  it('repeats a shared child under every parent with its own qty', () => {
    const roots = buildHierarchy(
      [{ id: 1, itemNumber: 'A' }, { id: 2, itemNumber: 'B' }],
      { nodes: [item(1, 'A'), item(2, 'B'), item(3, 'S')], edges: [bom(1, 3, '1'), bom(2, 3, '4')] },
      6,
    );

    expect(roots[0].children[0]).toMatchObject({ key: '1/3', qty: 1 });
    expect(roots[1].children[0]).toMatchObject({ key: '2/3', qty: 4 });
  });

  it('orders children items first, then parts, each by number', () => {
    const [root] = buildHierarchy(
      [{ id: 1, itemNumber: 'A' }],
      { nodes: [item(1, 'A'), item(2, 'Z'), item(3, 'B'), part(4, 'P-9'), part(5, 'P-1')], edges: [src(1, 4), bom(1, 2), src(1, 5), bom(1, 3)] },
      6,
    );

    expect(numbers(root.children)).toEqual(['B', 'Z', 'P-1', 'P-9']);
  });

  it('cuts cycles, ignores dangling and repeated edges, and skips missing anchors', () => {
    const roots = buildHierarchy(
      [{ id: 99, itemNumber: 'GONE' }, { id: 1, itemNumber: 'A' }, { id: 1, itemNumber: 'A' }],
      {
        nodes: [item(1, 'A'), item(2, 'B'), { id: 7, type: 'document', properties: {} }],
        edges: [bom(1, 2), bom(1, 2), bom(2, 1), bom(1, 404), bom(1, 7)],
      },
      6,
    );

    expect(numbers(roots)).toEqual(['A']);
    expect(roots[0].children).toHaveLength(1);
    expect(roots[0].children[0].children).toEqual([]); // 2 → 1 would close a cycle
  });

  it('stops at the requested depth', () => {
    const nodes = [1, 2, 3, 4].map((id) => item(id, `I-${id}`));
    const [root] = buildHierarchy([{ id: 1, itemNumber: 'I-1' }], { nodes, edges: [bom(1, 2), bom(2, 3), bom(3, 4)] }, 2);

    const level2 = root.children[0].children[0];
    expect(level2.id).toBe(3);
    expect(level2.children).toEqual([]);
  });

  it('maps item properties with readable labels and tolerates sparse nodes', () => {
    const [root] = buildHierarchy(
      [{ id: 1, itemNumber: 'A' }],
      {
        nodes: [item(1, 'A', { item_status: 'PRODN_APPROVED', 'environmental_compliance.eu_rohs': 'YES', is_product: 'true', structure_role: 'bom' })],
        edges: [],
      },
      6,
    );

    expect(asItem(root).item).toMatchObject({
      itemNumber: 'A',
      itemStatus: 'Production Approved',
      euRohs: 'Yes',
      isProduct: true,
      structureRole: 'BOM',
      revision: null,
      description: null,
    });
  });
});
