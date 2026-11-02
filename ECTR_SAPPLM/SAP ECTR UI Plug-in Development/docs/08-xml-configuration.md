# 8. XML configuration: rules, layouts and UI component descriptors

> **Verified vs illustrative.**
> - `validation-rules.xml` and `packaging-rules.xml` are **our own formats**. They are verified by loader tests.
> - `config/plm_ea.sample.xml` is **illustrative**. It is a requirements sheet, **not** the syntax of ECTR's real `plm_ea.xml`. We have no verified schema for that file, so we do not show any of its element or attribute names. Use the customer's existing file and the ECTR documentation for their release.

## Two kinds of XML in an ECTR project

| | Plug-in rule files (ours) | ECTR configuration (SAP's) |
|---|---|---|
| Examples | `validation-rules.xml`, `packaging-rules.xml` | `plm_ea.xml` and other ECTR layout/customising files |
| Owner | plug-in team | ECTR administrator / basis team |
| Controls | what the plug-in **enforces** | what the client **displays**: fields, order, labels, menus |
| Schema | defined in `RuleSetLoader` / `PackagingRulesLoader` | defined by SAP per ECTR release |

Keep them consistent. If the layout marks a field as required, the rule file should enforce it too. Only the plug-in actually blocks the save.

## Our rule files

### Where they are read from

1. **Bundled default**: `ectr-plugin-core/src/main/resources/rules/*.xml`, packaged in the JAR.
2. **External override**, without rebuilding: configure the PID `com.example.ectr.plugin.rules`:

| Property | Example |
|---|---|
| `validationRulesFile` | `C:/ectr/plugin-config/validation-rules.xml` |
| `packagingRulesFile` | `C:/ectr/plugin-config/packaging-rules.xml` |

In a runtime with Configuration Admin (Felix ConfigAdmin, optionally with FileInstall) this is a `.cfg` / `.config` file. A change re-creates `RuleRepository`, which reloads the files. Configuration Admin is **not** part of the minimal local runtime. Add `org.apache.felix.configadmin` to `ectr.bndrun` if you want to try it.

### Safety features of the loaders

| Problem | Behaviour |
|---|---|
| Typo in an element name (`<mandatroy>`) | Loading fails, and the message names the element |
| Unknown object type or packaging rule id | Loading fails and lists the allowed values |
| Invalid regex | Loading fails and shows the regex and the reason |
| Duplicate `<object-type>` | Loading fails |
| `<!DOCTYPE>` / external entities | Rejected: the parser is hardened against XXE (`SecureXml`) |

If loading fails, `RuleRepository` does not activate, so validation and drop handling stay **off** in a visible way (`scr:info`). This is deliberate. A typo must never silently disable a check. See [chapter 10](10-testing-deployment-troubleshooting.md).

## Layouts and multilingual UI descriptors

The project brief asks for "layout properties and XML configuration files such as `plm_ea.xml`" and "multilingual component descriptors". Our split:

| Concern | Where it lives |
|---|---|
| Field **labels** in each language for plug-in messages | `messages.properties`, `messages_de.properties` (`field.*`, `type.*`) |
| Component **descriptions** in each language (data) | `EngineeringObject.description()` (`LocalizedText`), checked by `<description-languages>` |
| Field order, visibility and labels in ECTR screens | ECTR configuration such as `plm_ea.xml`: **TODO (SDK/admin)** |

### Using `config/plm_ea.sample.xml`

Treat it as a checklist to fill in with the ECTR administrator:

```xml
<view objectType="PART" purpose="properties panel">
  <field key="MATERIAL_GROUP" mandatory="true" order="10">
    <label lang="en">Material group</label>
    <label lang="de">Warengruppe</label>
  </field>
  ...
</view>
<field-mapping>
  <map key="MATERIAL_GROUP" backend="TODO: SAP field / ECTR attribute name"/>
</field-mapping>
```

Then translate each entry into the real configuration syntax:

1. Get the customer's current `plm_ea.xml` (or equivalent) from the ECTR installation, and version it in their configuration repository.
2. Find out how existing fields are declared, ordered and labelled for each language, by reading SAP's documentation for that release.
3. Make the change in a test client first. Restart or reload ECTR as the documentation requires.
4. Keep the labels identical to `field.*` in our message bundles, so error messages use the same words as the screen.

## Adding a language

1. Copy `messages_de.properties` to, for example, `messages_fr.properties` and translate it (UTF-8).
2. Add the language to `<description-languages required="...">` if descriptions in that language are mandatory.
3. `MessagesTest` already checks that German is complete. Add the same check for the new file.

## Next

[Chapter 9](09-errors-logging-messages.md) covers exceptions, logging and user messages.
