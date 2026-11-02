// VERIFIED: plain Java. These are OUR logical property names, not SAP/ECTR attribute names.
package com.example.ectr.plugin.api.model;

/**
 * Logical property names that rules and XML configuration refer to.
 * <p>
 * The ECTR adapter maps real attributes (SAP DMS fields, material master fields,
 * CAD properties) onto these names. Keeping the mapping in one adapter class means the
 * rule XML never has to change when the backend field names do.
 */
public final class PropertyKey {

    public static final String MATERIAL_GROUP = "MATERIAL_GROUP";
    public static final String MATERIAL = "MATERIAL";
    public static final String WEIGHT_KG = "WEIGHT_KG";
    public static final String REVISION = "REVISION";
    public static final String CAD_FILE = "CAD_FILE";
    public static final String STATUS = "STATUS";
    /** On a DRAWING: id of the part or assembly the drawing documents. */
    public static final String REFERENCED_OBJECT = "REFERENCED_OBJECT";

    private PropertyKey() {
    }
}
