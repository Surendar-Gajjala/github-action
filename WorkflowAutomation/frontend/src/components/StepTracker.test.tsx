import { render, screen, within } from '@testing-library/react';
import type { StepResponse } from '../api/types';
import { StepTracker } from './StepTracker';

const steps: StepResponse[] = [
  { name: 'VALIDATE_DETAILS', label: 'Employee details validated', order: 1, status: 'SUCCESS', attempts: 1, startedAt: '2026-10-26T10:00:00Z', completedAt: '2026-10-26T10:00:00Z' },
  { name: 'SAVE_RECORD', label: 'Employee record saved', order: 2, status: 'SUCCESS', attempts: 1, externalReference: 'EMP-001001' },
  { name: 'GENERATE_CONTENT', label: 'AI email generated', order: 3, status: 'SUCCESS', attempts: 1 },
  { name: 'SEND_EMAIL', label: 'Welcome email sent', order: 4, status: 'IN_PROGRESS', attempts: 1 },
  { name: 'CREATE_GITHUB_ISSUE', label: 'GitHub onboarding issue created', order: 5, status: 'FAILED', attempts: 2, errorMessage: 'GitHub API returned HTTP 503.' },
  { name: 'COMPLETE_WORKFLOW', label: 'Workflow completed', order: 6, status: 'PENDING', attempts: 0 },
];

describe('StepTracker', () => {
  it('renders every step with its status', () => {
    render(<StepTracker steps={steps} />);

    expect(within(screen.getByTestId('step-VALIDATE_DETAILS')).getByText('Success')).toBeInTheDocument();
    expect(within(screen.getByTestId('step-SEND_EMAIL')).getByText('In Progress')).toBeInTheDocument();
    expect(within(screen.getByTestId('step-COMPLETE_WORKFLOW')).getByText('Pending')).toBeInTheDocument();
    expect(within(screen.getByTestId('step-SAVE_RECORD')).getByText(/Ref EMP-001001/)).toBeInTheDocument();
  });

  it('shows failures with their error message and attempt count', () => {
    render(<StepTracker steps={steps} />);

    const failed = screen.getByTestId('step-CREATE_GITHUB_ISSUE');
    expect(within(failed).getByText('Failed')).toBeInTheDocument();
    expect(within(failed).getByRole('alert')).toHaveTextContent('GitHub API returned HTTP 503.');
    expect(within(failed).getByText('2 attempts')).toBeInTheDocument();
  });
});
