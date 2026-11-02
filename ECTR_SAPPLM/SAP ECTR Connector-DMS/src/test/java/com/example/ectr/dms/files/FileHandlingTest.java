package com.example.ectr.dms.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileHandlingTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void detectsPdfByMagicNumber() {
        assertThat(detector.detect(ascii("%PDF-1.7\n..."))).contains(FileKind.PDF);
    }

    @Test
    void detectsAsciiAndBinaryDxf() {
        assertThat(detector.detect(ascii("  0\r\nSECTION\r\n  2\r\nHEADER"))).contains(FileKind.DXF);
        assertThat(detector.detect(ascii("AutoCAD Binary DXF\r\n\u001a\u0000"))).contains(FileKind.DXF);
    }

    @Test
    void rejectsOtherContent() {
        assertThat(detector.detect(ascii("hello"))).isEmpty();
        assertThat(detector.detect(new byte[0])).isEmpty();
        assertThat(detector.detect(ascii("PK\u0003\u0004"))).isEmpty(); // zip / docx
    }

    @Test
    void extensionMustMatchKind() {
        assertThat(detector.extensionMatches("drawing.PDF", FileKind.PDF)).isTrue();
        assertThat(detector.extensionMatches("drawing.dxf", FileKind.PDF)).isFalse();
        assertThat(detector.extensionMatches("noextension", FileKind.PDF)).isFalse();
    }

    @Test
    void storeWritesContentAndChecksum(@TempDir Path dir) throws Exception {
        LocalFileStore store = new LocalFileStore(dir);
        LocalFileStore.Written written = store.write(new ByteArrayInputStream(ascii("abc")));

        assertThat(written.size()).isEqualTo(3);
        // SHA-256("abc"), standard test vector
        assertThat(written.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Files.readString(store.path(written.fileId()))).isEqualTo("abc");
        try (var files = Files.list(dir)) {
            assertThat(files).hasSize(1); // no leftover temp file
        }
    }

    @Test
    void pathTraversalIsRejected(@TempDir Path dir) {
        LocalFileStore store = new LocalFileStore(dir);
        assertThatThrownBy(() -> store.path("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.path(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsafeFileNamesAreCleaned() {
        assertThat(OriginalFileService.safeName("C:\\temp\\..\\evil\r\nname.pdf")).isEqualTo("evil__name.pdf");
        assertThat(OriginalFileService.safeName(null)).isEqualTo("file");
    }
}
