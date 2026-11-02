// ILLUSTRATIVE port. The host calls this when the user drops objects in the Object Browser.
package com.example.ectr.plugin.api.port;

/**
 * Hook for drag-and-drop gestures in the Object Browser.
 * <p>
 * In a real ECTR client the adapter registers with whatever drop hook the SDK provides and
 * converts the native transfer data to a {@link DropEvent}. See docs/06-drag-and-drop.md.
 */
public interface DropHandler {

    DropOutcome onDrop(DropEvent event);
}
