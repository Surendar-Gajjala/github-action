# 10. Testing, deployment and troubleshooting

> **Verified vs illustrative.** All commands below were run against this project (JDK 17, Maven 3.9.9, bnd 7.0.0, Felix 7.0.5, SCR 2.2.10). The deployment steps for a real ECTR client are **TODO (SDK)**.

## Test pyramid

| Level | What | Command |
|---|---|---|
| Unit (JUnit 5) | Rules, loaders, engine, drag-and-drop rules, messages, the DS save component | `mvnw test` |
| Bundle checks | bnd checks imports and exports and generates DS/Metatype XML. A bad import fails the build. | `mvnw package` |
| Resolve | The bnd resolver proves all bundles plus Felix/SCR/Gogo resolve together | `mvnw verify` (runtime module) |
| Runtime smoke test | Start Felix and run `ectr:demo` | `java -jar ectr-plugin-runtime/target/ectr.jar` |
| Integration in ECTR | Adapter + real hooks + test SAP client | TODO (SDK) |

Current unit tests: **63** (58 in core, 5 in mock).

### Example: testing a rule from XML

```java
@Test
void bundledDefaultReportsEveryMissingField() {
    MetadataValidator validator = new MetadataValidator(RuleSetLoader.load(""));
    EngineeringObject incomplete = new EngineeringObject("P2", ObjectType.PART,
            Map.of(PropertyKey.REVISION, "a1"), LocalizedText.of("en", "Bracket"));

    ValidationResult result = validator.validate(incomplete);

    assertEquals(List.of("mandatory:MATERIAL_GROUP", "mandatory:MATERIAL", "mandatory:WEIGHT_KG",
                         "pattern:REVISION", "description-languages"),
                 result.violations().stream().map(Violation::ruleId).toList());
}
```

Conventions:

- Test against **rule ids and message keys**, not English text. The exception is `MessagesTest`, which tests the texts themselves.
- Use `TestFixtures.FakeReader` (core tests) for structures. Core tests deliberately do not depend on the mock bundle.
- Use one test per rule branch and per XML error case.

### Smoke test without typing

```bash
(sleep 5; printf 'scr:list\nectr:demo\n'; sleep 6; printf 'stop 0\n') | java -jar ectr-plugin-runtime/target/ectr.jar
```

The `sleep 5` gives SCR time to start. Commands sent earlier fail with `CommandNotFoundException`.

## Build and run

```powershell
.\mvnw.cmd verify                                   # build, test, resolve, export ectr.jar
java -jar ectr-plugin-runtime\target\ectr.jar       # start local runtime
.\mvnw.cmd -pl ectr-plugin-runtime -am package bnd-run:run   # alternative: run from Maven
```

`mvnw verify` may rewrite `-runbundles` in `ectr.bndrun` when dependency versions change. Commit that change.

## Deploying into ECTR: checklist (TODO (SDK))

1. Build with `mvnw verify`.
2. Take `ectr-plugin-api-*.jar`, `ectr-plugin-core-*.jar` and your **adapter** jar. **Never** take `ectr-plugin-mock`.
3. Install them the way the ECTR SDK documentation for the target release describes: plug-in folder, OSGi install, registration file, or another mechanism.
4. Provide the external rule files and configuration, if used (see [chapter 8](08-xml-configuration.md)).
5. Check the Java version: the bundles require **Java 17** (`osgi.ee` capability). If the ECTR client runs an older JVM, lower `maven.compiler.release` and remove the Java 17 language features (records, switch expressions, pattern matching). That is a significant change, so confirm the JVM version first.
6. Smoke test in a test client: one blocked save, one allowed save, one packaging drop.
7. Roll out with `blockOnViolation=false` first if the business wants a warning-only period.

## Troubleshooting

| Symptom | Likely cause | What to check or do |
|---|---|---|
| `lb` shows a bundle **Installed**, not Active | Unresolved `Import-Package` / `Require-Capability` | `start <id>`: Felix prints the missing requirement. `headers <id>` shows what the bundle imports. Usually a missing api bundle or the wrong Java version. |
| Bundle Active but `ectr:save` says "No SaveInterceptor registered" | DS component not satisfied | `scr:list`, then `scr:info com.example.ectr.plugin.core.osgi.ValidationSaveInterceptor`. Look for the missing reference (`EctrSession`, `UserNotifier`, `RuleRepository`). |
| `RuleRepository` not satisfied, log says "Validation rules not loaded: …" | Broken or missing rule XML | Fix the file named in the message. Unknown elements and bad regex are rejected on purpose (chapter 8). |
| Nothing from DS works at all | SCR not running | `lb` must show *Apache Felix Declarative Services* Active. It also needs `org.osgi.util.promise` and `org.osgi.util.function`. |
| `Command not found: ectr:…` | Mock bundle not running, or the command was sent before SCR finished starting | Check `lb`, wait, and try again |
| `ClassNotFoundException` for an api class in the adapter | Adapter imports a package that is not exported, or a version mismatch | Check `Import-Package` in the adapter manifest against `Export-Package` of the api bundle |
| `ClassCastException` between identical class names | The same package is loaded from two bundles (for example, api classes embedded in the adapter jar) | Do not embed api classes. Import them. |
| Messages in English although the UI is German | `EctrSession.locale()` returns the wrong value | Check the adapter. The JVM default locale is deliberately **ignored**. |
| Umlauts garbled in the console (`erg�nzen`) | Console code page, not the bundle | `chcp 65001` and `java -Dstdout.encoding=UTF-8 -jar ...`. The ECTR UI itself is not affected. |
| Translation shows a key like `save.blocked [..]` | Key missing in the language file | Add it. `MessagesTest` catches missing German keys. |
| Resolver fails during `mvnw verify` | A requirement in `ectr.bndrun` cannot be satisfied | Read the "Unresolved requirements" block. Add the missing bundle as a dependency of `ectr-plugin-runtime`. |
| Felix keeps old bundle versions | Bundle cache | `org.osgi.framework.storage.clean=onFirstInit` is set. Otherwise delete `felix-cache/`. |

## Release hygiene

- Bump the **package version** in `package-info.java` when you change a port. Adding a method to an interface that the adapter implements is a major change for implementers.
- Optionally enable bnd **baselining** (`bnd-baseline-maven-plugin`) once a 1.0.0 release exists in a repository. It then fails builds that change the API without the right version bump.
