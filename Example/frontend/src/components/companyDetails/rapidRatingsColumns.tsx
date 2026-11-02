import type { ReactNode } from 'react';

import { type RapidRatingsPeriod, type RapidRatingsPeriodAmounts, type RapidRatingsRating, TERM_PD_YEARS } from '../../types/rapidRatings';

/** One column of a RapidRatings history table; `render` returns null for "—". */
export interface HistoryColumn<T> {
  id: string;
  header: string;
  align?: 'left' | 'right';
  render: (row: T) => ReactNode | null;
}

const numberFormat = new Intl.NumberFormat('en-US', { maximumFractionDigits: 6 });

/** A number as received: sign and precision kept, nothing rescaled. */
export const formatNumber = (value: number) => numberFormat.format(value);

const n = (value: number | null) => (value === null ? null : formatNumber(value));

/** A link that opens in a new tab; only http(s) URLs are linked, anything else is shown as text. */
export function externalLink(url: string | null): ReactNode | null {
  if (url === null) return null;
  if (!/^https?:\/\//i.test(url)) return url;
  return (
    <a href={url} target="_blank" rel="noreferrer noopener" className="break-all text-blue-600 hover:underline">
      {url}
    </a>
  );
}

const linkCell = (url: string | null) =>
  url !== null && /^https?:\/\//i.test(url) ? (
    <a href={url} target="_blank" rel="noreferrer noopener" className="text-blue-600 hover:underline">
      Open
    </a>
  ) : url;

/** Ratings and Latest Ratings: same columns (spec 7.6). */
export const RATING_COLUMNS: HistoryColumn<RapidRatingsRating>[] = [
  { id: 'ratingId', header: 'Rating Period ID', render: (r) => r.ratingId },
  { id: 'eqyYear', header: 'EQY Year', align: 'right', render: (r) => r.eqyYear },
  { id: 'period', header: 'Period', render: (r) => r.period },
  { id: 'financialDate', header: 'Financial Date', render: (r) => r.financialDate },
  { id: 'reportingPeriod', header: 'Reporting Period', render: (r) => r.reportingPeriod },
  { id: 'fhr', header: 'FHR', align: 'right', render: (r) => n(r.fhr) },
  { id: 'chs', header: 'CHS', align: 'right', render: (r) => n(r.chs) },
  { id: 'probabilityDefault', header: 'Probability of Default (%)', align: 'right', render: (r) => n(r.probabilityDefault) },
  ...TERM_PD_YEARS.map(
    (year): HistoryColumn<RapidRatingsRating> => ({
      id: `termPd${year}`,
      header: `Term PD Year ${year} (%)`,
      align: 'right',
      render: (r) => n(r.termPd[year]),
    }),
  ),
  { id: 'delta', header: 'FHR Delta', align: 'right', render: (r) => n(r.delta) },
  { id: 'simulatedFhr', header: 'Simulated FHR', align: 'right', render: (r) => n(r.simulatedFhr) },
  { id: 'simulatedFhrDelta', header: 'Simulated FHR Delta', align: 'right', render: (r) => n(r.simulatedFhrDelta) },
  { id: 'operatingProfitability', header: 'Operating Profitability', align: 'right', render: (r) => n(r.operatingProfitability) },
  { id: 'netProfitability', header: 'Net Profitability', align: 'right', render: (r) => n(r.netProfitability) },
  { id: 'capitalStructureEfficiency', header: 'Capital Structure Efficiency', align: 'right', render: (r) => n(r.capitalStructureEfficiency) },
  { id: 'costStructureEfficiency', header: 'Cost Structure Efficiency', align: 'right', render: (r) => n(r.costStructureEfficiency) },
  { id: 'leverage', header: 'Leverage', render: (r) => r.leverage },
  { id: 'liquidity', header: 'Liquidity', render: (r) => r.liquidity },
  { id: 'earningsPerformance', header: 'Earnings Performance', render: (r) => r.earningsPerformance },
  { id: 'ratingUrl', header: 'Rating URL', render: (r) => linkCell(r.ratingUrl) },
];

const AMOUNTS: [keyof RapidRatingsPeriodAmounts, string][] = [
  ['salesRevenue', 'Sales Revenue'],
  ['earningsBeforeInterestAndTax', 'EBIT'],
  ['interestExpense', 'Interest Expense'],
  ['interestReceived', 'Interest Received'],
  ['companyTaxExpense', 'Company Tax Expense'],
  ['abnormalItems', 'Abnormal Items'],
  ['netProfitAfterTax', 'Net Profit After Tax'],
  ['bankCashBalances', 'Bank Cash Balances'],
  ['totalCurrentAssets', 'Total Current Assets'],
  ['fixedAssets', 'Fixed Assets'],
  ['financialAssets', 'Financial Assets'],
  ['totalAssets', 'Total Assets'],
  ['debtDueWithinOneYear', 'Debt Due Within One Year'],
  ['totalCurrentLiabilities', 'Total Current Liabilities'],
  ['termLoans', 'Term Loans'],
  ['totalLiabilities', 'Total Liabilities'],
  ['totalShareholderEquity', 'Total Shareholder Equity'],
  ['cashFromOperationsFromCashFlow', 'Cash From Operations'],
  ['netInvestingCashFlow', 'Net Investing Cash Flow'],
  ['netFundingCashFlow', 'Net Funding Cash Flow'],
];

/** Periods (spec 7.5). Amounts are in the company's currency at its data scale. */
export const PERIOD_COLUMNS: HistoryColumn<RapidRatingsPeriod>[] = [
  { id: 'periodId', header: 'Period ID', render: (p) => p.periodId },
  { id: 'eqyYear', header: 'EQY Year', align: 'right', render: (p) => p.eqyYear },
  { id: 'period', header: 'Period', render: (p) => p.period },
  { id: 'financialDate', header: 'Financial Date', render: (p) => p.financialDate },
  { id: 'currency', header: 'Currency', render: (p) => p.currency },
  ...AMOUNTS.map(
    ([key, header]): HistoryColumn<RapidRatingsPeriod> => ({ id: key, header, align: 'right', render: (p) => n(p[key]) }),
  ),
  { id: 'periodUrl', header: 'Period URL', render: (p) => linkCell(p.periodUrl) },
];
