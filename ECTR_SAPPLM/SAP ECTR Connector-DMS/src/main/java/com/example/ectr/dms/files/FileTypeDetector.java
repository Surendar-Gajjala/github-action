// VERIFIED: plain Java.
package com.example.ectr.dms.files;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognises a file kind from its <b>content</b> (first bytes) and checks that the file name extension
 * agrees. The client-supplied Content-Type header is ignored, because callers often send
 * application/octet-stream or a wrong value.
 */
@Component
public class FileTypeDetector {

    /** Bytes needed by {@link #detect}. */
    public static final int HEAD_SIZE = 64;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DXF_BINARY_MAGIC = "AutoCAD Binary DXF".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern DXF_ASCII_START = Pattern.compile("^\\s*0\\s*\\r?\\n\\s*SECTION\\b");

    public Optional<FileKind> detect(byte[] head) {
        if (startsWith(head, PDF_MAGIC)) {
            return Optional.of(FileKind.PDF);
        }
        if (startsWith(head, DXF_BINARY_MAGIC)) {
            return Optional.of(FileKind.DXF);
        }
        String text = new String(head, StandardCharsets.ISO_8859_1);
        if (DXF_ASCII_START.matcher(text).find()) {
            return Optional.of(FileKind.DXF);
        }
        return Optional.empty();
    }

    public boolean extensionMatches(String fileName, FileKind kind) {
        if (fileName == null) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return kind.extensions().contains(extension);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
