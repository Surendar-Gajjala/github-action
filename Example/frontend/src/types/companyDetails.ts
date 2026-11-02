import type { OverviewSection } from './itemDetails';

/**
 * Company details model, built from the Enterprise partial-object response
 * (`services/companyDetails.ts`). Reuses the shared overview section/field shape (see ./itemDetails.ts).
 */
export interface CompanyOverview {
  id: number;
  companyName: string | null;
  country: string | null;
  tier: string | null;
  sections: OverviewSection[];
}
