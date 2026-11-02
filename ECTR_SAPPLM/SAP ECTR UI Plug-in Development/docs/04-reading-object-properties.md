# 4. Reading engineering object properties and metadata

> **Verified vs illustrative.** `ObjectPropertyReader`, the model classes and their use in core are verified. The mock implementation (`MockObjectRepository`) is **illustrative**. Real data access through the ECTR SDK / PlmConnector API is **TODO (SDK)**; see [chapter 3](03-plugin-integration.md).

## The port

```java
public interface ObjectPropertyReader {
    Optional<EngineeringObject> read(String objectId);   // empty if not found
    CadStructureNode structure(String rootObjectId);     // leaf node for a part
    List<String> findDrawingsFor(String objectId);       // drawings documenting the object
}
```

## The model

```java
public record EngineeringObject(String id, ObjectType type,
                                Map<String, String> properties,   // logical key → value
                                LocalizedText description) {      // multilingual text
    public Optional<String> property(String key) { ... }          // blank counts as missing
}
```

Design choices:

- **Logical property keys** (`PropertyKey.MATERIAL_GROUP`, `WEIGHT_KG`, ...) instead of SAP field names. Rule XML, messages and tests all use these keys. Only the adapter knows the real attribute names.
- **Strings only.** The adapter converts SAP values to text, and the rules check formats with regex. This keeps the model independent of SAP data types. Add typed accessors later if rules need arithmetic.
- **Immutable snapshot.** Rules see one consistent state even if the UI changes the object meanwhile.
- **`LocalizedText`** stores descriptions per language, normalised to the language only (`de_DE` → `de`). `getOrFallback(locale, ENGLISH)` picks the right text for the UI.

## Using it in core

```java
// PackagingRuleEngine: read root and structure, resolve every component
EngineeringObject root = reader.read(rootId)
        .orElseThrow(() -> new NoSuchElementException("Object not found: " + rootId));
CadStructureNode structure = reader.structure(rootId);
for (String drawingId : reader.findDrawingsFor(node.objectId())) { ... }
```

```java
// DragDropController: validate everything inside an assembly before accepting it
ids.addAll(reader.structure(source.id()).allObjectIds());
```

## Try it

```
g! ectr:show ASM-100
ASM-100 (ASSEMBLY)
  description[en] = Brake caliper assembly
  description[de] = Bremssattel-Baugruppe
  REVISION = A
  CAD_FILE = asm-100.asm
  structure:
    1 x PRT-101
    2 x PRT-102
    ...
    1 x SUB-110
        1 x PRT-111
        2 x PRT-112
  drawings: DRW-100
```

## Rules for the real adapter

1. **Return `Optional.empty()`** for unknown ids. Do not return null, and do not throw.
2. **Be thread-safe.** DS can call the adapter from several threads.
3. **Batch backend calls.** Reading a 500-part structure one RFC at a time is slow. The SDK may offer structure or bulk reads; use them.
4. **Cache carefully.** Cache only for the duration of one user action, so validation never runs against stale data.
5. **Map types explicitly.** Unknown ECTR object types should map to something with no rules, or be logged, rather than silently becoming a `PART`.

## Next

[Chapter 5](05-mandatory-field-validation.md) covers mandatory-field validation before save.
