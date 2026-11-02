import type { HierarchyItemNode, HierarchyNode, HierarchyPartNode } from '../../types/hierarchy';

export interface VisibleRow {
  node: HierarchyItemNode;
  depth: number;
}

const isItem = (node: HierarchyNode): node is HierarchyItemNode => node.kind === 'ITEM';

/** BOM child items (item_bom): these are the tree rows that can be expanded. */
export const itemChildren = (node: HierarchyNode): HierarchyItemNode[] => node.children.filter(isItem);

/** Sourced parts (item_sources): shown inside the item's own row, not as tree rows. */
export const partChildren = (node: HierarchyNode): HierarchyPartNode[] =>
  node.children.filter((child): child is HierarchyPartNode => child.kind === 'PART');

/** Item rows to render: every root, plus the BOM children of each expanded item, depth-first. */
export function visibleRows(roots: HierarchyNode[], expanded: ReadonlySet<string>): VisibleRow[] {
  const rows: VisibleRow[] = [];
  const walk = (nodes: HierarchyItemNode[], depth: number) => {
    for (const node of nodes) {
      rows.push({ node, depth });
      if (expanded.has(node.key)) walk(itemChildren(node), depth + 1);
    }
  };
  walk(roots.filter(isItem), 0);
  return rows;
}

/** Keys of every item that has BOM child items (for "Expand all"). */
export function expandableKeys(roots: HierarchyNode[]): Set<string> {
  const keys = new Set<string>();
  const walk = (nodes: HierarchyItemNode[]) => {
    for (const node of nodes) {
      const children = itemChildren(node);
      if (children.length > 0) {
        keys.add(node.key);
        walk(children);
      }
    }
  };
  walk(roots.filter(isItem));
  return keys;
}

const ownText = (node: HierarchyItemNode) => [
  node.item.itemNumber,
  node.item.description,
  ...partChildren(node).flatMap((part) => [part.part.partNumber, part.part.manufacturer]),
];

/**
 * Search within the loaded page: keeps the products whose tree contains a match (an
 * item, or a part sourced by it) and returns the keys to expand so every match is visible.
 */
export function searchTree(roots: HierarchyNode[], query: string): { roots: HierarchyNode[]; expand: Set<string> } {
  const needle = query.trim().toLowerCase();
  const expand = new Set<string>();
  if (!needle) return { roots, expand };

  const matches = (node: HierarchyItemNode): boolean => {
    let found = ownText(node).some((text) => text?.toLowerCase().includes(needle));
    for (const child of itemChildren(node)) {
      if (matches(child)) {
        expand.add(node.key);
        found = true;
      }
    }
    return found;
  };
  return { roots: roots.filter((root) => isItem(root) && matches(root)), expand };
}
