package com.demo.itemintegration.partdetail.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.external.dto.ExternalObjectResponse;
import com.demo.itemintegration.itemdetail.dto.OverviewField;
import com.demo.itemintegration.itemdetail.dto.OverviewSection;
import com.demo.itemintegration.partdetail.dto.PartOverviewDto;
import com.fasterxml.jackson.databind.ObjectMapper;

class PartOverviewMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final PartOverviewMapper mapper = new PartOverviewMapper();

    /** Subset of the real partial-object response for part Y5363689, in the hosted wrapper format. */
    private static final String RESPONSE = """
            {"objectId": 4332025900, "objectTypeId": 4332013901, "objectTypeDbName": "part",
             "scope": {"id": 4332013722, "name": "Enterprise"},
             "properties": {
               "part_number": {"value": "Y5363689"},
               "manufacturer": {"value": "BRADY CORPORATION"},
               "mfr_nbr": {"value": ["105313"]},
               "country_of_origin": {"value": ""},
               "sourcing_type": {"value": "UNKNOWN", "property_is_calculated": true},
               "z2_properties_comparison": {"value": "Incomparable"},
               "availability_risk": {"value": "NOT_ASSESSED", "property_is_calculated": true},
               "availability_risk_reason": {"value": "Lifecycle unknown or not matched to the Z2 catalog."},
               "supply_chain_risk": {"value": "NOT_ASSESSED"},
               "supply_chain_risk_reason": {"value": "Not matched to the Z2 catalog."},
               "environmental_compliance_risk": {"value": "MEDIUM"},
               "environmental_compliance.eu_rohs": {"value": "UNK"},
               "environmental_compliance.pwb_lead_halogen": {"value": "NO_DATA"},
               "environmental_compliance.needs_review": {"value": "false"},
               "environmental_compliance.pnr": {"value": "OK"},
               "used_in_products": {"value": "N88890-100, N89319-100"},
               "products_impacted": {"value": "2"},
               "z2data.z2_match_status": {"value": "NO_MATCH"},
               "z2data.z2_last_validated_at": {"value": "2026-08-31 12:48:36.266"},
               "unlisted_property": {"value": "not exposed"}
             }}
            """;

    private PartOverviewDto overview() throws Exception {
        return mapper.toOverview(json.readValue(RESPONSE, ExternalObjectResponse.class));
    }

    /** Fields keyed "label@section"; labels are unique within the whole overview. */
    private Map<String, OverviewField> fields(PartOverviewDto dto) {
        Map<String, OverviewField> byKey = new LinkedHashMap<>();
        for (OverviewSection section : dto.sections()) {
            for (OverviewField field : section.fields()) {
                assertThat(byKey.put(field.label() + "@" + section.title(), field))
                        .as("duplicate " + field.label()).isNull();
            }
        }
        assertThat(byKey.keySet().stream().map(k -> k.substring(0, k.indexOf('@')))).doesNotHaveDuplicates();
        return byKey;
    }

    @Test
    void headerFieldsUseInternalNamesAndLabels() throws Exception {
        PartOverviewDto dto = overview();

        assertThat(dto.id()).isEqualTo(4332025900L);
        assertThat(dto.partNumber()).isEqualTo("Y5363689");
        assertThat(dto.manufacturer()).isEqualTo("BRADY CORPORATION");
    }

    @Test
    void groupsPropertiesIntoSectionsWithDisplayValues() throws Exception {
        PartOverviewDto dto = overview();
        Map<String, OverviewField> f = fields(dto);

        assertThat(dto.sections()).extracting(OverviewSection::title)
                .containsExactly("General", "Risk", "Environmental Compliance", "Usage & Impact", "Z2 Data");
        assertThat(f.get("Part Number@General").value()).isEqualTo("Y5363689");
        assertThat(f.get("Manufacturer@General").value()).isEqualTo("BRADY CORPORATION");
        assertThat(f.get("Manufacturer Number@General").value()).isEqualTo("105313");
        assertThat(f.get("Sourcing Type@General").value()).isEqualTo("Unknown");
        assertThat(f.get("Country of Origin@General").value()).isNull();
        assertThat(f.get("EU RoHS@Environmental Compliance").value()).isEqualTo("UNK");
        assertThat(f.get("PWB Lead / Halogen@Environmental Compliance").value()).isEqualTo("No Data");
        assertThat(f.get("Needs Review@Environmental Compliance").value()).isEqualTo("No");
        assertThat(f.get("Used In Products@Usage & Impact").value()).isEqualTo("N88890-100, N89319-100");
        assertThat(f.get("Z2 Match Status@Z2 Data").value()).isEqualTo("NO_MATCH");
    }

    @Test
    void riskFieldsCarryTheirReason() throws Exception {
        Map<String, OverviewField> f = fields(overview());

        OverviewField availability = f.get("Availability Risk@Risk");
        assertThat(availability.value()).isEqualTo("Not Assessed");
        assertThat(availability.reason()).isEqualTo("Lifecycle unknown or not matched to the Z2 catalog.");
        assertThat(f.get("Supply Chain Risk@Risk").value()).isEqualTo("Not Assessed");
        assertThat(f.get("Environmental Compliance Risk@Risk").value()).isEqualTo("MEDIUM");
    }

    @Test
    void doesNotExposeUnlistedOrExternalNames() throws Exception {
        String serialized = json.writeValueAsString(overview());

        assertThat(serialized).doesNotContain("not exposed", "z2data.", "environmental_compliance.", "property_id",
                "objectTypeId");
    }
}
