import { useState } from 'react';

export const PAGE_SIZE_OPTIONS = [10, 25, 50, 100] as const;

/** Page (1-based, as shown to the user) and rows-per-page selection for server-side paging. */
export function usePagination(initialPageSize = 25) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSizeState] = useState(initialPageSize);

  const setPageSize = (size: number) => {
    setPageSizeState(size);
    setPage(1);
  };

  return { page, pageSize, setPage, setPageSize };
}
