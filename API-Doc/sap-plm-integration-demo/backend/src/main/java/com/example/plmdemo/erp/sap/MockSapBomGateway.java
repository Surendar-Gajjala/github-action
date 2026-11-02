package com.example.plmdemo.erp.sap;

import com.example.plmdemo.erp.config.IntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MOCK SAP gateway. Keeps "created" BOMs in memory and returns MOCK-BOM-nnnnnn references.
 * Nothing is sent to any SAP system.
 *
 * Failure simulation via integration.sap.mock.failure-mode:
 *   NONE | TRANSIENT_ONCE | ALWAYS_TRANSIENT | PERMANENT
 */
@Component
@ConditionalOnProperty(name = "integration.sap.mode", havingValue = "mock", matchIfMissing = true)
public class MockSapBomGateway implements SapBomGateway {

    private static final Logger log = LoggerFactory.getLogger(MockSapBomGateway.class);

    public enum FailureMode { NONE, TRANSIENT_ONCE, ALWAYS_TRANSIENT, PERMANENT }

    private final FailureMode failureMode;
    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<String, String> referenceByIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> callsByIdempotencyKey = new ConcurrentHashMap<>();

    @Autowired
    public MockSapBomGateway(IntegrationProperties properties) {
        this(parse(properties.sap().mock().failureMode()));
    }

    public MockSapBomGateway(FailureMode failureMode) {
        this.failureMode = failureMode;
    }

    @Override
    public SapBomResponse submitBom(SapBomRequest request) {
        int call = callsByIdempotencyKey
                .computeIfAbsent(request.idempotencyKey(), k -> new AtomicInteger())
                .incrementAndGet();
        log.info("[MOCK SAP] submitBom material={} plant={} items={} call={}",
                request.headerMaterial(), request.plant(), request.items().size(), call);

        switch (failureMode) {
            case ALWAYS_TRANSIENT -> throw new SapTransientException("[MOCK] SAP system temporarily unavailable");
            case TRANSIENT_ONCE -> {
                if (call == 1) {
                    throw new SapTransientException("[MOCK] SAP timeout on first call");
                }
            }
            case PERMANENT -> throw new SapPermanentException(
                    "[MOCK] Plant " + request.plant() + " not valid for material " + request.headerMaterial());
            case NONE -> { }
        }

        // The mock is idempotent itself: the same key always returns the same reference.
        String reference = referenceByIdempotencyKey.computeIfAbsent(request.idempotencyKey(),
                k -> String.format("MOCK-BOM-%06d", sequence.incrementAndGet()));
        return new SapBomResponse(reference, "[MOCK] BOM stored in memory, no SAP system was called");
    }

    @Override
    public String mode() {
        return "MOCK";
    }

    private static FailureMode parse(String value) {
        return value == null || value.isBlank()
                ? FailureMode.NONE
                : FailureMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
