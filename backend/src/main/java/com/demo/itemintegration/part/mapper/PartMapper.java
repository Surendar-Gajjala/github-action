package com.demo.itemintegration.part.mapper;

import static com.demo.itemintegration.common.mapping.ExternalValues.enumKey;
import static com.demo.itemintegration.common.mapping.ExternalValues.text;
import static com.demo.itemintegration.common.mapping.ExternalValues.toLong;

import org.springframework.stereotype.Component;

import com.demo.itemintegration.external.dto.ExternalPartRecord;
import com.demo.itemintegration.part.dto.PartDto;
import com.demo.itemintegration.part.model.LifecycleStatus;
import com.demo.itemintegration.part.model.Part;
import com.demo.itemintegration.part.model.SourcingType;
import com.demo.itemintegration.part.model.SupplyChainRisk;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The boundary between the hosted server's Part representation and the internal Part
 * model. Generic value rules come from
 * {@link com.demo.itemintegration.common.mapping.ExternalValues}; Part-specific rules:
 * <ul>
 *   <li>sourcing type: {@code OTS} / off-the-shelf, custom, conflict; blank or unrecognised
 *       becomes Unknown</li>
 *   <li>supply chain risk: blank becomes Not Assessed, unrecognised becomes {@code null}</li>
 *   <li>lifecycle status: active, NRND, last time buy ({@code LTB}), obsolete; blank or
 *       unrecognised becomes Unknown</li>
 * </ul>
 */
@Component
public class PartMapper {

    public Part toPart(ExternalPartRecord external) {
        return new Part(
                toLong(external.id()),
                text(external.partNumber()),
                text(external.manufacturer()),
                text(external.description()),
                text(external.z2PropertiesComparison()),
                text(external.countryOfOrigin()),
                toSourcingType(external.sourcingType()),
                toSupplyChainRisk(external.supplyChainRisk()),
                toLifecycleStatus(external.lifecycleStatus()));
    }

    public PartDto toDto(Part part) {
        return new PartDto(
                part.id(),
                part.partNumber(),
                part.manufacturer(),
                part.description(),
                part.z2PropertiesComparison(),
                part.countryOfOrigin(),
                part.sourcingType(),
                part.supplyChainRisk(),
                part.lifecycleStatus());
    }

    public static SourcingType toSourcingType(JsonNode node) {
        String key = enumKey(node);
        if (key == null) {
            return SourcingType.UNKNOWN;
        }
        return switch (key) {
            case "OTS", "OFF THE SHELF", "OFFTHESHELF", "COTS" -> SourcingType.OFF_THE_SHELF;
            case "CUSTOM" -> SourcingType.CUSTOM;
            case "CONFLICT" -> SourcingType.CONFLICT;
            default -> SourcingType.UNKNOWN;
        };
    }

    public static SupplyChainRisk toSupplyChainRisk(JsonNode node) {
        String key = enumKey(node);
        if (key == null) {
            return SupplyChainRisk.NOT_ASSESSED;
        }
        return switch (key) {
            case "LOW" -> SupplyChainRisk.LOW;
            case "MEDIUM" -> SupplyChainRisk.MEDIUM;
            case "HIGH" -> SupplyChainRisk.HIGH;
            case "NOT ASSESSED" -> SupplyChainRisk.NOT_ASSESSED;
            default -> null;
        };
    }

    public static LifecycleStatus toLifecycleStatus(JsonNode node) {
        String key = enumKey(node);
        if (key == null) {
            return LifecycleStatus.UNKNOWN;
        }
        return switch (key) {
            case "ACTIVE" -> LifecycleStatus.ACTIVE;
            case "NRND", "NOT RECOMMENDED FOR NEW DESIGN", "NOT RECOMMENDED FOR NEW DESIGNS" -> LifecycleStatus.NRND;
            case "LASTTIMEBUY", "LAST TIME BUY", "LTB" -> LifecycleStatus.LAST_TIME_BUY;
            case "OBSOLETE" -> LifecycleStatus.OBSOLETE;
            default -> LifecycleStatus.UNKNOWN;
        };
    }
}
