# 2. OSGi fundamentals: bundles, lifecycle, dependencies, deployment

> **Verified vs illustrative.** Everything in this chapter is standard OSGi (Core R8 / Compendium R8) and is exercised by this project's build and Felix runtime. Whether and how *ECTR* hosts OSGi bundles is covered in [chapter 3](03-plugin-integration.md).

## Bundles

A **bundle** is a JAR with extra headers in `META-INF/MANIFEST.MF`. In this project, bnd generates those headers from the code and annotations, so nobody edits the manifest by hand.

Here is the generated manifest of `ectr-plugin-api`, shortened:

```
Bundle-SymbolicName: ectr-plugin-api
Bundle-Version: 1.0.0.202610230744
Export-Package: com.example.ectr.plugin.api.model;version="1.0.0",
                com.example.ectr.plugin.api.port;version="1.0.0";
                uses:="com.example.ectr.plugin.api.model"
Require-Capability: osgi.ee;filter:="(&(osgi.ee=JavaSE)(version=17))"
```

To print it yourself: `unzip -p ectr-plugin-api/target/ectr-plugin-api-1.0.0-SNAPSHOT.jar META-INF/MANIFEST.MF`

| Header | Where it comes from in this project |
|---|---|
| `Bundle-SymbolicName`, `Bundle-Version` | Maven `artifactId` and `version`. bnd turns `-SNAPSHOT` into a timestamp qualifier. |
| `Export-Package` | `@Export` + `@Version` in each `package-info.java` of the api module |
| `Import-Package` | Calculated by bnd from the classes the code actually uses |
| `Provide/Require-Capability` | DS components (`osgi.extender=osgi.component`), the Java version |

## Visibility: export and import

Each bundle has its own class loader, and a class is visible to another bundle only if its package is **exported** and **imported**.

- `ectr-plugin-api` exports `...api.model` and `...api.port`. That is the contract.
- `ectr-plugin-core` exports **nothing**. Its rules, loaders and components are private, so the host or other plug-ins cannot depend on internals.
- `ectr-plugin-mock` imports only the api packages, so it is easy to swap for a real adapter.

Semantic versioning of packages (`version="1.0.0"`, imports `[1.0,2)`) means that adding a method to a port interface is a *major* change for implementers. bnd's baselining can check this; see [chapter 10](10-testing-deployment-troubleshooting.md).

## Bundle lifecycle

```
INSTALLED ──resolve──► RESOLVED ──start──► STARTING ──► ACTIVE
    ▲                      │ ▲                              │
    └──── uninstall ◄──────┘ └─────────── stop ◄── STOPPING ◄┘
```

- **RESOLVED** means every `Import-Package` and `Require-Capability` is satisfied. If the runtime is missing a package, the bundle stays INSTALLED and `lb` shows that.
- **ACTIVE** means started. This project has **no `BundleActivator`**. All behaviour comes from Declarative Services.

## Declarative Services (DS)

DS (Felix SCR in our runtime) creates objects ("components") and wires them together through the service registry, based on annotations.

```java
@Component(service = SaveInterceptor.class, configurationPid = ValidationSaveInterceptor.PID)
@Designate(ocd = ValidationSaveInterceptor.Config.class)
public final class ValidationSaveInterceptor implements SaveInterceptor {

    @Activate
    public ValidationSaveInterceptor(@Reference RuleRepository rules,
                                     @Reference EctrSession session,
                                     @Reference UserNotifier notifier,
                                     Config config) { ... }
```

`ectr-plugin-core/.../osgi/ValidationSaveInterceptor.java` has the full class. The main points:

- **Constructor injection** (DS 1.4+). The class is immutable, and unit tests simply call `new ValidationSaveInterceptor(...)` without starting any framework.
- **Static, mandatory references.** The component starts only when all of its services exist. `scr:info <name>` shows which reference is missing.
- **Configuration** through a Metatype-annotated `@interface Config`. Defaults live in the code. Values can be overridden through Configuration Admin under the PID.

Component graph at runtime:

| Component (bundle) | Injected into its `@Activate` constructor | Provides service |
|---|---|---|
| `RuleRepository` (core) | only its `Config`; loads the rule XML | `RuleRepository` |
| `ValidationSaveInterceptor` (core) | `RuleRepository`, `EctrSession`, `UserNotifier`, `Config` | `SaveInterceptor` |
| `ObjectBrowserDropHandler` (core) | `ObjectPropertyReader`, `RuleRepository`, `EctrSession`, `UserNotifier` | `DropHandler` |
| `MockObjectRepository` (mock) | – | `ObjectPropertyReader` |
| `MockEctrSession` (mock) | its `Config` | `EctrSession` |
| `ConsoleNotifier` (mock) | – | `UserNotifier` |
| `EctrShellCommands` (mock "host") | `MockObjectRepository`, `MockEctrSession`, **all** `SaveInterceptor` and `DropHandler` services (cardinality `MULTIPLE`) | Gogo commands `ectr:*` |

```
mock ── ObjectPropertyReader, EctrSession, UserNotifier ──► core      (core uses them)
mock ◄── SaveInterceptor, DropHandler ────────────────────── core      (the host calls them)
```

`scr:list` shows these components as **SATISFIED** right after start-up. They are *delayed* components: SCR creates each object the first time its service is used. That is why the log line `Rules loaded (...)` appears only with the first `ectr:save` or `ectr:drop`.

## Deployment, in OSGi terms

Deploying means *install bundle JARs into a framework and start them*. This project does that in three ways:

1. **Executable jar.** `ectr-plugin-runtime` resolves `ectr.bndrun` and exports `target/ectr.jar`, which contains Felix and all bundles.
2. **bnd-run.** `mvnw -pl ectr-plugin-runtime -am package bnd-run:run` starts the same runtime from Maven.
3. **Manual.** In any OSGi shell: `install file:/path/ectr-plugin-core-1.0.0-SNAPSHOT.jar`, then `start <id>`.

The bnd **resolver** reads the requirements in `ectr.bndrun` (core, mock, Gogo) and computes the full `-runbundles` list, including SCR and its `org.osgi.util.*` dependencies. If a requirement cannot be met, the build fails and tells you which one.

## Next

[Chapter 3](03-plugin-integration.md) explains how these bundles connect to ECTR.
