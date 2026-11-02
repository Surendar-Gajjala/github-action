import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { fetchParts } from '../services/parts';

export const partKeys = {
  all: ['parts'] as const,
  list: (page: number, size: number) => [...partKeys.all, 'list', { page, size }] as const,
};

/** One page of parts; `page` is zero-based. Each page is one Enterprise Query Config V2 request. */
export function useParts(page: number, size: number) {
  return useQuery({
    queryKey: partKeys.list(page, size),
    queryFn: () => fetchParts(page, size),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}
