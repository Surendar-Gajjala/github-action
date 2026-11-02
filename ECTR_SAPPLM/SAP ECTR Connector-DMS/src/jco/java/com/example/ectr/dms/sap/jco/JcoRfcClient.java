// Compiled only with profile "jco". Uses the public SAP JCo 3 API (JCoDestinationManager, JCoContext,
// JCoFunction, JCoStructure, JCoTable). NOT compiled in this repository (no sapjco3.jar available):
// compile with -Pjco against your copy and test against a sandbox system before relying on it.
package com.example.ectr.dms.sap.jco;

import com.example.ectr.dms.sap.BapiReturn;
import com.example.ectr.dms.sap.RfcCall;
import com.example.ectr.dms.sap.RfcClient;
import com.example.ectr.dms.sap.RfcResult;
import com.example.ectr.dms.sap.SapBusinessException;
import com.example.ectr.dms.sap.SapCommunicationException;
import com.sap.conn.jco.AbapException;
import com.sap.conn.jco.JCoContext;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoField;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoRecord;
import com.sap.conn.jco.JCoStructure;
import com.sap.conn.jco.JCoTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Executes {@link RfcCall}s through SAP JCo. Thread-safe: JCo destinations are pooled and thread-safe. */
public class JcoRfcClient implements RfcClient {

    private static final Logger LOG = LoggerFactory.getLogger(JcoRfcClient.class);

    private final String destinationName;

    public JcoRfcClient(String destinationName) {
        this.destinationName = destinationName;
    }

    @Override
    public <T> T inSession(Function<RfcSession, T> work) {
        JCoDestination destination = destination();
        // Stateful context: all calls until end() share one connection and one LUW, which is
        // required for BAPI + BAPI_TRANSACTION_COMMIT.
        JCoContext.begin(destination);
        try {
            return work.apply(call -> execute(destination, call));
        } finally {
            try {
                JCoContext.end(destination);
            } catch (JCoException e) {
                LOG.warn("Could not end JCo context cleanly: {}", e.getMessage());
            }
        }
    }

    @Override
    public void ping() {
        try {
            destination().ping();
        } catch (JCoException e) {
            throw new SapCommunicationException("SAP ping failed: " + e.getMessage(), e);
        }
    }

    private JCoDestination destination() {
        try {
            return JCoDestinationManager.getDestination(destinationName);
        } catch (JCoException e) {
            throw new SapCommunicationException("SAP destination " + destinationName + " not available: "
                    + e.getMessage(), e);
        }
    }

    private RfcResult execute(JCoDestination destination, RfcCall call) {
        try {
            JCoFunction function = destination.getRepository().getFunction(call.functionName());
            if (function == null) {
                throw new SapCommunicationException("Function " + call.functionName()
                        + " not found or not RFC-enabled in the SAP system");
            }
            fillImports(function.getImportParameterList(), call);
            fillTables(function.getTableParameterList(), call);
            function.execute(destination);
            return new RfcResult(readRecord(function.getExportParameterList()), readTables(function.getTableParameterList()));
        } catch (AbapException e) {
            // Classic ABAP exception raised by the function module (BAPIs normally use RETURN instead).
            throw new SapBusinessException(call.functionName(),
                    List.of(new BapiReturn("E", "ABAP", "", e.getKey() + ": " + e.getMessage())));
        } catch (JCoException e) {
            // Communication, logon, system failure, ... Do not include credentials: JCo messages do not.
            throw new SapCommunicationException(call.functionName() + " failed (JCo group " + e.getGroup() + "): "
                    + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void fillImports(JCoParameterList imports, RfcCall call) {
        if (imports == null) {
            return;
        }
        call.imports().forEach((name, value) -> {
            if (value instanceof Map<?, ?> fields) {
                JCoStructure structure = imports.getStructure(name);
                ((Map<String, Object>) fields).forEach(structure::setValue);
            } else {
                imports.setValue(name, value);
            }
        });
    }

    private static void fillTables(JCoParameterList tables, RfcCall call) {
        if (tables == null) {
            return;
        }
        call.tables().forEach((name, rows) -> {
            JCoTable table = tables.getTable(name);
            for (Map<String, Object> row : rows) {
                table.appendRow();
                row.forEach(table::setValue);
            }
        });
    }

    private static Map<String, Object> readRecord(JCoRecord record) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (record == null) {
            return values;
        }
        for (JCoField field : record) {
            if (field.isStructure()) {
                values.put(field.getName(), readRecord(field.getStructure()));
            } else if (!field.isTable()) {
                values.put(field.getName(), field.getString());
            }
        }
        return values;
    }

    private static Map<String, List<Map<String, Object>>> readTables(JCoParameterList tables) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (tables == null) {
            return result;
        }
        for (JCoField field : tables) {
            JCoTable table = field.getTable();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < table.getNumRows(); i++) {
                table.setRow(i);
                rows.add(readRecord(table));
            }
            result.put(field.getName(), rows);
        }
        return result;
    }
}
