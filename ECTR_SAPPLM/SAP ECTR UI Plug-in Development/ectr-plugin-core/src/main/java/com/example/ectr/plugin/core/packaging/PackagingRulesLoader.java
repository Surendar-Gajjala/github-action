// VERIFIED: plain Java/JAXP. The XML format is OUR OWN schema, not an SAP ECTR configuration file.
package com.example.ectr.plugin.core.packaging;

import com.example.ectr.plugin.core.config.RuleConfigurationException;
import com.example.ectr.plugin.core.config.SecureXml;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code packaging-rules.xml}:
 * <pre>{@code
 * <packaging-rules>
 *   <rule id="group-by-material-group"/>
 *   <rule id="include-drawings"/>
 *   <rule id="max-components-per-package" max="4"/>
 * </packaging-rules>
 * }</pre>
 */
public final class PackagingRulesLoader {

    public static final String BUNDLED_RESOURCE = "rules/packaging-rules.xml";
    private static final String ROOT = "packaging-rules";

    private PackagingRulesLoader() {
    }

    public static List<PackagingRule> load(String externalPath) {
        String source = externalPath == null || externalPath.isBlank() ? BUNDLED_RESOURCE : externalPath.trim();
        return fromRoot(SecureXml.loadRoot(externalPath, BUNDLED_RESOURCE, ROOT), source);
    }

    public static List<PackagingRule> load(InputStream in, String sourceName) {
        return fromRoot(SecureXml.parse(in, sourceName, ROOT), sourceName);
    }

    private static List<PackagingRule> fromRoot(Element root, String source) {
        List<PackagingRule> rules = new ArrayList<>();
        for (Element e : SecureXml.childElements(root)) {
            if (!"rule".equals(e.getTagName())) {
                throw new RuleConfigurationException(source + ": unexpected element <" + e.getTagName() + ">");
            }
            String id = SecureXml.requiredAttribute(e, "id", source);
            rules.add(switch (id) {
                case SameMaterialGroupRule.ID -> new SameMaterialGroupRule();
                case IncludeDrawingsRule.ID -> new IncludeDrawingsRule();
                case MaxComponentCountRule.ID -> new MaxComponentCountRule(positiveInt(e, "max", source));
                default -> throw new RuleConfigurationException(source + ": unknown packaging rule '" + id
                        + "'. Allowed: " + SameMaterialGroupRule.ID + ", " + IncludeDrawingsRule.ID + ", "
                        + MaxComponentCountRule.ID);
            });
        }
        return rules;
    }

    private static int positiveInt(Element e, String attribute, String source) {
        String raw = SecureXml.requiredAttribute(e, attribute, source);
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new RuleConfigurationException(source + ": attribute '" + attribute
                    + "' must be a whole number >= 1, was '" + raw + "'");
        }
    }
}
