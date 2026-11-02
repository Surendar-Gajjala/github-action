import type { Company } from '../../types/company';
import { companyLink } from '../table/cells';
import type { Column } from '../table/types';

/**
 * One column per property selected by the Company query config, shown with internal
 * names. The hosted object ID is used as the row key but not shown, as on the other tabs.
 */
export const COMPANY_COLUMNS: Column<Company>[] = [
  { key: 'companyName', header: 'Company Name', render: (c) => companyLink(c.id, c.companyName) },
];
