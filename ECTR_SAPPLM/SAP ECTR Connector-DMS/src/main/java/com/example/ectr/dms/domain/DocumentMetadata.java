// VERIFIED: plain Java. Neutral description of a document to create; mapped to BAPI parameters
// by DocumentBapiMapper.
package com.example.ectr.dms.domain;

import java.util.List;

/**
 * Metadata for a new document info record. This is metadata only: the original file is a
 * separate step (see docs/07-metadata-vs-originals.md).
 *
 * @param documentType    required
 * @param documentNumber  {@code null} or blank = let SAP assign a number (internal number range)
 * @param documentPart    {@code null} or blank = SAP default
 * @param documentVersion {@code null} or blank = SAP default
 * @param description     short text in the logon language (max 40 characters)
 * @param laboratory      optional laboratory/design office
 * @param descriptions    additional short texts per language
 * @param objectLinks     links to other SAP objects, for example a material
 */
public record DocumentMetadata(String documentType,
                               String documentNumber,
                               String documentPart,
                               String documentVersion,
                               String description,
                               String laboratory,
                               List<Description> descriptions,
                               List<ObjectLink> objectLinks) {

    public DocumentMetadata {
        descriptions = descriptions == null ? List.of() : List.copyOf(descriptions);
        objectLinks = objectLinks == null ? List.of() : List.copyOf(objectLinks);
    }

    /** Short text in one language (ISO 639-1 code, for example EN, DE). */
    public record Description(String languageIso, String text) {
    }

    /** Link to another SAP object. Object type values (for example MARA for materials) are system-specific. */
    public record ObjectLink(String objectType, String objectKey) {
    }
}
