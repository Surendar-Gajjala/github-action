import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';

import { server } from '../test/server';
import { CompaniesPage } from './CompaniesPage';

const EXECUTE = '/enterprise/core/query-config-v2/execute';

/** An Enterprise `results` row, keyed by the company-query.json aliases. */
function rawCompany(id: number, name: string | null): Record<string, unknown> {
  return { company_name: name, company__id: id };
}

/** Serves one page per POST, Enterprise-envelope shaped, and records what was called. */
function serve(all: Record<string, unknown>[]) {
  const calls: { execute: string[]; bodies: unknown[] } = { execute: [], bodies: [] };
  server.use(
    http.post(EXECUTE, async ({ request }) => {
      const params = new URL(request.url).searchParams;
      const page = Number(params.get('page') ?? 0);
      const size = Number(params.get('size') ?? 25);
      calls.execute.push(`${page}/${size}`);
      calls.bodies.push(await request.json());
      return HttpResponse.json({
        results: all.slice(page * size, page * size + size),
        objectGraph: null,
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
        <CompaniesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const bodyRows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1);

describe('CompaniesPage', () => {
  it('posts the company query to the Enterprise execute endpoint and renders mapped rows', async () => {
    const calls = serve([rawCompany(13200, 'Sunex Inc'), rawCompany(22900, 'Seiko Epson Corporation')]);

    renderPage();

    expect(await screen.findByText('Sunex Inc')).toBeInTheDocument();
    expect(screen.getByText('Seiko Epson Corporation')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Company Name' })).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'ID' })).not.toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent('1–2 of 2');
    expect(calls.execute).toEqual(['0/25']);
    expect(calls.bodies[0]).toMatchObject({
      from: { entity: 'company', alias: 'company' },
      context: { tableId: 'objecttype-view-company' },
    });
  });

  it('filters the current page by company name', async () => {
    serve([rawCompany(1, 'Sunex Inc'), rawCompany(2, 'Accton Technology Corporation')]);
    renderPage();
    await screen.findByText('Sunex Inc');

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search companies' }), 'accton');

    await waitFor(() => expect(bodyRows()).toHaveLength(1));
    expect(screen.getByText('Accton Technology Corporation')).toBeInTheDocument();
  });

  it('shows the empty state when the hosted server has no companies', async () => {
    serve([]);

    renderPage();

    expect(await screen.findByText('No companies available.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
  });

  it('shows an error state when the Enterprise API fails', async () => {
    server.use(http.post(EXECUTE, () => HttpResponse.json({ message: 'nope' }, { status: 403 })));

    renderPage();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent('Companies could not be loaded');
  });
});
