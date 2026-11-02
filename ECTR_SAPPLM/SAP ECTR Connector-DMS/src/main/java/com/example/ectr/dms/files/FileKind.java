// VERIFIED: plain Java. Magic numbers: PDF files start with "%PDF-"; binary DXF starts with
// "AutoCAD Binary DXF"; ASCII DXF starts with group code 0 followed by "SECTION".
package com.example.ectr.dms.files;

import java.util.List;

/** Original file kinds the service accepts. */
public enum FileKind {
    PDF("application/pdf", List.of("pdf")),
    DXF("image/vnd.dxf", List.of("dxf"));

    private final String contentType;
    private final List<String> extensions;

    FileKind(String contentType, List<String> extensions) {
        this.contentType = contentType;
        this.extensions = extensions;
    }

    public String contentType() {
        return contentType;
    }

    public List<String> extensions() {
        return extensions;
    }
}
