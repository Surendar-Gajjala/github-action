// VERIFIED: plain Java mapping logic (unit-tested). The SAP names it uses come from BapiNames and are
// ILLUSTRATIVE until checked in SE37 on the target system.
package com.example.ectr.dms.sap;

import com.example.ectr.dms.domain.DocumentDetail;
import com.example.ectr.dms.domain.DocumentKey;
import com.example.ectr.dms.domain.DocumentMetadata;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.ectr.dms.sap.BapiNames.*;

/** Translates between our domain objects and BAPI parameters. Contains no I/O. */
@Component
public class DocumentBapiMapper {

    public RfcCall createDocument(DocumentMetadata meta) {
        Map<String, Object> documentData = new LinkedHashMap<>();
        documentData.put(DOCUMENTTYPE, meta.documentType());
        documentData.put(DOCUMENTNUMBER, meta.documentNumber());
        documentData.put(DOCUMENTPART, meta.documentPart());
        documentData.put(DOCUMENTVERSION, meta.documentVersion());
        documentData.put(DESCRIPTION, meta.description());
        documentData.put(LABORATORY, meta.laboratory());

        RfcCall.Builder call = RfcCall.function(BAPI_DOCUMENT_CREATE2).importStructure(DOCUMENTDATA, documentData);
        for (DocumentMetadata.Description d : meta.descriptions()) {
            call.tableRow(DOCUMENTDESCRIPTIONS, Map.of(LANGUAGE_ISO, d.languageIso(), DESCRIPTION, d.text()));
        }
        for (DocumentMetadata.ObjectLink link : meta.objectLinks()) {
            call.tableRow(OBJECTLINKS, Map.of(OBJECTTYPE, link.objectType(), OBJECTKEY, link.objectKey()));
        }
        return call.build();
    }

    /** Key that SAP actually created (it may have assigned number, part and version). */
    public DocumentKey createdKey(RfcResult result) {
        return new DocumentKey(
                nullToEmpty(result.exportValue(DOCUMENTTYPE)),
                nullToEmpty(result.exportValue(DOCUMENTNUMBER)),
                nullToEmpty(result.exportValue(DOCUMENTPART)),
                nullToEmpty(result.exportValue(DOCUMENTVERSION)));
    }

    public RfcCall getDetail(DocumentKey key) {
        return RfcCall.function(BAPI_DOCUMENT_GETDETAIL2)
                .importValue(DOCUMENTTYPE, key.documentType())
                .importValue(DOCUMENTNUMBER, key.documentNumber())
                .importValue(DOCUMENTPART, key.documentPart())
                .importValue(DOCUMENTVERSION, key.documentVersion())
                .build();
    }

    public DocumentDetail detail(DocumentKey requested, RfcResult result) {
        Map<String, Object> data = result.exportStructure(DOCUMENTDATA);
        return new DocumentDetail(requested, text(data, DESCRIPTION), text(data, STATUSINTERN));
    }

    public RfcCall commit() {
        return RfcCall.function(BAPI_TRANSACTION_COMMIT).importValue(WAIT, "X").build();
    }

    public RfcCall rollback() {
        return RfcCall.function(BAPI_TRANSACTION_ROLLBACK).build();
    }

    private static String text(Map<String, Object> data, String field) {
        Object value = data.get(field);
        return value == null ? "" : value.toString().trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
