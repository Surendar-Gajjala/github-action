// VERIFIED: Gogo shell command registration via the standard osgi.command.* service properties.
// ILLUSTRATIVE: the commands simulate what the ECTR client would do (save, drag-and-drop).
package com.example.ectr.plugin.mock;

import com.example.ectr.plugin.api.model.CadStructureNode;
import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.port.DropAction;
import com.example.ectr.plugin.api.port.DropEvent;
import com.example.ectr.plugin.api.port.DropHandler;
import com.example.ectr.plugin.api.port.DropOutcome;
import com.example.ectr.plugin.api.port.SaveDecision;
import com.example.ectr.plugin.api.port.SaveInterceptor;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Gogo commands that play the role of the ECTR client:
 * <pre>
 * ectr:list                         list demo objects
 * ectr:show  ASM-100                properties, descriptions and structure
 * ectr:save  PRT-001                run every SaveInterceptor, as the client would before saving
 * ectr:drop  FLD-PACK ASM-100       drop ASM-100 onto FLD-PACK (action COPY)
 * ectr:drop  ASM-200 PRT-002,PRT-001 LINK
 * ectr:lang  de                     switch the UI language
 * ectr:demo                         run a scripted walkthrough
 * </pre>
 */
@Component(service = Object.class, property = {
        "osgi.command.scope=ectr",
        "osgi.command.function=list",
        "osgi.command.function=show",
        "osgi.command.function=save",
        "osgi.command.function=drop",
        "osgi.command.function=lang",
        "osgi.command.function=demo"
})
public final class EctrShellCommands {

    private final MockObjectRepository repository;
    private final MockEctrSession session;
    private final List<SaveInterceptor> saveInterceptors;
    private final List<DropHandler> dropHandlers;

    @Activate
    public EctrShellCommands(@Reference MockObjectRepository repository,
                             @Reference MockEctrSession session,
                             @Reference(cardinality = ReferenceCardinality.MULTIPLE) List<SaveInterceptor> saveInterceptors,
                             @Reference(cardinality = ReferenceCardinality.MULTIPLE) List<DropHandler> dropHandlers) {
        this.repository = repository;
        this.session = session;
        this.saveInterceptors = saveInterceptors;
        this.dropHandlers = dropHandlers;
    }

    public void list() {
        for (EngineeringObject o : repository.all()) {
            System.out.printf("%-9s %-9s %s%n", o.id(), o.type(),
                    o.description().getOrFallback(session.locale(), Locale.ENGLISH).orElse(""));
        }
    }

    public void show(String id) {
        Optional<EngineeringObject> object = repository.read(id);
        if (object.isEmpty()) {
            System.out.println("Not found: " + id);
            return;
        }
        EngineeringObject o = object.get();
        System.out.println(o.id() + " (" + o.type() + ")");
        o.description().values().forEach((lang, text) -> System.out.println("  description[" + lang + "] = " + text));
        o.properties().forEach((k, v) -> System.out.println("  " + k + " = " + v));
        CadStructureNode structure = repository.structure(id);
        if (!structure.isLeaf()) {
            System.out.println("  structure:");
            printTree(structure, "    ");
        }
        List<String> drawings = repository.findDrawingsFor(id);
        if (!drawings.isEmpty()) {
            System.out.println("  drawings: " + String.join(", ", drawings));
        }
    }

    public void save(String id) {
        Optional<EngineeringObject> object = repository.read(id);
        if (object.isEmpty()) {
            System.out.println("Not found: " + id);
            return;
        }
        if (saveInterceptors.isEmpty()) {
            System.out.println("No SaveInterceptor registered - is the core bundle active? Try scr:list");
        }
        boolean allowed = true;
        for (SaveInterceptor interceptor : saveInterceptors) {
            SaveDecision decision = interceptor.beforeSave(object.get());
            allowed &= decision.allowed();
        }
        System.out.println(allowed ? "=> " + id + " SAVED (simulated)" : "=> SAVE OF " + id + " CANCELLED");
    }

    public void drop(String targetId, String sourceIds) {
        drop(targetId, sourceIds, DropAction.COPY.name());
    }

    public void drop(String targetId, String sourceIds, String action) {
        DropAction dropAction;
        try {
            dropAction = DropAction.valueOf(action.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            System.out.println("Unknown action " + action + ", use one of " + Arrays.toString(DropAction.values()));
            return;
        }
        if (dropHandlers.isEmpty()) {
            System.out.println("No DropHandler registered - is the core bundle active? Try scr:list");
            return;
        }
        DropEvent event = new DropEvent(Arrays.stream(sourceIds.split(",")).map(String::trim).toList(),
                targetId, dropAction);
        boolean accepted = true;
        for (DropHandler handler : dropHandlers) {
            DropOutcome outcome = handler.onDrop(event);
            accepted &= outcome.accepted();
        }
        System.out.println(accepted ? "=> DROP EXECUTED (simulated)" : "=> DROP CANCELLED");
    }

    public void lang(String languageTag) {
        session.setLocale(Locale.forLanguageTag(languageTag));
        System.out.println("UI language is now " + session.locale().getLanguage());
    }

    public void demo() {
        Locale before = session.locale();
        try {
            step("Save a complete part", () -> save("PRT-002"));
            step("Save a part with missing metadata", () -> save("PRT-001"));
            step("Same, with German UI", () -> {
                lang("de");
                save("PRT-001");
                lang(before.getLanguage());
            });
            step("Drop a complete assembly onto the packaging folder", () -> drop("FLD-PACK", "ASM-100"));
            step("Drop an assembly with an incomplete part onto the folder", () -> drop("FLD-PACK", "ASM-200"));
            step("Link a part into an assembly", () -> drop("ASM-200", "PRT-002", "LINK"));
            step("Try to create a cycle", () -> drop("SUB-110", "ASM-100", "LINK"));
        } finally {
            session.setLocale(before);
        }
    }

    private static void step(String title, Runnable action) {
        System.out.println();
        System.out.println("---- " + title + " ----");
        action.run();
    }

    private void printTree(CadStructureNode node, String indent) {
        for (CadStructureNode child : node.children()) {
            System.out.println(indent + child.quantity() + " x " + child.objectId());
            printTree(child, indent + "    ");
        }
    }
}
