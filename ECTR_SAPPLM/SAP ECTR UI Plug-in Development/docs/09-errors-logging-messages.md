# 9. Exceptions, logging and user-friendly validation messages

> **Verified vs illustrative.** The logging (`System.Logger`), exception handling and i18n code are verified. How messages are *displayed* in the ECTR client (`UserNotifier`) and where ECTR writes logs are **TODO (SDK)**.

## Three audiences, three channels

| Audience | Channel | Content |
|---|---|---|
| Engineer at the screen | `UserNotifier` → ECTR message/dialog | Localized, specific, actionable. No stack traces, no internal ids except the incident reference. |
| Support / developer | Log (`System.Logger`) | Object id, rule ids, decision, full stack trace for errors |
| Calling code / host | Return values (`SaveDecision`, `DropOutcome`) | `allowed` / `accepted` plus the same message lines |

## Exception strategy

| Situation | Where | Handling |
|---|---|---|
| Rule XML broken | `RuleRepository` activation | Log ERROR with the exact problem, rethrow. The component is not activated and dependents stay unsatisfied. **Fail fast at startup.** |
| Business rule violated | validators, `DragDropController` | **Not an exception.** Return `Violation` / `DragDropResult` with message keys. |
| Unknown object id | reader → controller | Return `Optional.empty()`, which becomes the message `drop.unknownObject` |
| Unexpected `RuntimeException` during save | `ValidationSaveInterceptor` | Log ERROR + stack trace with incident reference `ECTR-XXXXXXXX`. Show `save.internalError` with the same reference. **Block the save (fail closed).** |
| Unexpected `RuntimeException` during drop | `ObjectBrowserDropHandler` | Same as save: incident reference, cancel the drop |
| Session cannot tell the language | `Support.safeLocale` | Log WARNING, use English |
| Missing translation key | `Messages.format` | Show `key [args]` instead of failing, so the real problem stays visible |

Why fail closed? An unchecked save puts incomplete data into SAP, where it spreads into BOMs and downstream systems. A blocked save costs the user one retry and a support ticket that carries a reference number.

```java
} catch (RuntimeException e) {
    String reference = Support.newIncidentReference();                         // e.g. ECTR-3F9A12BC
    LOG.log(System.Logger.Level.ERROR, "Validation of " + object.id() + " failed, reference " + reference, e);
    String text = new Messages(Support.safeLocale(session)).format("save.internalError", object.id(), reference);
    notifier.error(text);
    return SaveDecision.reject(List.of(text));
}
```

## Logging

- The code uses **`java.lang.System.Logger`** (JDK 9+). It needs no extra dependency or import package. By default it goes to `java.util.logging`, and the host can route it elsewhere.
- **Levels**: ERROR = needs support; WARNING = degraded but working; INFO = one line per user decision (save blocked or allowed, drop accepted or rejected); DEBUG = passed validations.
- **What not to log**: never log full property maps, which may contain confidential engineering data, and never log credentials. Log object ids and rule ids.
- **Alternatives**: if the ECTR host offers the OSGi Log Service (`org.osgi.service.log.LoggerFactory`) or SLF4J, you can switch, but keep it to one logging API per bundle. Find out from the SDK documentation where the client's log files are written.

Example log lines:

```
INFO com.example.ectr.plugin.core.osgi.RuleRepository: Rules loaded (validation: bundled default, packaging: bundled default)
INFO com.example.ectr.plugin.core.osgi.ValidationSaveInterceptor: PRT-001: 3 validation violation(s) [mandatory:WEIGHT_KG, pattern:REVISION, description-languages], save blocked
```

## Writing good validation messages

The message bundle follows these rules:

1. **Say what to do, not what failed internally.** "Field "Weight (kg)" is mandatory." Avoid things like "NullPointerException in rule 3".
2. **Use the screen's words.** Field labels come from `field.*` keys, which should match the ECTR layout labels.
3. **List everything at once.** Show all violations in one dialog, not one per save attempt.
4. **Show the expected format** for pattern rules (`example="A, B ... ZZ"`).
5. **Translate late.** Rules return `LocalizableMessage(key, args)`. Translation happens in the component, using the **session** language rather than the JVM default, and nested messages and labels are translated too.
6. **Handle plurals** with `ChoiceFormat`: `{0,choice,1#1 object|1<{0} objects}`.
7. **Quote leading spaces** in `.properties` values (`'  '- {0}`), because `java.util.Properties` strips unquoted ones.

## Next

[Chapter 10](10-testing-deployment-troubleshooting.md) covers testing, deployment and troubleshooting.
