// VERIFIED: file validation, storage and streaming.
// ILLUSTRATIVE / TODO (SAP): files are kept in the middleware only. Checking an original into SAP
// (KPro / content server / vault) needs system-specific storage configuration and is NOT done here.
// See docs/07-metadata-vs-originals.md for the options.
package com.example.ectr.dms.files;

import com.example.ectr.dms.config.IntegrationProperties;
import com.example.ectr.dms.domain.DocumentKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** Receives PDF/DXF originals for a document and serves them to downstream applications. */
@Service
public class OriginalFileService {

    private static final Logger LOG = LoggerFactory.getLogger(OriginalFileService.class);

    private final LocalFileStore store;
    private final FileTypeDetector detector;
    private final Set<FileKind> allowed;
    /** In-memory index document key to files. For production, keep it in a database. */
    private final Map<DocumentKey, List<StoredFile>> index = new ConcurrentHashMap<>();

    public OriginalFileService(LocalFileStore store, FileTypeDetector detector, IntegrationProperties properties) {
        this.store = store;
        this.detector = detector;
        this.allowed = properties.storage().allowedTypes().stream()
                .map(t -> FileKind.valueOf(t.trim().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Validates and stores a file. The caller must have checked that the document exists. */
    public StoredFile store(DocumentKey key, MultipartFile upload) {
        if (upload.isEmpty()) {
            throw new InvalidFileException("The file is empty");
        }
        FileKind kind = detectKind(upload);
        if (!allowed.contains(kind)) {
            throw new InvalidFileException("File type " + kind + " is not accepted. Allowed: " + allowed);
        }
        if (!detector.extensionMatches(upload.getOriginalFilename(), kind)) {
            throw new InvalidFileException("File name '" + safeName(upload.getOriginalFilename())
                    + "' does not match its content (" + kind + ")");
        }
        try (InputStream in = upload.getInputStream()) {
            LocalFileStore.Written written = store.write(in);
            StoredFile file = new StoredFile(written.fileId(), safeName(upload.getOriginalFilename()), kind,
                    written.size(), written.sha256(), Instant.now());
            index.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(file);
            LOG.info("Stored {} original {} for {} ({} bytes, sha256 {})", kind, file.fileId(), key, file.size(),
                    file.sha256());
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store upload", e);
        }
    }

    public List<StoredFile> list(DocumentKey key) {
        return new ArrayList<>(index.getOrDefault(key, List.of()));
    }

    public Optional<StoredFile> find(DocumentKey key, String fileId) {
        return index.getOrDefault(key, List.of()).stream().filter(f -> f.fileId().equals(fileId)).findFirst();
    }

    public Path path(StoredFile file) {
        return store.path(file.fileId());
    }

    private FileKind detectKind(MultipartFile upload) {
        try (InputStream in = upload.getInputStream()) {
            byte[] head = in.readNBytes(FileTypeDetector.HEAD_SIZE);
            return detector.detect(head).orElseThrow(() -> new InvalidFileException(
                    "Unrecognised file content. Only " + allowed + " files are accepted."));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
    }

    /** Keeps only the last path segment and harmless characters (for headers and logs). */
    static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String last = name.replace('\\', '/');
        last = last.substring(last.lastIndexOf('/') + 1);
        String cleaned = last.replaceAll("[^A-Za-z0-9._ -]", "_");
        return cleaned.isBlank() ? "file" : cleaned;
    }
}
