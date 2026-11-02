// VERIFIED: plain Java.
package com.example.ectr.plugin.core.dnd;

import com.example.ectr.plugin.core.i18n.LocalizableMessage;
import com.example.ectr.plugin.core.packaging.PackagingPlan;

import java.util.List;

/**
 * Decision about a drop, before translation.
 *
 * @param accepted whether the host should carry out the drop
 * @param messages untranslated messages for the user
 * @param plans    packaging plans created by the drop (empty unless dropped onto a folder)
 */
public record DragDropResult(boolean accepted, List<LocalizableMessage> messages, List<PackagingPlan> plans) {

    public DragDropResult {
        messages = List.copyOf(messages);
        plans = List.copyOf(plans);
    }

    static DragDropResult reject(List<LocalizableMessage> messages) {
        return new DragDropResult(false, messages, List.of());
    }

    static DragDropResult reject(LocalizableMessage message) {
        return reject(List.of(message));
    }
}
