package com.demo.itemintegration.itemdetail.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.external.dto.ExternalGraphResponse;
import com.demo.itemintegration.part.dto.PartDto;
import com.demo.itemintegration.part.model.LifecycleStatus;
import com.demo.itemintegration.part.model.SourcingType;
import com.demo.itemintegration.part.model.SupplyChainRisk;
import com.fasterxml.jackson.databind.ObjectMapper;

class ItemSourcesMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ItemSourcesMapper mapper = new ItemSourcesMapper();

    @Test
    void sourcesAreThePartNodesMappedLikeThePartsTab() throws Exception {
        ExternalGraphResponse graph = json.readValue("""
                {"nodes": [
                  {"id": 4332025200, "type": "item", "properties": {}},
                  {"id": 2, "type": "part", "alias": "leaf", "properties": {"part_number": "RK73H1ETTP1693F",
                     "manufacturer": "KOA SPEER ELECTRONICS", "description": null, "sourcing_type": "OTS",
                     "supply_chain_risk": "MEDIUM", "lifecycle.status": null, "country_of_origin": ""}},
                  {"id": 1, "type": "part", "alias": "leaf", "properties": {"part_number": "CRCW0402169KFKED",
                     "manufacturer": "VISHAY", "sourcing_type": "OTS", "supply_chain_risk": "NOT_ASSESSED"}},
                  {"id": 1, "type": "part", "alias": "leaf", "properties": {"part_number": "CRCW0402169KFKED"}}],
                 "edges": [{"id": 9, "type": "item_sources", "fromNodeId": 4332025200, "toNodeId": 2, "properties": {}}]}
                """, ExternalGraphResponse.class);

        List<PartDto> parts = mapper.toSources(graph);

        assertThat(parts).extracting(PartDto::partNumber).containsExactly("CRCW0402169KFKED", "RK73H1ETTP1693F");
        PartDto koa = parts.get(1);
        assertThat(koa.id()).isEqualTo(2L);
        assertThat(koa.manufacturer()).isEqualTo("KOA SPEER ELECTRONICS");
        assertThat(koa.sourcingType()).isEqualTo(SourcingType.OFF_THE_SHELF);
        assertThat(koa.supplyChainRisk()).isEqualTo(SupplyChainRisk.MEDIUM);
        assertThat(koa.lifecycleStatus()).isEqualTo(LifecycleStatus.UNKNOWN);
        assertThat(koa.countryOfOrigin()).isNull();
        assertThat(parts.get(0).supplyChainRisk()).isEqualTo(SupplyChainRisk.NOT_ASSESSED);
    }

    @Test
    void emptyGraphGivesNoSources() throws Exception {
        ExternalGraphResponse graph = json.readValue("{\"nodes\": [], \"edges\": []}", ExternalGraphResponse.class);

        assertThat(mapper.toSources(graph)).isEmpty();
    }
}
