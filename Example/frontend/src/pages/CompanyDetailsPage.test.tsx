import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { server } from '../test/server';
import { CompanyDetailsPage } from './CompanyDetailsPage';

const OBJECT = '/enterprise/core/objects/partial/:id';
const RR_HOST = 'https://rridata.sandbox.rrilabs.co/v2.0';

/** The Enterprise partial-object shape: properties keyed by dotted name, wrapped as `{ value }`. */
function companyObject(id: number, name: string) {
  const properties: Record<string, { value: unknown }> = {
    company_name: { value: name },
    company_tier: { value: 'TIER_1' },
    'basic_info.country': { value: 'US' },
    'basic_info.duns_number': { value: '969484609' },
    'supplier_risk.band': { value: 'LOW' },
    'supplier_risk.score': { value: '0' },
    'certifications.iso_14001.status': { value: 'EXPIRED' },
    'certifications.iso_14001.issuer': { value: 'BSI' },
    'certifications.iso_14001.expiry': { value: '2026-07-02' },
    'cyber.has_ciso': { value: 'false' },
  };
  return { objectId: id, objectTypeDbName: 'company', properties };
}

/** A RapidRatings rating, as the API returns it. */
const rating = (rrid: string, id: string, fhr: number) => ({
  id,
  href: `${RR_HOST}/companies/${rrid}/ratings/${id}/`,
  eqyYear: Number(id.slice(0, 4)),
  period: id.slice(4),
  financialDate: '2020-12-31',
  reportingPeriod: '2021-03-15',
  fhr,
  chs: 55,
  probabilityDefault: 0.35,
  termPd: { year2: 1.1, year3: 2.2, year4: 3.3, year5: 4.4, year6: 5.5, year7: 6.6, year8: 7.7, year9: 8.8, year10: 9.9 },
  delta: null,
  simulatedFhr: 70,
  simulatedFhrDelta: null,
  operatingProfitability: 61,
  netProfitability: 58,
  capitalStructureEfficiency: 44,
  costStructureEfficiency: 80,
  leverage: 'Strong',
  liquidity: 'Average',
  earningsPerformance: 'Weak',
});

const period = (rrid: string, id: string, sales: number | null) => ({
  id,
  href: `${RR_HOST}/companies/${rrid}/periods/${id}/`,
  eqyYear: Number(id.slice(0, 4)),
  period: id.slice(4),
  financialDate: '2020-12-31',
  currency: 'USD',
  salesRevenue: sales,
  netProfitAfterTax: -12.5,
  cashFromOperationsFromCashFlow: null,
});

const rrCompany = (rrid: string, name: string) => ({
  id: rrid,
  name,
  country: 'USA',
  ticker: 'SNX',
  exchange: 'US',
  sector: 'Retail',
  href: `${RR_HOST}/companies/${rrid}/`,
  currency: 'USD',
  dataScale: 'millions',
  dataConfidential: 'PUB',
  externalSourceIds: [],
  reportLinks: { fhr: 'https://portal.example/fhr' },
  latestRatings: [rating(rrid, '2020YE', 72), rating(rrid, '2020Q3', 65)],
});

/** Serves the Enterprise company plus a RapidRatings sandbox with paged periods and ratings; records the calls. */
function serve(options: { name?: string; searchResults?: ReturnType<typeof rrCompany>[] } = {}) {
  const calls: string[] = [];
  const results = options.searchResults ?? [rrCompany('ABC1234', 'Sunex Inc.'), rrCompany('XYZ9876', 'Sunex Optics')];
  server.use(
    http.get(OBJECT, ({ params }) => HttpResponse.json(companyObject(Number(params.id), options.name ?? 'Sunex Inc'))),
    http.get('/rapidratings/companies/', ({ request }) => {
      const url = new URL(request.url);
      calls.push(`search ${url.searchParams.get('name')}`);
      return HttpResponse.json({ count: results.length, next: null, previous: null, results });
    }),
    http.get('/rapidratings/companies/:rrid/', ({ params }) => {
      calls.push(`company ${params.rrid}`);
      return HttpResponse.json({ ...rrCompany(String(params.rrid), 'Sunex Inc.'), sectorAverages: { fhr: 51.5, chs: 48 } });
    }),
    http.get('/rapidratings/companies/:rrid/periods/', ({ params, request }) => {
      const page = new URL(request.url).searchParams.get('page') ?? '1';
      calls.push(`periods ${params.rrid} p${page}`);
      const rrid = String(params.rrid);
      return page === '1'
        ? HttpResponse.json({ count: 2, previous: null, next: `${RR_HOST}/companies/${rrid}/periods/?page=2`, results: [period(rrid, '2020YE', 1575.0)] })
        : HttpResponse.json({ count: 2, previous: null, next: null, results: [period(rrid, '2019YE', null)] });
    }),
    http.get('/rapidratings/companies/:rrid/ratings/', ({ params }) => {
      calls.push(`ratings ${params.rrid}`);
      const rrid = String(params.rrid);
      return HttpResponse.json({ count: 3, previous: null, next: null, results: [rating(rrid, '2020YE', 72), rating(rrid, '2020Q3', 65), rating(rrid, '2019YE', 60)] });
    }),
  );
  return calls;
}

function renderPage(path = '/companies/13200') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/companies/:id" element={<CompanyDetailsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const section = (name: string) => screen.getByRole('region', { name: new RegExp(`^${name}`) });

describe('CompanyDetailsPage', () => {
  it('shows the Enterprise company overview without calling RapidRatings', async () => {
    const calls = serve();
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: 'Sunex Inc' })).toBeInTheDocument();
    expect(within(section('General')).getByText('Tier 1')).toBeInTheDocument();
    expect(within(section('Identifiers')).getByText('969484609')).toBeInTheDocument();
    expect(within(section('Risk')).getByText('LOW')).toBeInTheDocument();
    expect(within(section('Certifications')).getByText('Issued by BSI · Expires 2026-07-02')).toBeInTheDocument();
    expect(calls).toEqual([]);
  });

  it('shows the fixed demo RapidRatings company (Rite Aid, KawjbGJ) with its profile, periods and ratings', async () => {
    const calls = serve();
    renderPage();
    await screen.findByRole('heading', { level: 1, name: 'Sunex Inc' });

    await userEvent.click(screen.getByRole('tab', { name: 'RapidRatings' }));

    expect(await screen.findByText(/Demo data: every company shows the RapidRatings sandbox company/)).toHaveTextContent(
      'Rite Aid Corporation (RRID KawjbGJ)',
    );

    const profile = await screen.findByRole('region', { name: 'Profile' });
    expect(within(profile).getByText('KawjbGJ')).toBeInTheDocument();
    expect(within(profile).getByText('USA')).toBeInTheDocument();
    expect(within(section('Current Rating')).getByText('2020YE')).toBeInTheDocument();
    expect(within(section('Benchmark')).getByText('51.5')).toBeInTheDocument();
    expect(within(section('Report Links')).getByRole('link', { name: 'https://portal.example/fhr' })).toHaveAttribute('target', '_blank');

    // Latest ratings from the company; periods from both pages (following `next`); full ratings history.
    expect(within(section('Latest Ratings')).getAllByRole('row')).toHaveLength(3);
    const periods = section('Periods');
    expect(within(periods).getAllByRole('row')).toHaveLength(3);
    expect(within(periods).getByText('Amounts in USD millions')).toBeInTheDocument();
    expect(within(periods).getByText('1,575')).toBeInTheDocument();
    expect(within(periods).getAllByText('-12.5')).toHaveLength(2);
    expect(within(section('Ratings')).getAllByRole('row')).toHaveLength(4);

    // No name search; every call goes through the same-origin proxy, following `next` for periods.
    expect(calls.some((c) => c.startsWith('search'))).toBe(false);
    expect(calls).toEqual(expect.arrayContaining(['company KawjbGJ', 'periods KawjbGJ p1', 'periods KawjbGJ p2', 'ratings KawjbGJ']));
  });

  it('shows the proxy message when RapidRatings is not configured', async () => {
    serve();
    server.use(
      http.get('/rapidratings/companies/:rrid/', () =>
        HttpResponse.json(
          { code: 'PROXY_NOT_CONFIGURED', message: 'The RapidRatings proxy is not configured: missing RAPIDRATINGS_CLIENT_ID.' },
          { status: 500, headers: { 'X-RapidRatings-Proxy-Error': 'PROXY_NOT_CONFIGURED' } },
        ),
      ),
    );
    renderPage('/companies/13200?tab=rapidratings');

    const alert = await screen.findByRole('alert', {}, { timeout: 6000 });
    expect(alert).toHaveTextContent('missing RAPIDRATINGS_CLIENT_ID');
  });

  it('reports an unknown company id as not found', async () => {
    server.use(http.get(OBJECT, () => new HttpResponse(null, { status: 200 })));
    renderPage('/companies/999');

    expect(await screen.findByRole('alert')).toHaveTextContent('No company exists with this id.');
  });
});
