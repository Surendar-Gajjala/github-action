/** Rating object as returned by /companies/{rrid}/ratings/ and Company.latestRatings[] (verified 2026-10-28). */
export interface Rating {
  id: string;
  href?: string;
  eqyYear: number;
  period: string;
  financialDate: string;
  reportingPeriod?: string;
  fhr: number | null;
  chs: number | null;
  probabilityDefault: number | null;
  termPd?: Record<string, number | null>;
  delta?: number | null;
  simulatedFhr?: number | null;
  simulatedFhrDelta?: number | null;
  operatingProfitability?: number | null;
  netProfitability?: number | null;
  capitalStructureEfficiency?: number | null;
  costStructureEfficiency?: number | null;
  leverage?: string | null;
  liquidity?: string | null;
  earningsPerformance?: string | null;
}
