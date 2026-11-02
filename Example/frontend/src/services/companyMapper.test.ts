import { mapCompany } from './companyMapper';

describe('mapCompany', () => {
  it('maps every alias to the internal Company field, using company__id as the id', () => {
    const c = mapCompany({ company_name: 'Seiko Epson Corporation', company__id: 22900 });

    expect(c.id).toBe(22900);
    expect(c.companyName).toBe('Seiko Epson Corporation');
  });

  it('treats empty strings, nulls and missing fields as null', () => {
    expect(mapCompany({ company__id: 1, company_name: '   ' }).companyName).toBeNull();
    expect(mapCompany({ company__id: 2, company_name: null }).companyName).toBeNull();
    expect(mapCompany({}).id).toBeNull();
  });

  it('coerces a numeric-string id to a number', () => {
    expect(mapCompany({ company__id: '13200' }).id).toBe(13200);
  });
});
