# RapidRatings API: JSON Files and How to Get FHR and CHS

As of 2026-11-02

## Overview

A company's FHR and CHS come from the `/ratings/` endpoints or the `latestRatings` list on its company record. Six of the seven sample JSON files are API responses, and four of those six contain both scores.

The explanations here come from the sample files. The [RapidRatings documentation](https://rapidratings.stoplight.io/docs/documentation/xofd80x5ue5tr-changelog) could not be read when this was written, so endpoint paths are inferred from the `href` links in the files. The FHR and CHS definitions and score horizons are approximate, from general knowledge.

## What each JSON file is for

Use the ratings files for scores and the periods files for financial figures; `GetPortfolio.json` is not usable yet.

| File | Likely endpoint | What it returns | FHR and CHS |
| --- | --- | --- | --- |
| `ListCompanies.json` | `GET /v2.0/companies/` | All companies, 20 per page (96 in total) | Yes, in `latestRatings` |
| `Companies.json` | `GET /v2.0/companies/{companyId}/` | One company's details (Rite Aid in this sample), recent ratings and report links | Yes, in `latestRatings` |
| `ListOfRatingByCompany.json` | `GET /v2.0/companies/{companyId}/ratings/` | One company's rating history, 10 per page (26 in total) | Yes, for each period |
| `GetRatingByPeriodId.json` | `GET /v2.0/companies/{companyId}/ratings/{periodId}/` | The rating for one period | Yes |
| `ListOfPeriodsByCompany.json` | `GET /v2.0/companies/{companyId}/periods/` | Financial figures for each period, such as revenue, assets and debt | No |
| `PeriodById.json` | `GET /v2.0/companies/{companyId}/periods/{periodId}/` | Financial figures for one period | No |
| `GetPortfolio.json` | Unknown | Not a real response: only a companies URL with a stray `"` at the end | No |

- **Base URL:** all samples use the sandbox, `https://rridata.sandbox.rrilabs.co`. Production will probably use a different address.
- **Sample company:** the ratings and periods files, including the single-period ones, are for Tyson Foods (`XAZPzdJ`).
- **Period IDs:** the format is year plus period, such as `2020Q2` or `2019YE` (year end).

## How to get a company's FHR and CHS

Find the company's ID first, then request its scores with that ID.

1. **Find the company ID.** Call `/companies/` and match on `name` or `ticker`. Follow the `next` link until it is `null`; 96 companies at 20 per page means 5 pages.
2. **Get the scores you need.**
    - Latest: call `/companies/{companyId}/` and read `fhr` and `chs` from the first `latestRatings` entry.
    - History: call `/companies/{companyId}/ratings/` and follow `next` for more pages.
    - A specific period: call `/companies/{companyId}/ratings/{periodId}/`.

For example, Tyson Foods (`XAZPzdJ`) returns this for `2020Q2`, shortened to the key fields:

```json
{ "id": "2020Q2", "financialDate": "2020-03-28", "fhr": 49, "chs": 48, "probabilityDefault": 0.3343 }
```

## What the scores and fields mean

FHR shows short-term financial health and CHS shows medium-term operating health; both run from 0 to 100.

- **FHR (Financial Health Rating):** short-term financial health and default risk, over about 12 months.
- **CHS (Core Health Score):** medium-term operating health, over about 2–3 years.

A large gap between them can be a warning sign. Polaris has an FHR of 40 but a CHS of 18, which suggests its core operations are weaker than its FHR alone shows.

| Rating field | Meaning |
| --- | --- |
| `delta` | Change in FHR from the same period a year earlier. Rite Aid's 2020YE is 35 and 2019YE is 22, giving the +13 in the file. |
| `probabilityDefault` | Probability of default, apparently a percentage and probably 1-year. |
| `termPd` | Probability of default for years 2–10. It rises each year, so it looks cumulative. |
| `simulatedFhr`, `simulatedFhrDelta` | A simulated version of FHR and its change from a year earlier. The docs should explain how it is simulated. |
| `leverage`, `liquidity`, `earningsPerformance` | Text labels, such as `Weak` or `Average`. |
| `operatingProfitability`, `netProfitability`, `capitalStructureEfficiency`, `costStructureEfficiency` | Numeric scores for each area. |

## Things to watch for

Check each rating's date before using it, because "latest" does not always mean recent.

- **Old data:** Stillwater Mining's newest rating is `2016YE`, so always check `financialDate`.
- **Rating order:** the newest rating came first in the samples checked, but sort by `financialDate` rather than rely on that.
- **Varying history:** `latestRatings` has 2–5 entries depending on the company, so do not assume a fixed number.
- **Missing values:** some fields can be `null`, such as `cashFromOperationsFromCashFlow`, so code must handle that.

## To confirm in the official docs

Three details could not be found in the sample files.

- [ ] How to log in (the API key or token format)
- [ ] The real portfolio endpoint, so its actual response can be saved in `GetPortfolio.json`
- [ ] Whether companies can be searched by ticker directly, instead of going through every page
