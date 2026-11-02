package com.example.plmdemo.erp.domain;

import java.math.BigDecimal;

/** One component line of an engineering BOM, as held in the source PLM system. */
public record BomItem(String componentNumber, BigDecimal quantity, String unit) {
}
