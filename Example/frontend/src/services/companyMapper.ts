import type { Company } from '../types/company';
import { num, text } from './enterpriseValues';

/**
 * Maps one Enterprise Query Config V2 `results` row (keyed by the company-query.json
 * aliases) to the internal {@link Company} model, using the shared `enterpriseValues`
 * normalisation: blank/empty/missing become null, numbers are coerced from numeric strings.
 */
export function mapCompany(raw: Record<string, unknown>): Company {
  return {
    id: num(raw.company__id),
    companyName: text(raw.company_name),
  };
}
