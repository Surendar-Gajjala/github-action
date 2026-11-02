package com.demo.itemintegration.itemdetail.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.external.dto.ExternalObjectResponse;
import com.demo.itemintegration.itemdetail.dto.ItemOverviewDto;
import com.demo.itemintegration.itemdetail.dto.OverviewField;
import com.demo.itemintegration.itemdetail.dto.OverviewSection;
import com.fasterxml.jackson.databind.ObjectMapper;

class ItemOverviewMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ItemOverviewMapper mapper = new ItemOverviewMapper();

    /** Subset of the real partial-object response for A93548-290, in the hosted wrapper format. */
    private static final String RESPONSE = """
            {"objectId": 4332025200, "objectTypeId": 4332013781, "objectTypeDbName": "item",
             "scope": {"id": 4332013722, "name": "Enterprise"},
             "properties": {
               "item_number": {"id": 1, "value": "A93548-290", "property_id": 9, "property_is_calculated": false},
               "description": {"value": "RES D,0402,169.00 kOHM,1.00%,1/16W,YES"},
               "revision": {"value": "01"},
               "item_type": {"value": "RESISTOR_DISCRETE"},
               "item_status": {"value": "CONDITIONAL"},
               "make_buy": {"value": "BUY"},
               "business_unit": {"value": ""},
               "is_product": {"value": "false"},
               "restricted": {"value": "false"},
               "exemption_status": {"value": "N_A"},
               "structure_role": {"value": "ITEM"},
               "cost_risk": {"value": "NOT_ASSESSED", "property_is_calculated": true},
               "cost_risk_reason": {"value": "No active source has both a customer cost and a Z2 market price."},
               "environmental_compliance_risk": {"value": "LOW"},
               "environmental_compliance.eu_rohs": {"value": "Y_E"},
               "environmental_compliance.eu_rohs_lock": {"value": "IMPLICIT"},
               "environmental_compliance.ec_exemptions": {"value": ["7_C_I"]},
               "lifecycle": {"value": null},
               "lifecycle_metadata.total_sources": {"value": "6"},
               "manufacturer_parts_impacted": {"value": "6"},
               "unlisted_property": {"value": "not exposed"}
             }}
            """;

    private ItemOverviewDto overview() throws Exception {
        return mapper.toOverview(json.readValue(RESPONSE, ExternalObjectResponse.class));
    }

    /** Fields keyed "label@section"; labels are unique within the whole overview. */
    private Map<String, OverviewField> fields(ItemOverviewDto dto) {
        Map<String, OverviewField> byKey = new java.util.LinkedHashMap<>();
        for (OverviewSection section : dto.sections()) {
            for (OverviewField field : section.fields()) {
                assertThat(byKey.put(field.label() + "@" + section.title(), field)).as("duplicate " + field.label()).isNull();
            }
        }
        assertThat(byKey.keySet().stream().map(k -> k.substring(0, k.indexOf('@')))).doesNotHaveDuplicates();
        return byKey;
    }

    @Test
    void headerFieldsUseInternalNamesAndLabels() throws Exception {
        ItemOverviewDto dto = overview();

        assertThat(dto.id()).isEqualTo(4332025200L);
        assertThat(dto.itemNumber()).isEqualTo("A93548-290");
        assertThat(dto.revision()).isEqualTo("01");
        assertThat(dto.itemType()).isEqualTo("RESISTOR_DISCRETE");
        assertThat(dto.itemStatus()).isEqualTo("Conditional");
    }

    @Test
    void groupsPropertiesIntoSectionsWithDisplayValues() throws Exception {
        ItemOverviewDto dto = overview();
        Map<String, OverviewField> f = fields(dto);

        assertThat(dto.sections()).extracting(OverviewSection::title)
                .containsExactly("General", "Risk", "Lifecycle", "Environmental Compliance", "Usage & Impact", "Notes");
        assertThat(f.get("Item Status@General").value()).isEqualTo("Conditional");
        assertThat(f.get("Product@General").value()).isEqualTo("No");
        assertThat(f.get("Exemption Status@General").value()).isEqualTo("N/A");
        assertThat(f.get("Business Unit@General").value()).isNull();
        assertThat(f.get("EU RoHS@Environmental Compliance").value()).isEqualTo("Y_E");
        assertThat(f.get("EU RoHS Lock@Environmental Compliance").value()).isEqualTo("Implicit");
        assertThat(f.get("EC Exemptions@Environmental Compliance").value()).isEqualTo("7_C_I");
        assertThat(f.get("Total Sources@Lifecycle").value()).isEqualTo("6");
        assertThat(f.get("Manufacturer Parts Impacted@Usage & Impact").value()).isEqualTo("6");
    }

    @Test
    void riskFieldsCarryTheirReason() throws Exception {
        Map<String, OverviewField> f = fields(overview());

        OverviewField cost = f.get("Cost Risk@Risk");
        assertThat(cost.value()).isEqualTo("Not Assessed");
        assertThat(cost.reason()).isEqualTo("No active source has both a customer cost and a Z2 market price.");
        assertThat(f.get("Environmental Compliance Risk@Risk").value()).isEqualTo("LOW");
        assertThat(f.get("Quality Risk@Risk").value()).isNull();
        assertThat(f.get("Quality Risk@Risk").reason()).isNull();
    }

    @Test
    void doesNotExposeUnlistedOrExternalNames() throws Exception {
        ItemOverviewDto dto = overview();

        String serialized = json.writeValueAsString(dto);
        assertThat(serialized).doesNotContain("not exposed", "environmental_compliance.", "property_id", "objectTypeId");
    }
}
