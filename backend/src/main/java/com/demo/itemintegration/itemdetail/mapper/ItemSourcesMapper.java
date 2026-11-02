package com.demo.itemintegration.itemdetail.mapper;

import static com.demo.itemintegration.common.mapping.ExternalValues.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.demo.itemintegration.external.dto.ExternalGraphResponse;
import com.demo.itemintegration.external.dto.ExternalGraphResponse.ExternalGraphNode;
import com.demo.itemintegration.external.dto.ExternalSourcePartRecord;
import com.demo.itemintegration.part.dto.PartDto;
import com.demo.itemintegration.part.mapper.PartMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps the item_sources traversal of one item to its sourced parts (Sources tab). Part
 * values follow the same rules as the Parts tab ({@link PartMapper}).
 */
@Component
public class ItemSourcesMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Comparator<PartDto> PART_ORDER = Comparator
            .comparing(PartDto::partNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PartDto::manufacturer, Comparator.nullsLast(Comparator.naturalOrder()));

    /** Part nodes of the traversal, one per part, ordered by part number then manufacturer. */
    public List<PartDto> toSources(ExternalGraphResponse graph) {
        Map<Long, PartDto> parts = new LinkedHashMap<>();
        for (ExternalGraphNode node : graph.nodes()) {
            if (node != null && node.id() != null && "part".equalsIgnoreCase(node.type())) {
                parts.putIfAbsent(node.id(), toPart(node));
            }
        }
        List<PartDto> sorted = new ArrayList<>(parts.values());
        sorted.sort(PART_ORDER);
        return sorted;
    }

    private static PartDto toPart(ExternalGraphNode node) {
        ExternalSourcePartRecord r = JSON.convertValue(
                node.properties() == null ? JSON.createObjectNode() : node.properties(), ExternalSourcePartRecord.class);
        return new PartDto(
                node.id(),
                text(r.partNumber()),
                text(r.manufacturer()),
                text(r.description()),
                text(r.z2PropertiesComparison()),
                text(r.countryOfOrigin()),
                PartMapper.toSourcingType(r.sourcingType()),
                PartMapper.toSupplyChainRisk(r.supplyChainRisk()),
                PartMapper.toLifecycleStatus(r.lifecycleStatus()));
    }
}
