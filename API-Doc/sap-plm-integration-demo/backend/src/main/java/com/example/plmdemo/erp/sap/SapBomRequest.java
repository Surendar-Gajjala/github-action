package com.example.plmdemo.erp.sap;

import java.math.BigDecimal;
import java.util.List;

/**
 * Target-side contract used between BomMapper and SapBomGateway.
 *
 * ILLUSTRATIVE ONLY: this is the demo's own neutral shape, NOT an SAP payload schema.
 * A real adapter must translate it into whatever interface your SAP release supports
 * (for example an OData/REST API, IDoc, BAPI/RFC, or an SAP PLM integration add-on).
 */
public record SapBomRequest(
        String idempotencyKey,
        String correlationId,
        String headerMaterial,
        String plant,
        String revision,
        String changeNumber,
        String bomUsage,
        List<Item> items) {

    public SapBomRequest {
        items = List.copyOf(items);
    }

    public record Item(String itemNumber, String componentMaterial, BigDecimal quantity, String unit) {
    }
}
