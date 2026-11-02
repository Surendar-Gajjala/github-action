# ECTR UI Plug-in: Metadata Validation, Drag-and-Drop, Assembly Packaging

A Java 17 OSGi plug-in project for SAP Engineering Control Center (ECTR). It covers:

- checking mandatory metadata before an engineering object is saved
- reacting to drag-and-drop in the Object Browser
- building assembly packaging plans from configurable business rules
- showing user messages in several languages (English and German included)

It builds and runs **without SAP ECTR**. Every point where real ECTR code would be called sits behind a small interface (a "port"). In this repository those ports are implemented by an in-memory mock.

> **Verified vs illustrative.** Java, OSGi, Declarative Services, bnd, Felix and JUnit code is standard and verified by the build. Anything that stands for the SAP ECTR API is marked `ILLUSTRATIVE` in the file header. No ECTR package names, classes, event names or `plm_ea.xml` keys are invented. Where real ECTR code must go, the guide says so and lists what to look up.

## Quick start

Requirements: JDK 17. Maven is optional because the wrapper downloads it on first use, which needs internet access.

```powershell
# Windows
.\mvnw.cmd verify
java -jar ectr-plugin-runtime\target\ectr.jar
```
```bash
# Linux/macOS/Git Bash
./mvnw verify
java -jar ectr-plugin-runtime/target/ectr.jar
```

At the `g!` prompt:

```
lb                         # all bundles should be Active
scr:list                   # all components should be SATISFIED
ectr:demo                  # scripted walkthrough of every feature
ectr:save PRT-001          # blocked: missing weight, bad revision, no German text
ectr:lang de               # switch messages to German
ectr:drop FLD-PACK ASM-100 # packaging plan for the brake caliper assembly
stop 0                     # quit
```

## Layout

| Module | Bundle contents | ECTR dependency |
|---|---|---|
| `ectr-plugin-api` | Exported model (`EngineeringObject`, `CadStructureNode`, `LocalizedText`) and ports (`ObjectPropertyReader`, `SaveInterceptor`, `DropHandler`, `UserNotifier`, `EctrSession`) | none |
| `ectr-plugin-core` | Validation rules, packaging engine, drag-and-drop rules, i18n, DS components, default rule XML | none |
| `ectr-plugin-mock` | **ILLUSTRATIVE** in-memory host: sample data, console notifier, session, Gogo commands | none (it *simulates* ECTR) |
| `ectr-plugin-runtime` | `ectr.bndrun` → `target/ectr.jar` (Felix + SCR + Gogo + the bundles) | none |
| `config/` | `plm_ea.sample.xml`, a requirements sheet for UI configuration (**not** real `plm_ea.xml` syntax) | none |
| `docs/` | The step-by-step guide | none |

## Guide

1. [ECTR architecture in SAP PLM and CAD integration](docs/01-ectr-architecture.md)
2. [OSGi fundamentals](docs/02-osgi-fundamentals.md)
3. [How the plug-in integrates with ECTR](docs/03-plugin-integration.md)
4. [Reading engineering object properties](docs/04-reading-object-properties.md)
5. [Mandatory-field validation before save](docs/05-mandatory-field-validation.md)
6. [Drag-and-drop in the Object Browser](docs/06-drag-and-drop.md)
7. [Assembly packaging rules](docs/07-assembly-packaging.md)
8. [XML configuration, layouts and descriptors](docs/08-xml-configuration.md)
9. [Exceptions, logging and user messages](docs/09-errors-logging-messages.md)
10. [Testing, deployment and troubleshooting](docs/10-testing-deployment-troubleshooting.md)

## Moving to a real ECTR system

1. Get the ECTR SDK / customer API documentation for the customer's exact ECTR release.
2. Add a new module, `ectr-plugin-sap`, that implements `ObjectPropertyReader`, `UserNotifier` and `EctrSession` with the real API. It also calls `SaveInterceptor` and `DropHandler` from the host's real hooks.
3. Deploy `ectr-plugin-api`, `ectr-plugin-core` and `ectr-plugin-sap`. **Do not deploy `ectr-plugin-mock`.**

Chapter 3 has the full checklist.
