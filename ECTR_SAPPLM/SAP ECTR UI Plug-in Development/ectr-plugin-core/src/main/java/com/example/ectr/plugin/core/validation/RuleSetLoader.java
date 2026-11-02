// VERIFIED: plain Java/JAXP. The XML format is OUR OWN schema, not an SAP ECTR configuration file.
package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.api.model.ObjectType;
import com.example.ectr.plugin.core.config.RuleConfigurationException;
import com.example.ectr.plugin.core.config.SecureXml;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reads {@code validation-rules.xml}:
 * <pre>{@code
 * <validation-rules>
 *   <object-type name="PART">
 *     <mandatory property="MATERIAL_GROUP"/>
 *     <pattern property="REVISION" regex="[A-Z]{1,2}" example="A, B ... ZZ"/>
 *     <description-languages required="en,de"/>
 *   </object-type>
 * </validation-rules>
 * }</pre>
 * Any unknown element, attribute value or object type stops loading with a clear message,
 * so a typo cannot silently switch a check off.
 */
public final class RuleSetLoader {

    public static final String BUNDLED_RESOURCE = "rules/validation-rules.xml";
    private static final String ROOT = "validation-rules";

    private RuleSetLoader() {
    }

    /** Loads an external file when the path is set, otherwise the default packaged in the bundle. */
    public static RuleSet load(String externalPath) {
        return fromRoot(SecureXml.loadRoot(externalPath, BUNDLED_RESOURCE, ROOT), sourceName(externalPath));
    }

    public static RuleSet load(InputStream in, String sourceName) {
        return fromRoot(SecureXml.parse(in, sourceName, ROOT), sourceName);
    }

    private static RuleSet fromRoot(Element root, String source) {
        Map<ObjectType, List<ValidationRule>> rules = new EnumMap<>(ObjectType.class);
        for (Element typeElement : SecureXml.childElements(root)) {
            if (!"object-type".equals(typeElement.getTagName())) {
                throw new RuleConfigurationException(source + ": unexpected element <" + typeElement.getTagName()
                        + "> under <" + ROOT + ">");
            }
            ObjectType type = parseType(SecureXml.requiredAttribute(typeElement, "name", source), source);
            if (rules.containsKey(type)) {
                throw new RuleConfigurationException(source + ": object type " + type + " is defined twice");
            }
            String context = source + " [" + type + "]";
            List<ValidationRule> list = new ArrayList<>();
            for (Element ruleElement : SecureXml.childElements(typeElement)) {
                list.add(parseRule(ruleElement, context));
            }
            rules.put(type, list);
        }
        return new RuleSet(rules);
    }

    private static ValidationRule parseRule(Element e, String context) {
        return switch (e.getTagName()) {
            case "mandatory" -> new MandatoryFieldRule(SecureXml.requiredAttribute(e, "property", context));
            case "pattern" -> new PatternRule(
                    SecureXml.requiredAttribute(e, "property", context),
                    compile(SecureXml.requiredAttribute(e, "regex", context), context),
                    e.getAttribute("example"));
            case "description-languages" -> new RequiredLanguagesRule(
                    Arrays.stream(SecureXml.requiredAttribute(e, "required", context).split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Locale::forLanguageTag)
                            .toList());
            default -> throw new RuleConfigurationException(context + ": unknown rule <" + e.getTagName()
                    + ">. Allowed: mandatory, pattern, description-languages");
        };
    }

    private static ObjectType parseType(String name, String source) {
        try {
            return ObjectType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RuleConfigurationException(source + ": unknown object type '" + name
                    + "'. Allowed: " + Arrays.toString(ObjectType.values()));
        }
    }

    private static Pattern compile(String regex, String context) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException ex) {
            throw new RuleConfigurationException(context + ": invalid regex '" + regex + "': " + ex.getDescription());
        }
    }

    private static String sourceName(String externalPath) {
        return externalPath == null || externalPath.isBlank() ? BUNDLED_RESOURCE : externalPath.trim();
    }
}
