import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { server } from '../test/server';
import { DashboardPage } from './DashboardPage';

const EXECUTE = '/enterprise/core/query-config-v2/execute';

/**
 * Which listing a POSTed query-config payload is for: item, part or site, or "anchors"
 * for the Item Hierarchy anchor query (also an item query, but distinct top-of-BOM).
 */
async function entityOf(request: Request): Promise<string> {
  const body = (await request.json()) as { from?: { entity?: string }; distinct?: boolean };
  return body.distinct ? 'anchors' : (body.from?.entity ?? '');
}

function serveTotals(items: number, parts: number, sites: number, boms = 292) {
  const sizes: string[] = [];
  const totals: Record<string, number> = { item: items, part: parts, site: sites, anchors: boms };
  server.use(
    // Every tile comes from the Enterprise execute endpoint (POST); only the total is used here.
    http.post(EXECUTE, async ({ request }) => {
      const entity = await entityOf(request);
      if (entity === 'item') sizes.push(new URL(request.url).searchParams.get('size') ?? '');
      const totalElements = totals[entity] ?? 0;
      return HttpResponse.json({ results: [], totalElements, hasMore: totalElements > 1 });
    }),
    http.get('/enterprise/core/data-configuration/table-configs/item-hierarchy', () =>
      HttpResponse.json({ id: 'item-hierarchy', queryConfigObject: null }),
    ),
    // The tile must never trigger the slow graph traversal.
    http.post('/enterprise/core/graph/match/execute', () => HttpResponse.json({ status: 500 }, { status: 500 })),
  );
  return sizes;
}

function renderDashboard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/dashboards']}>
        <Routes>
          <Route path="/dashboards" element={<DashboardPage />} />
          <Route path="/items" element={<p>Items tab</p>} />
          <Route path="/parts" element={<p>Parts tab</p>} />
          <Route path="/sites" element={<p>Sites tab</p>} />
          <Route path="/item-hierarchy" element={<p>Item Hierarchy tab</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const tile = (name: RegExp) => screen.getByRole('link', { name });

describe('DashboardPage', () => {
  it('shows the top-level BOM, item, part and site totals', async () => {
    const sizes = serveTotals(5631, 10994, 0);

    renderDashboard();

    expect(await screen.findByTestId('Items-count')).toHaveTextContent('5,631');
    expect(await screen.findByTestId('Parts-count')).toHaveTextContent('11K');
    expect(within(tile(/parts/i)).getByText('10,994 total')).toBeInTheDocument();
    expect(await screen.findByTestId('Sites-count')).toHaveTextContent('0');
    expect(await screen.findByTestId('Top-level BOMs-count')).toHaveTextContent('292');
    expect(sizes).toEqual(['1']);
  });

  it.each([
    [/view items/i, 'Items tab'],
    [/view parts/i, 'Parts tab'],
    [/view sites/i, 'Sites tab'],
    [/view item hierarchy/i, 'Item Hierarchy tab'],
  ])('links %s to its tab', async (name, destination) => {
    serveTotals(1, 1, 1);
    renderDashboard();

    await userEvent.click(tile(name));

    expect(await screen.findByText(destination)).toBeInTheDocument();
  });

  it('marks a tile unavailable when its count fails, without hiding the others', async () => {
    serveTotals(5631, 10994, 0);
    server.use(
      http.post(EXECUTE, async ({ request }) => {
        const entity = await entityOf(request);
        if (entity === 'part') return HttpResponse.json({ message: 'timeout' }, { status: 504 });
        const totalElements = entity === 'item' ? 5631 : entity === 'anchors' ? 292 : 0;
        return HttpResponse.json({ results: [], totalElements, hasMore: false });
      }),
    );

    renderDashboard();

    expect(await within(tile(/parts/i)).findByText('Unavailable', {}, { timeout: 4000 })).toBeInTheDocument();
    expect(within(tile(/parts/i)).getByText(/Enterprise API is unavailable \(504\)/)).toBeInTheDocument();
    expect(await screen.findByTestId('Items-count')).toHaveTextContent('5,631');
  });
});
