package com.example.plmdemo.erp.mapping;

import com.example.plmdemo.erp.config.IntegrationProperties;
import com.example.plmdemo.erp.domain.BomItem;
import com.example.plmdemo.erp.domain.EngineeringBom;
import com.example.plmdemo.erp.sap.SapBomRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the PLM engineering BOM to the target-side contract.
 * Mapping rules here are demo rules. Agree the real ones with your SAP team.
 */
@Component
public class BomMapper {

    /** PLM unit -> target unit. Demo values only. */
    private static final Map<String, String> UNIT_MAP = Map.of("EA", "PC", "PCS", "PC");

    private final String bomUsage;
    private final String defaultUnit;

    @Autowired
    public BomMapper(IntegrationProperties properties) {
        this(properties.mapping().bomUsage(), properties.mapping().defaultUnit());
    }

    public BomMapper(String bomUsage, String defaultUnit) {
        this.bomUsage = bomUsage;
        this.defaultUnit = defaultUnit;
    }

    public SapBomRequest toSapRequest(EngineeringBom bom, String idempotencyKey, String correlationId) {
        List<SapBomRequest.Item> items = new ArrayList<>();
        int position = 10;
        for (BomItem item : bom.items()) {
            items.add(new SapBomRequest.Item(
                    String.format("%04d", position),
                    item.componentNumber().trim().toUpperCase(Locale.ROOT),
                    item.quantity().stripTrailingZeros(),
                    mapUnit(item.unit())));
            position += 10;
        }
        return new SapBomRequest(idempotencyKey, correlationId,
                bom.materialNumber().trim().toUpperCase(Locale.ROOT),
                bom.plant(), bom.revision(), bom.changeNumber(), bomUsage, items);
    }

    private String mapUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return defaultUnit;
        }
        String upper = unit.trim().toUpperCase(Locale.ROOT);
        return UNIT_MAP.getOrDefault(upper, upper);
    }
}
