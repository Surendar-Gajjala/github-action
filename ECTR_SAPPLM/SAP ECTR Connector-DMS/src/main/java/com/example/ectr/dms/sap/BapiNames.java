// ILLUSTRATIVE until checked: function, parameter and field names below are the commonly documented
// interface of these standard BAPIs. Interfaces can differ between SAP releases and support packages.
// BEFORE GO-LIVE, compare every name with transaction SE37 (Function Builder) / BAPI explorer on the
// target system. See docs/06-bapi-document-create2.md.
package com.example.ectr.dms.sap;

/** All SAP names used by the integration, in one place so they are easy to review. */
public final class BapiNames {

    // --- Function modules ---------------------------------------------------------------------
    public static final String BAPI_DOCUMENT_CREATE2 = "BAPI_DOCUMENT_CREATE2";
    public static final String BAPI_DOCUMENT_GETDETAIL2 = "BAPI_DOCUMENT_GETDETAIL2";
    public static final String BAPI_TRANSACTION_COMMIT = "BAPI_TRANSACTION_COMMIT";
    public static final String BAPI_TRANSACTION_ROLLBACK = "BAPI_TRANSACTION_ROLLBACK";

    // --- Parameters ---------------------------------------------------------------------------
    /** Import structure of CREATE2 / export structure of GETDETAIL2 (type BAPI_DOC_DRAW2). */
    public static final String DOCUMENTDATA = "DOCUMENTDATA";
    /** Table of short texts per language (type BAPI_DOC_DRAT). */
    public static final String DOCUMENTDESCRIPTIONS = "DOCUMENTDESCRIPTIONS";
    /** Table of object links (type BAPI_DOC_DRAD). */
    public static final String OBJECTLINKS = "OBJECTLINKS";
    /** BAPIRET2 structure with the result message. */
    public static final String RETURN = "RETURN";
    /** BAPI_TRANSACTION_COMMIT: 'X' = wait until the update task has finished. */
    public static final String WAIT = "WAIT";

    // --- Key fields (scalar imports of GETDETAIL2, scalar exports of CREATE2, fields of DOCUMENTDATA)
    public static final String DOCUMENTTYPE = "DOCUMENTTYPE";
    public static final String DOCUMENTNUMBER = "DOCUMENTNUMBER";
    public static final String DOCUMENTPART = "DOCUMENTPART";
    public static final String DOCUMENTVERSION = "DOCUMENTVERSION";

    // --- Other fields -------------------------------------------------------------------------
    public static final String DESCRIPTION = "DESCRIPTION";
    public static final String LABORATORY = "LABORATORY";
    public static final String STATUSINTERN = "STATUSINTERN";
    public static final String LANGUAGE_ISO = "LANGUAGE_ISO";
    public static final String OBJECTTYPE = "OBJECTTYPE";
    public static final String OBJECTKEY = "OBJECTKEY";

    private BapiNames() {
    }
}
