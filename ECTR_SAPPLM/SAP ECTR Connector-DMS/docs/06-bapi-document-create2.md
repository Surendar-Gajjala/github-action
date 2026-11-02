# 6. Invoking BAPI_DOCUMENT_CREATE2 with SAP JCo

> **Verified vs illustrative.**
> - **Verified:** the orchestration (create → check RETURN → commit or rollback, all in one session) and the mapping logic, both covered by tests.
> - **Illustrative until checked:** the parameter and field names in `BapiNames`. They are the commonly documented interface, but interfaces can differ by release and support package. **Open SE37 → `BAPI_DOCUMENT_CREATE2` on the target system and compare every name before go-live.**
> - **Not compiled here:** the JCo code below.

## The document-creation interface (what we use)

| Parameter | Kind | Type (check) | Fields we fill / read |
|---|---|---|---|
| `DOCUMENTDATA` | import structure | `BAPI_DOC_DRAW2` | `DOCUMENTTYPE`, `DOCUMENTNUMBER`, `DOCUMENTPART`, `DOCUMENTVERSION`, `DESCRIPTION`, `LABORATORY` |
| `DOCUMENTDESCRIPTIONS` | table | `BAPI_DOC_DRAT` | `LANGUAGE_ISO`, `DESCRIPTION` (one row per language) |
| `OBJECTLINKS` | table | `BAPI_DOC_DRAD` | `OBJECTTYPE`, `OBJECTKEY` (e.g. a material link) |
| `DOCUMENTTYPE`, `DOCUMENTNUMBER`, `DOCUMENTPART`, `DOCUMENTVERSION` | export | | key SAP actually created |
| `RETURN` | export structure | `BAPIRET2` | `TYPE`, `ID`, `NUMBER`, `MESSAGE` |

The BAPI has more parameters (characteristics, class allocations, files, long texts, …). Add them to `DocumentBapiMapper` when needed, and look up their exact structure in SE37 first.

### Required metadata and DMS configuration

| Metadata | Depends on SAP configuration (transaction DC10 and related customizing, check) |
|---|---|
| Document type | Must exist and be allowed for the RFC user |
| Document number | Internal or external number range, per document type. Leave it empty for internal numbering. |
| Part / version | Defaults and whether versions are assigned automatically |
| Status | Initial status from the document type's status network |
| Object links | The link type (e.g. material) must be enabled for the document type |
| Mandatory fields | Field selection per document type can make extra fields mandatory. SAP will then return an `E` message. |

Steps that need **SAP DMS configuration**: document types, number ranges, status network, object link types, field selection, and storage/content server for originals.
Steps that need **SAP authorisation**: creating and changing documents of each type, creating object links, and checking in originals.

## The orchestration (verified)

```java
// SapDocumentGateway.createDocument
return rfcClient.inSession(session -> {                       // ONE stateful session
    progress.accept(SAP_CALL_STARTED);
    RfcResult result = session.call(mapper.createDocument(meta));          // BAPI_DOCUMENT_CREATE2

    List<BapiReturn> errors = BapiReturn.errors(BapiReturn.fromStructure(result.exportStructure(RETURN)));
    if (!errors.isEmpty()) {
        session.call(mapper.rollback());                                   // BAPI_TRANSACTION_ROLLBACK
        throw new SapBusinessException(BAPI_DOCUMENT_CREATE2, errors);
    }
    DocumentKey key = mapper.createdKey(result);
    progress.accept(SAP_DOCUMENT_CREATED);

    RfcResult commit = session.call(mapper.commit());                      // BAPI_TRANSACTION_COMMIT, WAIT='X'
    … check commit RETURN too …
    return key;
});
```

`WAIT='X'` makes the commit wait for the update task. Without it, an immediate `GETDETAIL2` could miss the new document.

`SapDocumentGatewayTest.createWithoutCommitLeavesNothingBehind` shows the classic mistake: calling only the BAPI leaves nothing in SAP.

## The JCo layer (compiled only with `-Pjco`)

```java
// JcoRfcClient.inSession
JCoDestination destination = JCoDestinationManager.getDestination(destinationName);
JCoContext.begin(destination);                    // stateful: same connection for all calls
try {
    return work.apply(call -> execute(destination, call));
} finally {
    JCoContext.end(destination);                  // uncommitted work is discarded by SAP
}

// JcoRfcClient.execute
JCoFunction function = destination.getRepository().getFunction(call.functionName());
JCoStructure data = function.getImportParameterList().getStructure("DOCUMENTDATA");
data.setValue("DOCUMENTTYPE", "DRW");             // (generic loop over the RfcCall maps)
JCoTable texts = function.getTableParameterList().getTable("DOCUMENTDESCRIPTIONS");
texts.appendRow(); texts.setValue("LANGUAGE_ISO", "DE"); texts.setValue("DESCRIPTION", "…");
function.execute(destination);
JCoStructure ret = function.getExportParameterList().getStructure("RETURN");
```

Exception mapping in the adapter:

- `AbapException` → `SapBusinessException`. This is a classic ABAP exception; BAPIs normally report through RETURN instead.
- Any other `JCoException` (communication, logon, system failure) → `SapCommunicationException`, which the API returns as 503.
- A function that is not found → `SapCommunicationException` with a clear message. This usually means a missing RFC authorisation (`S_RFC`) or a wrong name.

## Reading back: BAPI_DOCUMENT_GETDETAIL2

The imports are the four scalar key fields, and the exports are `DOCUMENTDATA` and `RETURN`. Any E/A message is treated as "not found" and logged, because the exact message class and number for "does not exist" depend on the system.

## Next

[Chapter 7](07-metadata-vs-originals.md) covers metadata versus original files.
