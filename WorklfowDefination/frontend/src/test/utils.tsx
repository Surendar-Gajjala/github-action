import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi } from 'vitest';
import { setCredentials } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../components/Toast';

type Handler = (url: string, init?: RequestInit) => unknown;

/** Mocks fetch with a route table: { 'GET /api/me': body | (url, init) => body }. Unmatched calls fail the test. */
export function mockApi(routes: Record<string, unknown | Handler>) {
  const calls: { method: string; url: string; body?: unknown }[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    calls.push({ method, url, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    const entry = Object.entries(routes).find(([k]) => {
      const [m, path] = k.split(' ');
      return m === method && (url === path || url.startsWith(path + '?') || (path.endsWith('*') && url.startsWith(path.slice(0, -1))));
    });
    if (!entry) return new Response(JSON.stringify({ status: 404, message: `unmocked ${method} ${url}` }), { status: 404 });
    const value = typeof entry[1] === 'function' ? (entry[1] as Handler)(url, init) : entry[1];
    if (value instanceof Response) return value;
    return new Response(JSON.stringify(value), { status: 200, headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fetchMock);
  return { fetchMock, calls };
}

export function renderWithProviders(ui: ReactElement, { route = '/', user = 'engineer' } = {}) {
  setCredentials(user, user);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <ToastProvider>
          <AuthProvider>{ui}</AuthProvider>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

export const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
