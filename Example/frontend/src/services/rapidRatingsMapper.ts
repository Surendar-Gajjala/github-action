import {
  type RapidRatingsCompany,
  type RapidRatingsPeriod,
  type RapidRatingsRating,
  TERM_PD_YEARS,
  type TermPdYear,
} from '../types/rapidRatings';
import { int, list, num, scalarText, text } from './enterpriseValues';

/**
 * Maps RapidRatings API objects (company, period, rating) to the internal models. The
 * RapidRatings field names live only here. Values are kept as received: null and missing
 * become null (never 0), numbers keep their sign and precision (`1575.0` is 1575), and
 * integer fields that are not whole numbers become null.
 */

type Json = Record<string, unknown>;
const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null && !Array.isArray(value);
const objectAt = (value: unknown): Json => (isObject(value) ? value : {});

export function mapRating(raw: Json): RapidRatingsRating {
  const termPd = objectAt(raw.termPd);
  return {
    ratingId: text(raw.id),
    eqyYear: int(raw.eqyYear),
    period: text(raw.period),
    financialDate: text(raw.financialDate),
    reportingPeriod: text(raw.reportingPeriod),
    ratingUrl: text(raw.href),
    fhr: int(raw.fhr),
    chs: int(raw.chs),
    probabilityDefault: num(raw.probabilityDefault),
    termPd: Object.fromEntries(TERM_PD_YEARS.map((year) => [year, num(termPd[`year${year}`])])) as Record<TermPdYear, number | null>,
    delta: int(raw.delta),
    simulatedFhr: int(raw.simulatedFhr),
    simulatedFhrDelta: int(raw.simulatedFhrDelta),
    operatingProfitability: int(raw.operatingProfitability),
    netProfitability: int(raw.netProfitability),
    capitalStructureEfficiency: int(raw.capitalStructureEfficiency),
    costStructureEfficiency: int(raw.costStructureEfficiency),
    leverage: text(raw.leverage),
    liquidity: text(raw.liquidity),
    earningsPerformance: text(raw.earningsPerformance),
  };
}

export function mapPeriod(raw: Json): RapidRatingsPeriod {
  return {
    periodId: text(raw.id),
    eqyYear: int(raw.eqyYear),
    period: text(raw.period),
    financialDate: text(raw.financialDate),
    currency: text(raw.currency),
    periodUrl: text(raw.href),
    salesRevenue: num(raw.salesRevenue),
    earningsBeforeInterestAndTax: num(raw.earningsBeforeInterestAndTax),
    interestExpense: num(raw.interestExpense),
    interestReceived: num(raw.interestReceived),
    companyTaxExpense: num(raw.companyTaxExpense),
    abnormalItems: num(raw.abnormalItems),
    netProfitAfterTax: num(raw.netProfitAfterTax),
    bankCashBalances: num(raw.bankCashBalances),
    totalCurrentAssets: num(raw.totalCurrentAssets),
    fixedAssets: num(raw.fixedAssets),
    financialAssets: num(raw.financialAssets),
    totalAssets: num(raw.totalAssets),
    // The provider's own spelling.
    debtDueWithinOneYear: num(raw.debitOwnedWithinOneYear),
    totalCurrentLiabilities: num(raw.totalCurrentLiabilities),
    termLoans: num(raw.termLoans),
    totalLiabilities: num(raw.totalLiabilities),
    totalShareholderEquity: num(raw.totalShareholderEquity),
    cashFromOperationsFromCashFlow: num(raw.cashFromOperationsFromCashFlow),
    netInvestingCashFlow: num(raw.netInvestingCashFlow),
    netFundingCashFlow: num(raw.netFundingCashFlow),
  };
}

/** A company from the list or the detail endpoint; `sectorAverages` only comes with the detail. */
export function mapCompany(raw: Json): RapidRatingsCompany {
  const reportLinks = objectAt(raw.reportLinks);
  const sectorAverages = objectAt(raw.sectorAverages);
  const latestRatings = Array.isArray(raw.latestRatings) ? raw.latestRatings.filter(isObject).map(mapRating) : [];
  const current = latestRatings[0] ?? null;
  return {
    rrid: text(raw.id),
    sourceCompanyName: text(raw.name),
    sourceCountry: text(raw.country),
    ticker: text(raw.ticker),
    exchange: text(raw.exchange),
    sector: text(raw.sector),
    address: text(raw.address),
    companyUrl: text(raw.href),
    financialsUrl: text(raw.financials),
    periodsUrl: text(raw.periods),
    ratingsUrl: text(raw.ratings),
    currency: text(raw.currency),
    dataScale: text(raw.dataScale),
    dataConfidentiality: text(raw.dataConfidential),
    industryClassificationSystem: text(raw.industryClassificationSystem),
    industryClassificationCode: text(raw.industryClassificationCode),
    externalSourceId: text(raw.externalSourceId),
    externalSourceIds: list(raw.externalSourceIds, scalarText),
    reportLinkFhr: text(reportLinks.fhr),
    reportLinkFinancialDialog: text(reportLinks.financialDialog),
    reportLinkPeerBenchmark: text(reportLinks.peerBenchmark),
    sectorAverageFhr: num(sectorAverages.fhr),
    sectorAverageChs: num(sectorAverages.chs),
    currentRatingId: current?.ratingId ?? null,
    currentFhr: current?.fhr ?? null,
    currentChs: current?.chs ?? null,
    currentProbabilityDefault: current?.probabilityDefault ?? null,
    currentFinancialDate: current?.financialDate ?? null,
    latestRatings,
  };
}
