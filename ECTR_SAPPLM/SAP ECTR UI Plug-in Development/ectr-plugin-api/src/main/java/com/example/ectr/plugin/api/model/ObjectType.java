// VERIFIED: plain Java. Logical object categories used by our rules, not ECTR object type codes.
package com.example.ectr.plugin.api.model;

/**
 * Logical categories of engineering objects that the plug-in's rules work with.
 * The adapter for a real ECTR system decides how ECTR/SAP object types (for example
 * a DMS document type or a material type) map onto these values.
 */
public enum ObjectType {
    PART,
    ASSEMBLY,
    DRAWING,
    /** A container in the Object Browser that acts as a packaging target. */
    FOLDER
}
