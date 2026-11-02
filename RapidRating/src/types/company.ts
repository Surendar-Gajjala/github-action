import type { Rating } from "./rating";

/** Company object from /companies/ and /companies/{rrid}/ (verified 2026-10-28). */
export interface Company {
  /** RRID: the provider's company identifier, used for all per-company calls. */
  id: string;
  name: string;
  country?: string;
  ticker?: string;
  exchange?: string;
  sector?: string;
  href?: string;
  financials?: string;
  periods?: string;
  ratings?: string;
  latestRatings?: Rating[];
  reportLinks?: Record<string, string>;
  address?: string;
  industryClassificationSystem?: string | null;
  industryClassificationCode?: string | null;
  currency?: string;
  dataScale?: string;
  dataConfidential?: string;
  externalSourceId?: string | null;
  externalSourceIds?: unknown[];
  /** Detail endpoint only */
  reports?: { reportType: string; downloadHref: string }[];
  /** Detail endpoint only */
  sectorAverages?: { fhr?: number; chs?: number };
}

/** Query parameters accepted by GET /companies/ (verified; anything else returns 400). */
export interface CompanyQuery {
  page?: number;
  portfolio?: number;
  /** min 3 chars, case-insensitive partial match */
  name?: string;
  /** exact, case-sensitive */
  ticker?: string;
  externalSourceId?: string;
}
