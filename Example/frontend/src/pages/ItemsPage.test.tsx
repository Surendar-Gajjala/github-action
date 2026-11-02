import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';

import { server } from '../test/server';
import { ItemsPage } from './ItemsPage';

const EXECUTE = '/enterprise/core/query-config-v2/execute';

/** An Enterprise `results` row, keyed by the item-query.json / ExternalItemRecord aliases. */
function rawItem(overrides: Record<string, unknown>): Record<string, unknown> {
  return {
    item__id: 1,
    item_number: 'A-1',
    description: 'Resistor',
    item__revision: '01',
    item__business_unit: null,
    item__is_product: false,
    item__structure_role: 'ITEM',
    item__odm__name: [],
    item__odm__active: [],
    item__availability_risk: null,
    item__used_in_products: '99DKX3',
    item__product_families_impacted: 1,
    item__item_status__name: 'Production Approved',
    ...overrides,
  };
}

const TWO_ITEMS = [
  rawItem({ item__id: 1, item_number: 'A93548-290', description: 'RES D,0402,169.00 kOHM' }),
  rawItem({
    item__id: 2,
    item_number: 'ASSEMBLY-002',
    description: 'Mainboard assembly',
    item__structure_role: 'bom',
    item__odm__name: "['Flextronics']",
    item__odm__active: [true],
    item__item_status__name: 'Design',
  }),
];

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
        <ItemsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const bodyRows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1);

const manyItems = (n: number) =>
  Array.from({ length: n }, (_, i) => rawItem({ item__id: i + 1, item_number: `ITM-${i + 1}` }));

describe('ItemsPage', () => {
  it('POSTs item-query.json to the Enterprise execute endpoint', async () => {
    const calls = servePaged(TWO_ITEMS);

    renderPage();

    expect(await screen.findByText('A93548-290')).toBeInTheDocument();
    expect(calls.execute).toEqual(['0/25']);
    expect(calls.bodies[0]).toMatchObject({
      from: { entity: 'item', alias: 'item' },
      output: { format: 'table' },
      context: { tableId: 'objecttype-view-item' },
    });
  });

  it('shows every query-config property as a column with the mapped items', async () => {
    servePaged(TWO_ITEMS);

    renderPage();

    expect(await screen.findByText('A93548-290')).toBeInTheDocument();
    for (const header of ['Item Number', 'Description', 'Revision', 'Business Unit', 'Product', 'Structure Role',
      'ODM Name', 'ODM Active', 'Availability Risk', 'Used In Products', 'Families Impacted', 'Item Status']) {
      expect(screen.getByRole('columnheader', { name: header })).toBeInTheDocument();
    }
    expect(screen.queryByRole('columnheader', { name: 'ID' })).not.toBeInTheDocument();
    expect(screen.getByText('Flextronics')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('BOM')).toBeInTheDocument();
    expect(bodyRows()).toHaveLength(2);
    expect(screen.queryByRole('button', { name: /add item/i })).not.toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent('1–2 of 2');
  });

  it('filters the current page by number or description', async () => {
    servePaged(TWO_ITEMS);
    renderPage();
    await screen.findByText('A93548-290');

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search items' }), 'mainboard');

    await waitFor(() => expect(bodyRows()).toHaveLength(1));
    expect(screen.getByText('ASSEMBLY-002')).toBeInTheDocument();
  });

  it('requests the next and previous pages from Enterprise', async () => {
    const calls = servePaged(manyItems(60));
    renderPage();
    await screen.findByText('ITM-1');
    const footer = screen.getByRole('contentinfo');
    const previous = screen.getByRole('button', { name: 'Previous page' });
    const next = screen.getByRole('button', { name: 'Next page' });

    expect(footer).toHaveTextContent('1–25 of 60');
    expect(previous).toBeDisabled();

    await userEvent.click(next);
    expect(await screen.findByText('ITM-26')).toBeInTheDocument();
    expect(footer).toHaveTextContent('26–50 of 60');
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page');

    await userEvent.click(next);
    expect(await screen.findByText('ITM-51')).toBeInTheDocument();
    expect(footer).toHaveTextContent('51–60 of 60');
    expect(bodyRows()).toHaveLength(10);
    expect(next).toBeDisabled();

    await userEvent.click(previous);
    expect(await screen.findByText('ITM-26')).toBeInTheDocument();
    expect(footer).toHaveTextContent('26–50 of 60');

    expect(calls.execute).toEqual(['0/25', '1/25', '2/25']);
  });

  it('changes rows per page and restarts at page 1', async () => {
    const calls = servePaged(manyItems(60));
    renderPage();
    await screen.findByText('ITM-1');

    await userEvent.click(screen.getByRole('button', { name: 'Page 2' }));
    await screen.findByText('ITM-26');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Rows' }), '10');

    await waitFor(() => expect(screen.getByRole('contentinfo')).toHaveTextContent('1–10 of 60'));
    expect(screen.getByRole('button', { name: 'Page 6' })).toBeInTheDocument();
    expect(calls.execute.at(-1)).toBe('0/10');
  });

  it('links each item number to its details page using item__id', async () => {
    servePaged(TWO_ITEMS);
    renderPage();

    const link = await screen.findByRole('link', { name: 'A93548-290' });

    expect(link).toHaveAttribute('href', '/items/1');
    expect(screen.getByRole('link', { name: 'ASSEMBLY-002' })).toHaveAttribute('href', '/items/2');
  });

  it('has page navigation but no Go to page box', async () => {
    servePaged(manyItems(200));
    renderPage();
    await screen.findByText('ITM-1');

    expect(screen.getByRole('navigation', { name: 'Pagination' })).toBeInTheDocument();
    expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();
    expect(screen.queryByText('Go to page')).not.toBeInTheDocument();
  });

  it('shows an error state when the Enterprise API fails', async () => {
    server.use(http.post(EXECUTE, () => HttpResponse.json({ message: 'boom' }, { status: 502 })));

    renderPage();

    // useItems retries once before surfacing the error.
    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('Items could not be loaded');
    expect(alert).toHaveTextContent('The Enterprise API is unavailable (502).');
  });

  it('explains an expired token without exposing it', async () => {
    server.use(http.post(EXECUTE, () => HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })));

    renderPage();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('token is missing or expired (401)');
    expect(alert).not.toHaveTextContent(/Bearer/i);
  });

  it('shows an empty state when there are no items', async () => {
    servePaged([]);

    renderPage();

    expect(await screen.findByText('No items available.')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent('0–0 of 0');
  });
});
