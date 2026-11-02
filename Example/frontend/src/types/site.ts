import type { PageResponse } from './page';

/**
 * Internal Site model, mapped from Enterprise `results` rows by `siteMapper.ts`:
 * internal field names only, never the hosted server's external names.
 */
export type SiteType =
  | 'Fabrication'
  | 'IC Assembly'
  | 'Final Assembly'
  | 'Test'
  | 'Packaging'
  | 'Warehouse'
  | 'HQ'
  | 'Office';

export interface Site {
  id: number | null;
  siteId: number | null;
  internalSiteId: string | null;
  siteName: string | null;
  siteType: SiteType | null;
  fullAddress: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  addressLine3: string | null;
  cityLocality: string | null;
  districtCounty: string | null;
  stateProvince: string | null;
  postalCode: string | null;
  country: string | null;
  latitude: number | null;
  longitude: number | null;
}

export type SiteListResponse = PageResponse<Site>;
