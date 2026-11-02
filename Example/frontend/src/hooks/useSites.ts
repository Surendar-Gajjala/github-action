import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { fetchSites } from '../services/sites';

export const siteKeys = {
  all: ['sites'] as const,
  list: (page: number, size: number) => [...siteKeys.all, 'list', { page, size }] as const,
};

/** One page of sites; `page` is zero-based. Each page is one Enterprise Query Config V2 request. */
export function useSites(page: number, size: number) {
  return useQuery({
    queryKey: siteKeys.list(page, size),
    queryFn: () => fetchSites(page, size),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}
