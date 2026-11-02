/**
 * Internal RapidRatings models, mapped from the RapidRatings API by `rapidRatingsMapper.ts`
 * and shown on Company Details. Read-only: nothing is written back to the Z2 Company.
 * Field names follow the `rapidratings` group of the integration spec (7.4–7.6), in camelCase.
 * Amounts and percentages are kept exactly as received: null stays null, signs and precision
 * are kept, nothing is rescaled or converted.
 */

/** Years covered by the cumulative term probability of default (`termPd.year2` … `year10`). */
export const TERM_PD_YEARS = [2, 3, 4, 5, 6, 7, 8, 9, 10] as const;
export type TermPdYear = (typeof TERM_PD_YEARS)[number];

export interface RapidRatingsRating {
  ratingId: string | null;
  eqyYear: number | null;
  period: string | null;
  financialDate: string | null;
  reportingPeriod: string | null;
  ratingUrl: string | null;
  fhr: number | null;
  chs: number | null;
  /** 1-year probability of default, in percent. */
  probabilityDefault: number | null;
  /** Cumulative probability of default in percent, by year. */
  termPd: Record<TermPdYear, number | null>;
  delta: number | null;
  simulatedFhr: number | null;
  simulatedFhrDelta: number | null;
  operatingProfitability: number | null;
  netProfitability: number | null;
  capitalStructureEfficiency: number | null;
  costStructureEfficiency: number | null;
  leverage: string | null;
  liquidity: string | null;
  earningsPerformance: string | null;
}

export interface RapidRatingsPeriodAmounts {
  salesRevenue: number | null;
  earningsBeforeInterestAndTax: number | null;
  interestExpense: number | null;
  interestReceived: number | null;
  companyTaxExpense: number | null;
  abnormalItems: number | null;
  netProfitAfterTax: number | null;
  bankCashBalances: number | null;
  totalCurrentAssets: number | null;
  fixedAssets: number | null;
  financialAssets: number | null;
  totalAssets: number | null;
  debtDueWithinOneYear: number | null;
  totalCurrentLiabilities: number | null;
  termLoans: number | null;
  totalLiabilities: number | null;
  totalShareholderEquity: number | null;
  cashFromOperationsFromCashFlow: number | null;
  netInvestingCashFlow: number | null;
  netFundingCashFlow: number | null;
}

export interface RapidRatingsPeriod extends RapidRatingsPeriodAmounts {
  periodId: string | null;
  eqyYear: number | null;
  period: string | null;
  financialDate: string | null;
  currency: string | null;
  periodUrl: string | null;
}

export interface RapidRatingsCompany {
  // Profile
  rrid: string | null;
  sourceCompanyName: string | null;
  sourceCountry: string | null;
  ticker: string | null;
  exchange: string | null;
  sector: string | null;
  address: string | null;
  // URLs
  companyUrl: string | null;
  financialsUrl: string | null;
  periodsUrl: string | null;
  ratingsUrl: string | null;
  // Data context
  currency: string | null;
  dataScale: string | null;
  dataConfidentiality: string | null;
  industryClassificationSystem: string | null;
  industryClassificationCode: string | null;
  externalSourceId: string | null;
  externalSourceIds: string[];
  // Report links
  reportLinkFhr: string | null;
  reportLinkFinancialDialog: string | null;
  reportLinkPeerBenchmark: string | null;
  // Benchmark (detail endpoint only)
  sectorAverageFhr: number | null;
  sectorAverageChs: number | null;
  // Current rating, from latestRatings[0]
  currentRatingId: string | null;
  currentFhr: number | null;
  currentChs: number | null;
  currentProbabilityDefault: number | null;
  currentFinancialDate: string | null;
  /** Newest first, up to 5. */
  latestRatings: RapidRatingsRating[];
}

/** Everything shown for one RapidRatings company. */
export interface RapidRatingsDetails {
  company: RapidRatingsCompany;
  /** Full history, newest first. */
  periods: RapidRatingsPeriod[];
  /** Full history, newest first. */
  ratings: RapidRatingsRating[];
  /** When the data was read from RapidRatings (ISO timestamp). */
  retrievedAt: string;
}

/** RapidRatings companies found for a Z2 company name. */
export interface RapidRatingsSearchResult {
  /** The name actually sent to RapidRatings (legal suffixes removed), or null when too short to search. */
  searchTerm: string | null;
  candidates: RapidRatingsCompany[];
  /** The candidate whose name equals the Z2 company name (ignoring case, punctuation and legal suffixes). */
  exactMatch: RapidRatingsCompany | null;
}
