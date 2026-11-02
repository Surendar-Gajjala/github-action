import { useQuery } from '@tanstack/react-query';
import { itemsApi, partsApi } from '../api/endpoints';
import type { Option } from '../components/MultiSelect';

/** All parts as multi-select options (local master data is small; capped at the API's max page size). */
export function usePartOptions(enabled = true) {
  const q = useQuery({
    queryKey: ['parts', 'options'],
    queryFn: () => partsApi.list({ size: 200, sort: 'partNumber,asc' }),
    enabled,
  });
  const options: Option[] = (q.data?.content ?? []).map((p) => ({
    value: p.id,
    label: `${p.partNumber} · ${p.name}`,
    description: p.partType,
  }));
  return { options, loading: q.isLoading };
}

export function useItemOptions(enabled = true) {
  const q = useQuery({
    queryKey: ['items', 'options'],
    queryFn: () => itemsApi.list({ size: 200, sort: 'itemNumber,asc' }),
    enabled,
  });
  const options: Option[] = (q.data?.content ?? []).map((i) => ({
    value: i.id,
    label: `${i.itemNumber} · ${i.name}`,
    description: i.lifecycleStatus.replace('_', ' ').toLowerCase(),
  }));
  return { options, loading: q.isLoading, items: q.data?.content ?? [] };
}
