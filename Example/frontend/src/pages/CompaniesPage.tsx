import { COMPANY_COLUMNS } from '../components/companies/companyColumns';
import { PagedEntityPage } from '../components/table/PagedEntityPage';
import { useCompanies } from '../hooks/useCompanies';
import type { Company } from '../types/company';

const SEARCH_FIELDS = ['companyName'] as const;

export function CompaniesPage() {
  return (
    <PagedEntityPage<Company>
      title="Companies"
      subtitle="Manufacturers, suppliers and other companies."
      noun="companies"
      columns={COMPANY_COLUMNS}
      searchFields={SEARCH_FIELDS}
      searchPlaceholder="Search this page by company name..."
      getRowKey={(company, index) => company.id ?? `row-${index}`}
      usePage={useCompanies}
    />
  );
}
