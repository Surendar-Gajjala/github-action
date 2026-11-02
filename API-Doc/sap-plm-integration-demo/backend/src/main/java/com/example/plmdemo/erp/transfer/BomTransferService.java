package com.example.plmdemo.erp.transfer;

import com.example.plmdemo.erp.config.IntegrationProperties;
import com.example.plmdemo.erp.domain.EngineeringBom;
import com.example.plmdemo.erp.domain.TransferRecord;
import com.example.plmdemo.erp.domain.TransferStatus;
import com.example.plmdemo.erp.mapping.BomMapper;
import com.example.plmdemo.erp.sap.SapBomGateway;
import com.example.plmdemo.erp.sap.SapBomRequest;
import com.example.plmdemo.erp.sap.SapBomResponse;
import com.example.plmdemo.erp.sap.SapPermanentException;
import com.example.plmdemo.erp.sap.SapTransientException;
import com.example.plmdemo.erp.source.PlmBomSource;
import com.example.plmdemo.erp.validation.BomValidationException;
import com.example.plmdemo.erp.validation.BomValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates one BOM transfer: duplicate check -> read from PLM -> validate -> map -> send to SAP
 * (with retry for transient errors) -> record the outcome.
 *
 * Contains no SAP transport code. Everything SAP-specific sits behind SapBomGateway.
 */
@Service
public class BomTransferService {

    private static final Logger log = LoggerFactory.getLogger(BomTransferService.class);

    private final PlmBomSource source;
    private final BomValidator validator;
    private final BomMapper mapper;
    private final SapBomGateway gateway;
    private final TransferRepository repository;
    private final int maxAttempts;
    private final long backoffMillis;

    @Autowired
    public BomTransferService(PlmBomSource source, BomValidator validator, BomMapper mapper,
                              SapBomGateway gateway, TransferRepository repository,
                              IntegrationProperties properties) {
        this(source, validator, mapper, gateway, repository,
                properties.retry().maxAttempts(), properties.retry().backoffMillis());
    }

    public BomTransferService(PlmBomSource source, BomValidator validator, BomMapper mapper,
                              SapBomGateway gateway, TransferRepository repository,
                              int maxAttempts, long backoffMillis) {
        this.source = source;
        this.validator = validator;
        this.mapper = mapper;
        this.gateway = gateway;
        this.repository = repository;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMillis = Math.max(0, backoffMillis);
    }

    public TransferResult transfer(TransferCommand command) {
        String key = command.effectiveIdempotencyKey();

        TransferRepository.Registration registration = repository.register(key, previous -> newRecord(command, key));
        if (!registration.created()) {
            log.info("Duplicate submission for key={} -> returning transfer {}", key, registration.record().transferId());
            return new TransferResult(registration.record(), true);
        }
        TransferRecord record = registration.record();

        EngineeringBom bom = source.findBom(command.materialNumber(), command.revision(), command.plant())
                .orElse(null);
        if (bom == null) {
            BomNotFoundException notFound = new BomNotFoundException(
                    command.materialNumber(), command.revision(), command.plant());
            repository.save(record.withStatus(TransferStatus.VALIDATION_FAILED, List.of(notFound.getMessage())));
            throw notFound;
        }

        try {
            validator.validate(bom);
        } catch (BomValidationException e) {
            repository.save(record.withStatus(TransferStatus.VALIDATION_FAILED, e.getViolations()));
            throw e;
        }

        SapBomRequest request = mapper.toSapRequest(bom, key, command.correlationId());
        record = repository.save(record.withStatus(TransferStatus.IN_PROGRESS, List.of()));
        return new TransferResult(sendWithRetry(record, request), false);
    }

    public Optional<TransferRecord> findTransfer(String transferId) {
        return repository.findById(transferId);
    }

    private TransferRecord sendWithRetry(TransferRecord record, SapBomRequest request) {
        List<String> messages = new ArrayList<>();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            record = repository.save(record.withAttempts(attempt));
            try {
                SapBomResponse response = gateway.submitBom(request);
                messages.add(response.message());
                log.info("Transfer {} succeeded on attempt {} ref={}", record.transferId(), attempt, response.targetReference());
                return repository.save(record.succeeded(response.targetReference(), messages));
            } catch (SapPermanentException e) {
                messages.add("Attempt " + attempt + ": rejected: " + e.getMessage());
                log.warn("Transfer {} rejected by SAP: {}", record.transferId(), e.getMessage());
                return repository.save(record.withStatus(TransferStatus.FAILED_PERMANENT, messages));
            } catch (SapTransientException e) {
                messages.add("Attempt " + attempt + ": transient error: " + e.getMessage());
                log.warn("Transfer {} attempt {}/{} failed transiently: {}",
                        record.transferId(), attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(backoffMillis * attempt);
                }
            }
        }
        return repository.save(record.withStatus(TransferStatus.FAILED_TRANSIENT, messages));
    }

    private static TransferRecord newRecord(TransferCommand command, String key) {
        Instant now = Instant.now();
        return new TransferRecord(UUID.randomUUID().toString(), key, command.correlationId(),
                command.materialNumber(), command.revision(), command.plant(),
                TransferStatus.RECEIVED, 0, null, List.of(), now, now);
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SapTransientException("Interrupted while waiting to retry", e);
        }
    }
}
