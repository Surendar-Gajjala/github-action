// VERIFIED: plain Java + Spring. BAPI orchestration (create -> check RETURN -> commit or rollback)
// over the RfcClient port. Unit-tested with the mock client.
package com.example.ectr.dms.sap;

import com.example.ectr.dms.domain.DocumentDetail;
import com.example.ectr.dms.domain.DocumentKey;
import com.example.ectr.dms.domain.DocumentMetadata;
import com.example.ectr.dms.domain.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.ectr.dms.sap.BapiNames.BAPI_DOCUMENT_CREATE2;
import static com.example.ectr.dms.sap.BapiNames.BAPI_DOCUMENT_GETDETAIL2;
import static com.example.ectr.dms.sap.BapiNames.BAPI_TRANSACTION_COMMIT;
import static com.example.ectr.dms.sap.BapiNames.RETURN;

/**
 * Creates and reads SAP DMS documents.
 * <p>
 * BAPI_DOCUMENT_CREATE2, like most BAPIs, does <b>not</b> commit by itself. The document exists only
 * after BAPI_TRANSACTION_COMMIT has run in the <b>same</b> session. This class makes sure that:
 * <ul>
 *   <li>a RETURN of type E/A leads to BAPI_TRANSACTION_ROLLBACK and a {@link SapBusinessException},</li>
 *   <li>success leads to BAPI_TRANSACTION_COMMIT with WAIT = 'X', whose own RETURN is checked too.</li>
 * </ul>
 */
@Component
public class SapDocumentGateway {

    private static final Logger LOG = LoggerFactory.getLogger(SapDocumentGateway.class);

    private final RfcClient rfcClient;
    private final DocumentBapiMapper mapper;

    public SapDocumentGateway(RfcClient rfcClient, DocumentBapiMapper mapper) {
        this.rfcClient = rfcClient;
        this.mapper = mapper;
    }

    /**
     * @param progress receives the intermediate states SAP_CALL_STARTED and SAP_DOCUMENT_CREATED.
     *                 The final state is decided by the caller from the return value or exception.
     * @return the key SAP created and committed
     */
    public DocumentKey createDocument(DocumentMetadata meta, Consumer<TransactionState> progress) {
        return rfcClient.inSession(session -> {
            progress.accept(TransactionState.SAP_CALL_STARTED);
            RfcResult result = session.call(mapper.createDocument(meta));

            List<BapiReturn> messages = BapiReturn.fromStructure(result.exportStructure(RETURN));
            List<BapiReturn> errors = BapiReturn.errors(messages);
            if (!errors.isEmpty()) {
                session.call(mapper.rollback());
                throw new SapBusinessException(BAPI_DOCUMENT_CREATE2, errors);
            }
            DocumentKey key = mapper.createdKey(result);
            progress.accept(TransactionState.SAP_DOCUMENT_CREATED);
            LOG.info("{} created {} (not yet committed), messages={}", BAPI_DOCUMENT_CREATE2, key, messages);

            RfcResult commit = session.call(mapper.commit());
            List<BapiReturn> commitErrors = BapiReturn.errors(BapiReturn.fromStructure(commit.exportStructure(RETURN)));
            if (!commitErrors.isEmpty()) {
                throw new SapBusinessException(BAPI_TRANSACTION_COMMIT, commitErrors);
            }
            LOG.info("Committed {}", key);
            return key;
        });
    }

    /** Reads a document; empty if SAP reports that it does not exist. */
    public Optional<DocumentDetail> getDocument(DocumentKey key) {
        return rfcClient.inSession(session -> {
            RfcResult result = session.call(mapper.getDetail(key));
            List<BapiReturn> errors = BapiReturn.errors(BapiReturn.fromStructure(result.exportStructure(RETURN)));
            if (!errors.isEmpty()) {
                // GETDETAIL2 reports "document does not exist" as an error message. The message class
                // and number are system-specific, so all errors are treated as "not found" here and logged.
                LOG.info("{} {} returned {}", BAPI_DOCUMENT_GETDETAIL2, key, errors);
                return Optional.empty();
            }
            return Optional.of(mapper.detail(key, result));
        });
    }

    public void ping() {
        rfcClient.ping();
    }
}
