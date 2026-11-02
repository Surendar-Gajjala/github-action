// VERIFIED: standard OSGi Declarative Services. The DropHandler port it implements is ILLUSTRATIVE:
// a real ECTR adapter must call it from the Object Browser's actual drop hook.
package com.example.ectr.plugin.core.osgi;

import com.example.ectr.plugin.api.port.DropEvent;
import com.example.ectr.plugin.api.port.DropHandler;
import com.example.ectr.plugin.api.port.DropOutcome;
import com.example.ectr.plugin.api.port.EctrSession;
import com.example.ectr.plugin.api.port.ObjectPropertyReader;
import com.example.ectr.plugin.api.port.UserNotifier;
import com.example.ectr.plugin.core.dnd.DragDropController;
import com.example.ectr.plugin.core.dnd.DragDropResult;
import com.example.ectr.plugin.core.i18n.Messages;
import com.example.ectr.plugin.core.packaging.PackagingRuleEngine;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;

/** Applies the drag-and-drop business rules and reports the outcome to the user. */
@Component(service = DropHandler.class)
public final class ObjectBrowserDropHandler implements DropHandler {

    private static final System.Logger LOG = System.getLogger(ObjectBrowserDropHandler.class.getName());

    private final DragDropController controller;
    private final EctrSession session;
    private final UserNotifier notifier;

    @Activate
    public ObjectBrowserDropHandler(@Reference ObjectPropertyReader reader,
                                    @Reference RuleRepository rules,
                                    @Reference EctrSession session,
                                    @Reference UserNotifier notifier) {
        this.controller = new DragDropController(reader, rules.validator(),
                new PackagingRuleEngine(reader, rules.packagingRules()));
        this.session = session;
        this.notifier = notifier;
    }

    @Override
    public DropOutcome onDrop(DropEvent event) {
        try {
            DragDropResult result = controller.handle(event);
            Messages messages = new Messages(Support.safeLocale(session));
            List<String> lines = result.messages().stream().map(messages::format).toList();
            String text = String.join(System.lineSeparator(), lines);

            LOG.log(System.Logger.Level.INFO, "Drop {0} -> {1} ({2}): {3}", event.sourceIds(), event.targetId(),
                    event.action(), result.accepted() ? "accepted" : "rejected");
            if (result.accepted()) {
                notifier.info(text);
            } else {
                notifier.warn(text);
            }
            return new DropOutcome(result.accepted(), lines);
        } catch (RuntimeException e) {
            String reference = Support.newIncidentReference();
            LOG.log(System.Logger.Level.ERROR, "Drop " + event + " failed, reference " + reference, e);
            String text = new Messages(Support.safeLocale(session)).format("drop.internalError", reference);
            notifier.error(text);
            return new DropOutcome(false, List.of(text));
        }
    }
}
