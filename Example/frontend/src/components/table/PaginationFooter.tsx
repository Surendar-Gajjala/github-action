import { ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';

import { PAGE_SIZE_OPTIONS } from '../../hooks/usePagination';

interface Props {
  page: number;
  pageCount: number;
  pageSize: number;
  rangeStart: number;
  rangeEnd: number;
  /** Null when the source does not report a total. */
  total: number | null;
  isLoading: boolean;
  /** A page request is in flight; navigation is paused until it completes. */
  isFetching: boolean;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  /** Plural noun for the loading message, e.g. "items" or "parts". */
  noun: string;
}

type PageToken = number | 'gap-start' | 'gap-end';

/** Page numbers to show: all of them when few, otherwise first, last and a window around the current page. */
export function pageTokens(page: number, pageCount: number): PageToken[] {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_, i) => i + 1);
  const start = Math.max(2, Math.min(page - 1, pageCount - 4));
  const end = Math.min(pageCount - 1, Math.max(page + 1, 5));
  const tokens: PageToken[] = [1];
  if (start > 2) tokens.push('gap-start');
  for (let p = start; p <= end; p++) tokens.push(p);
  if (end < pageCount - 1) tokens.push('gap-end');
  tokens.push(pageCount);
  return tokens;
}

const arrowButton =
  'flex size-8 items-center justify-center rounded-md text-gray-500 hover:bg-gray-100 disabled:pointer-events-none disabled:text-gray-300';

/** Range, rows-per-page picker and page navigation for a server-paged table. */
export function PaginationFooter({
  page,
  pageCount,
  pageSize,
  rangeStart,
  rangeEnd,
  total,
  isLoading,
  isFetching,
  onPageChange,
  onPageSizeChange,
  noun,
}: Props) {
  return (
    <footer className="flex shrink-0 flex-wrap items-center justify-between gap-4 border-t border-gray-200 bg-white px-10 py-3 text-[15px] text-gray-600">
      <div className="flex items-center gap-5">
        <p aria-live="polite">
          {isLoading ? (
            `Loading ${noun}…`
          ) : (
            <>
              <span className="font-semibold text-gray-900">
                {rangeStart}–{rangeEnd}
              </span>{' '}
              of <span className="font-semibold text-gray-900">{total === null ? 'many' : total.toLocaleString()}</span>
            </>
          )}
        </p>

        <label className="flex items-center gap-2.5">
          Rows
          <span className="relative">
            <select
              value={pageSize}
              onChange={(event) => onPageSizeChange(Number(event.target.value))}
              className="appearance-none rounded-md border border-gray-200 bg-white py-1.5 pr-8 pl-2.5 text-gray-900 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 focus:outline-none"
            >
              {PAGE_SIZE_OPTIONS.map((size) => (
                <option key={size} value={size}>
                  {size}
                </option>
              ))}
            </select>
            <ChevronDown
              className="pointer-events-none absolute top-1/2 right-2 size-4 -translate-y-1/2 text-gray-500"
              aria-hidden
            />
          </span>
        </label>
      </div>

      <nav aria-label="Pagination" className="flex items-center gap-1">
        <button
          type="button"
          className={arrowButton}
          onClick={() => onPageChange(page - 1)}
          disabled={isFetching || page <= 1}
          aria-label="Previous page"
        >
          <ChevronLeft className="size-4" aria-hidden />
        </button>

        {pageTokens(page, pageCount).map((token) =>
          typeof token === 'number' ? (
            <button
              key={token}
              type="button"
              onClick={() => onPageChange(token)}
              disabled={isFetching && token !== page}
              aria-current={token === page ? 'page' : undefined}
              aria-label={`Page ${token}`}
              className={`flex size-8 items-center justify-center rounded-md text-[15px] font-semibold ${
                token === page ? 'bg-blue-600 text-white' : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              {token}
            </button>
          ) : (
            <span key={token} className="px-1 text-gray-400" aria-hidden>
              …
            </span>
          ),
        )}

        <button
          type="button"
          className={arrowButton}
          onClick={() => onPageChange(page + 1)}
          disabled={isFetching || page >= pageCount}
          aria-label="Next page"
        >
          <ChevronRight className="size-4" aria-hidden />
        </button>
      </nav>
    </footer>
  );
}
