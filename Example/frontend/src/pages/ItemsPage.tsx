import { ITEM_COLUMNS } from '../components/items/itemColumns';
import { PagedEntityPage } from '../components/table/PagedEntityPage';
import { useItems } from '../hooks/useItems';
import type { Item } from '../types/item';

const SEARCH_FIELDS = ['itemNumber', 'description', 'businessUnit', 'usedInProducts', 'itemStatusName'] as const;

export function ItemsPage() {
  return (
    <PagedEntityPage<Item>
      title="Items"
      subtitle="Products, assemblies and finished goods."
      noun="items"
      columns={ITEM_COLUMNS}
      searchFields={SEARCH_FIELDS}
      searchPlaceholder="Search this page by number or description..."
      getRowKey={(item, index) => item.id ?? `row-${index}`}
      usePage={useItems}
    />
  );
}
