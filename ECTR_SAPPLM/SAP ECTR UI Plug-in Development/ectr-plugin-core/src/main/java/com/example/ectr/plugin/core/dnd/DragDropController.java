// VERIFIED: plain Java. Receives drops as our DropEvent; converting native ECTR/Swing drag data
// into a DropEvent is the adapter's job (ILLUSTRATIVE, see docs/06-drag-and-drop.md).
package com.example.ectr.plugin.core.dnd;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.model.ObjectType;
import com.example.ectr.plugin.api.port.DropEvent;
import com.example.ectr.plugin.api.port.ObjectPropertyReader;
import com.example.ectr.plugin.core.i18n.LocalizableMessage;
import com.example.ectr.plugin.core.i18n.LocalizableMessage.TypeLabel;
import com.example.ectr.plugin.core.packaging.PackagingPlan;
import com.example.ectr.plugin.core.packaging.PackagingRuleEngine;
import com.example.ectr.plugin.core.validation.MetadataValidator;
import com.example.ectr.plugin.core.validation.ValidationResult;
import com.example.ectr.plugin.core.validation.Violation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Business rules for drops in the Object Browser:
 * <ul>
 *   <li><b>Assembly onto a FOLDER</b>: validate the whole structure; if it is complete,
 *       create a packaging plan. Only assemblies may be dropped onto folders.</li>
 *   <li><b>Any object onto an ASSEMBLY</b>: validate the dropped objects (and their structures)
 *       and refuse drops that would create a cycle.</li>
 *   <li><b>Onto a PART or DRAWING</b>: always refused.</li>
 * </ul>
 */
public final class DragDropController {

    private final ObjectPropertyReader reader;
    private final MetadataValidator validator;
    private final PackagingRuleEngine packagingEngine;

    public DragDropController(ObjectPropertyReader reader, MetadataValidator validator,
                              PackagingRuleEngine packagingEngine) {
        this.reader = reader;
        this.validator = validator;
        this.packagingEngine = packagingEngine;
    }

    public DragDropResult handle(DropEvent event) {
        Optional<EngineeringObject> target = reader.read(event.targetId());
        if (target.isEmpty()) {
            return DragDropResult.reject(LocalizableMessage.of("drop.unknownObject", event.targetId()));
        }
        List<EngineeringObject> sources = new ArrayList<>();
        for (String id : event.sourceIds()) {
            Optional<EngineeringObject> source = reader.read(id);
            if (source.isEmpty()) {
                return DragDropResult.reject(LocalizableMessage.of("drop.unknownObject", id));
            }
            sources.add(source.get());
        }

        return switch (target.get().type()) {
            case FOLDER -> dropOntoFolder(target.get(), sources);
            case ASSEMBLY -> dropOntoAssembly(target.get(), sources);
            case PART, DRAWING -> DragDropResult.reject(LocalizableMessage.of("drop.unsupportedTarget",
                    new TypeLabel(target.get().type()), target.get().id()));
        };
    }

    private DragDropResult dropOntoFolder(EngineeringObject folder, List<EngineeringObject> sources) {
        for (EngineeringObject source : sources) {
            if (source.type() != ObjectType.ASSEMBLY) {
                return DragDropResult.reject(LocalizableMessage.of("drop.onlyAssembliesToFolder",
                        folder.id(), source.id(), new TypeLabel(source.type())));
            }
        }
        List<LocalizableMessage> problems = validateStructures(sources);
        if (!problems.isEmpty()) {
            return DragDropResult.reject(problems);
        }
        List<PackagingPlan> plans = new ArrayList<>();
        List<LocalizableMessage> messages = new ArrayList<>();
        for (EngineeringObject assembly : sources) {
            PackagingPlan plan = packagingEngine.plan(assembly.id());
            plans.add(plan);
            messages.add(LocalizableMessage.of("drop.packagingCreated", assembly.id(), plan.packageCount()));
            plan.packages().forEach((name, ids) ->
                    messages.add(LocalizableMessage.of("drop.packageLine", name, String.join(", ", ids))));
            messages.addAll(plan.warnings());
        }
        return new DragDropResult(true, messages, plans);
    }

    private DragDropResult dropOntoAssembly(EngineeringObject assembly, List<EngineeringObject> sources) {
        for (EngineeringObject source : sources) {
            if (source.id().equals(assembly.id()) || reader.structure(source.id()).allObjectIds().contains(assembly.id())) {
                return DragDropResult.reject(LocalizableMessage.of("drop.cycle", source.id(), assembly.id()));
            }
        }
        List<LocalizableMessage> problems = validateStructures(sources);
        if (!problems.isEmpty()) {
            return DragDropResult.reject(problems);
        }
        return new DragDropResult(true,
                List.of(LocalizableMessage.of("drop.linkAccepted", sources.size(), assembly.id())), List.of());
    }

    /** Validates each source and, for assemblies, every object in its structure. */
    private List<LocalizableMessage> validateStructures(List<EngineeringObject> sources) {
        Set<String> ids = new LinkedHashSet<>();
        for (EngineeringObject source : sources) {
            ids.addAll(source.type() == ObjectType.ASSEMBLY
                    ? reader.structure(source.id()).allObjectIds()
                    : List.of(source.id()));
        }
        List<LocalizableMessage> messages = new ArrayList<>();
        for (String id : ids) {
            Optional<EngineeringObject> object = reader.read(id);
            if (object.isEmpty()) {
                messages.add(LocalizableMessage.of("drop.unknownObject", id));
                continue;
            }
            ValidationResult result = validator.validate(object.get());
            if (!result.isValid()) {
                messages.add(LocalizableMessage.of("drop.invalidSource", id));
                for (Violation v : result.violations()) {
                    messages.add(LocalizableMessage.of("drop.violationLine", id, v.message()));
                }
            }
        }
        return messages;
    }
}
