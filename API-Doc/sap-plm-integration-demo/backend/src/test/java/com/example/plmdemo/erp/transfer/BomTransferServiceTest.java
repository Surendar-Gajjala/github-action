package com.example.plmdemo.erp.transfer;

import com.example.plmdemo.erp.domain.EngineeringBom;
import com.example.plmdemo.erp.domain.ReleaseState;
import com.example.plmdemo.erp.domain.TransferStatus;
import com.example.plmdemo.erp.mapping.BomMapper;
import com.example.plmdemo.erp.sap.MockSapBomGateway;
import com.example.plmdemo.erp.sap.SapBomGateway;
import com.example.plmdemo.erp.sap.SapBomRequest;
import com.example.plmdemo.erp.sap.SapBomResponse;
import com.example.plmdemo.erp.source.PlmBomSource;
import com.example.plmdemo.erp.validation.BomValidationException;
import com.example.plmdemo.erp.validation.BomValidator;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.plmdemo.erp.TestBoms.item;
import static com.example.plmdemo.erp.TestBoms.motor100;
import static com.example.plmdemo.erp.TestBoms.releasedMotor100;
import static com.example.plmdemo.erp.sap.MockSapBomGateway.FailureMode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BomTransferServiceTest {

    private static final int MAX_ATTEMPTS = 3;

    private final InMemoryTransferRepository repository = new InMemoryTransferRepository();

    private BomTransferService service(EngineeringBom bom, SapBomGateway gateway) {
        PlmBomSource source = (material, revision, plant) -> Optional.ofNullable(bom);
        return new BomTransferService(source, new BomValidator(), new BomMapper("1", "PC"),
                gateway, repository, MAX_ATTEMPTS, 0);
    }

    private static TransferCommand motor100Command(String idempotencyKey) {
        return new TransferCommand("MOTOR-100", "B", "1000", idempotencyKey, "corr-test");
    }

    /** Wraps a gateway and counts calls. */
    private static final class CountingGateway implements SapBomGateway {
        private final SapBomGateway delegate;
        private final AtomicInteger calls = new AtomicInteger();

        CountingGateway(SapBomGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public SapBomResponse submitBom(SapBomRequest request) {
            calls.incrementAndGet();
            return delegate.submitBom(request);
        }

        @Override
        public String mode() {
            return delegate.mode();
        }
    }

    @Test
    void releasedBomIsTransferred() {
        var gateway = new CountingGateway(new MockSapBomGateway(FailureMode.NONE));

        TransferResult result = service(releasedMotor100(), gateway).transfer(motor100Command("K1"));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.record().status()).isEqualTo(TransferStatus.SUCCEEDED);
        assertThat(result.record().targetReference()).startsWith("MOCK-BOM-");
        assertThat(result.record().attempts()).isEqualTo(1);
        assertThat(result.record().correlationId()).isEqualTo("corr-test");
        assertThat(gateway.calls).hasValue(1);
    }

    // Release validation
    @Test
    void unreleasedBomIsNotSentToSap() {
        var gateway = new CountingGateway(new MockSapBomGateway(FailureMode.NONE));
        var bom = motor100(ReleaseState.IN_WORK, item("SHAFT-101", "1"));
        var service = service(bom, gateway);

        assertThatThrownBy(() -> service.transfer(motor100Command("K-REL")))
                .isInstanceOf(BomValidationException.class);
        assertThat(gateway.calls).hasValue(0);
    }

    // Invalid quantities
    @Test
    void invalidQuantityIsRecordedAsValidationFailed() {
        var gateway = new CountingGateway(new MockSapBomGateway(FailureMode.NONE));
        var bom = motor100(ReleaseState.RELEASED, item("SHAFT-101", "0"), item("BEARING-201", "-2"));
        var service = service(bom, gateway);

        assertThatThrownBy(() -> service.transfer(motor100Command("K-QTY")))
                .isInstanceOfSatisfying(BomValidationException.class,
                        e -> assertThat(e.getViolations()).hasSize(2));
        assertThat(gateway.calls).hasValue(0);
    }

    @Test
    void missingBomIsReported() {
        var service = service(null, new MockSapBomGateway(FailureMode.NONE));

        assertThatThrownBy(() -> service.transfer(motor100Command("K-404")))
                .isInstanceOf(BomNotFoundException.class);
    }

    // SAP failure
    @Test
    void transientSapFailureIsRetriedThenSucceeds() {
        var gateway = new CountingGateway(new MockSapBomGateway(FailureMode.TRANSIENT_ONCE));

        TransferResult result = service(releasedMotor100(), gateway).transfer(motor100Command("K-RETRY"));

        assertThat(result.record().status()).isEqualTo(TransferStatus.SUCCEEDED);
        assertThat(result.record().attempts()).isEqualTo(2);
        assertThat(gateway.calls).hasValue(2);
    }

    @Test
    void persistentTransientFailureStopsAfterMaxAttempts() {
        var gateway = new CountingGateway(new MockSapBomGateway(FailureMode.ALWAYS_TRANSIENT));

        TransferResult result = service(releasedMotor100(), gateway).transfer(motor100Command("K-DOWN"));

        assertThat(result.record().status()).isEqualTo(TransferStatus.FAILED_TRANSIENT);
        assertThat(result.record().attempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(result.record().messages()).hasSize(MAX_ATTEMPTS);
        assertThat(gateway.calls).hasValue(MAX_ATTEMPTS);
    }

    @Test
    void permanentSapRejectionIsNotRetried() {
        var gateway = new CountingGateway(new MockSapBomGateway(FailureMode.PERMANENT));

        TransferResult result = service(releasedMotor100(), gateway).transfer(motor100Command("K-REJ"));

        assertThat(result.record().status()).isEqualTo(TransferStatus.FAILED_PERMANENT);
        assertThat(result.record().targetReference()).isNull();
        assertThat(gateway.calls).hasValue(1);
    }

    // Duplicate submissions
    @Test
    void duplicateSubmissionReturnsFirstTransferWithoutCallingSapAgain() {
        var gateway = new CountingGateway(new MockSapBomGateway(FailureMode.NONE));
        var service = service(releasedMotor100(), gateway);

        TransferResult first = service.transfer(motor100Command("K-DUP"));
        TransferResult second = service.transfer(motor100Command("K-DUP"));

        assertThat(second.duplicate()).isTrue();
        assertThat(second.record().transferId()).isEqualTo(first.record().transferId());
        assertThat(second.record().targetReference()).isEqualTo(first.record().targetReference());
        assertThat(gateway.calls).hasValue(1);
    }

    @Test
    void withoutHeaderKeyTheBusinessKeyPreventsDuplicates() {
        var gateway = new CountingGateway(new MockSapBomGateway(FailureMode.NONE));
        var service = service(releasedMotor100(), gateway);

        service.transfer(motor100Command(null));
        TransferResult second = service.transfer(motor100Command(null));

        assertThat(second.duplicate()).isTrue();
        assertThat(second.record().idempotencyKey()).isEqualTo("BOM:MOTOR-100:B:1000");
        assertThat(gateway.calls).hasValue(1);
    }

    @Test
    void failedTransferCanBeResubmittedWithSameKey() {
        var gateway = new CountingGateway(new MockSapBomGateway(FailureMode.ALWAYS_TRANSIENT));
        var service = service(releasedMotor100(), gateway);

        TransferResult first = service.transfer(motor100Command("K-AGAIN"));
        TransferResult second = service.transfer(motor100Command("K-AGAIN"));

        assertThat(first.record().status()).isEqualTo(TransferStatus.FAILED_TRANSIENT);
        assertThat(second.duplicate()).isFalse();
        assertThat(gateway.calls).hasValue(2 * MAX_ATTEMPTS);
    }
}
