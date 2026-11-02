RapidRatings Integration -- Requirements and Solution Overview
1. Purpose
This document describes the requirements and proposed implementation for integrating RapidRatings with the Z2 Enterprise Data Integrations platform.

A tenant administrator should be able to configure RapidRatings from the Data Integrations area. Once the integration is enabled, Z2 should authenticate with RapidRatings, retrieve company and financial-risk information, map the response data to the existing Z2 Company DataModel, and show the results on the Company Details page.

The integration needs to use the common Data Integrations capabilities:

Configure credentials and test the connection.
Retrieve and maintain the provider's source schema.
Map source properties to the Z2 DataModel.
Run schema synchronization separately from business-data synchronization.
Support manual and scheduled data sync.
Show sync results and retain sync and configuration history.
This is an extension to the existing Company model. It is not intended to introduce a separate Company object type for RapidRatings.

2. Proposed Integration Flow
A tenant administrator opens Data Integrations and enables RapidRatings.
The administrator enters the RapidRatings Client ID and Client Secret.
The Z2 backend uses those credentials to request an access token.
After the connection is established, the backend retrieves the portfolios and companies available to that account.
Z2 uses the RapidRatings company identifier (rrid) to retrieve the selected company's profile, financial periods, and ratings.
The integration validates the response and maps the source properties to the RapidRatings group on the existing Z2 Company record.
The Company Details page displays the mapped company profile, rating information, financial periods, and reports.
An administrator can run synchronization manually or configure a schedule. Z2 records the outcome of each run and changes to the integration configuration.
Backend boundary: The browser must call Z2 APIs only. It must not call RapidRatings directly. The Client Secret and RapidRatings access token must remain on the server and must not be returned to the frontend or written to application logs.

3. RapidRatings API Endpoints
The following sandbox endpoints were used while preparing this integration brief.

Resource Method Endpoint Purpose

Authentication POST https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/ Request an access token.

Companies GET (confirm with https://rridata.sandbox.rrilabs.co/v2.0/companies/ List companies provider available to the contract) account.

Portfolios GET (confirm with https://rridata.sandbox.rrilabs.co/v2.0/portfolios/ List portfolios provider available to the contract) account.

Company details GET (confirm with https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ Retrieve one provider company's profile. contract)

Periods GET (confirm with https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/periods/ Retrieve provider financial-period contract) records.

Ratings GET (confirm with https://rridata.sandbox.rrilabs.co/v2.0/companies/{rrid}/ratings/ Retrieve rating provider records. contract)

Outreach requests To be confirmed https://api.sandbox.rrilabs.co/outreach-requests Outreach request API. Its method, authentication, permissions, and side effects are not yet confirmed.
{rrid} is a path parameter identifying the RapidRatings company. For example, KawjbGJ is the RRID in this request:

GET https://rridata.sandbox.rrilabs.co/v2.0/companies/KawjbGJ/

The company response includes links to related resources such as financials, periods, and ratings. The integration should use the RRID when requesting company-specific data.

3.1 Authentication
The sandbox token endpoint is:

POST https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/

Z2 has a RapidRatings Client ID and Client Secret for the configured account. The backend should use the authentication method specified by RapidRatings, obtain an access token, and reuse it until it is close to expiry.

Before implementation is finalized, verify the token request format, required headers, response property names, expiry behavior, and error responses against the provider's sandbox contract. Store the Client Secret in the approved Z2 secret store. Do not expose credentials or tokens in API responses, logs, or audit records.

4. Source Data Identified from the API Responses
The following inventory is based on the Company, Periods, and Ratings response samples supplied for this work. It records the properties observed in those samples; it should not be treated as a complete or authoritative provider schema until checked against the provider documentation and additional responses.

A sample value of null does not establish the property's declared type or whether the field is always nullable. Those details need to be confirmed.

4.1 Company properties
Source property Observed type Notes

id string RapidRatings company identifier (RRID).

name string Company name returned by RapidRatings.

country string Country value returned by the provider.

ticker string Stock ticker, when present.

exchange string Exchange value, when present.

sector string Sector classification.

href URI Company resource URL.

financials URI Financials resource URL.

periods URI Periods resource URL.

ratings URI Ratings resource URL.

latestRatings array of objects Latest rating records included in the Company response.

reportLinks object Contains report URLs.

reportLinks.fhr URI FHR report URL.

reportLinks.financialDialog URI Financial Dialogue report URL.

reportLinks.peerBenchmark URI Peer Benchmark report URL.

address string The supplied example contains an empty value.

industryClassificationSystem null in sample Actual type and nullability need confirmation.

industryClassificationCode null in sample Actual type and nullability need confirmation.

currency string Currency associated with the financial data.

dataScale string Scale used for financial amounts, for example millions.

dataConfidential string Confidentiality indicator; the sample value is PUB.

externalSourceId null in sample Actual type and nullability need confirmation.

externalSourceIds array Element type and meaning need confirmation.

sectorAverages object in sample Contains chs and fhr; confirm availability across companies.

reports array in sample Contains report links and report types; confirm its full structure and access rules.
Latest Ratings properties
Each object in latestRatings contained the following properties in the supplied sample:

Source property Observed type

id string href URI eqyYear integer period string financialDate date reportingPeriod date fhr integer chs integer probabilityDefault number termPd object termPd.year2 through termPd.year10 number delta integer simulatedFhr integer simulatedFhrDelta integer operatingProfitability integer netProfitability integer capitalStructureEfficiency integer costStructureEfficiency integer leverage string liquidity string earningsPerformance string

4.2 Periods properties
The Periods endpoint returns financial information by reporting period. The supplied response uses a paginated structure with count, previous, next, and results.

Source property Observed type Notes

id string Period identifier, for example 2020YE or 2019Q3.

href URI Period resource URL.

eqyYear integer EQY year.

period string Period code, such as YE or Q3.

financialDate date Financial date.

currency string Currency for the financial values.

salesRevenue number Revenue.

earningsBeforeInterestAndTax number Earnings before interest and tax.

interestExpense number Interest expense.

interestReceived number or null in Interest received. sample

companyTaxExpense number Company tax expense.

abnormalItems number Abnormal items.

netProfitAfterTax number Net profit after tax.

bankCashBalances number Bank and cash balances.

totalCurrentAssets number Total current assets.

fixedAssets number Fixed assets.

financialAssets null in sample Actual type and nullability need confirmation.

totalAssets number Total assets.

debitOwnedWithinOneYear number Keep the provider property name until its official meaning is confirmed.

totalCurrentLiabilities number Total current liabilities.

termLoans number Term loans.

totalLiabilities number Total liabilities.

totalShareholderEquity number Total shareholder equity.

cashFromOperationsFromCashFlow null in sample Actual type and nullability need confirmation.

netInvestingCashFlow number Net investing cash flow.

netFundingCashFlow number Net funding cash flow.
4.3 Ratings properties
The Ratings endpoint returns rating information by reporting period. Its supplied response also uses count, previous, next, and results for pagination.

Source property Observed type

id string href URI eqyYear integer period string financialDate date reportingPeriod date fhr integer chs integer probabilityDefault number termPd object termPd.year2 through termPd.year10 number delta integer simulatedFhr integer simulatedFhrDelta integer operatingProfitability integer netProfitability integer capitalStructureEfficiency integer costStructureEfficiency integer leverage string liquidity string earningsPerformance string

5. Mapping to the Z2 Company DataModel
Add a provider-specific RapidRatings nested group to the existing Z2 Company DataModel. Do not create a new Company object type for this integration.

The proposed group is:

Z2 Company (existing)
├── Basic Information (existing NESTED)
├── Financial (existing NESTED)
│   ├── Moody's Credit Rating
│   └── D&B Rating
├── Kharon Risk Data (existing NESTED)
└── RapidRatings (new NESTED)
    ├── Company Profile
    │   ├── RRID
    │   ├── Source Company Name
    │   ├── Source Country
    │   ├── Ticker
    │   ├── Exchange
    │   ├── Sector
    │   ├── Address
    │   ├── Company / Financials / Periods / Ratings URLs
    │   ├── Currency / Data Scale / Data Confidentiality
    │   └── External Source Identifiers
    ├── Report Links
    ├── Sector Averages / Benchmark
    ├── Current Rating Summary
    ├── Latest Ratings (PROPS_ARRAY)
    ├── Periods (PROPS_ARRAY)
    └── Ratings (PROPS_ARRAY)

The final property definitions must use structures supported by the Z2 DataModel runtime. In particular, confirm whether nested objects are supported inside PROPS_ARRAY items. If they are not, represent termPd.year2 through termPd.year10 as individual leaf properties in the array item.

5.1 Company profile
Map the Company response into the RapidRatings group. Keep the provider's company name and country as source values. They must not overwrite the canonical Z2 Company name or existing Basic Information fields.

Use the provider's id as the RRID. Retain the source URLs, currency, data scale, confidentiality value, and external identifiers where available. Do not rescale financial amounts or change their currency during mapping.

5.2 Latest Ratings
Map the latestRatings response collection to a Latest Ratings PROPS_ARRAY. These records are included in the Company response and represent the latest ratings supplied there.

Keep this collection separate from the full rating history retrieved from the dedicated Ratings endpoint. Do not treat the two collections as interchangeable.

5.3 Periods
Create one Periods array entry for each record returned by the Periods endpoint. Include the period identifier, EQY year, period code, financial date, currency, resource URL, and the financial properties listed in Section 4.2.

5.4 Ratings
Create one Ratings array entry for each record returned by the Ratings endpoint. Include the period identifier, reporting dates, FHR, CHS, probability of default, term probability-of-default values, rating deltas, profitability and efficiency metrics, and the categorical assessments.

5.5 Company and period association
Use the RRID to associate RapidRatings data with the correct Z2 Company. Use the period identifier to associate a Periods record with its corresponding Ratings record.

Do not rely on array position or response ordering for this association. Confirm with RapidRatings that a Rating id uses the same identifier as the corresponding Period id before relying on that behavior.

6. Company Details Page
Add a RapidRatings section to the Company Details page and display the data associated with the selected Z2 Company.

The proposed sections are:

Company Profile: RRID, source company name, country, ticker, exchange, sector, address, and available resource links.
Current Rating Summary: FHR, CHS, probability of default, reporting period, and sector averages when available.
Latest Ratings: table of the latest rating entries.
Periods: table of financial values by reporting period.
Ratings: table of rating history.
Report Links: FHR, Financial Dialogue, and Peer Benchmark reports when available.
Adding properties to the DataModel will not automatically make them visible on the page. The Company Details layout must include the RapidRatings fields and collection tables. Confirm the UI behavior for large tables, pagination, missing values, and links before finalizing the layout.

7. Schema and Data Synchronization
Schema synchronization and business-data synchronization are separate operations and should be managed independently.

7.1 Schema synchronization
Schema sync should inspect the RapidRatings API responses and maintain the source schema used by the integration. It should identify source properties that have been added, removed, or changed.

Detected changes should be available for review before mappings are updated or activated. Schema sync must not automatically change the Z2 Company DataModel or overwrite approved mappings. The handling of new, removed, and changed properties needs to be agreed as part of the Data Integrations workflow.

7.2 Data synchronization
Data sync retrieves Company, Periods, and Ratings data for the tenant's configured scope and maps the results to Z2 Company records.

The sync process should validate the RRID and period identifiers before writing data. Re-running a sync must not create duplicate company, period, or rating entries. The update and retention rules for existing financial and rating history need to be defined.

8. Manual and Scheduled Sync
Both manual and scheduled sync must use the same processing workflow so that validation and mapping behavior stays consistent.

Manual sync: An authorized administrator can start a sync for the integration, a selected portfolio, a selected company, or another scope supported by the platform.
Scheduled sync: An administrator can configure a recurring schedule, for example daily or weekly, subject to the scheduler capabilities available in Z2.
Run status: The UI should show the status, start and finish times, selected scope, record counts, failures, and a useful error summary.
The implementation still needs decisions on supported frequencies, time-zone handling, overlapping runs, and retry behavior. These should follow the existing Z2 scheduler conventions rather than introducing a separate scheduling mechanism for RapidRatings.

9. History and Audit
Keep synchronization history and configuration change history as separate records.

9.1 Synchronization history
For each run, record:

Trigger type: manual or scheduled.
Integration and tenant.
Sync scope, such as portfolio or company.
Start time and completion time.
Final status.
Number of records processed successfully and number that failed.
Error details needed to investigate failures.
Schema and mapping versions used, where available.
9.2 Configuration change history
Record changes that affect the integration, including:

Integration enabled or disabled.
Credential rotation event (never the credential value).
Connection test result.
Source schema approval.
Mapping changes.
Company association changes.
Schedule creation or modification.
Do not store Client Secrets or access tokens in audit history.

10. Outreach API -- Not Yet in Confirmed Scope
The supplied Outreach endpoint is:

https://api.sandbox.rrilabs.co/outreach-requests

The available information does not yet establish the HTTP method, authentication requirements, request and response formats, permissions, or side effects of this API.

Before adding Outreach to the first release, confirm whether creating a request sends an email or otherwise contacts an external party. Until the API contract and business approval are available, keep Outreach separate from the initial company, periods, and ratings integration scope.

11. Security and Data Handling
The implementation must follow these rules:

Store the RapidRatings Client Secret in the approved Z2 secret-management facility.
Keep access tokens on the backend. Do not expose them to the browser or include them in logs.
Route all provider requests through the Z2 backend; do not call RapidRatings directly from the frontend.
Use HTTPS for provider communication.
Keep integration settings, credentials, sync jobs, and mapped data isolated by tenant.
Preserve provider values, numeric signs, currency, data scale, and null values.
Do not replace a null financial value with zero.
Do not overwrite the canonical Z2 Company name with the provider's company name.
Apply bounded retries and report authentication, permission, validation, and provider errors distinctly.
12. Open Items Before Implementation
The following items need confirmation before the implementation can be considered complete:

Confirm the OAuth token request and response format, expiry fields, and error responses.
Confirm the HTTP methods, pagination behavior, supported filters, and rate limits for each endpoint.
Confirm whether the Company response consistently includes sectorAverages, reports, and latestRatings.
Check whether Periods and Ratings properties are consistent across companies and reporting periods.
Confirm the actual types and nullability of fields that were null in the samples, including financialAssets, cashFromOperationsFromCashFlow, and the industry classification fields.
Confirm the element type and meaning of externalSourceIds.
Confirm whether a Rating id always matches the corresponding Period id.
Agree on how an RRID is matched to an existing Z2 Company and which identifiers are available for matching.
Verify which nested structures are supported inside PROPS_ARRAY items and how the Company Details UI renders them.
Confirm supported scheduler frequencies, time-zone rules, overlap handling, and retry behavior.
Confirm Outreach authentication, permissions, request/response formats, and external side effects if it is considered for a later release.
Confirm production API URLs, credentials, data-retention requirements, and contractual restrictions.
13. Expected Result
After implementation, a tenant administrator should be able to configure and test the RapidRatings integration in Z2 Enterprise. Z2 should retrieve the selected companies and their related financial-period and rating data, map that information to a RapidRatings group on the existing Company DataModel, and display it on the Company Details page.

The integration should also support independent schema and data synchronization, manual and scheduled runs, and traceable sync and configuration history.

The open items in Section 12 should be resolved against the RapidRatings API contract and the Z2 platform capabilities before production rollout.