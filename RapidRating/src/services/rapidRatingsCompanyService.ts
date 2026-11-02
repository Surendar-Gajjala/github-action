import { apiRequest } from "./httpClient";
import { config } from "./config";
import { adaptObject, adaptRridataPage, collectAllPages } from "@/utils/responseAdapter";
import type { Company, CompanyQuery } from "@/types/company";
import type { PageResult } from "@/types/api";

export const companyService = {
  /** GET /companies/ — 20 per page (fixed). */
  async list(query: CompanyQuery = {}): Promise<PageResult<Company>> {
    const page = query.page ?? 1;
    const raw = await apiRequest("data", "companies/", {
      params: { page: page > 1 ? page : undefined, portfolio: query.portfolio, name: query.name, ticker: query.ticker, externalSourceId: query.externalSourceId },
    });
    return adaptRridataPage<Company>(raw, page, config.pageSize.companies, "companies");
  },

  /** GET /companies/{rrid}/ */
  async get(rrid: string): Promise<Company> {
    return adaptObject<Company>(await apiRequest("data", `companies/${encodeURIComponent(rrid)}/`), "company detail");
  },

  /** Follows `next` through every page. */
  listAll(query: Omit<CompanyQuery, "page"> = {}, onProgress?: (loaded: number, total: number) => void) {
    return collectAllPages((page) => this.list({ ...query, page }), onProgress);
  },
};
