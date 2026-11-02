import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import type { ItemDetail, ItemSummary, PartSummary } from '../../api/types';
import { mockApi, page, renderWithProviders } from '../../test/utils';
import { ItemsPage } from './ItemsPage';
import { ItemFormDialog } from './ItemFormDialog';

const parts: PartSummary[] = [
  { id: 1, partNumber: 'PART-101', name: 'Shaft', partType: 'MECHANICAL', associatedItemsCount: 0, createdAt: '', updatedAt: '', version: 0 },
  { id: 2, partNumber: 'PART-102', name: 'Bearing', partType: 'MECHANICAL', associatedItemsCount: 0, createdAt: '', updatedAt: '', version: 0 },
  { id: 3, partNumber: 'PART-103', name: 'Housing', partType: 'STRUCTURAL', associatedItemsCount: 0, createdAt: '', updatedAt: '', version: 0 },
];

const summary: ItemSummary = {
  id: 5, itemNumber: 'ITEM-1001', name: 'Hydraulic Pump Assembly', revision: 'B', approvedRevision: 'A', partCount: 2,
  lifecycleStatus: 'CHANGE_PENDING', updatedAt: '2026-10-27T10:00:00', version: 3,
};

const approvedDetail: ItemDetail = {
  id: 5, itemNumber: 'ITEM-1001', name: 'Hydraulic Pump Assembly', lifecycleStatus: 'APPROVED', revision: 'A', approvedRevision: 'A',
  createdAt: '', updatedAt: '', version: 2, editable: true, submittable: false,
  parts: [{ id: 1, partNumber: 'PART-101', name: 'Shaft', quantity: 1 }, { id: 3, partNumber: 'PART-103', name: 'Housing', quantity: 1 }],
};

describe('Items tab', () => {
  it('shows lifecycle status, revision and part count', async () => {
    mockApi({
      'GET /api/me': { username: 'author', roles: ['AUTHOR'] },
      'GET /api/items': page([summary]),
    });
    renderWithProviders(<Routes><Route path="/items/:itemId?" element={<ItemsPage />} /></Routes>, { route: '/items', user: 'author' });
    const row = (await screen.findByText('ITEM-1001')).closest('tr')!;
    expect(within(row).getByText('Change pending')).toBeInTheDocument();
    expect(within(row).getByText('(approved A)')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Submit' })).toBeEnabled();
  });

  it('creates an item with parts chosen from the searchable dropdown', async () => {
    const user = userEvent.setup();
    const { calls } = mockApi({
      'GET /api/me': { username: 'author', roles: ['AUTHOR'] },
      'GET /api/parts': page(parts),
      'POST /api/items': { ...approvedDetail, id: 9, lifecycleStatus: 'DRAFT', approvedRevision: undefined },
    });
    renderWithProviders(<ItemFormDialog open onClose={() => {}} />, { user: 'author' });

    await user.type(await screen.findByLabelText('Item number'), 'ITEM-1001');
    await user.type(screen.getByLabelText('Name'), 'Hydraulic Pump Assembly');
    const combo = screen.getByRole('combobox', { name: 'Parts' });
    for (const name of ['Shaft', 'Bearing', 'Housing']) {
      await user.click(combo);
      await user.clear(combo);
      await user.type(combo, name);
      await user.click(await screen.findByRole('option', { name: new RegExp(name) }));
    }
    await user.click(screen.getByRole('button', { name: 'Create item' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'POST')?.body).toMatchObject({
        itemNumber: 'ITEM-1001', name: 'Hydraulic Pump Assembly', partIds: [1, 2, 3],
      }),
    );
  });

  it('validates required fields before calling the API', async () => {
    const user = userEvent.setup();
    const { calls } = mockApi({ 'GET /api/me': { username: 'author', roles: ['AUTHOR'] }, 'GET /api/parts': page(parts) });
    renderWithProviders(<ItemFormDialog open onClose={() => {}} />, { user: 'author' });
    await user.click(await screen.findByRole('button', { name: 'Create item' }));
    expect(await screen.findByText('Item number is required')).toBeInTheDocument();
    expect(screen.getByText('Name is required')).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
  });

  it('warns that editing an approved item starts a change and keeps the approved revision', async () => {
    mockApi({ 'GET /api/me': { username: 'author', roles: ['AUTHOR'] }, 'GET /api/parts': page(parts) });
    renderWithProviders(<ItemFormDialog open item={approvedDetail} onClose={() => {}} />, { user: 'author' });
    expect(await screen.findByText(/Saving a change opens a change request/)).toBeInTheDocument();
    expect(await screen.findByText('PART-101 · Shaft')).toBeInTheDocument();
    expect(await screen.findByText('PART-103 · Housing')).toBeInTheDocument();
    expect(screen.getByLabelText('Item number')).toBeDisabled();
  });
});
