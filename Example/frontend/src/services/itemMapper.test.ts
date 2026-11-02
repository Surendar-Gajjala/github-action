import { mapItem } from './itemMapper';

describe('mapItem', () => {
  it('maps the item-query.json aliases, using item__id as the id', () => {
    const i = mapItem({
      item_number: ' A93548-290 ',
      description: 'RES D,0402,169.00 kOHM',
      item__business_unit: 'Networking',
      item__id: 4332025200,
    });

    expect(i.id).toBe(4332025200);
    expect(i.itemNumber).toBe('A93548-290');
    expect(i.description).toBe('RES D,0402,169.00 kOHM');
    expect(i.businessUnit).toBe('Networking');
  });

  it('gives unselected or blank properties their empty values', () => {
    const i = mapItem({ item__id: '7', item_number: '', description: null });

    expect(i.id).toBe(7);
    expect(i.itemNumber).toBeNull();
    expect(i.description).toBeNull();
    expect(i.revision).toBeNull();
    expect(i.isProduct).toBeNull();
    expect(i.structureRole).toBeNull();
    expect(i.odmName).toEqual([]);
    expect(i.odmActive).toEqual([]);
    expect(i.availabilityRisk).toBe('NOT ASSESSED');
    expect(i.productFamiliesImpacted).toBeNull();
  });

  it('normalises lists, booleans and enum-like values', () => {
    const i = mapItem({
      item__id: 1,
      item__is_product: 'yes',
      item__structure_role: 'bom',
      item__odm__name: "['Flextronics', 'Jabil']",
      item__odm__active: ['true', false],
      item__availability_risk: 'high',
      item__product_families_impacted: '3',
    });

    expect(i.isProduct).toBe(true);
    expect(i.structureRole).toBe('BOM');
    expect(i.odmName).toEqual(['Flextronics', 'Jabil']);
    expect(i.odmActive).toEqual([true, false]);
    expect(i.availabilityRisk).toBe('HIGH');
    expect(i.productFamiliesImpacted).toBe(3);
  });

  it('turns unrecognised enum values into null', () => {
    const i = mapItem({ item__id: 1, item__structure_role: 'KIT', item__availability_risk: 'EXTREME' });

    expect(i.structureRole).toBeNull();
    expect(i.availabilityRisk).toBeNull();
  });
});
