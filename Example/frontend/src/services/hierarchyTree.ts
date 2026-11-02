import type { StructureRole } from '../types/item';
import type { HierarchyItem, HierarchyNode, HierarchyPart } from '../types/hierarchy';
import { bool, codeLabel, enumKey, num, text } from './enterpriseValues';

/** A graph-match node as Enterprise returns it; `type` is the entity ("item" or "part"). */
export interface GraphNode {
  id: number;
  type: string;
  alias?: string | null;
  properties?: Record<string, unknown> | null;
}

/** A graph-match edge; `type` is "item_bom" or "item_sources". */
export interface GraphEdge {
  id?: number | null;
  type: string;
  label?: string | null;
  fromNodeId: number;
  toNodeId: number;
  properties?: Record<string, unknown> | null;
}

export interface GraphResponse {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

/** One hierarchy root: the internal item id (graph anchor) and its item number. */
export interface HierarchyAnchor {
  id: number;
  itemNumber: string | null;
}

const ITEM_BOM = 'item_bom';
const QTY_PROPERTY = 'qty_per_bom.qty';

type Kind = 'ITEM' | 'PART';

/**
 * Turns a flat graph-match response into the nested Item Hierarchy. Edges are the only
 * source of parent-child relationships:
 * - Roots are the anchor items, in anchor order (matched by internal id); anchors missing
 *   from the graph are skipped, duplicates are kept once.
 * - item_bom is many-to-many, so a shared item (and its subtree) is repeated under every
 *   parent, each occurrence carrying its own edge's qty and a unique path `key`.
 * - item_sources parts are leaves.
 * - Children: BOM items first (by item number), then parts (by part number).
 * - Cycles are cut and depth is limited to `maxDepth` (the traversal depth requested).
 * - Edges whose endpoints are not in the response, and repeated edges (same type, from
 *   and to), are ignored.
 */
export function buildHierarchy(anchors: HierarchyAnchor[], graph: GraphResponse, maxDepth: number): HierarchyNode[] {
  const nodesById = new Map<number, GraphNode>();
  for (const node of graph.nodes) {
    if (node && typeof node.id === 'number' && kindOf(node) !== null && !nodesById.has(node.id)) {
      nodesById.set(node.id, node);
    }
  }

  const childEdges = new Map<number, GraphEdge[]>();
  const seenEdges = new Set<string>();
  for (const edge of graph.edges) {
    if (!edge || !nodesById.has(edge.fromNodeId) || !nodesById.has(edge.toNodeId)) continue;
    const identity = `${edge.type}:${edge.fromNodeId}->${edge.toNodeId}`;
    if (seenEdges.has(identity)) continue;
    seenEdges.add(identity);
    const list = childEdges.get(edge.fromNodeId);
    if (list) list.push(edge);
    else childEdges.set(edge.fromNodeId, [edge]);
  }

  const build = (node: GraphNode, qty: number | undefined, parentKey: string | null, depth: number, onPath: Set<number>): HierarchyNode => {
    const key = parentKey === null ? String(node.id) : `${parentKey}/${node.id}`;
    if (kindOf(node) === 'PART') {
      return { key, id: node.id, kind: 'PART', qty, part: toPart(node), children: [] };
    }

    const children: HierarchyNode[] = [];
    if (depth < maxDepth) {
      onPath.add(node.id);
      for (const edge of childEdges.get(node.id) ?? []) {
        const child = nodesById.get(edge.toNodeId)!;
        if (!onPath.has(child.id)) children.push(build(child, qtyOf(edge), key, depth + 1, onPath));
      }
      onPath.delete(node.id);
      children.sort(compareChildren);
    }
    return { key, id: node.id, kind: 'ITEM', qty, item: toItem(node), children };
  };

  const roots: HierarchyNode[] = [];
  const seenAnchors = new Set<number>();
  for (const anchor of anchors) {
    const root = nodesById.get(anchor.id);
    if (root && kindOf(root) === 'ITEM' && !seenAnchors.has(anchor.id)) {
      seenAnchors.add(anchor.id);
      roots.push(build(root, undefined, null, 0, new Set()));
    }
  }
  return roots;
}

function kindOf(node: GraphNode): Kind | null {
  switch (node.type?.toLowerCase()) {
    case 'item':
      return 'ITEM';
    case 'part':
      return 'PART';
    default:
      return null;
  }
}

/** Quantity on an item_bom edge; undefined for item_sources edges or when not a number. */
function qtyOf(edge: GraphEdge): number | undefined {
  if (edge.type?.toLowerCase() !== ITEM_BOM) return undefined;
  return num(edge.properties?.[QTY_PROPERTY]) ?? undefined;
}

/** Item node properties, keyed by the `select.item` names in item-hierarchy-graph.json. */
function toItem(node: GraphNode): HierarchyItem {
  const p = node.properties ?? {};
  return {
    itemNumber: text(p.item_number),
    description: text(p.description),
    revision: text(p.revision),
    itemType: text(p.item_type),
    itemStatus: codeLabel(p.item_status),
    makeBuy: text(p.make_buy),
    designGroup: text(p.design_group),
    restricted: bool(p.restricted),
    exemptionStatus: codeLabel(p.exemption_status),
    project: text(p.project),
    sourceNotes: text(p.source_notes),
    collection: codeLabel(p['environmental_compliance.collection']),
    collectionLock: codeLabel(p['environmental_compliance.collection_lock']),
    euRohs: codeLabel(p['environmental_compliance.eu_rohs']),
    euRohsLock: codeLabel(p['environmental_compliance.eu_rohs_lock']),
    ecExemptions: codeLabel(p['environmental_compliance.ec_exemptions']),
    pwbLeadHalogen: codeLabel(p['environmental_compliance.pwb_lead_halogen']),
    pwbLeadHalogenLock: codeLabel(p['environmental_compliance.pwb_lead_halogen_lock']),
    plasticLeadHalogen: codeLabel(p['environmental_compliance.plastic_lead_halogen']),
    plasticLeadHalogenLock: codeLabel(p['environmental_compliance.plastic_lead_halogen_lock']),
    pnr: codeLabel(p['environmental_compliance.pnr']),
    pnrLock: codeLabel(p['environmental_compliance.pnr_lock']),
    businessUnit: text(p.business_unit),
    isProduct: bool(p.is_product),
    structureRole: structureRole(p.structure_role),
  };
}

function toPart(node: GraphNode): HierarchyPart {
  const p = node.properties ?? {};
  return { partNumber: text(p.part_number), manufacturer: text(p.manufacturer) };
}

function structureRole(value: unknown): StructureRole | null {
  const key = enumKey(value);
  return key === 'ITEM' || key === 'BOM' ? key : null;
}

const sortLabel = (node: HierarchyNode) => (node.kind === 'ITEM' ? node.item.itemNumber : node.part.partNumber);

/** Items before parts, then by number; missing numbers last. */
function compareChildren(a: HierarchyNode, b: HierarchyNode): number {
  if (a.kind !== b.kind) return a.kind === 'ITEM' ? -1 : 1;
  const x = sortLabel(a);
  const y = sortLabel(b);
  if (x === y) return 0;
  if (x === null) return 1;
  if (y === null) return -1;
  return x < y ? -1 : 1;
}
