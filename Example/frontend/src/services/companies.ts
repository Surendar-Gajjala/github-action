// The query-config payload (src/queries). Preserves the selected properties, output.format "table" and context.tableId "objecttype-view-company".
import companyQuery from '../queries/company-query.json';
import type { CompanyListResponse } from '../types/company';
import { mapCompany } from './companyMapper';
import { executeQuery } from './enterpriseQuery';

/**
 * Fetches one page of companies directly from the Enterprise Query Config V2 endpoint
 * (POST, `company-query.json` as the body), and maps `results` to the internal Company model.
 * `page` is zero-based.
 */
export function fetchCompanies(page: number, size: number): Promise<CompanyListResponse> {
  return executeQuery(companyQuery, page, size, mapCompany);
}
