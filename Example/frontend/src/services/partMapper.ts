import type { LifecycleStatus, Part, SourcingType, SupplyChainRisk } from '../types/part';
import { enumKey, num, text } from './enterpriseValues';

/**
 * Maps one Enterprise Query Config V2 `results` row (keyed by the part-query.json
 * aliases) to the internal {@link Part} model. Aliases the query does not select
 * arrive missing and take the defaults below. `part__id` is the Enterprise record id.
 */
export function mapPart(raw: Record<string, unknown>): Part {
  return {
    id: num(raw.part__id),
    partNumber: text(raw.part_number),
    manufacturer: text(raw.manufacturer),
    description: text(raw.description),
    z2PropertiesComparison: text(raw.z2_properties_comparison),
    countryOfOrigin: text(raw.part__country_of_origin),
    sourcingType: sourcingType(raw.part__sourcing_type),
    supplyChainRisk: supplyChainRisk(raw.part__supply_chain_risk),
    lifecycleStatus: lifecycleStatus(raw.part__lifecycle__status),
  };
}

/** OTS / off-the-shelf, custom, conflict; blank or unrecognised becomes Unknown. */
function sourcingType(value: unknown): SourcingType {
  switch (enumKey(value)) {
    case 'OTS':
    case 'OFF THE SHELF':
    case 'OFFTHESHELF':
    case 'COTS':
      return 'Off-the-shelf';
    case 'CUSTOM':
      return 'Custom';
    case 'CONFLICT':
      return 'Conflict';
    default:
      return 'Unknown';
  }
}

/** Blank becomes Not Assessed; unrecognised becomes null. */
function supplyChainRisk(value: unknown): SupplyChainRisk | null {
  const key = enumKey(value);
  if (key === null || key === 'NOT ASSESSED') return 'Not Assessed';
  return key === 'LOW' || key === 'MEDIUM' || key === 'HIGH' ? key : null;
}

/** Active, NRND, last time buy (LTB), obsolete; blank or unrecognised becomes Unknown. */
function lifecycleStatus(value: unknown): LifecycleStatus {
  switch (enumKey(value)) {
    case 'ACTIVE':
      return 'Active';
    case 'NRND':
    case 'NOT RECOMMENDED FOR NEW DESIGN':
    case 'NOT RECOMMENDED FOR NEW DESIGNS':
      return 'NRND';
    case 'LASTTIMEBUY':
    case 'LAST TIME BUY':
    case 'LTB':
      return 'LastTimeBuy';
    case 'OBSOLETE':
      return 'Obsolete';
    default:
      return 'Unknown';
  }
}
