import type { AvailabilityRisk, Item, StructureRole } from '../types/item';
import { bool, enumKey, int, list, num, scalarText, text } from './enterpriseValues';

/**
 * Maps one Enterprise Query Config V2 `results` row (keyed by the item-query.json
 * aliases) to the internal {@link Item} model. Aliases the query does not select arrive missing and
 * become null / empty. `item__id` is the Enterprise record id used for details links.
 */
export function mapItem(raw: Record<string, unknown>): Item {
  return {
    id: num(raw.item__id),
    itemNumber: text(raw.item_number),
    description: text(raw.description),
    revision: text(raw.item__revision),
    businessUnit: text(raw.item__business_unit),
    isProduct: bool(raw.item__is_product),
    structureRole: structureRole(raw.item__structure_role),
    odmName: list(raw.item__odm__name, scalarText),
    odmActive: list(raw.item__odm__active, bool),
    availabilityRisk: availabilityRisk(raw.item__availability_risk),
    usedInProducts: text(raw.item__used_in_products),
    productFamiliesImpacted: int(raw.item__product_families_impacted),
    itemStatusName: text(raw.item__item_status__name),
  };
}

/** Case-insensitive ITEM / BOM; anything else becomes null. */
function structureRole(value: unknown): StructureRole | null {
  const key = enumKey(value);
  return key === 'ITEM' || key === 'BOM' ? key : null;
}

/** Blank becomes NOT ASSESSED; unrecognised becomes null. */
function availabilityRisk(value: unknown): AvailabilityRisk | null {
  const key = enumKey(value);
  if (key === null) return 'NOT ASSESSED';
  return key === 'LOW' || key === 'MEDIUM' || key === 'HIGH' || key === 'NOT ASSESSED' ? key : null;
}
