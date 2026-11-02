# 1. SAP ECTR, SAP DMS, SAP JCo, RFC and BAPI

> **Verified vs illustrative.** These are general, publicly documented concepts. Version-specific details are marked "check": what a BAPI accepts, which DMS settings apply, and the JCo release. Check them against SAP Help and your own system.

| Term | What it is | Role in this project |
|---|---|---|
| **SAP ECTR** (Engineering Control Center) | Desktop client that connects CAD authoring tools to SAP PLM: documents, materials, BOMs, changes. | One possible caller. A custom ECTR connector, or any CAD app, sends documents to this service. The service does **not** use ECTR APIs. |
| **SAP DMS** (Document Management System) | Part of SAP PLM. It stores **document info records (DIR)**: metadata, status, object links, and references to original files. | Target system. We create DIRs and read them back. |
| **RFC** (Remote Function Call) | SAP's protocol for calling *RFC-enabled function modules* from outside. Calls can be stateless or stateful. | Transport between this service and SAP. |
| **BAPI** (Business API) | Standardised, released, RFC-enabled function modules for business objects (`BAPI_<OBJECT>_<METHOD>`). They report problems in a **RETURN** parameter (type BAPIRET2) instead of throwing exceptions. Most BAPIs do **not commit**. | `BAPI_DOCUMENT_CREATE2`, `BAPI_DOCUMENT_GETDETAIL2`, `BAPI_TRANSACTION_COMMIT`, `BAPI_TRANSACTION_ROLLBACK` |
| **SAP JCo** (Java Connector) | SAP's Java library (`sapjco3.jar` plus a native library) for RFC calls. You download it from SAP; it is not on Maven Central. | Used by `JcoRfcClient` in profile `jco` |

## Rules that drive the design

1. **A BAPI call is not saved until it is committed.** `BAPI_DOCUMENT_CREATE2` posts its changes into the current logical unit of work (LUW). The document exists only after `BAPI_TRANSACTION_COMMIT` runs in the **same** session. In JCo that means a stateful context: `JCoContext.begin/end`.
2. **Errors arrive as data.** A RETURN of type `E` or `A` means failure, even though the RFC call itself "succeeded". The code must check RETURN after every BAPI, including the commit.
3. **Metadata and file are separate.** A DIR is metadata. The CAD file or PDF ("original") is stored separately, for example on a content server, and is linked to the DIR. See [chapter 7](07-metadata-vs-originals.md).
4. **The RFC user needs authorisations.** It needs DMS authorisation objects for the document types and activities used. The exact objects and values are defined by the customer's SAP security team (check).

## Next

[Chapter 2](02-architecture.md) covers the architecture.
