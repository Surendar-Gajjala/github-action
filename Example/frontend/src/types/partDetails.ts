import type { OverviewSection } from './itemDetails';

/**
 * Part details model, built from the Enterprise partial-object response
 * (`services/partDetails.ts`). Reuses the shared overview section/field shape (see ./itemDetails.ts).
 */
export interface PartOverview {
  id: number;
  partNumber: string | null;
  manufacturer: string | null;
  description: string | null;
  sections: OverviewSection[];
}
