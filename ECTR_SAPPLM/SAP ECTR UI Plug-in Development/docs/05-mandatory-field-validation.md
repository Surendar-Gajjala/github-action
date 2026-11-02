# 5. Mandatory-field validation before save

> **Verified vs illustrative.** Rules, loader, validator and the DS component are verified by unit tests and the Felix runtime. The point where ECTR calls `SaveInterceptor` is **TODO (SDK)**. In this project, the mock command `ectr:save` plays the host.

## Flow

```
host "save" ──► SaveInterceptor.beforeSave(obj)         (ValidationSaveInterceptor)
                    │
                    ├─ RuleRepository.validator().validate(obj)
                    │      └─ RuleSet.rulesFor(obj.type()) → each ValidationRule.check(obj)
                    │
                    ├─ valid?   → SaveDecision.allow()
                    ├─ invalid? → localized message list → UserNotifier.error(...)
                    │             → SaveDecision.reject(lines)   (or allow + warn, see config)
                    └─ crash?   → incident reference, log stack trace → reject (fail closed)
```

## Rules

| Rule | XML | Fails when |
|---|---|---|
| `MandatoryFieldRule` | `<mandatory property="WEIGHT_KG"/>` | value missing, empty or whitespace |
| `PatternRule` | `<pattern property="REVISION" regex="[A-Z]{1,2}" example="A, B ... ZZ"/>` | value present but the *whole* value does not match |
| `RequiredLanguagesRule` | `<description-languages required="en,de"/>` | description missing or blank in a listed language |

`PatternRule` ignores empty values on purpose. Combine it with `mandatory` when the field is required. That way the user gets one clear message ("mandatory") instead of two.

The default rules are in `ectr-plugin-core/src/main/resources/rules/validation-rules.xml`:

```xml
<object-type name="PART">
  <mandatory property="MATERIAL_GROUP"/>
  <mandatory property="MATERIAL"/>
  <mandatory property="WEIGHT_KG"/>
  <pattern   property="WEIGHT_KG" regex="\d{1,6}(\.\d{1,3})?" example="12.5"/>
  <mandatory property="REVISION"/>
  <pattern   property="REVISION" regex="[A-Z]{1,2}" example="A, B ... ZZ"/>
  <description-languages required="en,de"/>
</object-type>
```

The validator reports **all** violations at once, so the user can fix everything in one go.

## Adding a new rule type

1. Implement `ValidationRule`: a stateless class with an `id()` and a `check(obj)` that returns `Optional<Violation>`.
2. Add a message key to `messages.properties` **and** `messages_de.properties`. `MessagesTest.germanBundleHasEveryEnglishKey` fails if you forget the German one.
3. Add a `case` in `RuleSetLoader.parseRule` for the new XML element.
4. Write a unit test next to `ValidationRulesTest`.

## Configuration

| PID | Property | Default | Effect |
|---|---|---|---|
| `com.example.ectr.plugin.validation` | `blockOnViolation` | `true` | `false` lets users save with warnings. This is useful for a soft roll-out. |
| `com.example.ectr.plugin.rules` | `validationRulesFile` | empty | Absolute path to an external rules file that replaces the bundled one |

## Try it

```
g! ectr:save PRT-001
[user ERROR] Cannot save part PRT-001. Please complete the following:
                - Field "Weight (kg)" is mandatory.
                - Field "Revision" has the value "a", but the expected format is: A, B ... ZZ.
                - The description is missing in language "de".
=> SAVE OF PRT-001 CANCELLED
g! ectr:save PRT-002
=> PRT-002 SAVED (simulated)
```

## Unit-test example

```java
@Test
void incompleteObjectIsBlockedWithReadableMessage() {
    SaveDecision decision = interceptor(RuleRepository.bundledDefaults(), Locale.ENGLISH, true)
            .beforeSave(incompletePart());

    assertFalse(decision.allowed());
    assertEquals("Cannot save part PRT-9. Please complete the following:", decision.messages().get(0));
    assertTrue(decision.messages().contains("  - Field \"Material group\" is mandatory."));
}
```

The full class is `ectr-plugin-core/src/test/java/.../osgi/ValidationSaveInterceptorTest.java`. It also covers German messages, warning mode and fail-closed behaviour.

## Next

[Chapter 6](06-drag-and-drop.md) covers drag-and-drop in the Object Browser.
