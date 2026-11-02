# 7. Implementing assembly packaging rules

> **Verified vs illustrative.** The engine, the rules and the XML loader are verified by `PackagingRuleEngineTest` and the Felix demo. The *business meaning* of the three sample rules is illustrative. Replace them with the client's real packaging policy.

## Engine

`PackagingRuleEngine.plan(rootId)` works in three steps:

1. **Collect**
   - Walk the CAD structure from `ObjectPropertyReader`.
   - Record every **leaf component**, with its total quantity multiplied along the path. For example, 2 × SUB-110 containing 3 × P gives 6 × P.
   - Record every **drawing** of the root, its sub-assemblies and its components.
   - If an object cannot be read, skip it and add a warning.
2. **Apply the rules**, sorted by phase. The sort is stable, so rules in the same phase keep their XML order.
   - `GROUP`: put components into packages
   - `ENRICH`: add related objects to packages
   - `SPLIT`: reshape packages once their content is final
3. **Catch everything left over.** Anything no rule placed goes into `UNASSIGNED`, so nothing is lost silently.

Sorting by phase means a misordered XML file cannot, for example, split packages before the drawings were added.

## Sample rules

| id | Phase | Behaviour |
|---|---|---|
| `group-by-material-group` | GROUP | One package `MG-<group>` per `MATERIAL_GROUP`. A missing group gives a warning and the component ends up in `UNASSIGNED`. |
| `include-drawings` | ENRICH | Each drawing joins the package of its `REFERENCED_OBJECT`. Drawings of the assembly or sub-assemblies go to `DOCUMENTATION`. |
| `max-components-per-package` | SPLIT | Packages with more than `max` entries become `NAME-1`, `NAME-2`, ... |

Configuration, in `rules/packaging-rules.xml`:

```xml
<packaging-rules>
  <rule id="group-by-material-group"/>
  <rule id="include-drawings"/>
  <rule id="max-components-per-package" max="3"/>
</packaging-rules>
```

## Try it

```
g! ectr:drop FLD-PACK ASM-100
[user INFO ] Packaging plan for ASM-100: 6 packages.
                Package MG-CAST: PRT-101, PRT-111, DRW-101
                Package MG-MACHINED: PRT-102
                Package MG-SEAL: PRT-103, PRT-104
                Package MG-FASTENER-1: PRT-105, PRT-106, PRT-112
                Package MG-FASTENER-2: PRT-113
                Package DOCUMENTATION: DRW-100, DRW-110
```

## Writing a new rule

Example: keep safety-relevant parts in their own package. This is illustrative, and the `SAFETY_CLASS` key does not exist yet.

```java
public final class SafetyPartsRule implements PackagingRule {
    public static final String ID = "separate-safety-parts";
    public String id() { return ID; }
    public Phase phase() { return Phase.GROUP; }   // runs together with other GROUP rules, in XML order

    public void apply(PackagingContext ctx) {
        ctx.components().values().stream()
           .filter(c -> c.property("SAFETY_CLASS").isPresent())
           .filter(c -> !ctx.isPackaged(c.id()))
           .forEach(c -> ctx.addToPackage("SAFETY", c.id()));
    }
}
```

Then:

1. Add `case SafetyPartsRule.ID -> new SafetyPartsRule();` in `PackagingRulesLoader`.
2. List it **before** `group-by-material-group` in the XML, so it claims the parts first.
3. Add the key to `PropertyKey`, to the adapter mapping, and to a test.

## What a plan is (and is not)

`PackagingPlan` is a **proposal**: a map from package name to object ids, plus warnings. Persisting it in SAP (for example as documents, a BOM variant or a handling-unit proposal) is a separate step. That step must use whatever SAP objects and APIs the client's process defines. Agree it with the SAP functional team; it is **not** implemented here.

## Next

[Chapter 8](08-xml-configuration.md) covers XML configuration.
