import { apiRequest } from "./httpClient";
import { config } from "./config";
import { adaptCursorPage, adaptObject } from "@/utils/responseAdapter";
import type { PageResult } from "@/types/api";
import type { OutreachCommunications, OutreachQuery, OutreachRequest, OutreachRequestCreate } from "@/types/outreach";

/**
 * Documented operations only (OpenAPI spec, api.sandbox.rrilabs.co/openapi.yaml):
 *   GET  /outreach-requests                         list (cursor pagination)
 *   POST /outreach-requests                         create (sends real emails to the counterparty)
 *   GET  /outreach-requests/{id}                    detail
 *   GET  /outreach-requests/{id}/communications     communication attempts
 * No PUT/PATCH/DELETE is documented, so none is implemented.
 */
export const outreachService = {
  async list(query: OutreachQuery = {}): Promise<PageResult<OutreachRequest>> {
    const q = new URLSearchParams();
    if (query.companyName) q.set("counterparty_company_name", query.companyName);
    if (query.referenceId) q.set("reference_id", query.referenceId);
    if (query.isRated) q.set("is_rated", query.isRated);
    const limit = query.limit ?? config.outreachPageSize;
    const raw = await apiRequest("outreach", "outreach-requests", {
      params: { limit, cursor: query.cursor, sort: query.sort, q: q.toString() || undefined },
    });
    return adaptCursorPage<OutreachRequest>(raw, limit, "outreach requests");
  },

  async get(id: string): Promise<OutreachRequest> {
    return adaptObject<OutreachRequest>(await apiRequest("outreach", `outreach-requests/${encodeURIComponent(id)}`), "outreach request");
  },

  async communications(id: string): Promise<OutreachCommunications> {
    return adaptObject<OutreachCommunications>(await apiRequest("outreach", `outreach-requests/${encodeURIComponent(id)}/communications`), "outreach communications");
  },

  async create(body: OutreachRequestCreate): Promise<OutreachRequest> {
    return adaptObject<OutreachRequest>(await apiRequest("outreach", "outreach-requests", { method: "POST", data: body }), "created outreach request");
  },

  /** Follows next_cursor through every page (used by manual sync). */
  async listAll(onProgress?: (loaded: number, total: number) => void, maxPages = 200): Promise<OutreachRequest[]> {
    const items: OutreachRequest[] = [];
    let cursor: string | undefined;
    for (let i = 0; i < maxPages; i++) {
      const page = await this.list({ limit: 100, cursor });
      items.push(...page.items);
      onProgress?.(items.length, page.total);
      if (!page.hasNext || !page.nextCursor) break;
      cursor = page.nextCursor;
    }
    return items;
  },
};
