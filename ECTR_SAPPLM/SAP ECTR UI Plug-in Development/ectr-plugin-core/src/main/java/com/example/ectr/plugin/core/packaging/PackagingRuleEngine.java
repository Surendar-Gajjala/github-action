// VERIFIED: plain Java. Reads data only through the ObjectPropertyReader port.
package com.example.ectr.plugin.core.packaging;

import com.example.ectr.plugin.api.model.CadStructureNode;
import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.port.ObjectPropertyReader;
import com.example.ectr.plugin.core.i18n.LocalizableMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Builds a {@link PackagingPlan} for an assembly:
 * <ol>
 *   <li>read the CAD structure and resolve every leaf component and related drawing,</li>
 *   <li>run the configured rules ordered by phase (GROUP, ENRICH, SPLIT),</li>
 *   <li>put anything no rule placed into package {@value #UNASSIGNED}.</li>
 * </ol>
 */
public final class PackagingRuleEngine {

    public static final String UNASSIGNED = "UNASSIGNED";

    private final ObjectPropertyReader reader;
    private final List<PackagingRule> rules;

    public PackagingRuleEngine(ObjectPropertyReader reader, List<PackagingRule> rules) {
        this.reader = reader;
        // List.sort is stable, so rules in the same phase keep their configured order.
        List<PackagingRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparing(PackagingRule::phase));
        this.rules = List.copyOf(sorted);
    }

    /**
     * @throws NoSuchElementException if the root object does not exist
     */
    public PackagingPlan plan(String rootId) {
        EngineeringObject root = reader.read(rootId)
                .orElseThrow(() -> new NoSuchElementException("Object not found: " + rootId));
        CadStructureNode structure = reader.structure(rootId);

        Map<String, EngineeringObject> components = new LinkedHashMap<>();
        Map<String, Integer> quantities = new LinkedHashMap<>();
        Map<String, EngineeringObject> drawings = new LinkedHashMap<>();
        List<LocalizableMessage> readWarnings = new ArrayList<>();

        collect(structure, 1, true, rootId, components, quantities, drawings, readWarnings);

        PackagingContext context = new PackagingContext(root, components, quantities, drawings);
        readWarnings.forEach(context::warn);
        rules.forEach(rule -> rule.apply(context));

        components.keySet().stream().filter(id -> !context.isPackaged(id))
                .forEach(id -> context.addToPackage(UNASSIGNED, id));
        drawings.keySet().stream().filter(id -> !context.isPackaged(id))
                .forEach(id -> context.addToPackage(UNASSIGNED, id));

        return new PackagingPlan(rootId, context.packages(), context.warnings());
    }

    private void collect(CadStructureNode node, int parentQuantity, boolean isRoot, String rootId,
                         Map<String, EngineeringObject> components, Map<String, Integer> quantities,
                         Map<String, EngineeringObject> drawings, List<LocalizableMessage> warnings) {
        int quantity = isRoot ? 1 : parentQuantity * node.quantity();

        for (String drawingId : reader.findDrawingsFor(node.objectId())) {
            if (!drawings.containsKey(drawingId)) {
                read(drawingId, rootId, warnings).ifPresent(d -> drawings.put(drawingId, d));
            }
        }

        if (node.isLeaf() && !isRoot) {
            Optional<EngineeringObject> component = components.containsKey(node.objectId())
                    ? Optional.of(components.get(node.objectId()))
                    : read(node.objectId(), rootId, warnings);
            component.ifPresent(c -> {
                components.putIfAbsent(c.id(), c);
                quantities.merge(c.id(), quantity, Integer::sum);
            });
            return;
        }
        for (CadStructureNode child : node.children()) {
            collect(child, quantity, false, rootId, components, quantities, drawings, warnings);
        }
    }

    private Optional<EngineeringObject> read(String id, String rootId, List<LocalizableMessage> warnings) {
        Optional<EngineeringObject> object = reader.read(id);
        if (object.isEmpty()) {
            warnings.add(LocalizableMessage.of("packaging.unknownObject", id, rootId));
        }
        return object;
    }
}
