package com.example.ectr.dms.service;

import com.example.ectr.dms.config.ExecutorConfig;
import com.example.ectr.dms.config.IntegrationProperties;
import com.example.ectr.dms.domain.DocumentMetadata;
import com.example.ectr.dms.domain.TransactionState;
import com.example.ectr.dms.sap.DocumentBapiMapper;
import com.example.ectr.dms.sap.SapDocumentGateway;
import com.example.ectr.dms.sap.mock.MockRfcClient;
import com.example.ectr.dms.sap.mock.MockSapProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentServiceTest {

    private ThreadPoolTaskExecutor executor;

    private DocumentService service(Duration latency, Duration timeout, boolean sapDown) {
        IntegrationProperties props = new IntegrationProperties(
                new IntegrationProperties.Sap(timeout, 2, 10),
                new IntegrationProperties.Storage(Path.of("target/unused"), List.of("PDF")));
        executor = new ExecutorConfig().sapExecutor(props);
        MockRfcClient mock = new MockRfcClient(new MockSapProperties(latency, List.of("DRW"), sapDown));
        return new DocumentService(new SapDocumentGateway(mock, new DocumentBapiMapper()), new TransactionMonitor(),
                executor, props);
    }

    @AfterEach
    void shutdown() {
        executor.shutdown();
    }

    private static DocumentMetadata meta(String type) {
        return new DocumentMetadata(type, null, null, null, "Test", null, List.of(), List.of());
    }

    @Test
    void successEndsCommittedWithKeyAndHistory() {
        DocumentService.CreateOutcome outcome = service(Duration.ZERO, Duration.ofSeconds(5), false)
                .createDocument(meta("DRW"), null);

        TransactionRecord tx = outcome.transaction();
        assertThat(outcome.replayed()).isFalse();
        assertThat(tx.state()).isEqualTo(TransactionState.COMMITTED);
        assertThat(tx.documentKey()).isNotNull();
        assertThat(tx.history()).extracting(TransactionRecord.StateChange::state).containsExactly(
                TransactionState.RECEIVED, TransactionState.SAP_CALL_STARTED,
                TransactionState.SAP_DOCUMENT_CREATED, TransactionState.COMMITTED);
    }

    @Test
    void sapRejectionBecomes422KindWithMessages() {
        DocumentService service = service(Duration.ZERO, Duration.ofSeconds(5), false);
        assertThatThrownBy(() -> service.createDocument(meta("XXX"), null))
                .isInstanceOfSatisfying(IntegrationException.class, e -> {
                    assertThat(e.kind()).isEqualTo(IntegrationException.Kind.SAP_REJECTED);
                    assertThat(e.sapMessages()).isNotEmpty();
                    assertThat(service.transaction(e.transactionId()).orElseThrow().state())
                            .isEqualTo(TransactionState.REJECTED_BY_SAP);
                });
    }

    @Test
    void unreachableSapBecomesUnavailable() {
        DocumentService service = service(Duration.ZERO, Duration.ofSeconds(5), true);
        assertThatThrownBy(() -> service.createDocument(meta("DRW"), null))
                .isInstanceOfSatisfying(IntegrationException.class,
                        e -> assertThat(e.kind()).isEqualTo(IntegrationException.Kind.SAP_UNAVAILABLE));
    }

    @Test
    void timeoutReportsUnknownOutcomeAndLaterRecordsRealResult() throws Exception {
        DocumentService service = service(Duration.ofMillis(600), Duration.ofMillis(100), false);

        IntegrationException e = org.junit.jupiter.api.Assertions.assertThrows(IntegrationException.class,
                () -> service.createDocument(meta("DRW"), null));
        assertThat(e.kind()).isEqualTo(IntegrationException.Kind.SAP_TIMEOUT);
        assertThat(service.transaction(e.transactionId()).orElseThrow().state())
                .isEqualTo(TransactionState.TIMEOUT_OUTCOME_UNKNOWN);

        // The SAP call keeps running and its real outcome is recorded afterwards.
        long deadline = System.currentTimeMillis() + 5000;
        TransactionRecord tx;
        do {
            Thread.sleep(50);
            tx = service.transaction(e.transactionId()).orElseThrow();
        } while (tx.state() != TransactionState.COMMITTED && System.currentTimeMillis() < deadline);
        assertThat(tx.state()).isEqualTo(TransactionState.COMMITTED);
        assertThat(tx.documentKey()).isNotNull();
    }

    @Test
    void sameIdempotencyKeyDoesNotCreateSecondDocument() {
        DocumentService service = service(Duration.ZERO, Duration.ofSeconds(5), false);
        DocumentService.CreateOutcome first = service.createDocument(meta("DRW"), "cad-job-42");
        DocumentService.CreateOutcome second = service.createDocument(meta("DRW"), "cad-job-42");

        assertThat(second.replayed()).isTrue();
        assertThat(second.transaction().id()).isEqualTo(first.transaction().id());
        assertThat(second.transaction().documentKey()).isEqualTo(first.transaction().documentKey());
    }
}
