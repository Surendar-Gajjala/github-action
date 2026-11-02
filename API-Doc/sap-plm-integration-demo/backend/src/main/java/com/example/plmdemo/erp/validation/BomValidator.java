package com.example.plmdemo.erp.validation;

import com.example.plmdemo.erp.domain.BomItem;
import com.example.plmdemo.erp.domain.EngineeringBom;
import com.example.plmdemo.erp.domain.ReleaseState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Business validation before anything is sent to SAP.
 * Pure Java, no SAP or transport dependencies.
 */
@Component
public class BomValidator {

    static final int MAX_QUANTITY_SCALE = 3;

    public void validate(EngineeringBom bom) {
        List<String> violations = new ArrayList<>();

        if (bom.releaseState() != ReleaseState.RELEASED) {
            violations.add("BOM " + bom.materialNumber() + " rev " + bom.revision()
                    + " is " + bom.releaseState() + " in PLM; only RELEASED BOMs can be transferred");
        }
        if (isBlank(bom.plant())) {
            violations.add("Plant is missing");
        }
        if (isBlank(bom.revision())) {
            violations.add("Revision is missing");
        }
        if (bom.items().isEmpty()) {
            violations.add("BOM has no items");
        }

        Set<String> seen = new HashSet<>();
        for (BomItem item : bom.items()) {
            String component = item.componentNumber();
            if (isBlank(component)) {
                violations.add("Item without component number");
                continue;
            }
            if (component.equals(bom.materialNumber())) {
                violations.add("Component " + component + " references its own header material");
            }
            if (!seen.add(component)) {
                violations.add("Component " + component + " appears more than once");
            }
            BigDecimal qty = item.quantity();
            if (qty == null || qty.signum() <= 0) {
                violations.add("Component " + component + " has invalid quantity " + qty + " (must be > 0)");
            } else if (qty.stripTrailingZeros().scale() > MAX_QUANTITY_SCALE) {
                violations.add("Component " + component + " quantity " + qty
                        + " has more than " + MAX_QUANTITY_SCALE + " decimals");
            }
        }

        if (!violations.isEmpty()) {
            throw new BomValidationException(violations);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
