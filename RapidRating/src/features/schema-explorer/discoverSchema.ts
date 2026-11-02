import { companyService } from "@/services/rapidRatingsCompanyService";
import { portfolioService } from "@/services/rapidRatingsPortfolioService";
import { periodService } from "@/services/rapidRatingsPeriodService";
import { ratingService } from "@/services/rapidRatingsRatingService";
import { outreachService } from "@/services/rapidRatingsOutreachService";
import { getAuthMode } from "@/services/config";
import { inferSchema } from "@/utils/fieldTypeDetector";
import { recordDiscovery } from "@/schemas/sourceSchemaRegistry";
import type { ResourceKey, ResourceSchema } from "@/types/schema";

/**
 * Fetches one real page of a resource and infers its schema from the returned records.
 * The provider exposes no schema discovery endpoint for rridata, so this uses validated responses.
 */
export async function discoverSchema(resource: ResourceKey, rrid?: string): Promise<{ schema: ResourceSchema; records: number }> {
  let records: unknown[];
  let sample: unknown;
  let what: string;

  switch (resource) {
    case "companies": {
      const page = await companyService.list({ page: 1 });
      records = page.items;
      what = "GET /companies/ (page 1)";
      break;
    }
    case "portfolios": {
      const page = await portfolioService.list(1);
      records = page.items;
      what = "GET /portfolios/ (page 1)";
      break;
    }
    case "periods":
    case "ratings": {
      const id = rrid || (await companyService.list({ page: 1 })).items[0]?.id;
      if (!id) throw { kind: "not-found", message: "No company available to sample periods/ratings from." };
      const page = resource === "periods" ? await periodService.list(id, 1) : await ratingService.list(id, 1);
      records = page.items;
      what = `GET /companies/${id}/${resource}/ (page 1)`;
      break;
    }
    case "outreaches": {
      const page = await outreachService.list({ limit: 50 });
      records = page.items;
      what = "GET /outreach-requests (limit 50)";
      break;
    }
  }
  sample = records.slice(0, 3);

  const origin = `${getAuthMode() === "mock" ? "DEMO data · " : ""}Inferred from ${records.length} record(s) of ${what}`;
  const schema = inferSchema(records, resource, origin);
  recordDiscovery(schema, sample);
  return { schema, records: records.length };
}
