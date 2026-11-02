import { mapCompany, mapPeriod, mapRating } from './rapidRatingsMapper';

const RATING = {
  id: '2020YE',
  href: 'https://rr.example/v2.0/companies/ABC1234/ratings/2020YE/',
  eqyYear: 2020,
  period: 'YE',
  financialDate: '2020-12-31',
  reportingPeriod: '2021-03-15',
  fhr: 72.0,
  chs: 55,
  probabilityDefault: 0.35,
  termPd: { year2: 1.1, year3: 2.2, year10: 9.75 },
  delta: null,
  simulatedFhr: 70,
  simulatedFhrDelta: -3,
  operatingProfitability: 61,
  netProfitability: 58,
  capitalStructureEfficiency: 44,
  costStructureEfficiency: 80,
  leverage: 'Strong',
  liquidity: 'Average',
  earningsPerformance: 'Weak',
};

describe('mapRating', () => {
  it('maps every rating field, flattening termPd by year', () => {
    const r = mapRating(RATING);
    expect(r).toMatchObject({
      ratingId: '2020YE',
      eqyYear: 2020,
      fhr: 72,
      chs: 55,
      probabilityDefault: 0.35,
      simulatedFhrDelta: -3,
      leverage: 'Strong',
      ratingUrl: RATING.href,
    });
    expect(r.termPd[2]).toBe(1.1);
    expect(r.termPd[10]).toBe(9.75);
    expect(r.termPd[5]).toBeNull();
  });

  it('keeps null as null and rejects non-integers in integer fields', () => {
    const r = mapRating({ ...RATING, delta: null, fhr: 72.5 });
    expect(r.delta).toBeNull();
    expect(r.fhr).toBeNull();
  });
});

describe('mapPeriod', () => {
  it('keeps nulls, negative signs and precision, and renames the provider spelling', () => {
    const p = mapPeriod({
      id: '2019Q3',
      eqyYear: 2019,
      period: 'Q3',
      financialDate: '2019-09-30',
      currency: 'USD',
      salesRevenue: 1575.0,
      netProfitAfterTax: -12.345678,
      cashFromOperationsFromCashFlow: null,
      debitOwnedWithinOneYear: 42.5,
    });
    expect(p.periodId).toBe('2019Q3');
    expect(p.salesRevenue).toBe(1575);
    expect(p.netProfitAfterTax).toBe(-12.345678);
    expect(p.cashFromOperationsFromCashFlow).toBeNull();
    expect(p.debtDueWithinOneYear).toBe(42.5);
    expect(p.termLoans).toBeNull(); // missing
  });
});

describe('mapCompany', () => {
  it('maps the profile and derives the current rating from latestRatings[0]', () => {
    const c = mapCompany({
      id: 'ABC1234',
      name: 'Sunex Inc.',
      country: 'USA',
      ticker: 'SNX',
      exchange: 'US',
      sector: 'Retail',
      address: '',
      href: 'https://rr.example/v2.0/companies/ABC1234/',
      currency: 'USD',
      dataScale: 'millions',
      dataConfidential: 'PUB',
      industryClassificationSystem: null,
      externalSourceId: null,
      externalSourceIds: [],
      reportLinks: { fhr: 'https://portal.example/fhr', financialDialog: 'https://portal.example/fd' },
      sectorAverages: { fhr: 51.5, chs: 48 },
      latestRatings: [RATING, { ...RATING, id: '2020Q3', fhr: 65 }],
    });
    expect(c).toMatchObject({
      rrid: 'ABC1234',
      sourceCompanyName: 'Sunex Inc.',
      sourceCountry: 'USA',
      dataConfidentiality: 'PUB',
      reportLinkFhr: 'https://portal.example/fhr',
      reportLinkPeerBenchmark: null,
      sectorAverageFhr: 51.5,
      currentRatingId: '2020YE',
      currentFhr: 72,
      currentProbabilityDefault: 0.35,
      currentFinancialDate: '2020-12-31',
    });
    expect(c.address).toBeNull();
    expect(c.externalSourceIds).toEqual([]);
    expect(c.latestRatings.map((r) => r.ratingId)).toEqual(['2020YE', '2020Q3']);
  });

  it('leaves the current rating and sector averages null when absent (list endpoint)', () => {
    const c = mapCompany({ id: 'X1', name: 'X' });
    expect(c.currentFhr).toBeNull();
    expect(c.sectorAverageChs).toBeNull();
    expect(c.latestRatings).toEqual([]);
  });
});
