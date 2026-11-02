import { useParams } from 'react-router-dom';

import { PageHeader } from '../components/PageHeader';
import { OverviewTab } from '../components/itemDetails/OverviewTab';
import { LoadError } from '../components/table/LoadError';
import { Badge } from '../components/ui/Badge';
import { usePartOverview } from '../hooks/usePartDetails';

/** Part details: a single Overview tab with all part properties. */
export function PartDetailsPage() {
  const { id } = useParams();
  const partId = Number(id);
  if (!Number.isSafeInteger(partId) || partId <= 0) {
    return (
      <div className="flex h-full flex-col">
        <PageHeader title="Part" />
        <p className="px-10 py-8 text-gray-600">This part link is not valid.</p>
      </div>
    );
  }
  return <PartDetails partId={partId} />;
}

function PartDetails({ partId }: { partId: number }) {
  const overview = usePartOverview(partId);
  const part = overview.data;

  return (
    <div className="flex h-full flex-col">
      <PageHeader title={part?.partNumber ?? 'Part'} subtitle={part?.manufacturer ?? undefined} />

      {part?.manufacturer && (
        <div className="flex flex-wrap items-center gap-2 px-10 pt-4 text-sm text-gray-600">
          <Badge>{part.manufacturer}</Badge>
        </div>
      )}

      <div role="tablist" aria-label="Part details" className="mt-4 flex gap-1 border-b border-gray-200 px-10">
        <button
          type="button"
          role="tab"
          id="tab-overview"
          aria-selected="true"
          aria-controls="part-details-panel"
          className="-mb-px border-b-2 border-blue-600 px-4 py-3 text-[16px] font-semibold text-blue-600"
        >
          Overview
        </button>
      </div>

      <div
        role="tabpanel"
        id="part-details-panel"
        aria-labelledby="tab-overview"
        className="flex min-h-0 flex-1 flex-col overflow-auto bg-gray-50 px-10 py-6"
      >
        {overview.isError ? (
          <LoadError title="Part" error={overview.error} />
        ) : overview.isLoading || !part ? (
          <p className="text-gray-500">Loading part…</p>
        ) : (
          <OverviewTab sections={part.sections} />
        )}
      </div>
    </div>
  );
}
