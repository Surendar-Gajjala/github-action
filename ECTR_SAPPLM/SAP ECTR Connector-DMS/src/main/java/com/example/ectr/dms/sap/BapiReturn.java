// VERIFIED: plain Java. Field names follow the standard BAPI return structure BAPIRET2
// (TYPE, ID, NUMBER, MESSAGE). Type codes: S success, I info, W warning, E error, A abort.
package com.example.ectr.dms.sap;

import java.util.List;
import java.util.Map;

/** One message from a BAPI RETURN parameter. */
public record BapiReturn(String type, String id, String number, String message) {

    public static BapiReturn fromRow(Map<String, Object> row) {
        return new BapiReturn(text(row, "TYPE"), text(row, "ID"), text(row, "NUMBER"), text(row, "MESSAGE"));
    }

    /** Reads RETURN when it is a structure; an empty structure means "no message". */
    public static List<BapiReturn> fromStructure(Map<String, Object> structure) {
        BapiReturn r = fromRow(structure);
        return r.type().isEmpty() && r.message().isEmpty() ? List.of() : List.of(r);
    }

    public static List<BapiReturn> fromTable(List<Map<String, Object>> rows) {
        return rows.stream().map(BapiReturn::fromRow).filter(r -> !r.type().isEmpty() || !r.message().isEmpty()).toList();
    }

    public static List<BapiReturn> errors(List<BapiReturn> messages) {
        return messages.stream().filter(BapiReturn::isError).toList();
    }

    /** E (error) and A (abort) mean the BAPI did not do its work. */
    public boolean isError() {
        return "E".equals(type) || "A".equals(type);
    }

    @Override
    public String toString() {
        return type + " " + id + "/" + number + " " + message;
    }

    private static String text(Map<String, Object> row, String field) {
        Object value = row.get(field);
        return value == null ? "" : value.toString().trim();
    }
}
