/** Financial period from /companies/{rrid}/periods/ (verified 2026-10-28). Amounts use the company's dataScale. */
export interface Period {
  id: string;
  href?: string;
  eqyYear: number;
  period: string;
  financialDate: string;
  currency?: string;
  salesRevenue?: number | null;
  earningsBeforeInterestAndTax?: number | null;
  interestExpense?: number | null;
  interestReceived?: number | null;
  companyTaxExpense?: number | null;
  abnormalItems?: number | null;
  netProfitAfterTax?: number | null;
  bankCashBalances?: number | null;
  totalCurrentAssets?: number | null;
  fixedAssets?: number | null;
  financialAssets?: number | null;
  totalAssets?: number | null;
  debitOwnedWithinOneYear?: number | null;
  totalCurrentLiabilities?: number | null;
  termLoans?: number | null;
  totalLiabilities?: number | null;
  totalShareholderEquity?: number | null;
  cashFromOperationsFromCashFlow?: number | null;
  netInvestingCashFlow?: number | null;
  netFundingCashFlow?: number | null;
}
