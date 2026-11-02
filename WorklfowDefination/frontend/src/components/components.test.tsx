import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MultiSelect, type Option } from './MultiSelect';
import { StatusBadge } from './StatusBadge';
import { DataTable } from './DataTable';

const OPTIONS: Option[] = [
  { value: 1, label: 'PART-101 · Shaft' },
  { value: 2, label: 'PART-102 · Bearing' },
  { value: 3, label: 'PART-103 · Housing' },
];

function Harness({ initial = [] as number[], onChange = (_: number[]) => {} }) {
  const [value, setValue] = useState(initial);
  return <MultiSelect ariaLabel="Parts" options={OPTIONS} value={value} onChange={(v) => { setValue(v); onChange(v); }} />;
}

describe('MultiSelect', () => {
  it('filters options by search text and selects several', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);

    await user.click(screen.getByRole('combobox', { name: 'Parts' }));
    expect(screen.getAllByRole('option')).toHaveLength(3);

    await user.type(screen.getByRole('combobox'), 'bear');
    const options = screen.getAllByRole('option');
    expect(options).toHaveLength(1);
    await user.click(options[0]);
    expect(onChange).toHaveBeenLastCalledWith([2]);

    await user.clear(screen.getByRole('combobox'));
    await user.click(screen.getByRole('option', { name: /Housing/ }));
    expect(onChange).toHaveBeenLastCalledWith([2, 3]);
  });

  it('shows selected parts as chips that can be removed', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness initial={[1, 3]} onChange={onChange} />);
    expect(screen.getByText('PART-101 · Shaft')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove PART-101 · Shaft' }));
    expect(onChange).toHaveBeenLastCalledWith([3]);
  });

  it('supports keyboard selection and backspace removal', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    const input = screen.getByRole('combobox');
    await user.click(input);
    await user.keyboard('{ArrowDown}{Enter}');
    expect(onChange).toHaveBeenLastCalledWith([2]);
    await user.keyboard('{Backspace}');
    expect(onChange).toHaveBeenLastCalledWith([]);
  });
});

describe('StatusBadge', () => {
  it('renders humanized lifecycle statuses', () => {
    render(<StatusBadge status="CHANGE_PENDING" />);
    expect(screen.getByTestId('status-badge')).toHaveTextContent('Change pending');
  });
});

describe('DataTable', () => {
  it('renders rows, empty state and toggles sort', async () => {
    const user = userEvent.setup();
    const onSort = vi.fn();
    const columns = [{ key: 'n', header: 'Number', sortKey: 'itemNumber', render: (r: { n: string }) => r.n }];
    const { rerender } = render(
      <DataTable columns={columns} rowKey={(r) => r.n} sort={{ property: 'itemNumber', direction: 'asc' }} onSortChange={onSort}
        page={{ content: [{ n: 'ITEM-1' }, { n: 'ITEM-2' }], page: 0, size: 20, totalElements: 2, totalPages: 1 }} />,
    );
    const table = screen.getByRole('table');
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    await user.click(screen.getByRole('button', { name: /Number/ }));
    expect(onSort).toHaveBeenCalledWith({ property: 'itemNumber', direction: 'desc' });

    rerender(<DataTable columns={columns} rowKey={(r) => r.n} emptyTitle="Nothing here"
      page={{ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }} />);
    expect(screen.getByText('Nothing here')).toBeInTheDocument();
  });
});
