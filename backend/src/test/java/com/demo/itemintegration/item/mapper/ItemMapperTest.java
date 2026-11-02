package com.demo.itemintegration.item.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.demo.itemintegration.external.dto.ExternalItemRecord;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.item.model.AvailabilityRisk;
import com.demo.itemintegration.item.model.Item;
import com.demo.itemintegration.item.model.StructureRole;
import com.fasterxml.jackson.databind.ObjectMapper;

class ItemMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ItemMapper mapper = new ItemMapper();

    private ExternalItemRecord singleRecord(String body) throws Exception {
        List<ExternalItemRecord> records = json.readValue(body, ExternalQueryResponse.class).records(ExternalItemRecord.class);
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    @Test
    void mapsAllFieldsFromObjectRow() throws Exception {
        Item item = mapper.toItem(singleRecord("""
                {"zql": "SELECT ...", "properties": [], "objects": {},
                 "results": [{
                   "item_number": " ITM-001 ", "description": "Widget", "item__revision": "B",
                   "item__business_unit": "Hardware", "item__is_product": true,
                   "item__structure_role": "bom", "item__odm__name": ["Acme", "Globex"],
                   "item__odm__active": [true, false], "item__availability_risk": "MEDIUM",
                   "item__used_in_products": "P1, P2", "item__product_families_impacted": 3,
                   "item__item_status__name": "Released", "item__id": 42
                 }]}
                """));

        assertThat(item.id()).isEqualTo(42L);
        assertThat(item.itemNumber()).isEqualTo("ITM-001");
        assertThat(item.description()).isEqualTo("Widget");
        assertThat(item.revision()).isEqualTo("B");
        assertThat(item.businessUnit()).isEqualTo("Hardware");
        assertThat(item.isProduct()).isTrue();
        assertThat(item.structureRole()).isEqualTo(StructureRole.BOM);
        assertThat(item.odmName()).containsExactly("Acme", "Globex");
        assertThat(item.odmActive()).containsExactly(true, false);
        assertThat(item.availabilityRisk()).isEqualTo(AvailabilityRisk.MEDIUM);
        assertThat(item.usedInProducts()).isEqualTo("P1, P2");
        assertThat(item.productFamiliesImpacted()).isEqualTo(3);
        assertThat(item.itemStatusName()).isEqualTo("Released");
    }

    @Test
    void mapsPositionalRowUsingPropertyNames() throws Exception {
        Item item = mapper.toItem(singleRecord("""
                {"properties": ["item__id", "item_number", "item__availability_risk", "item__odm__name"],
                 "results": [["7", "ITM-7", "NOT_ASSESSED", "[]"]]}
                """));

        assertThat(item.id()).isEqualTo(7L);
        assertThat(item.itemNumber()).isEqualTo("ITM-7");
        assertThat(item.availabilityRisk()).isEqualTo(AvailabilityRisk.NOT_ASSESSED);
        assertThat(item.odmName()).isEmpty();
    }

    @Test
    void emptyAndMissingValuesDoNotFail() throws Exception {
        Item item = mapper.toItem(singleRecord("""
                {"results": [{"item_number": "", "description": null, "item__odm__name": "",
                              "item__odm__active": null, "item__product_families_impacted": "n/a",
                              "item__id": "abc"}]}
                """));

        assertThat(item.itemNumber()).isNull();
        assertThat(item.description()).isNull();
        assertThat(item.revision()).isNull();
        assertThat(item.isProduct()).isNull();
        assertThat(item.structureRole()).isNull();
        assertThat(item.odmName()).isEmpty();
        assertThat(item.odmActive()).isEmpty();
        assertThat(item.availabilityRisk()).isEqualTo(AvailabilityRisk.NOT_ASSESSED);
        assertThat(item.productFamiliesImpacted()).isNull();
        assertThat(item.id()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"[]\"                     | ''",
            "[]                         | ''",
            "\"Acme\"                   | Acme",
            "[\"Acme\"]                 | Acme",
            "\"['Acme', 'Globex']\"     | Acme;Globex",
            "\"[\\\"Acme\\\",\\\"Globex\\\"]\" | Acme;Globex",
            "[\"Acme\", \"\", null, \"Globex\"] | Acme;Globex"
    })
    void normalisesOdmNameShapes(String rawJson, String expected) throws Exception {
        Item item = mapper.toItem(singleRecord("{\"results\":[{\"item__odm__name\": " + rawJson + "}]}"));

        List<String> expectedList = expected.isEmpty() ? List.of() : List.of(expected.split(";"));
        assertThat(item.odmName()).isEqualTo(expectedList);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"[]\"              | ''",
            "true                | true",
            "\"[true, false]\"   | true;false",
            "[\"Y\", \"N\", 1]   | true;false;true"
    })
    void normalisesOdmActiveShapes(String rawJson, String expected) throws Exception {
        Item item = mapper.toItem(singleRecord("{\"results\":[{\"item__odm__active\": " + rawJson + "}]}"));

        List<Boolean> expectedList = expected.isEmpty() ? List.of()
                : Arrays.stream(expected.split(";")).map(Boolean::valueOf).toList();
        assertThat(item.odmActive()).isEqualTo(expectedList);
    }

    @ParameterizedTest
    @CsvSource({
            "LOW, LOW", "low, LOW", "MEDIUM, MEDIUM", "HIGH, HIGH",
            "NOT ASSESSED, NOT_ASSESSED", "NOT_ASSESSED, NOT_ASSESSED", "not-assessed, NOT_ASSESSED"
    })
    void normalisesAvailabilityRisk(String external, AvailabilityRisk expected) throws Exception {
        Item item = mapper.toItem(singleRecord(
                "{\"results\":[{\"item__availability_risk\": \"" + external + "\"}]}"));

        assertThat(item.availabilityRisk()).isEqualTo(expected);
    }

    @Test
    void unknownEnumValuesBecomeNull() throws Exception {
        Item item = mapper.toItem(singleRecord("""
                {"results": [{"item__availability_risk": "EXTREME", "item__structure_role": "ASSEMBLY"}]}
                """));

        assertThat(item.availabilityRisk()).isNull();
        assertThat(item.structureRole()).isNull();
    }

    @Test
    void skipsMalformedRowsAndHandlesMissingResults() throws Exception {
        assertThat(json.readValue("{\"results\": [null, 5, {\"item_number\": \"A\"}]}",
                ExternalQueryResponse.class).records(ExternalItemRecord.class)).hasSize(1);
        assertThat(json.readValue("{}", ExternalQueryResponse.class).records(ExternalItemRecord.class)).isEmpty();
    }

    @Test
    void dtoCarriesInternalModelValues() throws Exception {
        Item item = mapper.toItem(singleRecord("{\"results\":[{\"item__id\": 1, \"item_number\": \"X\"}]}"));

        assertThat(mapper.toDto(item).id()).isEqualTo(1L);
        assertThat(mapper.toDto(item).itemNumber()).isEqualTo("X");
    }
}
