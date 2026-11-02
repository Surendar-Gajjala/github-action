// VERIFIED: plain Java. The four fields mirror the SAP DMS document key (DOKAR/DOKNR/DOKTL/DOKVR).
package com.example.ectr.dms.domain;

import java.util.Objects;

/**
 * Identifies one SAP DMS document info record (DIR) version.
 *
 * @param documentType    document type, for example DRW (SAP field DOKAR, CHAR 3)
 * @param documentNumber  document number (DOKNR, CHAR 25)
 * @param documentPart    document part (DOKTL, CHAR 3)
 * @param documentVersion document version (DOKVR, CHAR 2)
 */
public record DocumentKey(String documentType, String documentNumber, String documentPart, String documentVersion) {

    public DocumentKey {
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(documentPart, "documentPart");
        Objects.requireNonNull(documentVersion, "documentVersion");
    }

    /** Human-readable form used in logs and messages, for example {@code DRW/10000001/000/00}. */
    @Override
    public String toString() {
        return documentType + "/" + documentNumber + "/" + documentPart + "/" + documentVersion;
    }
}
