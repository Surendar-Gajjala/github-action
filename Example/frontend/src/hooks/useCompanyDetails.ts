import { useQuery } from '@tanstack/react-query';

import { fetchCompanyOverview } from '../services/companyDetails';
import { fetchRapidRatingsDetails, findRapidRatingsCompanies } from '../services/rapidRatings';
import { UserFacingError } from '../services/userFacingError';

export const companyDetailKeys = {
  all: (companyId: number) => ['company-details', companyId] as const,
  overview: (companyId: number) => [...companyDetailKeys.all(companyId), 'overview'] as const,
};

export const rapidRatingsKeys = {
  search: (companyName: string) => ['rapidratings', 'search', companyName] as const,
  details: (rrid: string) => ['rapidratings', 'details', rrid] as const,
};

const shared = {
  staleTime: 60_000,
  refetchOnWindowFocus: false,
  // A client error (unknown id, rejected credentials, bad request) will not change on retry.
  retry: (failures: number, error: unknown) =>
    !(error instanceof UserFacingError && error.status !== null && error.status < 500) && failures < 1,
};

export function useCompanyOverview(companyId: number) {
  return useQuery({ queryKey: companyDetailKeys.overview(companyId), queryFn: () => fetchCompanyOverview(companyId), ...shared });
}

/** RapidRatings companies matching the Z2 company name; runs only once the name is known. */
export function useRapidRatingsSearch(companyName: string | null) {
  return useQuery({
    queryKey: rapidRatingsKeys.search(companyName ?? ''),
    queryFn: () => findRapidRatingsCompanies(companyName!),
    enabled: companyName !== null,
    ...shared,
  });
}

/** One RapidRatings company with all its periods and ratings; runs only once an RRID is chosen. */
export function useRapidRatingsDetails(rrid: string | null) {
  return useQuery({
    queryKey: rapidRatingsKeys.details(rrid ?? ''),
    queryFn: () => fetchRapidRatingsDetails(rrid!),
    enabled: rrid !== null,
    ...shared,
  });
}
