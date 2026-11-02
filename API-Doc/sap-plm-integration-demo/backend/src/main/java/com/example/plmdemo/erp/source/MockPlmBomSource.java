package com.example.plmdemo.erp.source;

import com.example.plmdemo.erp.domain.BomItem;
import com.example.plmdemo.erp.domain.EngineeringBom;
import com.example.plmdemo.erp.domain.ReleaseState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MOCK PLM store with seeded demo data, kept in memory (reset on restart).
 * CAD imports and releases write here. Replace with an adapter for your real PLM
 * (Teamcenter, Windchill, ...) using that product's supported API.
 */
@Component
public class MockPlmBomSource implements PlmBomStore {

    private static final String SOURCE = "MOCK-PLM";

    private final Map<String, EngineeringBom> boms = new ConcurrentHashMap<>();

    public MockPlmBomSource() {
        save(new EngineeringBom(SOURCE, "MOTOR-100", "B", "1000",
                ReleaseState.RELEASED, "ECN-4711", List.of(
                new BomItem("SHAFT-101", new BigDecimal("1"), "EA"),
                new BomItem("BEARING-201", new BigDecimal("2"), "EA"))));

        // Not yet released in PLM: must be rejected by validation.
        save(new EngineeringBom(SOURCE, "MOTOR-100", "C", "1000",
                ReleaseState.IN_WORK, "ECN-4712", List.of(
                new BomItem("SHAFT-102", new BigDecimal("1"), "EA"),
                new BomItem("BEARING-201", new BigDecimal("2"), "EA"))));

        // Bad data in PLM: zero quantity must be rejected by validation.
        save(new EngineeringBom(SOURCE, "MOTOR-900", "A", "1000",
                ReleaseState.RELEASED, "ECN-9000", List.of(
                new BomItem("SHAFT-101", BigDecimal.ZERO, "EA"),
                new BomItem("BEARING-201", new BigDecimal("-2"), "EA"))));
    }

    @Override
    public Optional<EngineeringBom> findBom(String materialNumber, String revision, String plant) {
        return Optional.ofNullable(boms.get(key(materialNumber, revision, plant)));
    }

    @Override
    public EngineeringBom save(EngineeringBom bom) {
        boms.put(key(bom.materialNumber(), bom.revision(), bom.plant()), bom);
        return bom;
    }

    private static String key(String material, String revision, String plant) {
        return material + "|" + revision + "|" + plant;
    }
}
