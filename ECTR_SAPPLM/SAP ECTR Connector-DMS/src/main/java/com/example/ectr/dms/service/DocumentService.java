// VERIFIED: plain Java/Spring orchestration (unit- and integration-tested with the mock SAP backend).
package com.example.ectr.dms.service;

import com.example.ectr.dms.config.IntegrationProperties;
import com.example.ectr.dms.domain.DocumentDetail;
import com.example.ectr.dms.domain.DocumentKey;
import com.example.ectr.dms.domain.DocumentMetadata;
import com.example.ectr.dms.domain.TransactionState;
import com.example.ectr.dms.sap.BapiReturn;
import com.example.ectr.dms.sap.SapBusinessException;
import com.example.ectr.dms.sap.SapCommunicationException;
import com.example.ectr.dms.sap.SapDocumentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Creates and reads documents with timeouts and transaction tracking.
 * <p>
 * <b>Timeouts:</b> a running RFC call cannot be safely aborted from Java. When the timeout expires, the
 * caller gets HTTP 504 with the transaction id, and the SAP call keeps running in the background. Its
 * final result (COMMITTED, REJECTED_BY_SAP, ...) is still written to the transaction, so the caller can
 * poll {@code GET /api/v1/transactions/{id}} instead of blindly retrying and creating a duplicate.
 */
@Service
public class DocumentService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentService.class);

    private final SapDocumentGateway gateway;
    private final TransactionMonitor monitor;
    private final ThreadPoolTaskExecutor sapExecutor;
    private final Duration timeout;

    public DocumentService(SapDocumentGateway gateway, TransactionMonitor monitor,
                           ThreadPoolTaskExecutor sapExecutor, IntegrationProperties properties) {
        this.gateway = gateway;
        this.monitor = monitor;
        this.sapExecutor = sapExecutor;
        this.timeout = properties.sap().callTimeout();
    }

    /**
     * Result of {@link #createDocument}.
     *
     * @param transaction final (or, for a replay, current) state of the transaction
     * @param replayed    {@code true} if the idempotency key was seen before and no new call was made
     */
    public record CreateOutcome(TransactionRecord transaction, boolean replayed) {
    }

    /**
     * @param idempotencyKey optional; a repeated key returns the first transaction instead of creating
     *                       a second document
     */
    public CreateOutcome createDocument(DocumentMetadata meta, String idempotencyKey) {
        TransactionMonitor.Started started = monitor.start(idempotencyKey);
        TransactionRecord record = started.record();
        if (started.replay()) {
            LOG.info("Idempotency key replay, returning transaction {} in state {}", record.id(), record.state());
            return new CreateOutcome(record, true);
        }
        String txId = record.id();
        LOG.info("Transaction {}: create document type={} number={}", txId, meta.documentType(),
                blankToInternal(meta.documentNumber()));

        CompletableFuture<DocumentKey> future;
        try {
            future = CompletableFuture.supplyAsync(
                    () -> gateway.createDocument(meta, state -> monitor.update(txId, state)), sapExecutor);
        } catch (TaskRejectedException e) {
            monitor.update(txId, TransactionState.COMMUNICATION_FAILED, null, List.of("Service busy"));
            throw new IntegrationException(IntegrationException.Kind.SAP_UNAVAILABLE, txId,
                    "Too many parallel SAP requests, please retry later", List.of(), e);
        }
        // Record the final outcome even if the caller has already timed out.
        future.whenComplete((key, error) -> recordOutcome(txId, key, error));

        try {
            DocumentKey key = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            // whenComplete may not have run yet on this thread's view; record the outcome explicitly.
            return new CreateOutcome(monitor.update(txId, TransactionState.COMMITTED, key, List.of()), false);
        } catch (TimeoutException e) {
            monitor.update(txId, TransactionState.TIMEOUT_OUTCOME_UNKNOWN, null,
                    List.of("No answer from SAP within " + timeout.toSeconds() + "s"));
            throw new IntegrationException(IntegrationException.Kind.SAP_TIMEOUT, txId,
                    "SAP did not answer within " + timeout.toSeconds() + "s. The document may still be created; "
                            + "check the transaction status before retrying.", List.of(), e);
        } catch (ExecutionException e) {
            throw translate(txId, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IntegrationException(IntegrationException.Kind.INTERNAL, txId, "Request interrupted", List.of(), e);
        }
    }

    public Optional<DocumentDetail> getDocument(DocumentKey key) {
        return withTimeout(() -> gateway.getDocument(key));
    }

    public Optional<TransactionRecord> transaction(String id) {
        return monitor.find(id);
    }

    private void recordOutcome(String txId, DocumentKey key, Throwable error) {
        Throwable cause = error instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : error;
        if (cause == null) {
            monitor.update(txId, TransactionState.COMMITTED, key, List.of());
        } else if (cause instanceof SapBusinessException sbe) {
            monitor.update(txId, TransactionState.REJECTED_BY_SAP, null,
                    sbe.messages().stream().map(BapiReturn::toString).toList());
        } else if (cause instanceof SapCommunicationException) {
            monitor.update(txId, TransactionState.COMMUNICATION_FAILED, null, List.of(cause.getMessage()));
        } else {
            LOG.error("Transaction {} failed unexpectedly", txId, cause);
            monitor.update(txId, TransactionState.FAILED, null, List.of("Internal error"));
        }
    }

    private <T> T withTimeout(Supplier<T> call) {
        try {
            return CompletableFuture.supplyAsync(call, sapExecutor).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TaskRejectedException e) {
            throw new IntegrationException(IntegrationException.Kind.SAP_UNAVAILABLE, null,
                    "Too many parallel SAP requests, please retry later", List.of(), e);
        } catch (TimeoutException e) {
            throw new IntegrationException(IntegrationException.Kind.SAP_TIMEOUT, null,
                    "SAP did not answer within " + timeout.toSeconds() + "s", List.of(), e);
        } catch (ExecutionException e) {
            throw translate(null, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IntegrationException(IntegrationException.Kind.INTERNAL, null, "Request interrupted", List.of(), e);
        }
    }

    private static IntegrationException translate(String txId, Throwable cause) {
        if (cause instanceof SapBusinessException sbe) {
            return new IntegrationException(IntegrationException.Kind.SAP_REJECTED, txId,
                    "SAP rejected the request", sbe.messages(), sbe);
        }
        if (cause instanceof SapCommunicationException sce) {
            return new IntegrationException(IntegrationException.Kind.SAP_UNAVAILABLE, txId,
                    "SAP system is not reachable, please retry later", List.of(), sce);
        }
        return new IntegrationException(IntegrationException.Kind.INTERNAL, txId, "Unexpected error", List.of(), cause);
    }

    private static String blankToInternal(String number) {
        return number == null || number.isBlank() ? "<internal>" : number;
    }
}
