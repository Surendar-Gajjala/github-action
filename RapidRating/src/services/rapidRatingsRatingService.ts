import { apiRequest } from "./httpClient";
import { config } from "./config";
import { adaptObject, adaptRridataPage, collectAllPages } from "@/utils/responseAdapter";
import type { Rating } from "@/types/rating";
import type { PageResult } from "@/types/api";

export const ratingService = {
  /** GET /companies/{rrid}/ratings/ — 10 per page, newest first. */
  async list(rrid: string, page = 1): Promise<PageResult<Rating>> {
    const raw = await apiRequest("data", `companies/${encodeURIComponent(rrid)}/ratings/`, { params: { page: page > 1 ? page : undefined } });
    return adaptRridataPage<Rating>(raw, page, config.pageSize.ratings, "ratings");
  },

  async get(rrid: string, ratingId: string): Promise<Rating> {
    return adaptObject<Rating>(await apiRequest("data", `companies/${encodeURIComponent(rrid)}/ratings/${encodeURIComponent(ratingId)}/`), "rating detail");
  },

  listAll(rrid: string, onProgress?: (loaded: number, total: number) => void) {
    return collectAllPages((page) => this.list(rrid, page), onProgress);
  },
};
