import type { PageResponse } from './page';

/**
 * Internal Company model, mapped from Enterprise `results` rows by `companyMapper.ts`:
 * internal field names only, never the hosted server's external names.
 */
export interface Company {
  id: number | null;
  companyName: string | null;
}

export type CompanyListResponse = PageResponse<Company>;
