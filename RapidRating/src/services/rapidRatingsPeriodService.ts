import { apiRequest } from "./httpClient";
import { config } from "./config";
import { adaptObject, adaptRridataPage, collectAllPages } from "@/utils/responseAdapter";
import type { Period } from "@/types/period";
import type { PageResult } from "@/types/api";

export const periodService = {
  /** GET /companies/{rrid}/periods/ — 10 per page, newest first. */
  async list(rrid: string, page = 1): Promise<PageResult<Period>> {
    const raw = await apiRequest("data", `companies/${encodeURIComponent(rrid)}/periods/`, { params: { page: page > 1 ? page : undefined } });
    return adaptRridataPage<Period>(raw, page, config.pageSize.periods, "periods");
  },

  async get(rrid: string, periodId: string): Promise<Period> {
    return adaptObject<Period>(await apiRequest("data", `companies/${encodeURIComponent(rrid)}/periods/${encodeURIComponent(periodId)}/`), "period detail");
  },

  listAll(rrid: string, onProgress?: (loaded: number, total: number) => void) {
    return collectAllPages((page) => this.list(rrid, page), onProgress);
  },
};
