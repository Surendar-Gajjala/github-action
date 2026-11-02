// VERIFIED: plain Java.
package com.example.ectr.plugin.core.packaging;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.core.i18n.LocalizableMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Working state while one assembly is being packaged. Not thread-safe; one instance per run.
 */
public final class PackagingContext {

    private final EngineeringObject root;
    private final Map<String, EngineeringObject> components;
    private final Map<String, Integer> quantities;
    private final Map<String, EngineeringObject> drawings;
    private final LinkedHashMap<String, List<String>> packages = new LinkedHashMap<>();
    private final List<LocalizableMessage> warnings = new ArrayList<>();

    PackagingContext(EngineeringObject root,
                     Map<String, EngineeringObject> components,
                     Map<String, Integer> quantities,
                     Map<String, EngineeringObject> drawings) {
        this.root = root;
        this.components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
        this.quantities = Collections.unmodifiableMap(new LinkedHashMap<>(quantities));
        this.drawings = Collections.unmodifiableMap(new LinkedHashMap<>(drawings));
    }

    public EngineeringObject root() {
        return root;
    }

    /** Leaf components of the structure, in structure order. */
    public Map<String, EngineeringObject> components() {
        return components;
    }

    /** Total quantity of each component in the whole assembly (multiplied along the path). */
    public int quantity(String componentId) {
        return quantities.getOrDefault(componentId, 1);
    }

    /** Drawings documenting the root, its sub-assemblies, or its components. */
    public Map<String, EngineeringObject> drawings() {
        return drawings;
    }

    /** Live view of packages: package name to object ids. Rules may modify it. */
    public LinkedHashMap<String, List<String>> packages() {
        return packages;
    }

    public void addToPackage(String packageName, String objectId) {
        packages.computeIfAbsent(packageName, k -> new ArrayList<>()).add(objectId);
    }

    public Optional<String> packageOf(String objectId) {
        return packages.entrySet().stream()
                .filter(e -> e.getValue().contains(objectId))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public boolean isPackaged(String objectId) {
        return packageOf(objectId).isPresent();
    }

    public void warn(LocalizableMessage message) {
        warnings.add(message);
    }

    List<LocalizableMessage> warnings() {
        return warnings;
    }
}
