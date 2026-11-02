import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';

import { server } from '../test/server';
import { PartsPage } from './PartsPage';

const EXECUTE = '/enterprise/core/query-config-v2/execute';

/** An Enterprise `results` row, keyed by the part-query.json / ExternalPartRecord aliases. */
function rawPart(overrides: Record<string, unknown>): Record<string, unknown> {
  return {
    part__id: 1,
    part_number: 'FC FBMJ3216HS480NT',
    manufacturer: 'TAIYO YUDEN,(USA),INC',
    description: null,
    z2_properties_comparison: 'incomparable',
    part__country_of_origin: 'China',
    part__sourcing_type: 'OTS',
    part__supply_chain_risk: '',
    part__lifecycle__status: null,
    ...overrides,
  };
}

interface Calls {
  execute: string[];
  bodies: Record<string, unknown>[];
}

/** Serves `all` the way Enterprise does: one page per POST, with paging metadata. */
function servePaged(all: Record<string, unknown>[]): Calls {
  const calls: Calls = { execute: [], bodies: [] };
  server.use(
    http.post(EXECUTE, async ({ request }) => {
      const params = new URL(request.url).searchParams;
      const page = Number(params.get('page') ?? 0);
      const size = Number(params.get('size') ?? 25);
      calls.execute.push(`${page}/${size}`);
      calls.bodies.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json({
        properties: [],
        results: all.slice(page * size, page * size + size),
        pageNumber: page,
        pageSize: size,
        totalElements: all.length,
        exactTotal: true,
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
        <PartsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const bodyRows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1);

describe('PartsPage', () => {
  it('POSTs part-query.json to the Enterprise execute endpoint', async () => {
    const calls = servePaged([rawPart({})]);

    renderPage();

    expect(await screen.findByText('FC FBMJ3216HS480NT')).toBeInTheDocument();
    expect(calls.execute).toEqual(['0/25']);
    expect(calls.bodies[0]).toMatchObject({
      from: { entity: 'part', alias: 'part' },
      output: { format: 'table' },
      context: { tableId: 'objecttype-view-part' },
    });
  });

  it('shows every part query-config property as a column', async () => {
    servePaged([
      rawPart({ part__id: 1 }),
      rawPart({
        part__id: 2,
        part_number: 'P-2',
        manufacturer: 'Murata',
        part__sourcing_type: 'custom',
        part__supply_chain_risk: 'medium',
        part__lifecycle__status: 'ACTIVE',
      }),
    ]);

    renderPage();

    expect(await screen.findByText('FC FBMJ3216HS480NT')).toBeInTheDocument();
    for (const header of ['Part Number', 'Manufacturer', 'Description', 'Sourcing Type', 'Supply Chain Risk',
      'Lifecycle Status', 'Country of Origin', 'Z2 Properties Comparison']) {
      expect(screen.getByRole('columnheader', { name: header })).toBeInTheDocument();
    }
    expect(screen.queryByRole('columnheader', { name: 'ID' })).not.toBeInTheDocument();
    expect(screen.getByText('Off-the-shelf')).toBeInTheDocument();
    expect(screen.getByText('MEDIUM')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Parts' })).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent('1–2 of 2');
  });

  it('links each part number to its part details page using part__id', async () => {
    servePaged([rawPart({ part__id: 42 })]);

    renderPage();

    const link = await screen.findByRole('link', { name: 'FC FBMJ3216HS480NT' });
    expect(link).toHaveAttribute('href', '/parts/42');
  });

  it('filters the current page by manufacturer', async () => {
    servePaged([rawPart({ part__id: 1 }), rawPart({ part__id: 2, part_number: 'P-2', manufacturer: 'Murata' })]);
    renderPage();
    await screen.findByText('P-2');

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search parts' }), 'murata');

    await waitFor(() => expect(bodyRows()).toHaveLength(1));
    expect(screen.getByText('P-2')).toBeInTheDocument();
  });

  it('requests the next page from Enterprise', async () => {
    const calls = servePaged(Array.from({ length: 40 }, (_, i) => rawPart({ part__id: i + 1, part_number: `PRT-${i + 1}` })));
    renderPage();
    await screen.findByText('PRT-1');

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(await screen.findByText('PRT-26')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent('26–40 of 40');
    expect(calls.execute).toEqual(['0/25', '1/25']);
  });

  it('uses hasMore when Enterprise reports no total', async () => {
    server.use(
      http.post(EXECUTE, () =>
        HttpResponse.json({ results: [rawPart({})], pageNumber: 0, pageSize: 25, exactTotal: false, hasMore: true }),
      ),
    );

    renderPage();

    await screen.findByText('FC FBMJ3216HS480NT');
    expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled();
  });

  it('shows an error state when the Enterprise API fails', async () => {
    server.use(http.post(EXECUTE, () => HttpResponse.json({ message: 'slow' }, { status: 504 })));

    renderPage();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('Parts could not be loaded');
    expect(alert).toHaveTextContent('The Enterprise API is unavailable (504).');
  });

  it('shows the deployment proxy\'s own message for a proxy configuration error', async () => {
    server.use(
      http.post(EXECUTE, () =>
        HttpResponse.json(
          { code: 'PROXY_NOT_CONFIGURED', message: 'The Enterprise proxy is not configured: missing ENTERPRISE_API_TOKEN.' },
          { status: 500, headers: { 'X-Enterprise-Proxy-Error': 'PROXY_NOT_CONFIGURED' } },
        ),
      ),
    );

    renderPage();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('The Enterprise proxy is not configured: missing ENTERPRISE_API_TOKEN.');
    expect(alert).not.toHaveTextContent('unavailable (500)');
  });

  it('reports an unexpected response shape instead of rendering it', async () => {
    server.use(http.post(EXECUTE, () => HttpResponse.json({ results: 'nope' })));

    renderPage();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('Unexpected response from the Enterprise API.');
  });
});
