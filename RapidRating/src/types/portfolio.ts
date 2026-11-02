/** Portfolio object from /portfolios/ (verified 2026-10-28). */
export interface Portfolio {
  id: number;
  name: string;
  href?: string;
  /** URL of /companies/?portfolio={id}: the only documented portfolio-company relationship. */
  companies?: string;
}
