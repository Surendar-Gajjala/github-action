import type { LifecycleStatus, Part, SourcingType, SupplyChainRisk } from '../../types/part';
import { dash, partLink, textCell } from '../table/cells';
import type { Column } from '../table/types';
import { Badge, type BadgeTone } from '../ui/Badge';

const SOURCING_TONE: Record<SourcingType, BadgeTone> = {
  'Off-the-shelf': 'gray',
  Custom: 'violet',
  Conflict: 'red',
  Unknown: 'gray',
};

const RISK_TONE: Record<SupplyChainRisk, BadgeTone> = {
  LOW: 'green',
  MEDIUM: 'amber',
  HIGH: 'red',
  'Not Assessed': 'gray',
};

const LIFECYCLE_TONE: Record<LifecycleStatus, BadgeTone> = {
  Active: 'green',
  NRND: 'amber',
  LastTimeBuy: 'amber',
  Obsolete: 'red',
  Unknown: 'gray',
};

/**
 * One column per property selected by the Part query config, shown with internal
 * names (the ID is used as the row key but not shown, as on the Items tab).
 */
export const PART_COLUMNS: Column<Part>[] = [
  { key: 'partNumber', header: 'Part Number', render: (p) => partLink(p.id, p.partNumber) },
  { key: 'manufacturer', header: 'Manufacturer', render: (p) => textCell(p.manufacturer) },
  { key: 'description', header: 'Description', wide: true, render: (p) => textCell(p.description) },
  {
    key: 'sourcingType',
    header: 'Sourcing Type',
    render: (p) => (p.sourcingType ? <Badge tone={SOURCING_TONE[p.sourcingType]}>{p.sourcingType}</Badge> : dash),
  },
  {
    key: 'supplyChainRisk',
    header: 'Supply Chain Risk',
    render: (p) => (p.supplyChainRisk ? <Badge tone={RISK_TONE[p.supplyChainRisk]}>{p.supplyChainRisk}</Badge> : dash),
  },
  {
    key: 'lifecycleStatus',
    header: 'Lifecycle Status',
    render: (p) =>
      p.lifecycleStatus ? <Badge tone={LIFECYCLE_TONE[p.lifecycleStatus]}>{p.lifecycleStatus}</Badge> : dash,
  },
  { key: 'countryOfOrigin', header: 'Country of Origin', render: (p) => textCell(p.countryOfOrigin) },
  {
    key: 'z2PropertiesComparison',
    header: 'Z2 Properties Comparison',
    wide: true,
    render: (p) => textCell(p.z2PropertiesComparison),
  },
];
