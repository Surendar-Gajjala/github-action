import type { CompanyOverview } from '../types/companyDetails';
import { text } from './enterpriseValues';
import {
  type SectionSpec,
  type ValueOf,
  asBand,
  asCode,
  asNumber,
  asText,
  asYesNo,
  buildSections,
  fetchObject,
  humanizeCode,
  scoreReason,
  valuesOf,
} from './objectOverview';

/** "Issued by BSI · Expires 2026-07-02" under a certification status. */
const certificationReason = (base: string) => (get: ValueOf) => {
  const issuer = text(get(`${base}.issuer`));
  const expiry = text(get(`${base}.expiry`));
  const parts = [issuer ? `Issued by ${issuer}` : null, expiry ? `Expires ${expiry}` : null].filter(Boolean);
  return parts.length ? parts.join(' · ') : null;
};

const certification = (label: string, base: string) => ({
  label,
  value: asCode(`${base}.status`),
  reason: certificationReason(base),
});

/** "2% of parts supplied" under the at-risk parts count. */
const atRiskShare = (get: ValueOf) => {
  const pct = asNumber('parts_summary.at_risk_parts_pct', '%')(get);
  return pct ? `${pct} of parts supplied` : null;
};

/** Company Overview sections, named after the company properties Enterprise actually returns. */
const COMPANY_SECTIONS: SectionSpec[] = [
  {
    title: 'General',
    fields: [
      { label: 'Company Name', value: asText('company_name') },
      { label: 'Formatted Name', value: asText('basic_info.formatted_name') },
      { label: 'Company Tier', value: asCode('company_tier') },
      { label: 'Country', value: asText('basic_info.country') },
      { label: 'Website', value: asText('basic_info.website') },
      { label: 'Founded', value: asText('basic_info.founded_year') },
      { label: 'Employees', value: asNumber('basic_info.employee_count') },
      { label: 'Revenue Band', value: asCode('basic_info.revenue_band') },
    ],
  },
  {
    title: 'Risk',
    fields: [
      {
        label: 'Supplier Risk',
        value: asBand('supplier_risk.band'),
        reason: scoreReason('supplier_risk', { property: 'supplier_risk.dominant_factor', label: 'Driven by' }),
      },
    ],
  },
  {
    title: 'Identifiers',
    fields: [
      { label: 'DUNS Number', value: asText('basic_info.duns_number') },
      { label: 'CAGE Code', value: asText('basic_info.cage_code') },
      { label: 'Tax ID', value: asText('basic_info.tax_id') },
    ],
  },
  {
    title: 'Financial',
    fields: [
      { label: 'D&B Rating', value: asText('financial.dnb_rating') },
      { label: 'Moody’s Credit Rating', value: asText('financial.moodys_credit_rating') },
    ],
  },
  {
    title: 'Ownership',
    fields: [
      { label: 'Ownership Type', value: asCode('ownership.ownership_type') },
      { label: 'FOCI Status', value: asCode('ownership.foci_status') },
      { label: 'Foreign Ownership', value: asNumber('ownership.foreign_ownership_pct', '%') },
    ],
  },
  {
    title: 'Sourcing',
    fields: [
      { label: 'Preferred Sourcing', value: asCode('preferred_sourcing.status'), reason: asText('preferred_sourcing.reason') },
      { label: 'Parts Supplied', value: asNumber('parts_summary.parts_supplied_count') },
      { label: 'At-Risk Parts', value: asNumber('parts_summary.at_risk_parts_count'), reason: atRiskShare },
      { label: 'Obsolete Parts', value: asNumber('parts_summary.obsolete_parts_count') },
    ],
  },
  {
    title: 'Certifications',
    fields: [
      certification('ISO 9001', 'certifications.iso_9001'),
      certification('ISO 14001', 'certifications.iso_14001'),
      certification('ISO 13485', 'certifications.iso_13485'),
      certification('IATF 16949', 'certifications.iatf_16949'),
      certification('AS9100', 'certifications.as9100'),
    ],
  },
  {
    title: 'ESG',
    fields: [
      { label: 'CDP Score', value: asText('esg.cdp_score') },
      { label: 'SBTi Target Set', value: asYesNo('esg.sbti_target_set') },
      { label: 'Sustainability Report Filed', value: asYesNo('esg.sustainability_report_filed') },
      { label: 'Last Sustainability Report', value: asText('esg.last_sustainability_report_year') },
      { label: 'Scope 1 Emissions Disclosed', value: asYesNo('esg.scope_1_emissions_disclosed') },
      { label: 'Scope 2 Emissions Disclosed', value: asYesNo('esg.scope_2_emissions_disclosed') },
      { label: 'Scope 3 Emissions Disclosed', value: asYesNo('esg.scope_3_emissions_disclosed') },
    ],
  },
  {
    title: 'Cyber',
    fields: [
      { label: 'Has CISO', value: asYesNo('cyber.has_ciso') },
      { label: 'SOC 2 Type 2', value: asYesNo('cyber.has_soc2_type2') },
      { label: 'Incident Response Plan', value: asYesNo('cyber.has_incident_response_plan') },
      { label: 'Recent Breaches', value: asNumber('cyber.breach_history_recent_count') },
      { label: 'Last Breach', value: asText('cyber.last_breach_date') },
    ],
  },
  {
    title: 'Kharon Risk Data',
    fields: [
      { label: 'Kharon ID', value: asText('kharon.kharon_id') },
      { label: 'Kharon Name', value: asText('kharon.kharon_name') },
      { label: 'Supplier City', value: asText('kharon.supplier_city') },
      { label: 'Supplier Country', value: asText('kharon.supplier_country') },
      { label: 'Max Risk Score', value: asNumber('kharon.max_risk_score') },
      { label: 'Total Risks', value: asNumber('kharon.total_risks_count') },
      { label: 'Any Risk Above Threshold', value: asYesNo('kharon.any_risk_above_threshold') },
      { label: 'Risks Above Threshold', value: asNumber('kharon.risks_above_threshold_count') },
      { label: 'Last Modified', value: asText('kharon.last_modified') },
    ],
  },
];

/** The company's Overview (one partial-object request). Unknown ids and non-companies are "not found". */
export async function fetchCompanyOverview(companyId: number): Promise<CompanyOverview> {
  const get = valuesOf(await fetchObject(companyId, 'company'));
  return {
    id: companyId,
    companyName: text(get('company_name')),
    country: text(get('basic_info.country')),
    tier: humanizeCode(get('company_tier')),
    sections: buildSections(COMPANY_SECTIONS, get),
  };
}
