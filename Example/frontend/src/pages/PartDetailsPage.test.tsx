import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { server } from '../test/server';
import { PartDetailsPage } from './PartDetailsPage';

const OBJECT = '/enterprise/core/objects/partial/:id';

const wrap = (values: Record<string, unknown>) =>
  Object.fromEntries(Object.entries(values).map(([k, value], i) => [k, { id: i, value, property_id: i }]));

const PART_OBJECT = {
  objectId: 150800,
  objectTypeDbName: 'part',
  properties: wrap({
    part_number: 'MIC5159YM6-TR',
    manufacturer: 'MCHP',
    description: 'IC VOLT REG Positive Adjustable',
    'part_risk.obsolescence.band': 'HIGH',
    'part_risk.obsolescence.score': '40',
    'part_risk.obsolescence.dominant_factor': 'MULTI_SOURCE',
    'lifecycle.status': 'ACTIVE',
    'lifecycle.is_at_risk': 'false',
    'sourcing.is_sole_sourced': 'true',
    inventory_position_weeks: '44',
    'origin.country_of_assembly': ['TH'],
    'parametric.operating_temp.min_c': '-40',
    'parametric.operating_temp.max_c': '125',
    'parametric.voltage_rating.value': '16',
    'parametric.voltage_rating.unit': 'V',
    'env_compliance.reach_svhc.status': 'SVHC_BELOW_THRESHOLD',
    'env_compliance.reach_svhc.list_version': '2025-01',
  }),
};

function serve() {
  const calls: string[] = [];
  server.use(
    http.get(OBJECT, ({ params }) => {
      calls.push(`object:${params.id}`);
      return HttpResponse.json(PART_OBJECT);
    }),
  );
  return calls;
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/parts/:id" element={<PartDetailsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PartDetailsPage', () => {
  it('shows overview sections from the Enterprise object', async () => {
    const calls = serve();

    renderAt('/parts/150800');

    expect(await screen.findByRole('heading', { name: 'MIC5159YM6-TR' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Overview' })).toHaveAttribute('aria-selected', 'true');
    const risk = screen.getByRole('region', { name: 'Risk' });
    expect(within(risk).getByText('HIGH')).toBeInTheDocument();
    expect(within(risk).getByText('Score 40 · Driven by: Multi Source')).toBeInTheDocument();
    const lifecycle = screen.getByRole('region', { name: 'Lifecycle' });
    expect(within(lifecycle).getByText('Active')).toBeInTheDocument();
    expect(within(lifecycle).getByText('No')).toBeInTheDocument();
    const sourcing = screen.getByRole('region', { name: 'Sourcing' });
    expect(within(sourcing).getByText('Yes')).toBeInTheDocument();
    expect(within(sourcing).getByText('44 weeks')).toBeInTheDocument();
    const technical = screen.getByRole('region', { name: 'Technical' });
    expect(within(technical).getByText('-40 °C to 125 °C')).toBeInTheDocument();
    expect(within(technical).getByText('16 V')).toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Origin & Trade' })).getByText('TH')).toBeInTheDocument();
    const compliance = screen.getByRole('region', { name: 'Environmental Compliance' });
    expect(within(compliance).getByText('SVHC Below Threshold')).toBeInTheDocument();
    expect(within(compliance).getByText('SVHC list 2025-01')).toBeInTheDocument();
    expect(calls).toEqual(['object:150800']);
  });

  it('shows not found for an unknown part', async () => {
    server.use(http.get(OBJECT, () => new HttpResponse(null, { status: 200 })));

    renderAt('/parts/1');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Part could not be loaded');
    expect(alert).toHaveTextContent('No part exists with this id.');
  });

  it('rejects an invalid part link without calling the API', async () => {
    const calls = serve();

    renderAt('/parts/abc');

    expect(screen.getByText('This part link is not valid.')).toBeInTheDocument();
    expect(calls).toEqual([]);
  });
});
