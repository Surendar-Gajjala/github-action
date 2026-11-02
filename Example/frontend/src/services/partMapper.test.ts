import { mapPart } from './partMapper';

describe('mapPart', () => {
  it('maps the part-query.json aliases, using part__id as the id', () => {
    const p = mapPart({
      part_number: 'FC FBMJ3216HS480NT',
      manufacturer: 'TAIYO YUDEN,(USA),INC',
      description: 'Ferrite bead',
      part__lifecycle__status: 'Active',
      part__id: 4332025900,
    });

    expect(p.id).toBe(4332025900);
    expect(p.partNumber).toBe('FC FBMJ3216HS480NT');
    expect(p.manufacturer).toBe('TAIYO YUDEN,(USA),INC');
    expect(p.description).toBe('Ferrite bead');
    expect(p.lifecycleStatus).toBe('Active');
  });

  it('applies the defaults to unselected or blank properties', () => {
    const p = mapPart({ part__id: 1, description: '  ' });

    expect(p.description).toBeNull();
    expect(p.countryOfOrigin).toBeNull();
    expect(p.z2PropertiesComparison).toBeNull();
    expect(p.sourcingType).toBe('Unknown');
    expect(p.supplyChainRisk).toBe('Not Assessed');
    expect(p.lifecycleStatus).toBe('Unknown');
  });

  it('normalises enum-like values case- and separator-insensitively', () => {
    expect(mapPart({ part__sourcing_type: 'cots' }).sourcingType).toBe('Off-the-shelf');
    expect(mapPart({ part__sourcing_type: 'off-the-shelf' }).sourcingType).toBe('Off-the-shelf');
    expect(mapPart({ part__supply_chain_risk: 'not_assessed' }).supplyChainRisk).toBe('Not Assessed');
    expect(mapPart({ part__supply_chain_risk: 'low' }).supplyChainRisk).toBe('LOW');
    expect(mapPart({ part__supply_chain_risk: 'severe' }).supplyChainRisk).toBeNull();
    expect(mapPart({ part__lifecycle__status: 'last_time_buy' }).lifecycleStatus).toBe('LastTimeBuy');
    expect(mapPart({ part__lifecycle__status: 'Not recommended for new designs' }).lifecycleStatus).toBe('NRND');
    expect(mapPart({ part__lifecycle__status: 'retired' }).lifecycleStatus).toBe('Unknown');
  });
});
