import { apiRequest } from "./httpClient";
import { config } from "./config";
import { adaptObject, adaptRridataPage, collectAllPages } from "@/utils/responseAdapter";
import type { Portfolio } from "@/types/portfolio";
import type { PageResult } from "@/types/api";

export const portfolioService = {
  /** GET /portfolios/ — only `page` is accepted by the provider. */
  async list(page = 1): Promise<PageResult<Portfolio>> {
    const raw = await apiRequest("data", "portfolios/", { params: { page: page > 1 ? page : undefined } });
    return adaptRridataPage<Portfolio>(raw, page, config.pageSize.portfolios, "portfolios");
  },

  /** GET /portfolios/{id}/ */
  async get(id: number | string): Promise<Portfolio> {
    return adaptObject<Portfolio>(await apiRequest("data", `portfolios/${encodeURIComponent(String(id))}/`), "portfolio detail");
  },

  listAll(onProgress?: (loaded: number, total: number) => void) {
    return collectAllPages((page) => this.list(page), onProgress);
  },
};
