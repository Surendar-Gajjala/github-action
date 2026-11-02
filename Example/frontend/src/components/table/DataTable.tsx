import type { Column } from './types';

interface Props<T> {
  caption: string;
  columns: Column<T>[];
  rows: T[];
  getRowKey: (row: T, index: number) => string | number;
  isLoading: boolean;
  emptyMessage: string;
}

const SKELETON_ROWS = 8;

export function DataTable<T>({ caption, columns, rows, getRowKey, isLoading, emptyMessage }: Props<T>) {
  return (
    <div className="min-h-0 flex-1 overflow-auto border-t border-gray-200 bg-white">
      <table className="w-full min-w-max border-collapse text-left text-[17px] text-gray-800" aria-busy={isLoading}>
        <caption className="sr-only">{caption}</caption>
        <thead className="sticky top-0 z-10 bg-gray-50">
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={`border-r border-b border-gray-200 px-5 py-4 font-semibold whitespace-nowrap text-gray-900 first:pl-10 last:border-r-0 ${
                  column.align === 'right' ? 'text-right' : ''
                }`}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {isLoading
            ? Array.from({ length: SKELETON_ROWS }, (_, row) => (
                <tr key={row} className="border-b border-gray-200">
                  {columns.map((column) => (
                    <td key={column.key} className="px-5 py-[22px] first:pl-10">
                      <span className="block h-4 w-24 animate-pulse rounded bg-gray-200" />
                    </td>
                  ))}
                </tr>
              ))
            : rows.map((row, index) => (
                <tr key={getRowKey(row, index)} className="border-b border-gray-200 hover:bg-gray-50/70">
                  {columns.map((column) => (
                    <td
                      key={column.key}
                      className={`px-5 py-[22px] align-middle first:pl-10 ${
                        column.wide ? 'max-w-sm truncate' : 'whitespace-nowrap'
                      } ${column.align === 'right' ? 'text-right tabular-nums' : ''}`}
                      title={column.wide ? String(row[column.key] ?? '') : undefined}
                    >
                      {column.render(row)}
                    </td>
                  ))}
                </tr>
              ))}
          {!isLoading && rows.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="px-10 py-16 text-center text-gray-500">
                {emptyMessage}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
