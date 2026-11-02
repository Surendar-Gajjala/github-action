# 3. How a Java plug-in integrates with SAP ECTR

> **Verified vs illustrative.** The port/adapter structure and all code in `api` and `core` are verified by the build. **How ECTR loads customer extensions, and which hooks exist for "before save" and "drop", is not verified here.** We had no ECTR SDK. Those points are marked **TODO (SDK)** and must be confirmed in the documentation for the customer's ECTR release.

## The approach: ports and adapters

```
                ┌───────────────── this repository ─────────────────┐
 SAP ECTR       │                                                    │
 client  ◄──────┤  ectr-plugin-sap   (TO BE WRITTEN, uses ECTR SDK)  │
 (host)         │      │ implements ObjectPropertyReader,             │
                │      │ UserNotifier, EctrSession                    │
                │      │ calls SaveInterceptor / DropHandler          │
                │      ▼                                              │
                │  ectr-plugin-api   (ports + model, exported)        │
                │      ▲                                              │
                │      │                                              │
                │  ectr-plugin-core  (rules, DS components)           │
                │                                                     │
                │  ectr-plugin-mock  (replaces ectr-plugin-sap locally)│
                └─────────────────────────────────────────────────────┘
```

Why we built it this way:

- **No guessing about ECTR APIs.** The core never imports ECTR classes, so nothing in it can be wrong about them.
- **Testable.** 63 unit tests (58 in core, 5 in mock) run in seconds on any machine.
- **Release-proof.** When ECTR changes its API between releases, only the adapter changes.

## The ports

| Port (package `com.example.ectr.plugin.api.port`) | Direction | Real adapter must… |
|---|---|---|
| `ObjectPropertyReader` | core → host | read object metadata, CAD structure and linked drawings through the ECTR SDK / PlmConnector API, and map them to `EngineeringObject` / `CadStructureNode` |
| `EctrSession` | core → host | return the logged-on user and the UI language |
| `UserNotifier` | core → host | show messages in the ECTR client's own message facility |
| `SaveInterceptor` | host → core | be **called** from ECTR's before-save hook; honour `SaveDecision.allowed()` |
| `DropHandler` | host → core | be **called** from the Object Browser drop hook; honour `DropOutcome.accepted()` |

## Adapter checklist: TODO (SDK)

Work through this list with the ECTR SDK documentation for the target release:

1. **Packaging and loading.** How does this ECTR release load customer Java extensions? Look for OSGi bundles, a plug-in folder, a registration file, or something else. Is there a required manifest header or descriptor? If ECTR is not OSGi-based for customer code, `api` and `core` still work as plain JARs: call `RuleRepository.bundledDefaults()` and construct the components with `new`, exactly as the unit tests do.
2. **Before-save hook.** Find the documented extension point that runs before an object is saved. Find out whether it can **veto** the save and how a veto is reported.
3. **Drop hook.** Find the Object Browser drag-and-drop extension point. Find out what the transfer data contains (object keys? UI nodes?) and whether a drop can be refused.
4. **Reading metadata.** Find the supported API for document/material attributes, CAD structure and document links. The project brief mentions the PlmConnector API; confirm its availability and licence.
5. **Field mapping.** Fill in the `field-mapping` section of [`config/plm_ea.sample.xml`](../config/plm_ea.sample.xml) so each logical `PropertyKey` maps to a real attribute.
6. **Messages.** Find out how a plug-in shows an info, warning or error dialog in the ECTR client.
7. **Threading.** Find out on which thread the hooks are called. Never block the UI thread with slow SAP calls; read data in advance or on a worker thread if the SDK allows it.

## Skeleton of the real adapter (ILLUSTRATIVE)

This is **pseudocode**. Every `Ectr…` name below is a placeholder for whatever the SDK really provides.

```java
// ILLUSTRATIVE PSEUDOCODE: replace every placeholder after reading the ECTR SDK documentation.
@Component(service = ObjectPropertyReader.class)
public final class SapObjectPropertyReader implements ObjectPropertyReader {

    @Override
    public Optional<EngineeringObject> read(String objectId) {
        // 1. look up the object with the SDK                (placeholder: sdk.find(objectId))
        // 2. read the attributes the rules need             (see config/plm_ea.sample.xml mapping)
        // 3. read descriptions per language
        // 4. map the ECTR object type to ObjectType         (PART / ASSEMBLY / DRAWING / FOLDER)
        // 5. return new EngineeringObject(id, type, props, new LocalizedText(descriptions));
        throw new UnsupportedOperationException("TODO (SDK)");
    }
    ...
}

// Wherever the SDK offers a before-save hook:
//   SaveDecision d = saveInterceptor.beforeSave(mapToEngineeringObject(nativeObject));
//   if (!d.allowed()) { veto the save the way the SDK requires }
```

## Deployment set

| Environment | Bundles |
|---|---|
| Local dev / CI | api + core + mock (+ Felix, SCR, Gogo via `ectr.jar`) |
| ECTR client | api + core + **sap adapter**. Never the mock. |

## Next

[Chapter 4](04-reading-object-properties.md) covers reading object properties through the port.
