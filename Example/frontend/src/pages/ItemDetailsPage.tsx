import type { ReactNode } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';

import { PageHeader } from '../components/PageHeader';
import { OverviewTab } from '../components/itemDetails/OverviewTab';
import { PART_COLUMNS } from '../components/parts/partColumns';
import { DataTable } from '../components/table/DataTable';
import { LoadError } from '../components/table/LoadError';
import { Badge } from '../components/ui/Badge';
import { statusTone } from '../components/ui/statusTone';
import { useItemOverview, useItemSources } from '../hooks/useItemDetails';

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'sources', label: 'Sources' },
] as const;

type TabId = (typeof TABS)[number]['id'];

const isTab = (value: string | null): value is TabId => TABS.some((tab) => tab.id === value);

/** Item details: Overview (all properties) and Sources (sourced parts). */
export function ItemDetailsPage() {
  const { id } = useParams();
  const itemId = Number(id);
  if (!Number.isSafeInteger(itemId) || itemId <= 0) {
    return (
      <div className="flex h-full flex-col">
        <PageHeader title="Item" />
        <p className="px-10 py-8 text-gray-600">This item link is not valid.</p>
      </div>
    );
  }
  return <ItemDetails itemId={itemId} />;
}

function ItemDetails({ itemId }: { itemId: number }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get('tab');
  const tab: TabId = isTab(requested) ? requested : 'overview';

  const overview = useItemOverview(itemId);
  const sources = useItemSources(itemId, tab === 'sources');

  const item = overview.data;
  const counts: Partial<Record<TabId, number>> = { sources: sources.data?.length };

  const selectTab = (next: TabId) =>
    setSearchParams(next === 'overview' ? {} : { tab: next }, { replace: true });

  let panel: ReactNode;
  if (tab === 'overview') {
    panel = overview.isError ? (
      <LoadError title="Item" error={overview.error} />
    ) : overview.isLoading || !item ? (
      <p className="text-gray-500">Loading item…</p>
    ) : (
      <OverviewTab sections={item.sections} />
    );
  } else {
    panel = sources.isError ? (
      <LoadError title="Sources" error={sources.error} />
    ) : (
      <div className="flex min-h-[320px] flex-col overflow-hidden rounded-xl border border-gray-200 bg-white">
        <DataTable
          caption="Sources"
          columns={PART_COLUMNS}
          rows={sources.data ?? []}
          getRowKey={(part, index) => part.id ?? `row-${index}`}
          isLoading={sources.isLoading}
          emptyMessage="This item has no sourced parts."
        />
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <PageHeader title={item?.itemNumber ?? 'Item'} subtitle={item?.description ?? undefined} />

      {item && (
        <div className="flex flex-wrap items-center gap-2 px-10 pt-4 text-sm text-gray-600">
          {item.revision && <Badge>Rev {item.revision}</Badge>}
          {item.itemType && <Badge>{item.itemType}</Badge>}
          {item.itemStatus && <Badge tone={statusTone(item.itemStatus)}>{item.itemStatus}</Badge>}
        </div>
      )}

      <div role="tablist" aria-label="Item details" className="mt-4 flex gap-1 border-b border-gray-200 px-10">
        {TABS.map(({ id, label }) => {
          const selected = tab === id;
          const count = counts[id];
          return (
            <button
              key={id}
              type="button"
              role="tab"
              id={`tab-${id}`}
              aria-selected={selected}
              aria-controls="item-details-panel"
              onClick={() => selectTab(id)}
              className={`-mb-px border-b-2 px-4 py-3 text-[16px] font-semibold transition-colors ${
                selected ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-600 hover:text-gray-900'
              }`}
            >
              {label}
              {count !== undefined && <span className="ml-2 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-700">{count}</span>}
            </button>
          );
        })}
      </div>

      <div
        role="tabpanel"
        id="item-details-panel"
        aria-labelledby={`tab-${tab}`}
        className="flex min-h-0 flex-1 flex-col overflow-auto bg-gray-50 px-10 py-6"
      >
        {panel}
      </div>
    </div>
  );
}
