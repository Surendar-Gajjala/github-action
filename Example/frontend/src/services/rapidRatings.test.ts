import { normaliseCompanyName, searchTermFor, toProxyPath } from './rapidRatings';

describe('company name matching', () => {
  it('ignores case, punctuation and trailing legal suffixes', () => {
    expect(normaliseCompanyName('Sunex Inc')).toBe('sunex');
    expect(normaliseCompanyName('SUNEX, INC.')).toBe('sunex');
    expect(normaliseCompanyName('Seiko Epson Corporation')).toBe('seiko epson');
    expect(normaliseCompanyName('Procter & Gamble Co')).toBe('procter and gamble');
  });

  it('never strips a name down to nothing', () => {
    expect(normaliseCompanyName('Company')).toBe('company');
  });

  it('searches without legal suffixes, falling back to the full name when that is too short', () => {
    expect(searchTermFor('Sunex Inc')).toBe('Sunex');
    expect(searchTermFor('Seiko Epson Corporation')).toBe('Seiko Epson');
    expect(searchTermFor('Accton Technology Corp.')).toBe('Accton Technology');
    expect(searchTermFor('HP Inc')).toBe('HP Inc');
    expect(searchTermFor('3M')).toBeNull();
  });
});

describe('toProxyPath', () => {
  it('keeps the resource path and query of a RapidRatings next URL', () => {
    expect(toProxyPath('https://rridata.sandbox.rrilabs.co/v2.0/companies/ABC1234/periods/?page=2')).toBe(
      '/companies/ABC1234/periods/?page=2',
    );
    expect(toProxyPath('https://rridata.sandbox.rrilabs.co/v2.0/companies/?name=Sunex&page=3')).toBe('/companies/?name=Sunex&page=3');
  });

  it('refuses anything that is not a RapidRatings resource URL', () => {
    expect(toProxyPath('not a url')).toBeNull();
    expect(toProxyPath('https://rridata.sandbox.rrilabs.co/v2.0/oauth/token/')).toBeNull();
  });
});
