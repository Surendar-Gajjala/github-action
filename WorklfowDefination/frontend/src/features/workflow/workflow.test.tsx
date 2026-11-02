import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ApprovalTask, LifecycleTransition } from '../../api/types';
import { mockApi, page, renderWithProviders } from '../../test/utils';
import { TasksSection } from './TasksSection';
import { LifecycleSection } from './LifecycleSection';

const task = (over: Partial<ApprovalTask> = {}): ApprovalTask => ({
  id: 7,
  name: 'Engineering Approval',
  elementId: 'engineeringApproval',
  status: 'OPEN',
  candidateGroup: 'ENGINEERING',
  itemId: 2,
  itemNumber: 'ITEM-1001',
  itemName: 'Hydraulic Pump Assembly',
  workflowInstanceId: 'a1b2c3d4-0000-0000-0000-000000000000',
  definitionKey: 'ITEM_LIFECYCLE_APPROVAL',
  definitionVersion: 1,
  currentStage: 'Engineering Approval',
  createdAt: '2026-10-27T10:00:00',
  overdue: false,
  ...over,
});

describe('Approval task inbox', () => {
  it('lists tasks and requires a rejection reason before rejecting', async () => {
    const user = userEvent.setup();
    const { calls } = mockApi({
      'GET /api/me': { username: 'engineer', roles: ['ENGINEERING'] },
      'GET /api/workflows/tasks': page([task()]),
      'POST /api/workflows/tasks/7/reject': task({ status: 'REJECTED', decisionBy: 'engineer' }),
    });
    renderWithProviders(<TasksSection />);

    const row = (await screen.findByText('ITEM-1001')).closest('tr')!;
    expect(within(row).getByText('Hydraulic Pump Assembly')).toBeInTheDocument();
    expect(within(row).getByText('a1b2c3d4')).toBeInTheDocument();

    await user.click(within(row).getByRole('button', { name: 'Reject' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Reject' }));
    expect(await within(dialog).findByText(/at least 5 characters/)).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'POST')).toBe(false);

    await user.type(within(dialog).getByLabelText(/Rejection reason/), 'Wrong bearing size');
    await user.click(within(dialog).getByRole('button', { name: 'Reject' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ reason: 'Wrong bearing size' }));
  });

  it('disables decisions on tasks for a role the user does not have', async () => {
    mockApi({
      'GET /api/me': { username: 'engineer', roles: ['ENGINEERING'] },
      'GET /api/workflows/tasks': page([task({ id: 8, candidateGroup: 'MANAGER', name: 'Manager Approval' })]),
    });
    renderWithProviders(<TasksSection />);
    const row = (await screen.findByText('Manager Approval')).closest('tr')!;
    expect(within(row).getByRole('button', { name: 'Approve' })).toBeDisabled();
    expect(within(row).getByRole('button', { name: 'Reject' })).toBeDisabled();
  });

  it('approves with an optional comment', async () => {
    const user = userEvent.setup();
    const { calls } = mockApi({
      'GET /api/me': { username: 'engineer', roles: ['ENGINEERING'] },
      'GET /api/workflows/tasks': page([task()]),
      'POST /api/workflows/tasks/7/approve': task({ status: 'APPROVED' }),
    });
    renderWithProviders(<TasksSection />);
    const row = (await screen.findByText('ITEM-1001')).closest('tr')!;
    await user.click(within(row).getByRole('button', { name: 'Approve' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url.endsWith('/approve'))).toBe(true));
  });

  it('shows a server error (e.g. segregation of duties) in the dialog', async () => {
    const user = userEvent.setup();
    mockApi({
      'GET /api/me': { username: 'engineer', roles: ['ENGINEERING'] },
      'GET /api/workflows/tasks': page([task()]),
      'POST /api/workflows/tasks/7/approve': new Response(
        JSON.stringify({ status: 403, error: 'Forbidden', message: 'Segregation of duties: you submitted CR-00001' }),
        { status: 403 },
      ),
    });
    renderWithProviders(<TasksSection />);
    const row = (await screen.findByText('ITEM-1001')).closest('tr')!;
    await user.click(within(row).getByRole('button', { name: 'Approve' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Approve' }));
    expect(await within(dialog).findByText(/Segregation of duties/)).toBeInTheDocument();
  });
});

describe('Lifecycle state configuration', () => {
  const transitions: LifecycleTransition[] = [
    { id: 1, fromStatus: 'DRAFT', toStatus: 'IN_REVIEW', enabled: true, system: true, description: 'Submit' },
    { id: 2, fromStatus: 'IN_REVIEW', toStatus: 'APPROVED', enabled: true, system: true, description: 'Release' },
  ];

  it('lets an admin toggle a transition', async () => {
    const user = userEvent.setup();
    const { calls } = mockApi({
      'GET /api/me': { username: 'admin', roles: ['ADMIN', 'AUTHOR'] },
      'GET /api/workflows/lifecycle-transitions': transitions,
      'PUT /api/workflows/lifecycle-transitions': { ...transitions[0], enabled: false },
    });
    renderWithProviders(<LifecycleSection />, { user: 'admin' });
    const checkbox = await screen.findByRole('checkbox', { name: 'Enable DRAFT to IN_REVIEW' });
    await user.click(checkbox);
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ fromStatus: 'DRAFT', toStatus: 'IN_REVIEW', enabled: false }),
    );
  });

  it('is read-only for non-admins', async () => {
    mockApi({
      'GET /api/me': { username: 'author', roles: ['AUTHOR'] },
      'GET /api/workflows/lifecycle-transitions': transitions,
    });
    renderWithProviders(<LifecycleSection />, { user: 'author' });
    expect(await screen.findByRole('checkbox', { name: 'Enable DRAFT to IN_REVIEW' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Add transition' })).not.toBeInTheDocument();
  });
});
