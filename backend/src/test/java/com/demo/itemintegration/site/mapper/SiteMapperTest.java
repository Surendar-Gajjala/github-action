package com.demo.itemintegration.site.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.external.dto.ExternalSiteRecord;
import com.demo.itemintegration.site.model.Site;
import com.demo.itemintegration.site.model.SiteType;
import com.fasterxml.jackson.databind.ObjectMapper;

class SiteMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final SiteMapper mapper = new SiteMapper();

    private Site map(String row) throws Exception {
        List<ExternalSiteRecord> records = json.readValue("{\"results\":[" + row + "]}", ExternalQueryResponse.class)
                .records(ExternalSiteRecord.class);
        assertThat(records).hasSize(1);
        return mapper.toSite(records.get(0));
    }

    @Test
    void mapsAllFields() throws Exception {
        Site site = map("""
                {"site_id": 1201, "internal_site_id": "PEN-01", "site_name": " Penang Assembly ",
                 "site_type": "FINAL_ASSEMBLY", "address_full_address": "Plot 13, Bayan Lepas, Penang",
                 "address_address_line_1": "Plot 13", "address_address_line_2": "Phase 4",
                 "address_address_line_3": "", "address_city_locality": "Bayan Lepas",
                 "address_district_county": "Barat Daya", "address_state_province": "Penang",
                 "address_postal_code": "11900", "address_country": "Malaysia",
                 "address_latitude": 5.2945, "address_longitude": "100.2593", "site__id": 4332013900}
                """);

        assertThat(site.id()).isEqualTo(4_332_013_900L);
        assertThat(site.siteId()).isEqualTo(1201L);
        assertThat(site.internalSiteId()).isEqualTo("PEN-01");
        assertThat(site.siteName()).isEqualTo("Penang Assembly");
        assertThat(site.siteType()).isEqualTo(SiteType.FINAL_ASSEMBLY);
        assertThat(site.fullAddress()).isEqualTo("Plot 13, Bayan Lepas, Penang");
        assertThat(site.addressLine1()).isEqualTo("Plot 13");
        assertThat(site.addressLine2()).isEqualTo("Phase 4");
        assertThat(site.addressLine3()).isNull();
        assertThat(site.cityLocality()).isEqualTo("Bayan Lepas");
        assertThat(site.districtCounty()).isEqualTo("Barat Daya");
        assertThat(site.stateProvince()).isEqualTo("Penang");
        assertThat(site.postalCode()).isEqualTo("11900");
        assertThat(site.country()).isEqualTo("Malaysia");
        assertThat(site.latitude()).isEqualTo(5.2945);
        assertThat(site.longitude()).isEqualTo(100.2593);
    }

    @Test
    void numericPostalCodeStaysText() throws Exception {
        assertThat(map("{\"address_postal_code\": 11900}").postalCode()).isEqualTo("11900");
    }

    @Test
    void emptyAndMissingValuesDoNotFail() throws Exception {
        Site site = map("""
                {"site_id": "", "site_name": null, "site_type": "", "address_latitude": "n/a", "address_longitude": ""}
                """);

        assertThat(site.siteId()).isNull();
        assertThat(site.siteName()).isNull();
        assertThat(site.siteType()).isNull();
        assertThat(site.latitude()).isNull();
        assertThat(site.longitude()).isNull();
        assertThat(site.country()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "Fabrication, FABRICATION", "FAB, FABRICATION", "IC Assembly, IC_ASSEMBLY", "IC_ASSEMBLY, IC_ASSEMBLY",
            "final assembly, FINAL_ASSEMBLY", "TEST, TEST", "Packaging, PACKAGING", "WAREHOUSE, WAREHOUSE",
            "HQ, HQ", "Headquarters, HQ", "office, OFFICE"
    })
    void normalisesSiteType(String external, SiteType expected) throws Exception {
        assertThat(map("{\"site_type\": \"" + external + "\"}").siteType()).isEqualTo(expected);
    }

    @Test
    void unknownSiteTypeBecomesNull() throws Exception {
        assertThat(map("{\"site_type\": \"Laboratory\"}").siteType()).isNull();
    }
}
