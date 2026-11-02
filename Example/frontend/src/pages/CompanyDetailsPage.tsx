import type { ReactNode } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';

import { PageHeader } from '../components/PageHeader';
import { RapidRatingsTab } from '../components/companyDetails/RapidRatingsTab';
import { OverviewTab } from '../components/itemDetails/OverviewTab';
import { LoadError } from '../components/table/LoadError';
import { Badge } from '../components/ui/Badge';
import { useCompanyOverview } from '../hooks/useCompanyDetails';

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'rapidratings', label: 'RapidRatings' },
] as const;

type TabId = (typeof TABS)[number]['id'];

const isTab = (value: string | null): value is TabId => TABS.some((tab) => tab.id === value);

/** Company details: Overview (Enterprise properties) and RapidRatings (read live by company name). */
export function CompanyDetailsPage() {
  const { id } = useParams();
  const companyId = Number(id);
  if (!Number.isSafeInteger(companyId) || companyId <= 0) {
    return (
      <div className="flex h-full flex-col">
        <PageHeader title="Company" />
        <p className="px-10 py-8 text-gray-600">This company link is not valid.</p>
      </div>
    );
  }
  return <CompanyDetails companyId={companyId} />;
}

function CompanyDetails({ companyId }: { companyId: number }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get('tab');
  const tab: TabId = isTab(requested) ? requested : 'overview';

  const overview = useCompanyOverview(companyId);
  const company = overview.data;

  const selectTab = (next: TabId) => setSearchParams(next === 'overview' ? {} : { tab: next }, { replace: true });

  let panel: ReactNode;
  if (overview.isError) {
    panel = <LoadError title="Company" error={overview.error} />;
  } else if (overview.isLoading || !company) {
    panel = <p className="text-gray-500">Loading company…</p>;
  } else if (tab === 'overview') {
    panel = <OverviewTab sections={company.sections} />;
  } else {
    // RapidRatings is only called once this tab is opened, keyed so a new company starts fresh.
    panel = <RapidRatingsTab key={companyId} companyName={company.companyName} />;
  }

  return (
    <div className="flex h-full flex-col">
      <PageHeader title={company?.companyName ?? 'Company'} />

      {company && (company.tier || company.country) && (
        <div className="flex flex-wrap items-center gap-2 px-10 pt-4 text-sm text-gray-600">
          {company.tier && <Badge>{company.tier}</Badge>}
          {company.country && <Badge>{company.country}</Badge>}
        </div>
      )}

      <div role="tablist" aria-label="Company details" className="mt-4 flex gap-1 border-b border-gray-200 px-10">
        {TABS.map(({ id, label }) => {
          const selected = tab === id;
          return (
            <button
              key={id}
              type="button"
              role="tab"
              id={`tab-${id}`}
              aria-selected={selected}
              aria-controls="company-details-panel"
              onClick={() => selectTab(id)}
              className={`-mb-px border-b-2 px-4 py-3 text-[16px] font-semibold transition-colors ${
                selected ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-600 hover:text-gray-900'
              }`}
            >
              {label}
            </button>
          );
        })}
      </div>

      <div
        role="tabpanel"
        id="company-details-panel"
        aria-labelledby={`tab-${tab}`}
        className="flex min-h-0 flex-1 flex-col overflow-auto bg-gray-50 px-10 py-6"
      >
        {panel}
      </div>
    </div>
  );
}
