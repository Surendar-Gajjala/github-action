import type { ReactNode } from 'react';

import type { HierarchyItemNode } from '../../types/hierarchy';
import { dash, textCell } from '../table/cells';
import { Badge } from '../ui/Badge';
import { statusTone } from '../ui/statusTone';

/** A grid column after the tree column; rendered for item rows only (part rows leave it empty). */
export interface HierarchyColumn {
  id: string;
  header: string;
  align?: 'left' | 'right';
  wide?: boolean;
  render: (node: HierarchyItemNode) => ReactNode;
  /** Plain text for the hover title of wide columns. */
  text?: (node: HierarchyItemNode) => string | null;
}

const badge = (value: string | null) => (value ? <Badge>{value}</Badge> : dash);
const yesNo = (value: boolean | null) => (value === null ? dash : <Badge>{value ? 'Yes' : 'No'}</Badge>);
const qtyFormat = new Intl.NumberFormat(undefined, { maximumFractionDigits: 6 });

/**
 * Columns of the table config's "grid" and "edge" sections, after the Item Number
 * tree column, in table-config order (Qty, the item_bom edge property, follows Revision).
 */
export const HIERARCHY_COLUMNS: HierarchyColumn[] = [
  {
    id: 'description',
    header: 'Description',
    wide: true,
    render: (n) => textCell(n.item.description),
    text: (n) => n.item.description,
  },
  { id: 'revision', header: 'Revision', render: (n) => textCell(n.item.revision) },
  {
    id: 'qty',
    header: 'Qty',
    align: 'right',
    render: (n) => (n.qty === undefined || n.qty === null ? dash : qtyFormat.format(n.qty)),
  },
  { id: 'itemType', header: 'Item Type', render: (n) => badge(n.item.itemType) },
  {
    id: 'itemStatus',
    header: 'Item Status',
    render: (n) => (n.item.itemStatus ? <Badge tone={statusTone(n.item.itemStatus)}>{n.item.itemStatus}</Badge> : dash),
  },
  { id: 'makeBuy', header: 'Make / Buy', render: (n) => badge(n.item.makeBuy) },
  { id: 'designGroup', header: 'Design Group', render: (n) => badge(n.item.designGroup) },
  { id: 'restricted', header: 'Restricted', render: (n) => yesNo(n.item.restricted) },
  { id: 'exemptionStatus', header: 'Exemption Status', render: (n) => badge(n.item.exemptionStatus) },
  { id: 'project', header: 'Project', render: (n) => badge(n.item.project) },
  {
    id: 'sourceNotes',
    header: 'Source Notes',
    wide: true,
    render: (n) => textCell(n.item.sourceNotes),
    text: (n) => n.item.sourceNotes,
  },
  { id: 'collection', header: 'Collection', render: (n) => textCell(n.item.collection) },
  { id: 'collectionLock', header: 'Collection Lock', render: (n) => textCell(n.item.collectionLock) },
  { id: 'euRohs', header: 'EU RoHS', render: (n) => textCell(n.item.euRohs) },
  { id: 'euRohsLock', header: 'EU RoHS Lock', render: (n) => textCell(n.item.euRohsLock) },
  { id: 'ecExemptions', header: 'EC Exemptions', render: (n) => textCell(n.item.ecExemptions) },
  { id: 'pwbLeadHalogen', header: 'PWB Lead / Halogen', render: (n) => textCell(n.item.pwbLeadHalogen) },
  { id: 'pwbLeadHalogenLock', header: 'PWB Lead / Halogen Lock', render: (n) => textCell(n.item.pwbLeadHalogenLock) },
  { id: 'plasticLeadHalogen', header: 'Plastic Lead / Halogen', render: (n) => textCell(n.item.plasticLeadHalogen) },
  {
    id: 'plasticLeadHalogenLock',
    header: 'Plastic Lead / Halogen Lock',
    render: (n) => textCell(n.item.plasticLeadHalogenLock),
  },
  { id: 'pnr', header: 'PNR', render: (n) => textCell(n.item.pnr) },
  { id: 'pnrLock', header: 'PNR Lock', render: (n) => textCell(n.item.pnrLock) },
  { id: 'businessUnit', header: 'Business Unit', render: (n) => textCell(n.item.businessUnit) },
  { id: 'isProduct', header: 'Product', render: (n) => yesNo(n.item.isProduct) },
  {
    id: 'structureRole',
    header: 'Structure',
    render: (n) => (n.item.structureRole ? <Badge>{n.item.structureRole === 'BOM' ? 'BOM' : 'Item'}</Badge> : dash),
  },
];
