import { mapSite } from './siteMapper';

/** A full Enterprise `results` row, keyed by the site-query.json aliases. */
const RAW = {
  site__id: 4332013843,
  site_id: 1201,
  internal_site_id: 'PEN-01',
  site_name: 'Penang Assembly',
  site_type: 'FINAL_ASSEMBLY',
  address_full_address: 'Plot 13, Bayan Lepas, Penang',
  address_address_line_1: 'Plot 13',
  address_address_line_2: '',
  address_address_line_3: null,
  address_city_locality: 'Bayan Lepas',
  address_district_county: null,
  address_state_province: 'Penang',
  address_postal_code: '11900',
  address_country: 'Malaysia',
  address_latitude: 5.2945,
  address_longitude: '100.2593',
};

describe('mapSite', () => {
  it('maps every alias to the internal Site field, using site__id as the id', () => {
    const s = mapSite(RAW);

    expect(s.id).toBe(4332013843);
    expect(s.siteId).toBe(1201);
    expect(s.internalSiteId).toBe('PEN-01');
    expect(s.siteName).toBe('Penang Assembly');
    expect(s.siteType).toBe('Final Assembly');
    expect(s.fullAddress).toBe('Plot 13, Bayan Lepas, Penang');
    expect(s.addressLine1).toBe('Plot 13');
    expect(s.cityLocality).toBe('Bayan Lepas');
    expect(s.stateProvince).toBe('Penang');
    expect(s.postalCode).toBe('11900');
    expect(s.country).toBe('Malaysia');
  });

  it('treats empty strings, nulls and missing fields as null', () => {
    const s = mapSite({ site__id: 2, site_name: '   ' });

    expect(s.addressLine2).toBeNull(); // ''
    expect(s.addressLine3).toBeNull(); // null
    expect(s.districtCounty).toBeNull(); // missing
    expect(s.siteName).toBeNull(); // blank after trim
    expect(s.siteType).toBeNull(); // missing
  });

  it('parses latitude/longitude from numbers and numeric strings, null otherwise', () => {
    expect(mapSite(RAW).latitude).toBe(5.2945);
    expect(mapSite(RAW).longitude).toBeCloseTo(100.2593);
    expect(mapSite({ site__id: 3, address_latitude: 'n/a' }).latitude).toBeNull();
    expect(mapSite({ site__id: 4 }).longitude).toBeNull();
  });

  it('normalises site_type (case / separators) to the SiteType label, unknown -> null', () => {
    expect(mapSite({ site__id: 1, site_type: 'ic_assembly' }).siteType).toBe('IC Assembly');
    expect(mapSite({ site__id: 2, site_type: 'HQ' }).siteType).toBe('HQ');
    expect(mapSite({ site__id: 3, site_type: 'Warehouse' }).siteType).toBe('Warehouse');
    expect(mapSite({ site__id: 4, site_type: 'SPACEPORT' }).siteType).toBeNull();
  });

  it('coerces a numeric-string site_id to a number', () => {
    expect(mapSite({ site__id: 9, site_id: '1201' }).siteId).toBe(1201);
  });
});
