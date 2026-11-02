# 7. Document key, metadata, original file and version

> **Verified vs illustrative.** The distinctions below are standard SAP DMS concepts. This service creates **metadata in SAP** and stores **originals in the middleware**. Checking originals into SAP storage is **not implemented**, because it depends on the customer's storage set-up. The options are listed below (check with the SAP basis/DMS team).

## Four different things

| Concept | Example | Where it lives | In this project |
|---|---|---|---|
| **Document key** | `DRW / 10000001 / 000 / 00` = type / number / part / version | SAP (DRAW table key) | `DocumentKey` record, the URL path segments |
| **Document metadata** | short text, texts per language, status, laboratory, object links, classification | SAP DIR | `DocumentMetadata` → `BAPI_DOCUMENT_CREATE2` |
| **Original file content** | `caliper.pdf`, `caliper.dxf`, the native CAD model | Content server / vault / archive, *referenced* from the DIR | `OriginalFileService` stores files locally with a SHA-256 checksum |
| **Document version** | `00`, `01`, … the last part of the key | SAP. A new version is a new DIR with its own metadata and originals. | `documentVersion` field |

Rules worth remembering:

- **Creating metadata ≠ uploading the file.** After `BAPI_DOCUMENT_CREATE2` + commit, the DIR exists even if no file was ever checked in.
- **A version is not a file revision.** Uploading a corrected PDF to the same DIR replaces or adds an original. A *new version* creates a new DIR version, usually after a status or change process.
- The **document part** splits one document into separately managed pieces. Most CAD integrations use `000`.

## Two-step flow in this service

```
1. POST /api/v1/documents                                   -> DIR created and committed in SAP (metadata)
2. POST /api/v1/documents/DRW/10000001/000/00/originals     -> PDF/DXF validated and stored (original)
   response: "sapCheckin": "STORED_IN_MIDDLEWARE_ONLY"
3. GET  /api/v1/documents/DRW/10000001/000/00/originals/{fileId}  -> downstream apps fetch the file
```

Step 2 checks that the DIR exists in SAP first, which means a `GETDETAIL2` call, so a file is never attached to a document that does not exist.

## Options for checking originals into SAP (TODO, system-specific)

| Option | How | Needs |
|---|---|---|
| Files in `BAPI_DOCUMENT_CREATE2` (table `DOCUMENTFILES`) or `BAPI_DOCUMENT_CHECKIN2` | Pass a storage category and a file path that the SAP **application server** can read | Shared file system or an FTP/HTTP RFC destination; KPro storage category set up |
| SAP Content Server via HTTP | Upload to the content server, then link to the DIR | Content server, storage category, certificates |
| ECTR / CAD client checks in the native file | The middleware only handles metadata and the downstream PDF/DXF | Client-side check-in through ECTR |

Pick one with the SAP DMS and basis team. After that, implement it as a new port next to `OriginalFileService`, and change `sapCheckin` to report the real result.

## Next

[Chapter 8](08-file-handling.md) covers PDF/DXF file handling.
