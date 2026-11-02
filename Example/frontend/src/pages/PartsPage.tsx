import { PART_COLUMNS } from '../components/parts/partColumns';
import { PagedEntityPage } from '../components/table/PagedEntityPage';
import { useParts } from '../hooks/useParts';
import type { Part } from '../types/part';

const SEARCH_FIELDS = ['partNumber', 'manufacturer', 'description', 'countryOfOrigin'] as const;

export function PartsPage() {
  return (
    <PagedEntityPage<Part>
      title="Parts"
      subtitle="Manufacturer parts with sourcing, supply chain risk and lifecycle."
      noun="parts"
      columns={PART_COLUMNS}
      searchFields={SEARCH_FIELDS}
      searchPlaceholder="Search this page by part number or manufacturer..."
      getRowKey={(part, index) => part.id ?? `row-${index}`}
      usePage={useParts}
    />
  );
}
