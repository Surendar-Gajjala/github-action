# 6. Handling drag-and-drop in the Object Browser

> **Verified vs illustrative.** `DragDropController`, `ObjectBrowserDropHandler` and their tests are verified. **Capturing the native drop in the ECTR Object Browser is TODO (SDK).** We do not know which ECTR hook delivers drop events or what its transfer data looks like. The adapter must convert it into our `DropEvent`.

## From UI gesture to business rule

```
ECTR Object Browser drop            (native UI; TODO (SDK))
        │  adapter extracts object keys + action
        ▼
DropEvent(sourceIds, targetId, action)        ← our port type
        │
        ▼
DropHandler.onDrop(event)                     (ObjectBrowserDropHandler, DS component)
        │
        ▼
DragDropController.handle(event)              (pure Java business rules)
        │
        ├─ accepted → DropOutcome(true, messages)   → host performs the drop
        └─ rejected → DropOutcome(false, messages)  → host cancels the drop
```

If the ECTR client's UI is Swing-based, the adapter would typically sit in a `TransferHandler`-style callback. That is an **assumption to verify**. Whatever the mechanism, keep the adapter thin: read the object keys, build a `DropEvent`, call the handler, and honour `accepted()`.

## Business rules (DragDropController)

| Target | Sources | Result |
|---|---|---|
| FOLDER | only ASSEMBLY | Validate every object in each structure. If all are valid, build a **packaging plan** ([chapter 7](07-assembly-packaging.md)). |
| FOLDER | PART / DRAWING | Rejected: `drop.onlyAssembliesToFolder` |
| ASSEMBLY | PART or ASSEMBLY | Rejected if it would create a **cycle** (target is in the source's structure, or source = target). Otherwise validate the sources and their structures, then accept. |
| PART, DRAWING | any | Rejected: `drop.unsupportedTarget` |
| unknown id | | Rejected: `drop.unknownObject` |

Every rejection includes the reason. A refused drop with no explanation is the most common complaint about UI customisations.

## Try it

```
g! ectr:drop FLD-PACK ASM-200
[user WARN ] PRT-202 has incomplete metadata and cannot be used here:
                - PRT-202: Field "Material group" is mandatory.
                - PRT-202: The description is missing in language "de".
=> DROP CANCELLED

g! ectr:drop SUB-110 ASM-100 LINK
[user WARN ] ASM-100 cannot be added to SUB-110, because SUB-110 is already part of the structure of ASM-100.
=> DROP CANCELLED

g! ectr:drop ASM-200 PRT-002 LINK
[user INFO ] 1 object can be added to assembly ASM-200.
=> DROP EXECUTED (simulated)
```

## Unit-test example

```java
@Test
void droppingParentIntoOwnChildIsRejectedAsCycle() {
    DragDropResult result = controller.handle(new DropEvent(List.of("ASM-TARGET"), "ASM-OK", DropAction.LINK));
    assertFalse(result.accepted());
    assertEquals(List.of("drop.cycle"), keys(result));
}
```

Tests check **message keys**, not English text, so changing a translation never breaks a test. All 9 cases are in `DragDropControllerTest`.

## Performance note

Drop handlers run while the user is waiting, so the validation reads the whole structure. For very large assemblies the adapter should:

- use bulk reads (see [chapter 4](04-reading-object-properties.md)), and/or
- accept the drop immediately and run packaging in the background, if the SDK allows asynchronous work and the business accepts it.

## Next

[Chapter 7](07-assembly-packaging.md) covers the assembly packaging rules.
