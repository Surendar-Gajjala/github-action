// VERIFIED: plain Java enum used by the ILLUSTRATIVE DropHandler port.
package com.example.ectr.plugin.api.port;

/** What the user asked for with the drag gesture. Mirrors the common Swing/AWT DnD actions. */
public enum DropAction {
    COPY,
    MOVE,
    LINK
}
