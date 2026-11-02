import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

import { server } from '../test/server';
import { ItemDetailsPage } from './ItemDetailsPage';

const OBJECT = '/enterprise/core/objects/partial/:id';
const GRAPH = '/enterprise/core/graph/match/execute';

/** Partial-object response shape: every property wrapped with metadata; only `value` is used. */
const wrap = (values: Record<string, unknown>) =>
  Object.fromEntries(Object.entries(values).map(([k, value], i) => [k, { id: i, value, property_id: i, is_overriden: false }]));

const ITEM_OBJECT = {
  objectId: 402600,
  objectTypeId: 137,
  objectTypeDbName: 'item',
  scope: { id: 516 },
  properties: wrap({
    item_number: 'PRD-1000',
    description: 'Laptop 14in i7 16GB',
    type: 'FINISHED_GOOD',
    business_unit: 'Client Computing',
    item_lifecycle_status: 'N/A',
    'item_risk.overview': null,
    'item_risk.overview.band': 'MEDIUM',
    'item_risk.overview.score': '26',
    'item_risk.overview.dominant_axis': 'SOURCING',
    'multi_sourcing.status': 'SINGLE_SOURCE',
    'env_compliance.eu_rohs.status': 'NON_COMPLIANT',
    'env_compliance.eu_rohs.exemptions': ['6', '7'],
  }),
};

const SOURCES = {
  nodes: [
    { id: 4100, type: 'item', alias: null, properties: {} },
    { id: 150800, type: 'part', alias: 'leaf', properties: { part_number: 'MIC5159YM6-TR', manufacturer: 'MCHP', description: 'IC VOLT REG', 'origin.country_of_origin': 'US', 'lifecycle.status': 'ACTIVE' } },
    { id: 150700, type: 'part', alias: 'leaf', properties: { part_number: 'AD536AKDZ', manufacturer: 'ANALOG DEVICES', 'lifecycle.status': 'OBSOLETE' } },
  ],
  edges: [],
};

function serve() {
  const calls: string[] = [];
  const graphBodies: { traversal: Record<string, unknown>; select: Record<string, unknown> }[] = [];
  server.use(
    http.get(OBJECT, ({ params }) => {
      calls.push(`object:${params.id}`);
      return HttpResponse.json(ITEM_OBJECT);
    }),
    http.post(GRAPH, async ({ request }) => {
      calls.push('sources');
      graphBodies.push((await request.json()) as (typeof graphBodies)[number]);
      return HttpResponse.json(SOURCES);
    }),
  );
  return { calls, graphBodies };
}

function Location() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/items/:id" element={<ItemDetailsPage />} />
        </Routes>
        <Location />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ItemDetailsPage', () => {
  it('shows overview sections from the Enterprise object, without loading Sources', async () => {
    const { calls } = serve();

    renderAt('/items/402600');

    expect(await screen.findByRole('heading', { name: 'PRD-1000' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Overview' })).toHaveAttribute('aria-selected', 'true');
    // Type shows as a header badge and in General.
    expect(screen.getAllByText('Finished Good')).toHaveLength(2);

    const general = screen.getByRole('region', { name: 'General' });
    expect(within(general).getByText('Client Computing')).toBeInTheDocument();
    const risk = screen.getByRole('region', { name: 'Risk' });
    expect(within(risk).getByText('MEDIUM')).toBeInTheDocument();
    expect(within(risk).getByText('Score 26 · Driven by: Sourcing')).toBeInTheDocument();
    const sourcing = screen.getByRole('region', { name: 'Sourcing' });
    expect(within(sourcing).getByText('Single Source')).toBeInTheDocument();
    const compliance = screen.getByRole('region', { name: 'Environmental Compliance' });
    expect(within(compliance).getByText('Non Compliant')).toBeInTheDocument();
    expect(within(compliance).getByText('6, 7')).toBeInTheDocument();

    expect(calls).toEqual(['object:402600']);
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['Overview', 'Sources']);
  });

  it('Sources tab runs one item_sources graph match on the item id and lists the parts', async () => {
    const { calls, graphBodies } = serve();
    renderAt('/items/402600');
    await screen.findByRole('heading', { name: 'PRD-1000' });

    await userEvent.click(screen.getByRole('tab', { name: 'Sources' }));

    expect(await screen.findByText('MIC5159YM6-TR')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'MIC5159YM6-TR' })).toHaveAttribute('href', '/parts/150800');
    expect(screen.getByText('Obsolete')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Lifecycle Status' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Sources\s*2/ })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByTestId('location')).toHaveTextContent('/items/402600?tab=sources');
    // Sorted by part number; the anchor item node is not a source.
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
    expect(rows.map((row) => within(row).getAllByRole('link')[0].textContent)).toEqual(['AD536AKDZ', 'MIC5159YM6-TR']);
    expect(graphBodies[0].traversal).toMatchObject({ relationshipTypes: ['item_sources'], anchorProperty: 'id', anchorValues: ['402600'] });
    expect(graphBodies[0].select.part).not.toContain('z2_properties_comparison');
    expect(calls).toEqual(['object:402600', 'sources']);
  });

  it('shows not found when Enterprise returns an empty body for an unknown id', async () => {
    server.use(http.get(OBJECT, () => new HttpResponse(null, { status: 200 })));

    renderAt('/items/1');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Item could not be loaded');
    expect(alert).toHaveTextContent('No item exists with this id.');
  });

  it('shows not found when the id belongs to a part', async () => {
    server.use(http.get(OBJECT, () => HttpResponse.json({ ...ITEM_OBJECT, objectTypeDbName: 'part' })));

    renderAt('/items/150800');

    expect(await screen.findByRole('alert')).toHaveTextContent('No item exists with this id.');
  });

  it('falls back to Overview for an old Where Used link', async () => {
    serve();

    renderAt('/items/402600?tab=where-used');

    expect(await screen.findByRole('region', { name: 'General' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Overview' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.queryByRole('tab', { name: /Where Used/ })).not.toBeInTheDocument();
  });

  it('rejects an invalid item link without calling the API', async () => {
    const { calls } = serve();

    renderAt('/items/abc');

    expect(screen.getByText('This item link is not valid.')).toBeInTheDocument();
    expect(calls).toEqual([]);
  });
});
