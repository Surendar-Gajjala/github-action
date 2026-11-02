import type { StructureRole } from './item';
import type { PageResponse } from './page';

/**
 * Internal Item Hierarchy model, built from the Enterprise graph-match response by
 * `services/hierarchyTree.ts`. Internal names only;
 * status-like values are readable labels.
 */
export interface HierarchyItem {
  itemNumber: string | null;
  description: string | null;
  revision: string | null;
  itemType: string | null;
  itemStatus: string | null;
  makeBuy: string | null;
  designGroup: string | null;
  restricted: boolean | null;
  exemptionStatus: string | null;
  project: string | null;
  sourceNotes: string | null;
  collection: string | null;
  collectionLock: string | null;
  euRohs: string | null;
  euRohsLock: string | null;
  ecExemptions: string | null;
  pwbLeadHalogen: string | null;
  pwbLeadHalogenLock: string | null;
  plasticLeadHalogen: string | null;
  plasticLeadHalogenLock: string | null;
  pnr: string | null;
  pnrLock: string | null;
  businessUnit: string | null;
  isProduct: boolean | null;
  structureRole: StructureRole | null;
}

export interface HierarchyPart {
  partNumber: string | null;
  manufacturer: string | null;
}

interface NodeBase {
  /** Unique per occurrence: a shared item appears once under every parent that uses it. */
  key: string;
  id: number | null;
  /** Quantity on the BOM edge from the parent; absent for top-level products and parts. */
  qty?: number;
  children: HierarchyNode[];
}

export interface HierarchyItemNode extends NodeBase {
  kind: 'ITEM';
  item: HierarchyItem;
}

export interface HierarchyPartNode extends NodeBase {
  kind: 'PART';
  part: HierarchyPart;
}

export type HierarchyNode = HierarchyItemNode | HierarchyPartNode;

export type HierarchyPageResponse = PageResponse<HierarchyNode>;
