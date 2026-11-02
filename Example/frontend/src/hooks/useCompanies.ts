import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { fetchCompanies } from '../services/companies';

export const companyKeys = {
  all: ['companies'] as const,
  list: (page: number, size: number) => [...companyKeys.all, 'list', { page, size }] as const,
};

/** One page of companies; `page` is zero-based. Each page is one Enterprise Query Config V2 request. */
export function useCompanies(page: number, size: number) {
  return useQuery({
    queryKey: companyKeys.list(page, size),
    queryFn: () => fetchCompanies(page, size),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}
