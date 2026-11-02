// VERIFIED: Spring MVC REST controller.
package com.example.ectr.dms.api;

import com.example.ectr.dms.api.dto.CreateDocumentRequest;
import com.example.ectr.dms.api.dto.Responses.CreateDocumentResponse;
import com.example.ectr.dms.api.dto.Responses.DocumentDetailResponse;
import com.example.ectr.dms.api.dto.Responses.DocumentKeyDto;
import com.example.ectr.dms.api.dto.Responses.OriginalFileResponse;
import com.example.ectr.dms.domain.DocumentKey;
import com.example.ectr.dms.files.OriginalFileService;
import com.example.ectr.dms.files.StoredFile;
import com.example.ectr.dms.service.DocumentService;
import com.example.ectr.dms.service.TransactionRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Document API.
 * <pre>
 * POST /api/v1/documents                                        create metadata (DIR) in SAP
 * GET  /api/v1/documents/{type}/{number}/{part}/{version}       read metadata from SAP
 * POST /api/v1/documents/{key}/originals      (multipart)       upload a PDF/DXF original
 * GET  /api/v1/documents/{key}/originals                        list originals
 * GET  /api/v1/documents/{key}/originals/{fileId}               download (streamed, supports Range)
 * </pre>
 */
@Validated
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final String KEY_PATH = "/{type}/{number}/{part}/{version}";

    private final DocumentService documents;
    private final OriginalFileService originals;

    public DocumentController(DocumentService documents, OriginalFileService originals) {
        this.documents = documents;
        this.originals = originals;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateDocumentResponse> create(
            @Valid @RequestBody CreateDocumentRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false)
            @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]*") String idempotencyKey) {

        DocumentService.CreateOutcome outcome = documents.createDocument(request.toDomain(), idempotencyKey);
        TransactionRecord tx = outcome.transaction();
        boolean replayed = outcome.replayed();
        CreateDocumentResponse body = new CreateDocumentResponse(tx.id(), tx.state().name(),
                DocumentKeyDto.of(tx.documentKey()), replayed);

        if (tx.documentKey() == null) {
            // Replay of a transaction that has not finished yet.
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .location(URI.create("/api/v1/transactions/" + tx.id())).body(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path(KEY_PATH)
                .buildAndExpand(keyArgs(tx.documentKey())).toUri();
        return ResponseEntity.status(replayed ? HttpStatus.OK : HttpStatus.CREATED).location(location).body(body);
    }

    @GetMapping(KEY_PATH)
    public DocumentDetailResponse get(@PathVariable String type, @PathVariable String number,
                                      @PathVariable String part, @PathVariable String version) {
        DocumentKey key = new DocumentKey(type, number, part, version);
        return documents.getDocument(key).map(DocumentDetailResponse::of)
                .orElseThrow(() -> new NotFoundException("Document " + key + " not found in SAP"));
    }

    @PostMapping(path = KEY_PATH + "/originals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OriginalFileResponse> upload(@PathVariable String type, @PathVariable String number,
                                                       @PathVariable String part, @PathVariable String version,
                                                       @RequestPart("file") MultipartFile file) {
        DocumentKey key = new DocumentKey(type, number, part, version);
        documents.getDocument(key).orElseThrow(() -> new NotFoundException("Document " + key + " not found in SAP"));
        StoredFile stored = originals.store(key, file);
        URI download = ServletUriComponentsBuilder.fromCurrentRequest().path("/{fileId}")
                .buildAndExpand(stored.fileId()).toUri();
        return ResponseEntity.created(download).body(OriginalFileResponse.of(stored, download.toString()));
    }

    @GetMapping(KEY_PATH + "/originals")
    public List<OriginalFileResponse> list(@PathVariable String type, @PathVariable String number,
                                           @PathVariable String part, @PathVariable String version) {
        DocumentKey key = new DocumentKey(type, number, part, version);
        String base = ServletUriComponentsBuilder.fromCurrentRequest().toUriString();
        return originals.list(key).stream().map(f -> OriginalFileResponse.of(f, base + "/" + f.fileId())).toList();
    }

    /** Streams the file from disk. Spring adds Range / 206 support for {@link Resource} bodies. */
    @GetMapping(KEY_PATH + "/originals/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String type, @PathVariable String number,
                                             @PathVariable String part, @PathVariable String version,
                                             @PathVariable String fileId) {
        DocumentKey key = new DocumentKey(type, number, part, version);
        StoredFile file = originals.find(key, fileId)
                .orElseThrow(() -> new NotFoundException("File " + fileId + " not found for " + key));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.kind().contentType()))
                .contentLength(file.size())
                .eTag("\"" + file.sha256() + "\"")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(originals.path(file)));
    }

    private static Object[] keyArgs(DocumentKey key) {
        return new Object[] {key.documentType(), key.documentNumber(), key.documentPart(), key.documentVersion()};
    }
}
