import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { fetchItems } from '../services/items';

export const itemKeys = {
  all: ['items'] as const,
  list: (page: number, size: number) => [...itemKeys.all, 'list', { page, size }] as const,
};

/** One page of items; `page` is zero-based. Each page is one Enterprise Query Config V2 request. */
export function useItems(page: number, size: number) {
  return useQuery({
    queryKey: itemKeys.list(page, size),
    queryFn: () => fetchItems(page, size),
    // Keep showing the current page while the next one loads.
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}
