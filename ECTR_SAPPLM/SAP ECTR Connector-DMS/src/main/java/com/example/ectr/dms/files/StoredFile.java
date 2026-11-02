// VERIFIED: plain Java.
package com.example.ectr.dms.files;

import java.time.Instant;

/**
 * An original file received by the middleware.
 *
 * @param fileId       random id, also the file name on disk (never derived from user input)
 * @param originalName file name sent by the client (for Content-Disposition only)
 * @param kind         detected file kind
 * @param size         bytes
 * @param sha256       hex checksum, used as ETag and for integrity checks downstream
 * @param receivedAt   time of upload
 */
public record StoredFile(String fileId, String originalName, FileKind kind, long size, String sha256, Instant receivedAt) {
}
