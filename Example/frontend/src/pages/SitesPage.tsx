import { SITE_COLUMNS } from '../components/sites/siteColumns';
import { PagedEntityPage } from '../components/table/PagedEntityPage';
import { useSites } from '../hooks/useSites';
import type { Site } from '../types/site';

const SEARCH_FIELDS = ['siteName', 'internalSiteId', 'cityLocality', 'stateProvince', 'country', 'fullAddress'] as const;

export function SitesPage() {
  return (
    <PagedEntityPage<Site>
      title="Sites"
      subtitle="Fabrication, assembly, test and office locations."
      noun="sites"
      columns={SITE_COLUMNS}
      searchFields={SEARCH_FIELDS}
      searchPlaceholder="Search this page by name, ID or location..."
      getRowKey={(site, index) => site.id ?? `row-${index}`}
      usePage={useSites}
    />
  );
}
