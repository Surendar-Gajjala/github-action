package com.example.plmdemo.erp.sap;

/**
 * Port to the SAP side. Business logic depends only on this interface,
 * never on SAP transport code.
 *
 * Implementations must throw:
 *  - SapTransientException for temporary problems (timeout, 503, lock, connection refused); these are retried
 *  - SapPermanentException for business rejections (unknown material, invalid plant, authorization); never retried
 */
public interface SapBomGateway {

    SapBomResponse submitBom(SapBomRequest request);

    /** Shown in API responses so callers can see whether a real system was involved. */
    String mode();
}
