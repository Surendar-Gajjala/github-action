import type { AvailabilityRisk, Item } from '../../types/item';
import { dash, itemLink, textCell as text } from '../table/cells';
import type { Column } from '../table/types';
import { Badge, type BadgeTone } from '../ui/Badge';
import { statusTone } from '../ui/statusTone';

const RISK_TONE: Record<AvailabilityRisk, BadgeTone> = {
  LOW: 'green',
  MEDIUM: 'amber',
  HIGH: 'red',
  'NOT ASSESSED': 'gray',
};

/**
 * One column per property selected by the hosted query config, shown with internal
 * names. Identity, type and status come first (as in the reference layout); the
 * remaining properties follow.
 */
export const ITEM_COLUMNS: Column<Item>[] = [
  {
    key: 'itemNumber',
    header: 'Item Number',
    render: (i) => itemLink(i.id, i.itemNumber),
  },
  { key: 'description', header: 'Description', wide: true, render: (i) => text(i.description) },
  {
    key: 'structureRole',
    header: 'Structure Role',
    render: (i) => (i.structureRole ? <Badge>{i.structureRole === 'BOM' ? 'BOM' : 'Item'}</Badge> : dash),
  },
  {
    key: 'itemStatusName',
    header: 'Item Status',
    render: (i) => (i.itemStatusName ? <Badge tone={statusTone(i.itemStatusName)}>{i.itemStatusName}</Badge> : dash),
  },
  { key: 'revision', header: 'Revision', render: (i) => text(i.revision) },
  { key: 'businessUnit', header: 'Business Unit', render: (i) => text(i.businessUnit) },
  {
    key: 'isProduct',
    header: 'Product',
    render: (i) => (i.isProduct === null ? dash : i.isProduct ? 'Yes' : 'No'),
  },
  {
    key: 'availabilityRisk',
    header: 'Availability Risk',
    render: (i) => (i.availabilityRisk ? <Badge tone={RISK_TONE[i.availabilityRisk]}>{i.availabilityRisk}</Badge> : dash),
  },
  {
    key: 'odmName',
    header: 'ODM Name',
    render: (i) => (i.odmName.length ? i.odmName.join(', ') : dash),
  },
  {
    key: 'odmActive',
    header: 'ODM Active',
    render: (i) =>
      i.odmActive.length ? (
        <span className="flex gap-1">
          {i.odmActive.map((active, index) => (
            <Badge key={index} tone={active ? 'green' : 'gray'}>
              {active ? 'Active' : 'Inactive'}
            </Badge>
          ))}
        </span>
      ) : (
        dash
      ),
  },
  { key: 'usedInProducts', header: 'Used In Products', wide: true, render: (i) => text(i.usedInProducts) },
  {
    key: 'productFamiliesImpacted',
    header: 'Families Impacted',
    align: 'right',
    render: (i) => i.productFamiliesImpacted ?? dash,
  },
];
