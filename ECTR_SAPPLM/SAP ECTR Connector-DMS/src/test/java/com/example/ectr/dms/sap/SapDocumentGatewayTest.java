package com.example.ectr.dms.sap;

import com.example.ectr.dms.domain.DocumentKey;
import com.example.ectr.dms.domain.DocumentMetadata;
import com.example.ectr.dms.domain.TransactionState;
import com.example.ectr.dms.sap.mock.MockRfcClient;
import com.example.ectr.dms.sap.mock.MockSapProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SapDocumentGatewayTest {

    /** Wraps the mock and records which functions were called, in order. */
    static final class RecordingClient implements RfcClient {
        final RfcClient delegate;
        final List<String> calls = new ArrayList<>();

        RecordingClient(RfcClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T inSession(Function<RfcSession, T> work) {
            return delegate.inSession(session -> work.apply(call -> {
                calls.add(call.functionName());
                return session.call(call);
            }));
        }

        @Override
        public void ping() {
            delegate.ping();
        }
    }

    private final MockRfcClient mock = new MockRfcClient(
            new MockSapProperties(Duration.ZERO, List.of("DRW"), false));
    private final RecordingClient client = new RecordingClient(mock);
    private final SapDocumentGateway gateway = new SapDocumentGateway(client, new DocumentBapiMapper());

    private static DocumentMetadata meta(String type) {
        return new DocumentMetadata(type, null, null, null, "Test", null, List.of(), List.of());
    }

    @Test
    void successfulCreateIsCommittedInSameSession() {
        List<TransactionState> progress = new ArrayList<>();

        DocumentKey key = gateway.createDocument(meta("DRW"), progress::add);

        assertThat(client.calls).containsExactly("BAPI_DOCUMENT_CREATE2", "BAPI_TRANSACTION_COMMIT");
        assertThat(progress).containsExactly(TransactionState.SAP_CALL_STARTED, TransactionState.SAP_DOCUMENT_CREATED);
        assertThat(key.documentType()).isEqualTo("DRW");
        assertThat(mock.committedCount()).isEqualTo(1);
        assertThat(gateway.getDocument(key)).isPresent();
    }

    @Test
    void sapErrorIsRolledBackAndNeverCommitted() {
        assertThatThrownBy(() -> gateway.createDocument(meta("XXX"), s -> { }))
                .isInstanceOf(SapBusinessException.class)
                .satisfies(e -> assertThat(((SapBusinessException) e).messages()).allMatch(BapiReturn::isError));

        assertThat(client.calls).containsExactly("BAPI_DOCUMENT_CREATE2", "BAPI_TRANSACTION_ROLLBACK");
        assertThat(mock.committedCount()).isZero();
    }

    @Test
    void createWithoutCommitLeavesNothingBehind() {
        // Demonstrates why the commit matters: calling only the BAPI does not persist anything.
        client.inSession(session -> session.call(new DocumentBapiMapper().createDocument(meta("DRW"))));
        assertThat(mock.committedCount()).isZero();
    }

    @Test
    void unknownDocumentIsEmpty() {
        assertThat(gateway.getDocument(new DocumentKey("DRW", "999", "000", "00"))).isEmpty();
    }

    @Test
    void communicationFailureIsReported() {
        SapDocumentGateway down = new SapDocumentGateway(
                new MockRfcClient(new MockSapProperties(Duration.ZERO, List.of("DRW"), true)), new DocumentBapiMapper());
        assertThatThrownBy(() -> down.createDocument(meta("DRW"), s -> { }))
                .isInstanceOf(SapCommunicationException.class);
    }
}
