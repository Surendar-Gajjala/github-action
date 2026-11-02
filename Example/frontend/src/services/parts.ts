// The query-config payload (src/queries). Preserves the selected properties, output.format "table" and context.tableId "objecttype-view-part".
import partQuery from '../queries/part-query.json';
import type { PartListResponse } from '../types/part';
import { executeQuery } from './enterpriseQuery';
import { mapPart } from './partMapper';

/**
 * Fetches one page of parts directly from the Enterprise Query Config V2 endpoint
 * (POST, `part-query.json` as the body), and maps `results` to the internal Part model.
 * `page` is zero-based.
 */
export function fetchParts(page: number, size: number): Promise<PartListResponse> {
  return executeQuery(partQuery, page, size, mapPart);
}
