// The query-config payload (src/queries). Preserves the selected properties, output.format "table" and context.tableId "objecttype-view-item".
import itemQuery from '../queries/item-query.json';
import type { ItemListResponse } from '../types/item';
import { executeQuery } from './enterpriseQuery';
import { mapItem } from './itemMapper';

/**
 * Fetches one page of items directly from the Enterprise Query Config V2 endpoint
 * (POST, `item-query.json` as the body), and maps `results` to the internal Item model.
 * `page` is zero-based.
 */
export function fetchItems(page: number, size: number): Promise<ItemListResponse> {
  return executeQuery(itemQuery, page, size, mapItem);
}
