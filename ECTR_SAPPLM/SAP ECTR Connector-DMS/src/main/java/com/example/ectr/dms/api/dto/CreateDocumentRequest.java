// VERIFIED: Bean Validation (Jakarta Validation 3). Lengths follow the SAP DMS key fields
// (DOKAR 3, DOKNR 25, DOKTL 3, DOKVR 2) and the 40-character short text; confirm them for your system.
package com.example.ectr.dms.api.dto;

import com.example.ectr.dms.domain.DocumentMetadata;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** JSON body of POST /api/v1/documents. */
public record CreateDocumentRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9]{1,3}", message = "must be 1-3 upper-case letters or digits")
        String documentType,

        @Size(max = 25) @Pattern(regexp = "[A-Z0-9_-]*", message = "must contain only A-Z, 0-9, _ or -")
        String documentNumber,

        @Pattern(regexp = "[A-Z0-9]{0,3}", message = "must be up to 3 upper-case letters or digits")
        String documentPart,

        @Pattern(regexp = "[A-Z0-9]{0,2}", message = "must be up to 2 upper-case letters or digits")
        String documentVersion,

        @NotBlank @Size(max = 40)
        String description,

        @Size(max = 3)
        String laboratory,

        @Valid @Size(max = 20)
        List<DescriptionDto> descriptions,

        @Valid @Size(max = 50)
        List<ObjectLinkDto> objectLinks) {

    public record DescriptionDto(
            @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO code, e.g. EN") String languageIso,
            @NotBlank @Size(max = 40) String description) {
    }

    public record ObjectLinkDto(@NotBlank @Size(max = 10) String objectType,
                                @NotBlank @Size(max = 50) String objectKey) {
    }

    public DocumentMetadata toDomain() {
        return new DocumentMetadata(documentType, documentNumber, documentPart, documentVersion, description,
                laboratory,
                descriptions == null ? List.of()
                        : descriptions.stream().map(d -> new DocumentMetadata.Description(d.languageIso(), d.description())).toList(),
                objectLinks == null ? List.of()
                        : objectLinks.stream().map(l -> new DocumentMetadata.ObjectLink(l.objectType(), l.objectKey())).toList());
    }
}
