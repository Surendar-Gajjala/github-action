import sourcesQuery from '../queries/item-sources-graph.json';
import type { ItemOverview } from '../types/itemDetails';
import type { Part } from '../types/part';
import { EnterpriseQueryError, enterprisePost } from './enterpriseQuery';
import { text } from './enterpriseValues';
import {
  type SectionSpec,
  asBand,
  asCode,
  asList,
  asNumber,
  asText,
  buildSections,
  fetchObject,
  humanizeCode,
  scoreReason,
  valuesOf,
} from './objectOverview';
import { mapPart } from './partMapper';

const risk = (label: string, base: string, withDriver = false) => ({
  label,
  value: asBand(`${base}.band`),
  reason: scoreReason(base, withDriver ? { property: `${base}.dominant_axis`, label: 'Driven by' } : undefined),
});

/** Item Overview sections, named after the item properties Enterprise actually returns. */
const ITEM_SECTIONS: SectionSpec[] = [
  {
    title: 'General',
    fields: [
      { label: 'Item Number', value: asText('item_number') },
      { label: 'Description', value: asText('description') },
      { label: 'Type', value: asCode('type') },
      { label: 'Business Unit', value: asText('business_unit') },
      { label: 'Product Family', value: asCode('product_family') },
      { label: 'Lifecycle Phase', value: asCode('lifecycle_phase') },
      { label: 'Lifecycle Status', value: asCode('item_lifecycle_status') },
      { label: 'OTS Classification', value: asCode('ots_classification') },
    ],
  },
  {
    title: 'Risk',
    fields: [
      risk('Overall Risk', 'item_risk.overview', true),
      risk('Sourcing Risk', 'item_risk.sourcing'),
      risk('Obsolescence Risk', 'item_risk.obsolescence'),
      risk('Geographic Risk', 'item_risk.geographic'),
      risk('Compliance Risk', 'item_risk.compliance'),
      risk('Supplier Health Risk', 'item_risk.supplier_health'),
    ],
  },
  {
    title: 'Sourcing',
    fields: [
      { label: 'Multi-Sourcing Status', value: asCode('multi_sourcing.status') },
      { label: 'Total Sources', value: asNumber('multi_sourcing.total_sources_count') },
      { label: 'Active Sources', value: asNumber('multi_sourcing.active_sources_count') },
      { label: 'At-Risk Parts', value: asNumber('at_risk_part_count') },
      { label: 'Affected Finished Goods', value: asNumber('item_risk.affected_finished_goods_count') },
    ],
  },
  {
    title: 'Geographic Concentration',
    fields: [
      { label: 'Country Status', value: asCode('geographic_concentration.country_status') },
      { label: 'Dominant Country', value: asText('geographic_concentration.dominant_country') },
      { label: 'Active Countries', value: asNumber('geographic_concentration.distinct_active_countries_count') },
      { label: 'Site Status', value: asCode('geographic_concentration.site_status') },
      { label: 'Active Sites', value: asNumber('geographic_concentration.distinct_active_sites_count') },
    ],
  },
  {
    title: 'Environmental Compliance',
    fields: [
      { label: 'EU RoHS', value: asCode('env_compliance.eu_rohs.status') },
      { label: 'EU RoHS Exemptions', value: asList('env_compliance.eu_rohs.exemptions') },
      { label: 'EU RoHS Test', value: asCode('compliance_regulatory.eu_rohs_test.rohs_compliance_status') },
      { label: 'EU RoHS Data Completeness', value: asNumber('compliance_regulatory.eu_rohs_test.data_completeness_pct', '%') },
      { label: 'China RoHS', value: asCode('env_compliance.china_rohs.status') },
      { label: 'REACH SVHC', value: asCode('env_compliance.reach_svhc.status') },
      { label: 'Halogen Free', value: asCode('env_compliance.halogen_free.status') },
      { label: 'PFAS', value: asCode('env_compliance.pfas.status') },
      { label: 'POPs', value: asCode('env_compliance.pops.status') },
      { label: 'Prop 65', value: asCode('env_compliance.prop65.status') },
      { label: 'TSCA 6(h)', value: asCode('env_compliance.tsca_6h.status') },
    ],
  },
  {
    title: 'Structure',
    fields: [
      { label: 'BOM Depth', value: asNumber('bom_depth') },
      { label: 'Descendant Parts', value: asNumber('descendant_part_count') },
    ],
  },
];

/** The item's Overview (one partial-object request). Unknown ids and non-items are "not found". */
export async function fetchItemOverview(itemId: number): Promise<ItemOverview> {
  const get = valuesOf(await fetchObject(itemId, 'item'));
  return {
    id: itemId,
    itemNumber: text(get('item_number')),
    description: text(get('description')),
    revision: text(get('revision')),
    itemType: humanizeCode(get('type')),
    itemStatus: humanizeCode(get('item_lifecycle_status')),
    sections: buildSections(ITEM_SECTIONS, get),
  };
}

/**
 * The item's sourced parts: one item_sources graph match anchored on the item id. Part
 * values follow the Parts list rules (`mapPart`); ordered by part number, then manufacturer.
 */
export async function fetchItemSources(itemId: number): Promise<Part[]> {
  const payload = { ...sourcesQuery, traversal: { ...sourcesQuery.traversal, anchorValues: [String(itemId)] } };
  const data = await enterprisePost<{ nodes?: unknown }>('/core/graph/match/execute', payload);
  if (typeof data !== 'object' || data === null || !Array.isArray(data.nodes)) {
    throw new EnterpriseQueryError('Unexpected sources response from the Enterprise API.');
  }
  const parts = new Map<number, Part>();
  for (const node of data.nodes as { id?: unknown; type?: unknown; properties?: Record<string, unknown> | null }[]) {
    if (typeof node?.id !== 'number' || String(node.type).toLowerCase() !== 'part' || parts.has(node.id)) continue;
    const p = node.properties ?? {};
    parts.set(
      node.id,
      mapPart({
        part__id: node.id,
        part_number: p.part_number,
        manufacturer: p.manufacturer,
        description: p.description,
        part__country_of_origin: p['origin.country_of_origin'],
        part__lifecycle__status: p['lifecycle.status'],
      }),
    );
  }
  return [...parts.values()].sort(
    (a, b) => compare(a.partNumber, b.partNumber) || compare(a.manufacturer, b.manufacturer),
  );
}

/** Natural order with nulls last. */
function compare(a: string | null, b: string | null): number {
  if (a === b) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  return a < b ? -1 : 1;
}
