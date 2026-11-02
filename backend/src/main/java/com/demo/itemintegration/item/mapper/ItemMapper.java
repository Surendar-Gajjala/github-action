package com.demo.itemintegration.item.mapper;

import static com.demo.itemintegration.common.mapping.ExternalValues.enumKey;
import static com.demo.itemintegration.common.mapping.ExternalValues.list;
import static com.demo.itemintegration.common.mapping.ExternalValues.scalarText;
import static com.demo.itemintegration.common.mapping.ExternalValues.text;
import static com.demo.itemintegration.common.mapping.ExternalValues.toBoolean;
import static com.demo.itemintegration.common.mapping.ExternalValues.toInteger;
import static com.demo.itemintegration.common.mapping.ExternalValues.toLong;

import org.springframework.stereotype.Component;

import com.demo.itemintegration.external.dto.ExternalItemRecord;
import com.demo.itemintegration.item.dto.ItemDto;
import com.demo.itemintegration.item.model.AvailabilityRisk;
import com.demo.itemintegration.item.model.Item;
import com.demo.itemintegration.item.model.StructureRole;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The boundary between the hosted server's Item representation and the internal Item
 * model. Generic value rules (blank text, numbers, ODM lists) come from
 * {@link com.demo.itemintegration.common.mapping.ExternalValues}; Item-specific rules:
 * <ul>
 *   <li>structure role: case-insensitive ITEM / BOM, otherwise {@code null}</li>
 *   <li>availability risk: blank becomes NOT ASSESSED, unrecognised becomes {@code null}</li>
 * </ul>
 */
@Component
public class ItemMapper {

    public Item toItem(ExternalItemRecord external) {
        return new Item(
                toLong(external.id()),
                text(external.itemNumber()),
                text(external.description()),
                text(external.revision()),
                text(external.businessUnit()),
                toBoolean(external.isProduct()),
                toStructureRole(external.structureRole()),
                list(external.odmName(), node -> scalarText(node)),
                list(external.odmActive(), node -> toBoolean(node)),
                toAvailabilityRisk(external.availabilityRisk()),
                text(external.usedInProducts()),
                toInteger(external.productFamiliesImpacted()),
                text(external.itemStatusName()));
    }

    public ItemDto toDto(Item item) {
        return new ItemDto(
                item.id(),
                item.itemNumber(),
                item.description(),
                item.revision(),
                item.businessUnit(),
                item.isProduct(),
                item.structureRole(),
                item.odmName(),
                item.odmActive(),
                item.availabilityRisk(),
                item.usedInProducts(),
                item.productFamiliesImpacted(),
                item.itemStatusName());
    }

    /** Case-insensitive ITEM / BOM; anything else becomes {@code null}. Shared with the Item Hierarchy. */
    public static StructureRole toStructureRole(JsonNode node) {
        String key = enumKey(node);
        if (key == null) {
            return null;
        }
        return switch (key) {
            case "ITEM" -> StructureRole.ITEM;
            case "BOM" -> StructureRole.BOM;
            default -> null;
        };
    }

    static AvailabilityRisk toAvailabilityRisk(JsonNode node) {
        String key = enumKey(node);
        if (key == null) {
            return AvailabilityRisk.NOT_ASSESSED;
        }
        return switch (key) {
            case "LOW" -> AvailabilityRisk.LOW;
            case "MEDIUM" -> AvailabilityRisk.MEDIUM;
            case "HIGH" -> AvailabilityRisk.HIGH;
            case "NOT ASSESSED" -> AvailabilityRisk.NOT_ASSESSED;
            default -> null;
        };
    }
}
