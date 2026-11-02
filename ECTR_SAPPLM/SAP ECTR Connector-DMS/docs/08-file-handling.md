# 8. PDF/DXF file handling and streaming

> **Verified.** Covered by `FileHandlingTest`, by `DocumentApiIntegrationTest.pdfUploadAndStreamingDownload`, and by a curl round trip (the downloaded file was byte-identical to the upload).

## Upload

```bash
curl -F "file=@caliper.pdf" http://localhost:8080/api/v1/documents/DRW/10000001/000/00/originals
```

```json
{ "fileId": "c45d1e13-…", "fileName": "caliper.pdf", "fileType": "PDF", "size": 15,
  "sha256": "1e7313ac…", "downloadUrl": "http://…/originals/c45d1e13-…", "sapCheckin": "STORED_IN_MIDDLEWARE_ONLY" }
```

Checks, in order:

1. **The document exists in SAP** (`GETDETAIL2`), otherwise 404.
2. **The file is not empty**, otherwise 415.
3. **Content detection** (`FileTypeDetector`) reads the first 64 bytes:
   - PDF starts with `%PDF-`
   - binary DXF starts with `AutoCAD Binary DXF`
   - ASCII DXF starts with group code `0` followed by `SECTION`

   The client's `Content-Type` header is **ignored**, because CAD tools often send `application/octet-stream`.
4. **The type is allowed** (`app.storage.allowed-types`), otherwise 415.
5. **The extension matches the content.** A PDF named `.dxf` is rejected (415).
6. **Size limit**: `spring.servlet.multipart.max-file-size` (default 50MB). A larger file gets 413.

Storage (`LocalFileStore`):

- The file is **streamed** to a temp file (`InputStream.transferTo`), never loaded fully into memory, and the SHA-256 is computed while copying (`DigestInputStream`).
- After a complete write, an **atomic move** puts it in place. A broken upload never leaves a half file.
- The name on disk is a **random UUID**. The client's file name is only cleaned (`safeName`) and used for `Content-Disposition`. A download id must match the UUID format, which blocks path traversal (`../../etc/passwd`).

## Download / transfer to downstream applications

```bash
curl -OJ http://localhost:8080/api/v1/documents/DRW/10000001/000/00/originals/c45d1e13-…
```

Response headers (real output):

```
Content-Type: application/pdf
ETag: "1e7313ace78f0fb481a486939b4885902663102818090805515553d84e0bbfd3"
Content-Disposition: attachment; filename*=UTF-8''caliper.pdf
```

- The body is a `FileSystemResource`, which Spring **streams**. It also handles `Range` requests (206), so viewers can load large files in parts.
- The `ETag` is the SHA-256, so receivers can check integrity and cache.
- To **push** files to a downstream system (MES, supplier portal), add an outbound client that reads `OriginalFileService.path(file)` as a stream. Never use `Files.readAllBytes` for CAD-sized files.

## Production notes

- `./data/originals` is local disk, and the index is in memory. For more than one instance, use shared or object storage (NFS, S3-compatible) and a database table for the index.
- Run a virus scanner on uploads if files come from outside the company network.
- Add a retention and clean-up job once the files have been handed over to SAP or downstream systems.

## Next

[Chapter 9](09-errors-timeouts-monitoring.md) covers errors, timeouts and monitoring.
