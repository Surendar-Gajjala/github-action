// The query-config payload (src/queries). Preserves the selected properties, output.format "table" and context.tableId "objecttype-view-site".
import siteQuery from '../queries/site-query.json';
import type { SiteListResponse } from '../types/site';
import { executeQuery } from './enterpriseQuery';
import { mapSite } from './siteMapper';

/**
 * Fetches one page of sites directly from the Enterprise Query Config V2 endpoint
 * (POST, `site-query.json` as the body), and maps `results` to the internal Site model.
 * `page` is zero-based.
 */
export function fetchSites(page: number, size: number): Promise<SiteListResponse> {
  return executeQuery(siteQuery, page, size, mapSite);
}
