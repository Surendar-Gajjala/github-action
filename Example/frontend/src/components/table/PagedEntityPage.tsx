import type { UseQueryResult } from '@tanstack/react-query';

import { usePagination } from '../../hooks/usePagination';
import { type TextKey, useTableSearch } from '../../hooks/useTableSearch';
import type { PageResponse } from '../../types/page';
import { PageHeader } from '../PageHeader';
import { DataTable } from './DataTable';
import { LoadError } from './LoadError';
import { PaginationFooter } from './PaginationFooter';
import { SearchBox } from './SearchBox';
import type { Column } from './types';

interface Props<T> {
  title: string;
  subtitle: string;
  /** Plural noun used in messages, e.g. "items". */
  noun: string;
  columns: Column<T>[];
  searchFields: readonly TextKey<T>[];
  searchPlaceholder: string;
  getRowKey: (row: T, index: number) => string | number;
  /** Fetches one page; `page` is zero-based. */
  usePage: (page: number, size: number) => UseQueryResult<PageResponse<T>>;
}

/**
 * Layout shared by every entity tab (Items, Parts, ...): header, search over the
 * current page, server-paged table and pagination footer.
 */
export function PagedEntityPage<T>({
  title,
  subtitle,
  noun,
  columns,
  searchFields,
  searchPlaceholder,
  getRowKey,
  usePage,
}: Props<T>) {
  const { page, pageSize, setPage, setPageSize } = usePagination();
  const { data, isLoading, isError, error, isFetching, isPlaceholderData } = usePage(page - 1, pageSize);
  const rows = data?.objects ?? [];
  const { query, setQuery, filtered } = useTableSearch(rows, searchFields);

  const offset = (page - 1) * pageSize;
  const pageCount = data ? (data.totalPages ?? (data.hasMore ? page + 1 : page)) : 1;

  return (
    <div className="flex h-full flex-col">
      <PageHeader title={title} subtitle={subtitle} />

      <div className="flex min-h-0 flex-1 flex-col bg-gray-50">
        <div className="px-10 py-5">
          <SearchBox label={`Search ${noun}`} value={query} onChange={setQuery} placeholder={searchPlaceholder} />
        </div>

        {isError ? (
          <LoadError title={title} error={error} />
        ) : (
          <>
            <div className={`flex min-h-0 flex-1 flex-col transition-opacity ${isPlaceholderData ? 'opacity-60' : ''}`}>
              <DataTable
                caption={title}
                columns={columns}
                rows={filtered}
                getRowKey={getRowKey}
                isLoading={isLoading}
                emptyMessage={query ? `No ${noun} on this page match "${query}".` : `No ${noun} available.`}
              />
            </div>
            <PaginationFooter
              noun={noun}
              page={page}
              pageCount={pageCount}
              pageSize={pageSize}
              rangeStart={data && data.count > 0 ? offset + 1 : 0}
              rangeEnd={offset + (data?.count ?? 0)}
              total={data?.totalObjects ?? null}
              isLoading={isLoading}
              isFetching={isFetching}
              onPageChange={setPage}
              onPageSizeChange={setPageSize}
            />
          </>
        )}
      </div>
    </div>
  );
}
