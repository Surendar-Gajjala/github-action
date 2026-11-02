package com.demo.itemintegration.hierarchy.mapper;

import static com.demo.itemintegration.common.mapping.CodeLabels.label;
import static com.demo.itemintegration.common.mapping.ExternalValues.text;
import static com.demo.itemintegration.common.mapping.ExternalValues.toBoolean;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.demo.itemintegration.external.dto.ExternalGraphResponse.ExternalGraphEdge;
import com.demo.itemintegration.external.dto.ExternalGraphResponse.ExternalGraphNode;
import com.demo.itemintegration.external.dto.ExternalHierarchyItemRecord;
import com.demo.itemintegration.external.dto.ExternalHierarchyPartRecord;
import com.demo.itemintegration.hierarchy.dto.HierarchyItemDto;
import com.demo.itemintegration.hierarchy.dto.HierarchyPartDto;
import com.demo.itemintegration.hierarchy.model.NodeKind;
import com.demo.itemintegration.item.mapper.ItemMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps graph-match nodes and edges to Item Hierarchy DTOs. External property names
 * stay in the external record DTOs; generic value rules come from
 * {@link com.demo.itemintegration.common.mapping.ExternalValues}.
 */
@Component
public class HierarchyMapper {

    static final String ITEM_BOM = "item_bom";
    static final String QTY_PROPERTY = "qty_per_bom.qty";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** ITEM or PART for the node's entity type; {@code null} for any other entity. */
    public NodeKind kindOf(ExternalGraphNode node) {
        if (node.type() == null) {
            return null;
        }
        return switch (node.type().toLowerCase(Locale.ROOT)) {
            case "item" -> NodeKind.ITEM;
            case "part" -> NodeKind.PART;
            default -> null;
        };
    }

    public HierarchyItemDto toItem(ExternalGraphNode node) {
        ExternalHierarchyItemRecord r = read(node.properties(), ExternalHierarchyItemRecord.class);
        return new HierarchyItemDto(
                text(r.itemNumber()),
                text(r.description()),
                text(r.revision()),
                text(r.itemType()),
                label(r.itemStatus()),
                text(r.makeBuy()),
                text(r.designGroup()),
                toBoolean(r.restricted()),
                label(r.exemptionStatus()),
                text(r.project()),
                text(r.sourceNotes()),
                label(r.collection()),
                label(r.collectionLock()),
                label(r.euRohs()),
                label(r.euRohsLock()),
                label(r.ecExemptions()),
                label(r.pwbLeadHalogen()),
                label(r.pwbLeadHalogenLock()),
                label(r.plasticLeadHalogen()),
                label(r.plasticLeadHalogenLock()),
                label(r.pnr()),
                label(r.pnrLock()),
                text(r.businessUnit()),
                toBoolean(r.isProduct()),
                ItemMapper.toStructureRole(r.structureRole()));
    }

    public HierarchyPartDto toPart(ExternalGraphNode node) {
        ExternalHierarchyPartRecord r = read(node.properties(), ExternalHierarchyPartRecord.class);
        return new HierarchyPartDto(text(r.partNumber()), text(r.manufacturer()));
    }

    /** Quantity on an item_bom edge; {@code null} for item_sources edges or when not a number. */
    public BigDecimal qtyOf(ExternalGraphEdge edge) {
        if (!ITEM_BOM.equalsIgnoreCase(edge.type()) || edge.properties() == null) {
            return null;
        }
        String value = text(edge.properties().get(QTY_PROPERTY));
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static <T> T read(JsonNode properties, Class<T> type) {
        return JSON.convertValue(properties == null ? JSON.createObjectNode() : properties, type);
    }
}
