package com.example.plmdemo.erp;

import com.example.plmdemo.erp.domain.BomItem;
import com.example.plmdemo.erp.domain.EngineeringBom;
import com.example.plmdemo.erp.domain.ReleaseState;

import java.math.BigDecimal;
import java.util.List;

/** Test fixtures based on the MOTOR-100 demo scenario. */
public final class TestBoms {

    private TestBoms() {
    }

    public static EngineeringBom motor100(ReleaseState state, BomItem... items) {
        return new EngineeringBom("TEST-PLM", "MOTOR-100", "B", "1000", state, "ECN-4711", List.of(items));
    }

    public static EngineeringBom releasedMotor100() {
        return motor100(ReleaseState.RELEASED, item("SHAFT-101", "1"), item("BEARING-201", "2"));
    }

    public static BomItem item(String component, String quantity) {
        return new BomItem(component, quantity == null ? null : new BigDecimal(quantity), "EA");
    }
}
