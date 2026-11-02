package com.example.plmdemo.erp.mapping;

import com.example.plmdemo.erp.sap.SapBomRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.example.plmdemo.erp.TestBoms.releasedMotor100;
import static org.assertj.core.api.Assertions.assertThat;

class BomMapperTest {

    private final BomMapper mapper = new BomMapper("1", "PC");

    @Test
    void mapsHeaderItemsAndUnits() {
        SapBomRequest request = mapper.toSapRequest(releasedMotor100(), "KEY-1", "CORR-1");

        assertThat(request.headerMaterial()).isEqualTo("MOTOR-100");
        assertThat(request.plant()).isEqualTo("1000");
        assertThat(request.revision()).isEqualTo("B");
        assertThat(request.changeNumber()).isEqualTo("ECN-4711");
        assertThat(request.bomUsage()).isEqualTo("1");
        assertThat(request.idempotencyKey()).isEqualTo("KEY-1");
        assertThat(request.correlationId()).isEqualTo("CORR-1");

        assertThat(request.items()).containsExactly(
                new SapBomRequest.Item("0010", "SHAFT-101", new BigDecimal("1"), "PC"),
                new SapBomRequest.Item("0020", "BEARING-201", new BigDecimal("2"), "PC"));
    }
}
