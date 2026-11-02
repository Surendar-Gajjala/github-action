package com.example.plmdemo.erp.api;

import com.example.plmdemo.common.CorrelationIdFilter;
import com.example.plmdemo.common.InvalidRequestException;
import com.example.plmdemo.erp.api.dto.TransferRequestDto;
import com.example.plmdemo.erp.api.dto.TransferResponseDto;
import com.example.plmdemo.erp.domain.TransferStatus;
import com.example.plmdemo.erp.sap.SapBomGateway;
import com.example.plmdemo.erp.transfer.BomTransferService;
import com.example.plmdemo.erp.transfer.TransferCommand;
import com.example.plmdemo.erp.transfer.TransferResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo API of the integration layer (this service's own API, NOT an SAP endpoint).
 *
 *   POST /api/v1/bom-transfers           start a transfer (Idempotency-Key header recommended)
 *   GET  /api/v1/bom-transfers/{id}      read transfer status
 */
@RestController
@RequestMapping("/api/v1/bom-transfers")
public class BomTransferController {

    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final BomTransferService service;
    private final SapBomGateway gateway;

    public BomTransferController(BomTransferService service, SapBomGateway gateway) {
        this.service = service;
        this.gateway = gateway;
    }

    @PostMapping
    public ResponseEntity<TransferResponseDto> submit(
            @Valid @RequestBody TransferRequestDto body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {

        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw new InvalidRequestException("Idempotency-Key must be 1-" + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }

        TransferResult result = service.transfer(new TransferCommand(
                body.materialNumber(), body.revision(), body.plant(), idempotencyKey, correlationId));

        TransferResponseDto dto = TransferResponseDto.from(result.record(), result.duplicate(), gateway.mode());
        return ResponseEntity.status(httpStatusFor(result)).body(dto);
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponseDto> status(@PathVariable String transferId) {
        return service.findTransfer(transferId)
                .map(r -> ResponseEntity.ok(TransferResponseDto.from(r, false, gateway.mode())))
                .orElse(ResponseEntity.notFound().build());
    }

    private static HttpStatus httpStatusFor(TransferResult result) {
        if (result.duplicate()) {
            return HttpStatus.OK;
        }
        TransferStatus status = result.record().status();
        return switch (status) {
            case SUCCEEDED -> HttpStatus.CREATED;
            case FAILED_PERMANENT -> HttpStatus.BAD_GATEWAY;
            case FAILED_TRANSIENT -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.ACCEPTED;
        };
    }
}
