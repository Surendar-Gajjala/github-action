import type { Site, SiteType } from '../../types/site';
import { dash, keyCell, textCell } from '../table/cells';
import type { Column } from '../table/types';
import { Badge, type BadgeTone } from '../ui/Badge';

const SITE_TYPE_TONE: Record<SiteType, BadgeTone> = {
  Fabrication: 'violet',
  'IC Assembly': 'blue',
  'Final Assembly': 'blue',
  Test: 'amber',
  Packaging: 'gray',
  Warehouse: 'gray',
  HQ: 'green',
  Office: 'gray',
};

const coordinate = new Intl.NumberFormat(undefined, { maximumFractionDigits: 6 });
const coordinateCell = (value: number | null) => (value === null ? dash : coordinate.format(value));

/**
 * One column per property selected by the Site query config, shown with internal
 * names. The hosted object ID is used as the row key but not shown, as on the other tabs.
 */
export const SITE_COLUMNS: Column<Site>[] = [
  { key: 'siteName', header: 'Site Name', render: (s) => keyCell(s.siteName) },
  { key: 'internalSiteId', header: 'Internal Site ID', render: (s) => textCell(s.internalSiteId) },
  { key: 'siteId', header: 'Site ID', align: 'right', render: (s) => s.siteId ?? dash },
  {
    key: 'siteType',
    header: 'Site Type',
    render: (s) => (s.siteType ? <Badge tone={SITE_TYPE_TONE[s.siteType]}>{s.siteType}</Badge> : dash),
  },
  { key: 'cityLocality', header: 'City / Locality', render: (s) => textCell(s.cityLocality) },
  { key: 'stateProvince', header: 'State / Province', render: (s) => textCell(s.stateProvince) },
  { key: 'country', header: 'Country', render: (s) => textCell(s.country) },
  { key: 'fullAddress', header: 'Full Address', wide: true, render: (s) => textCell(s.fullAddress) },
  { key: 'addressLine1', header: 'Address Line 1', render: (s) => textCell(s.addressLine1) },
  { key: 'addressLine2', header: 'Address Line 2', render: (s) => textCell(s.addressLine2) },
  { key: 'addressLine3', header: 'Address Line 3', render: (s) => textCell(s.addressLine3) },
  { key: 'districtCounty', header: 'District / County', render: (s) => textCell(s.districtCounty) },
  { key: 'postalCode', header: 'Postal Code', render: (s) => textCell(s.postalCode) },
  { key: 'latitude', header: 'Latitude', align: 'right', render: (s) => coordinateCell(s.latitude) },
  { key: 'longitude', header: 'Longitude', align: 'right', render: (s) => coordinateCell(s.longitude) },
];
