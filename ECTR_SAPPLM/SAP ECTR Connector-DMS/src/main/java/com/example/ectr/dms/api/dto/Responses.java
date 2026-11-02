// VERIFIED: plain Java records serialised by Jackson.
package com.example.ectr.dms.api.dto;

import com.example.ectr.dms.domain.DocumentDetail;
import com.example.ectr.dms.domain.DocumentKey;
import com.example.ectr.dms.files.StoredFile;
import com.example.ectr.dms.service.TransactionRecord;

import java.time.Instant;
import java.util.List;

/** Response bodies. Grouped in one file because each is a few lines. */
public final class Responses {

    private Responses() {
    }

    public record DocumentKeyDto(String documentType, String documentNumber, String documentPart, String documentVersion) {
        public static DocumentKeyDto of(DocumentKey key) {
            return key == null ? null
                    : new DocumentKeyDto(key.documentType(), key.documentNumber(), key.documentPart(), key.documentVersion());
        }
    }

    /** Body of a successful POST /api/v1/documents. */
    public record CreateDocumentResponse(String transactionId, String state, DocumentKeyDto document, boolean replayed) {
    }

    public record DocumentDetailResponse(DocumentKeyDto document, String description, String status) {
        public static DocumentDetailResponse of(DocumentDetail detail) {
            return new DocumentDetailResponse(DocumentKeyDto.of(detail.key()), detail.description(), detail.status());
        }
    }

    /**
     * @param sapCheckin always STORED_IN_MIDDLEWARE_ONLY in this version: the file is NOT checked into SAP
     */
    public record OriginalFileResponse(String fileId, String fileName, String fileType, long size, String sha256,
                                       Instant receivedAt, String downloadUrl, String sapCheckin) {
        public static OriginalFileResponse of(StoredFile f, String downloadUrl) {
            return new OriginalFileResponse(f.fileId(), f.originalName(), f.kind().name(), f.size(), f.sha256(),
                    f.receivedAt(), downloadUrl, "STORED_IN_MIDDLEWARE_ONLY");
        }
    }

    public record TransactionResponse(String transactionId, String state, boolean finished, DocumentKeyDto document,
                                      List<String> messages, List<TransactionRecord.StateChange> history) {
        public static TransactionResponse of(TransactionRecord r) {
            return new TransactionResponse(r.id(), r.state().name(), r.state().isTerminal(),
                    DocumentKeyDto.of(r.documentKey()), r.messages(), r.history());
        }
    }
}
