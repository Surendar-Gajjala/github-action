import { useEffect, useState, type ReactNode } from 'react';
import type { Page } from '../api/types';
import { EmptyState, ErrorState, LoadingState } from './States';

export interface Column<T> {
  key: string;
  header: ReactNode;
  render: (row: T) => ReactNode;
  /** Backend sort property; omit for non-sortable columns. */
  sortKey?: string;
  className?: string;
}

export interface SortState {
  property: string;
  direction: 'asc' | 'desc';
}

export const toSortParam = (s: SortState) => `${s.property},${s.direction}`;

interface Props<T> {
  columns: Column<T>[];
  page?: Page<T>;
  loading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  sort?: SortState;
  onSortChange?: (s: SortState) => void;
  onPageChange?: (page: number) => void;
  rowKey: (row: T) => string | number;
  onRowClick?: (row: T) => void;
  emptyTitle?: string;
  emptyHint?: ReactNode;
}

export function DataTable<T>({
  columns, page, loading, error, onRetry, sort, onSortChange, onPageChange, rowKey, onRowClick, emptyTitle = 'No records', emptyHint,
}: Props<T>) {
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (loading && !page) return <LoadingState />;
  const rows = page?.content ?? [];
  return (
    <div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200">
          <thead className="bg-slate-50">
            <tr>
              {columns.map((c) => {
                const active = sort && c.sortKey === sort.property;
                return (
                  <th key={c.key} className={`th ${c.className ?? ''}`} aria-sort={active ? (sort!.direction === 'asc' ? 'ascending' : 'descending') : undefined}>
                    {c.sortKey && onSortChange ? (
                      <button
                        type="button"
                        className="inline-flex items-center gap-1 uppercase hover:text-slate-800"
                        onClick={() =>
                          onSortChange({
                            property: c.sortKey!,
                            direction: active && sort!.direction === 'asc' ? 'desc' : 'asc',
                          })
                        }
                      >
                        {c.header}
                        <span className="text-[10px]">{active ? (sort!.direction === 'asc' ? '▲' : '▼') : '↕'}</span>
                      </button>
                    ) : (
                      c.header
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody className={`divide-y divide-slate-100 bg-white ${loading ? 'opacity-60' : ''}`}>
            {rows.map((row) => (
              <tr
                key={rowKey(row)}
                className={onRowClick ? 'cursor-pointer hover:bg-indigo-50/40' : ''}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {columns.map((c) => (
                  <td key={c.key} className={`td ${c.className ?? ''}`}>{c.render(row)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rows.length === 0 && <EmptyState title={emptyTitle} hint={emptyHint} />}
      {page && page.totalPages > 1 && onPageChange && (
        <div className="flex items-center justify-between border-t border-slate-200 px-3 py-2 text-slate-600">
          <span>
            {page.page * page.size + 1}–{page.page * page.size + rows.length} of {page.totalElements}
          </span>
          <div className="flex gap-1">
            <button className="btn-secondary btn-sm" disabled={page.page === 0} onClick={() => onPageChange(page.page - 1)}>
              Previous
            </button>
            <button className="btn-secondary btn-sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onPageChange(page.page + 1)}>
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export function useDebounced<T>(value: T, delay = 300): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return v;
}

export function SearchInput({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="relative w-full max-w-xs">
      <span className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400">⌕</span>
      <input
        type="search"
        aria-label={placeholder ?? 'Search'}
        className="input pl-7"
        placeholder={placeholder ?? 'Search…'}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}
