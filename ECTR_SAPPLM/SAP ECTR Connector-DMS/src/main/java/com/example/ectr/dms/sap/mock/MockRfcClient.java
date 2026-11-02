// ILLUSTRATIVE: an in-memory imitation of the few SAP functions this service uses. Its messages and
// numbering are invented. It reproduces the behaviour that matters for the integration design:
// documents created by BAPI_DOCUMENT_CREATE2 are only kept after BAPI_TRANSACTION_COMMIT in the same session.
package com.example.ectr.dms.sap.mock;

import com.example.ectr.dms.sap.RfcCall;
import com.example.ectr.dms.sap.RfcClient;
import com.example.ectr.dms.sap.RfcResult;
import com.example.ectr.dms.sap.SapCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.example.ectr.dms.sap.BapiNames.*;

/** Active unless the "jco" profile is on. */
@Component
@Profile("!jco")
public class MockRfcClient implements RfcClient {

    private static final Logger LOG = LoggerFactory.getLogger(MockRfcClient.class);

    private final MockSapProperties properties;
    private final Map<String, Map<String, Object>> committed = new ConcurrentHashMap<>();
    private final AtomicLong numberRange = new AtomicLong(10000000);

    public MockRfcClient(MockSapProperties properties) {
        this.properties = properties;
        LOG.warn("Using MOCK SAP backend - no real SAP system is called. Known document types: {}",
                properties.documentTypes());
    }

    @Override
    public <T> T inSession(Function<RfcSession, T> work) {
        if (properties.failCommunication()) {
            throw new SapCommunicationException("Mock: SAP system not reachable (mock.sap.fail-communication=true)");
        }
        MockSession session = new MockSession();
        try {
            return work.apply(session);
        } finally {
            if (!session.staged.isEmpty()) {
                LOG.info("Mock: session ended without commit, discarding {} staged document(s)", session.staged.size());
            }
        }
    }

    @Override
    public void ping() {
        if (properties.failCommunication()) {
            throw new SapCommunicationException("Mock: SAP system not reachable");
        }
    }

    /** Number of committed documents, for tests. */
    public int committedCount() {
        return committed.size();
    }

    private final class MockSession implements RfcSession {

        private final List<Map<String, Object>> staged = new ArrayList<>();

        @Override
        public RfcResult call(RfcCall call) {
            return switch (call.functionName()) {
                case BAPI_DOCUMENT_CREATE2 -> create(call);
                case BAPI_TRANSACTION_COMMIT -> commit();
                case BAPI_TRANSACTION_ROLLBACK -> rollback();
                case BAPI_DOCUMENT_GETDETAIL2 -> getDetail(call);
                default -> throw new SapCommunicationException("Mock: function " + call.functionName() + " not available");
            };
        }

        private RfcResult create(RfcCall call) {
            sleep();
            Map<String, Object> data = new LinkedHashMap<>(call.importStructure(DOCUMENTDATA));
            String type = (String) data.getOrDefault(DOCUMENTTYPE, "");
            if (!properties.documentTypes().contains(type)) {
                return error("Mock: document type " + type + " is not defined");
            }
            data.putIfAbsent(DOCUMENTNUMBER, String.valueOf(numberRange.incrementAndGet()));
            data.putIfAbsent(DOCUMENTPART, "000");
            data.putIfAbsent(DOCUMENTVERSION, "00");
            String key = key(data.get(DOCUMENTTYPE), data.get(DOCUMENTNUMBER), data.get(DOCUMENTPART), data.get(DOCUMENTVERSION));
            if (committed.containsKey(key)) {
                return error("Mock: document " + key + " already exists");
            }
            data.put(STATUSINTERN, "IA");
            staged.add(data);
            return new RfcResult(Map.of(
                    DOCUMENTTYPE, data.get(DOCUMENTTYPE),
                    DOCUMENTNUMBER, data.get(DOCUMENTNUMBER),
                    DOCUMENTPART, data.get(DOCUMENTPART),
                    DOCUMENTVERSION, data.get(DOCUMENTVERSION),
                    RETURN, Map.of("TYPE", "S", "MESSAGE", "Mock: document " + key + " created")), Map.of());
        }

        private RfcResult commit() {
            staged.forEach(d -> committed.put(key(d.get(DOCUMENTTYPE), d.get(DOCUMENTNUMBER), d.get(DOCUMENTPART),
                    d.get(DOCUMENTVERSION)), d));
            staged.clear();
            return new RfcResult(Map.of(RETURN, Map.of()), Map.of());
        }

        private RfcResult rollback() {
            staged.clear();
            return new RfcResult(Map.of(RETURN, Map.of()), Map.of());
        }

        private RfcResult getDetail(RfcCall call) {
            String key = key(call.importValue(DOCUMENTTYPE), call.importValue(DOCUMENTNUMBER),
                    call.importValue(DOCUMENTPART), call.importValue(DOCUMENTVERSION));
            Map<String, Object> data = committed.get(key);
            if (data == null) {
                return error("Mock: document " + key + " does not exist");
            }
            return new RfcResult(Map.of(DOCUMENTDATA, data, RETURN, Map.of()), Map.of());
        }

        private RfcResult error(String message) {
            return new RfcResult(Map.of(RETURN, Map.of("TYPE", "E", "ID", "MOCK", "NUMBER", "001", "MESSAGE", message)),
                    Map.of());
        }

        private void sleep() {
            if (!properties.latency().isZero()) {
                try {
                    Thread.sleep(properties.latency().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SapCommunicationException("Mock: call interrupted", e);
                }
            }
        }
    }

    private static String key(Object type, Object number, Object part, Object version) {
        return type + "/" + number + "/" + part + "/" + version;
    }
}
