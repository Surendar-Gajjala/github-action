import type { ReactNode } from 'react';

/** One column of a {@link DataTable}. */
export interface Column<T> {
  key: keyof T & string;
  header: string;
  align?: 'left' | 'right';
  /** Wide free-text columns truncate (full text on hover) instead of forcing the row wider. */
  wide?: boolean;
  render: (row: T) => ReactNode;
}
