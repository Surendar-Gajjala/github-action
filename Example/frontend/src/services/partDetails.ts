import type { PartOverview } from '../types/partDetails';
import { num, text } from './enterpriseValues';
import {
  type SectionSpec,
  type ValueOf,
  asBand,
  asCode,
  asList,
  asMeasure,
  asNumber,
  asText,
  asYesNo,
  buildSections,
  fetchObject,
  scoreReason,
  valuesOf,
} from './objectOverview';

const risk = (label: string, base: string, driver?: { property: string; label: string }) => ({
  label,
  value: asBand(`${base}.band`),
  reason: scoreReason(base, driver),
});

/** "-40 °C to 125 °C" from the operating temperature bounds; one bound alone is shown as-is. */
const operatingTemp = (get: ValueOf) => {
  const min = num(get('parametric.operating_temp.min_c'));
  const max = num(get('parametric.operating_temp.max_c'));
  if (min === null && max === null) return null;
  if (min !== null && max !== null) return `${min} °C to ${max} °C`;
  return min !== null ? `min ${min} °C` : `max ${max} °C`;
};

/** Part Overview sections, named after the part properties Enterprise actually returns. */
const PART_SECTIONS: SectionSpec[] = [
  {
    title: 'General',
    fields: [
      { label: 'Part Number', value: asText('part_number') },
      { label: 'Manufacturer', value: asText('manufacturer') },
      { label: 'Description', value: asText('description') },
      { label: 'Package / Case', value: asText('parametric.package_case') },
      { label: 'Datasheet', value: asText('datasheet_url') },
    ],
  },
  {
    title: 'Risk',
    fields: [
      risk('Overall Risk', 'part_risk.overview', { property: 'part_risk.overview.dominant_axis', label: 'Driven by' }),
      risk('Sourcing Risk', 'part_risk.sourcing'),
      risk('Obsolescence Risk', 'part_risk.obsolescence', { property: 'part_risk.obsolescence.dominant_factor', label: 'Driven by' }),
      risk('Geographic Risk', 'part_risk.geographic'),
      risk('Compliance Risk', 'part_risk.compliance'),
      risk('Supplier Health Risk', 'part_risk.supplier_health'),
    ],
  },
  {
    title: 'Lifecycle',
    fields: [
      { label: 'Lifecycle Status', value: asCode('lifecycle.status') },
      { label: 'Status Source', value: asText('lifecycle.status_source') },
      { label: 'At Risk', value: asYesNo('lifecycle.is_at_risk') },
      { label: 'Customer Status', value: asCode('lifecycle.customer_status'), reason: asText('lifecycle.customer_status_reason') },
      { label: 'Forecasted End of Life', value: asText('lifecycle.forecasted_end_of_life_date') },
      { label: 'Weeks to Forecasted End of Life', value: asNumber('lifecycle.weeks_to_forecasted_end_of_life') },
      { label: 'Supplier Commitment Date', value: asText('lifecycle.supplier_commitment_date') },
      { label: 'Obsolescence Notice', value: asText('obsolescence.notice_date') },
      { label: 'Last Time Buy', value: asText('obsolescence.last_time_buy_date') },
      { label: 'Last Ship', value: asText('obsolescence.last_ship_date') },
      { label: 'Years Remaining (estimate)', value: asNumber('obsolescence.years_remaining_estimate') },
    ],
  },
  {
    title: 'Sourcing',
    fields: [
      { label: 'Sole Sourced', value: asYesNo('sourcing.is_sole_sourced') },
      { label: 'Qualified Alternates', value: asNumber('sourcing.qualified_alternates_count') },
      { label: 'NCNR', value: asYesNo('sourcing.ncnr') },
      { label: 'PO Terms', value: asText('sourcing.po_terms') },
      { label: 'Annual Spend', value: asCode('sourcing.annual_spend') },
      { label: 'Inventory Position', value: asNumber('inventory_position_weeks', ' weeks') },
      { label: 'Forecast Coverage', value: asNumber('forecast_coverage_weeks', ' weeks') },
    ],
  },
  {
    title: 'Origin & Trade',
    fields: [
      { label: 'Country of Origin', value: asText('origin.country_of_origin') },
      { label: 'Country of Assembly', value: asList('origin.country_of_assembly') },
      { label: 'Country of Diffusion', value: asList('origin.country_of_diffusion') },
      { label: 'ECCN', value: asText('trade_codes.eccn') },
      { label: 'HTS Code', value: asText('trade_codes.hts_code') },
      { label: 'Trade Classification', value: asCode('trade_codes.classification') },
    ],
  },
  {
    title: 'Technical',
    fields: [
      { label: 'Package Type', value: asText('technology.package_type') },
      { label: 'Process Node', value: asText('technology.process_node_technology') },
      { label: 'Wafer Size', value: asText('technology.wafer_size') },
      { label: 'Operating Temperature', value: operatingTemp },
      { label: 'Voltage Rating', value: asMeasure('parametric.voltage_rating') },
      { label: 'Capacitance', value: asMeasure('parametric.capacitance') },
    ],
  },
  {
    title: 'Qualifications',
    fields: [
      { label: 'AEC-Q100', value: asYesNo('qualifications.aec_q100') },
      { label: 'AEC-Q101', value: asYesNo('qualifications.aec_q101') },
      { label: 'AEC-Q200', value: asYesNo('qualifications.aec_q200') },
      { label: 'MIL-Spec', value: asYesNo('qualifications.mil_spec') },
      { label: 'Radiation Hardened', value: asYesNo('qualifications.radiation_hardened') },
      { label: 'IPC Class', value: asText('qualifications.ipc_class') },
    ],
  },
  {
    title: 'Environmental Compliance',
    fields: [
      { label: 'EU RoHS', value: asCode('env_compliance.eu_rohs.status') },
      { label: 'EU RoHS Exemptions', value: asList('env_compliance.eu_rohs.exemptions') },
      { label: 'China RoHS', value: asCode('env_compliance.china_rohs.status') },
      { label: 'REACH SVHC', value: asCode('env_compliance.reach_svhc.status'), reason: (get) => {
        const version = text(get('env_compliance.reach_svhc.list_version'));
        return version ? `SVHC list ${version}` : null;
      } },
      { label: 'Halogen Free', value: asCode('env_compliance.halogen_free.status') },
      { label: 'PFAS', value: asCode('env_compliance.pfas.status') },
      { label: 'POPs', value: asCode('env_compliance.pops.status') },
      { label: 'Prop 65', value: asCode('env_compliance.prop65.status') },
      { label: 'TSCA 6(h)', value: asCode('env_compliance.tsca_6h.status') },
    ],
  },
];

/** The part's Overview (one partial-object request). Unknown ids and non-parts are "not found". */
export async function fetchPartOverview(partId: number): Promise<PartOverview> {
  const get = valuesOf(await fetchObject(partId, 'part'));
  return {
    id: partId,
    partNumber: text(get('part_number')),
    manufacturer: text(get('manufacturer')),
    description: text(get('description')),
    sections: buildSections(PART_SECTIONS, get),
  };
}
