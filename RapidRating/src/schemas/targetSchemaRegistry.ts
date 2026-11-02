/**
 * TARGET schema registry (A1). EXAMPLE ONLY.
 *
 * No A1 project documentation was supplied, so these entities and properties are an illustrative,
 * clearly labelled example to exercise the mapping UI. Replace this registry with the real A1
 * metadata (or load it from an A1 metadata endpoint) before using mappings for anything real.
 */
import type { TargetSchemaRegistry } from "@/types/mapping";

export const targetSchemaRegistry: TargetSchemaRegistry = {
  system: "A1",
  version: "example-0.1",
  isExample: true,
  entities: [
    {
      name: "Supplier",
      description: "Example: a supplier/counterparty master record",
      properties: [
        { name: "externalProviderId", type: "string", required: true, description: "Provider's company identifier" },
        { name: "legalName", type: "string", required: true },
        { name: "countryCode", type: "string", required: false },
        { name: "stockTicker", type: "string", required: false },
        { name: "industrySector", type: "string", required: false },
        { name: "reportingCurrency", type: "string", required: false },
        { name: "amountScale", type: "string", required: false },
      ],
    },
    {
      name: "SupplierRiskRating",
      description: "Example: one provider rating for one reporting period",
      properties: [
        { name: "externalProviderId", type: "string", required: true },
        { name: "ratingPeriodId", type: "string", required: true },
        { name: "fiscalYear", type: "integer", required: false },
        { name: "fiscalPeriod", type: "string", required: false },
        { name: "financialStatementDate", type: "date", required: true },
        { name: "ratingPublishedDate", type: "date", required: false },
        { name: "financialHealthRating", type: "integer", required: true },
        { name: "coreHealthScore", type: "integer", required: false },
        { name: "probabilityOfDefaultPct", type: "decimal", required: false },
        { name: "ratingChange", type: "integer", required: false },
        { name: "termProbabilityOfDefault", type: "json", required: false },
      ],
    },
    {
      name: "SupplierFinancialPeriod",
      description: "Example: financial statement figures for one period",
      properties: [
        { name: "externalProviderId", type: "string", required: true },
        { name: "periodId", type: "string", required: true },
        { name: "statementDate", type: "date", required: true },
        { name: "currency", type: "string", required: false },
        { name: "revenue", type: "decimal", required: false },
        { name: "ebit", type: "decimal", required: false },
        { name: "netIncome", type: "decimal", required: false },
        { name: "totalAssets", type: "decimal", required: false },
        { name: "totalLiabilities", type: "decimal", required: false },
        { name: "shareholderEquity", type: "decimal", required: false },
      ],
    },
    {
      name: "SupplierPortfolio",
      description: "Example: a monitored group of suppliers",
      properties: [
        { name: "externalPortfolioId", type: "string", required: true },
        { name: "portfolioName", type: "string", required: true },
      ],
    },
    {
      name: "SupplierOutreach",
      description: "Example: a request for a counterparty to submit financials",
      properties: [
        { name: "outreachId", type: "string", required: true },
        { name: "supplierName", type: "string", required: true },
        { name: "supplierReference", type: "string", required: false },
        { name: "contactEmail", type: "string", required: false },
        { name: "isRated", type: "boolean", required: false },
        { name: "externalProviderId", type: "string", required: false },
        { name: "createdAt", type: "datetime", required: false },
      ],
    },
  ],
};
