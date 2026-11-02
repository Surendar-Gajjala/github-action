import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';

import { server } from '../test/server';
import { SitesPage } from './SitesPage';

const EXECUTE = '/enterprise/core/query-config-v2/execute';

/** An Enterprise `results` row, keyed by the site-query.json aliases. */
function rawSite(overrides: Record<string, unknown>): Record<string, unknown> {
  return {
    site__id: 1,
    site_id: 1201,
    internal_site_id: 'PEN-01',
    site_name: 'Penang Assembly',
    site_type: 'FINAL_ASSEMBLY',
    address_full_address: 'Plot 13, Bayan Lepas, Penang',
    address_address_line_1: 'Plot 13',
    address_address_line_2: null,
    address_address_line_3: null,
    address_city_locality: 'Bayan Lepas',
    address_district_county: null,
    address_state_province: 'Penang',
    address_postal_code: '11900',
    address_country: 'Malaysia',
    address_latitude: 5.2945,
    address_longitude: 100.2593,
    ...overrides,
  };
}

/** Serves one page per POST, Enterprise-envelope shaped, and records what was called. */
function serve(all: Record<string, unknown>[]) {
  const calls: { execute: string[] } = { execute: [] };
  server.use(
    http.post(EXECUTE, async ({ request }) => {
      const params = new URL(request.url).searchParams;
      const page = Number(params.get('page') ?? 0);
      const size = Number(params.get('size') ?? 25);
      calls.execute.push(`${page}/${size}`);
      const results = all.slice(page * size, page * size + size);
      return HttpResponse.json({
        zql: 'SELECT ...',
        properties: [],
        objects: [],
        results,
        pageNumber: page,
        pageSize: size,
        totalElements: all.length,
        hasMore: (page + 1) * size < all.length,
      });
    }),
  );
  return calls;
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SitesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const bodyRows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1);

describe('SitesPage', () => {
  it('fetches from the Enterprise execute endpoint (POST) and renders mapped columns', async () => {
    const calls = serve([
      rawSite({ site__id: 1 }),
      rawSite({ site__id: 2, site_name: 'Austin HQ', site_type: 'HQ', address_country: 'United States' }),
    ]);

    renderPage();

    expect(await screen.findByText('Penang Assembly')).toBeInTheDocument();
    for (const header of ['Site Name', 'Internal Site ID', 'Site ID', 'Site Type', 'City / Locality',
      'State / Province', 'Country', 'Full Address', 'Address Line 1', 'Address Line 2', 'Address Line 3',
      'District / County', 'Postal Code', 'Latitude', 'Longitude']) {
      expect(screen.getByRole('columnheader', { name: header })).toBeInTheDocument();
    }
    expect(screen.queryByRole('columnheader', { name: 'ID' })).not.toBeInTheDocument();
    expect(screen.getByText('Final Assembly')).toBeInTheDocument();
    expect(screen.getByText('HQ')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent('1–2 of 2');
    expect(calls.execute).toEqual(['0/25']);
  });

  it('filters the current page by location', async () => {
    serve([
      rawSite({ site__id: 1 }),
      rawSite({ site__id: 2, site_name: 'Austin HQ', address_country: 'United States', address_city_locality: 'Austin' }),
    ]);
    renderPage();
    await screen.findByText('Austin HQ');

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search sites' }), 'united states');

    await waitFor(() => expect(bodyRows()).toHaveLength(1));
    expect(screen.getByText('Austin HQ')).toBeInTheDocument();
  });

  it('shows the empty state when the hosted server has no sites', async () => {
    serve([]);

    renderPage();

    expect(await screen.findByText('No sites available.')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent('0–0 of 0');
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
  });

  it('shows an error state when the Enterprise API fails', async () => {
    server.use(
      http.post(EXECUTE, () => HttpResponse.json({ message: 'nope' }, { status: 403 })),
    );

    renderPage();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('Sites could not be loaded');
  });
});
