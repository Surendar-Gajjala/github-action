package com.demo.itemintegration.part.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.demo.itemintegration.external.dto.ExternalPartRecord;
import com.demo.itemintegration.external.dto.ExternalQueryResponse;
import com.demo.itemintegration.part.model.LifecycleStatus;
import com.demo.itemintegration.part.model.Part;
import com.demo.itemintegration.part.model.SourcingType;
import com.demo.itemintegration.part.model.SupplyChainRisk;
import com.fasterxml.jackson.databind.ObjectMapper;

class PartMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final PartMapper mapper = new PartMapper();

    private Part map(String row) throws Exception {
        List<ExternalPartRecord> records = json.readValue("{\"results\":[" + row + "]}", ExternalQueryResponse.class)
                .records(ExternalPartRecord.class);
        assertThat(records).hasSize(1);
        return mapper.toPart(records.get(0));
    }

    @Test
    void mapsHostedServerRow() throws Exception {
        Part part = map("""
                {"part_number": "FC FBMJ3216HS480NT", "manufacturer": "TAIYO YUDEN,(USA),INC",
                 "description": null, "z2_properties_comparison": "incomparable",
                 "part__country_of_origin": "China", "part__sourcing_type": "OTS",
                 "part__supply_chain_risk": "NOT_ASSESSED", "part__lifecycle__status": null,
                 "part__id": 4332025100}
                """);

        assertThat(part.id()).isEqualTo(4_332_025_100L);
        assertThat(part.partNumber()).isEqualTo("FC FBMJ3216HS480NT");
        assertThat(part.manufacturer()).isEqualTo("TAIYO YUDEN,(USA),INC");
        assertThat(part.description()).isNull();
        assertThat(part.z2PropertiesComparison()).isEqualTo("incomparable");
        assertThat(part.countryOfOrigin()).isEqualTo("China");
        assertThat(part.sourcingType()).isEqualTo(SourcingType.OFF_THE_SHELF);
        assertThat(part.supplyChainRisk()).isEqualTo(SupplyChainRisk.NOT_ASSESSED);
        assertThat(part.lifecycleStatus()).isEqualTo(LifecycleStatus.UNKNOWN);
    }

    @Test
    void emptyAndMissingValuesDoNotFail() throws Exception {
        Part part = map("{\"part_number\": \"\", \"part__country_of_origin\": \"\", \"part__id\": \"x\"}");

        assertThat(part.partNumber()).isNull();
        assertThat(part.countryOfOrigin()).isNull();
        assertThat(part.id()).isNull();
        assertThat(part.sourcingType()).isEqualTo(SourcingType.UNKNOWN);
        assertThat(part.supplyChainRisk()).isEqualTo(SupplyChainRisk.NOT_ASSESSED);
        assertThat(part.lifecycleStatus()).isEqualTo(LifecycleStatus.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({
            "OTS, OFF_THE_SHELF", "Off-the-shelf, OFF_THE_SHELF", "custom, CUSTOM",
            "CONFLICT, CONFLICT", "UNKNOWN, UNKNOWN", "something-else, UNKNOWN"
    })
    void normalisesSourcingType(String external, SourcingType expected) throws Exception {
        assertThat(map("{\"part__sourcing_type\": \"" + external + "\"}").sourcingType()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"LOW, LOW", "medium, MEDIUM", "HIGH, HIGH", "NOT_ASSESSED, NOT_ASSESSED", "Not Assessed, NOT_ASSESSED"})
    void normalisesSupplyChainRisk(String external, SupplyChainRisk expected) throws Exception {
        assertThat(map("{\"part__supply_chain_risk\": \"" + external + "\"}").supplyChainRisk()).isEqualTo(expected);
    }

    @Test
    void unknownSupplyChainRiskBecomesNull() throws Exception {
        assertThat(map("{\"part__supply_chain_risk\": \"EXTREME\"}").supplyChainRisk()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "Active, ACTIVE", "NRND, NRND", "LastTimeBuy, LAST_TIME_BUY", "LAST_TIME_BUY, LAST_TIME_BUY",
            "LTB, LAST_TIME_BUY", "obsolete, OBSOLETE", "Unknown, UNKNOWN", "retired, UNKNOWN"
    })
    void normalisesLifecycleStatus(String external, LifecycleStatus expected) throws Exception {
        assertThat(map("{\"part__lifecycle__status\": \"" + external + "\"}").lifecycleStatus()).isEqualTo(expected);
    }
}
