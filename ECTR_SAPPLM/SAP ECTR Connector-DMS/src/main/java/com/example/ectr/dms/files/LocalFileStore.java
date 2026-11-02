// VERIFIED: plain Java NIO. Streams uploads to disk (no full file in memory), computes SHA-256 on the
// fly, and never uses client-supplied names as paths.
package com.example.ectr.dms.files;

import com.example.ectr.dms.config.IntegrationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stores original files in a local directory. */
@Component
public class LocalFileStore {

    private static final Pattern FILE_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path root;

    @Autowired
    public LocalFileStore(IntegrationProperties properties) {
        this(properties.storage().root());
    }

    /** For tests. */
    LocalFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + this.root, e);
        }
    }

    /** Result of {@link #write}: generated id, size and checksum. */
    public record Written(String fileId, long size, String sha256) {
    }

    /** Writes the stream to a temp file first and moves it into place only when complete. */
    public Written write(InputStream content) throws IOException {
        String fileId = UUID.randomUUID().toString();
        Path temp = Files.createTempFile(root, "upload-", ".part");
        try {
            MessageDigest digest = sha256();
            long size;
            try (InputStream in = new DigestInputStream(content, digest);
                 OutputStream out = Files.newOutputStream(temp)) {
                size = in.transferTo(out);
            }
            Files.move(temp, root.resolve(fileId), StandardCopyOption.ATOMIC_MOVE);
            return new Written(fileId, size, HexFormat.of().formatHex(digest.digest()));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Resolves a stored file. The id must look like a UUID, which rules out path traversal
     * ({@code ../../etc/passwd}).
     */
    public Path path(String fileId) {
        if (fileId == null || !FILE_ID.matcher(fileId).matches()) {
            throw new IllegalArgumentException("Invalid file id");
        }
        return root.resolve(fileId);
    }

    public void delete(String fileId) throws IOException {
        Files.deleteIfExists(path(fileId));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
