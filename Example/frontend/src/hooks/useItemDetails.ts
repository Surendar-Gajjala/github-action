import { useQuery } from '@tanstack/react-query';

import { EnterpriseQueryError } from '../services/enterpriseQuery';
import { fetchItemOverview, fetchItemSources } from '../services/itemDetails';

export const itemDetailKeys = {
  all: (itemId: number) => ['item-details', itemId] as const,
  overview: (itemId: number) => [...itemDetailKeys.all(itemId), 'overview'] as const,
  sources: (itemId: number) => [...itemDetailKeys.all(itemId), 'sources'] as const,
};

const shared = {
  staleTime: 60_000,
  refetchOnWindowFocus: false,
  // A missing item will not appear on retry.
  retry: (failures: number, error: unknown) => !(error instanceof EnterpriseQueryError && error.status === 404) && failures < 1,
};

export function useItemOverview(itemId: number) {
  return useQuery({ queryKey: itemDetailKeys.overview(itemId), queryFn: () => fetchItemOverview(itemId), ...shared });
}

/** Loaded only when the Sources tab is opened (`enabled`); one hosted call. */
export function useItemSources(itemId: number, enabled: boolean) {
  return useQuery({ queryKey: itemDetailKeys.sources(itemId), queryFn: () => fetchItemSources(itemId), enabled, ...shared });
}
