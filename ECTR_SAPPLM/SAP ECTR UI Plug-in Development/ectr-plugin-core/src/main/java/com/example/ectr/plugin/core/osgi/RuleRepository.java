// VERIFIED: standard OSGi Declarative Services + Metatype annotations (OSGi Compendium R8).
package com.example.ectr.plugin.core.osgi;

import com.example.ectr.plugin.core.config.RuleConfigurationException;
import com.example.ectr.plugin.core.packaging.PackagingRule;
import com.example.ectr.plugin.core.packaging.PackagingRulesLoader;
import com.example.ectr.plugin.core.validation.MetadataValidator;
import com.example.ectr.plugin.core.validation.RuleSet;
import com.example.ectr.plugin.core.validation.RuleSetLoader;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.List;

/**
 * Loads the validation and packaging rule XML once and shares it with the other components.
 * <p>
 * If a rule file is broken, activation fails on purpose: the components that depend on this
 * one stay unsatisfied (visible with {@code scr:info}) instead of running with half the rules.
 * A configuration change re-creates the component, which reloads the files.
 */
@Component(service = RuleRepository.class, configurationPid = RuleRepository.PID)
@Designate(ocd = RuleRepository.Config.class)
public final class RuleRepository {

    public static final String PID = "com.example.ectr.plugin.rules";

    @ObjectClassDefinition(name = "ECTR plug-in: rule files",
            description = "Locations of the validation and packaging rule XML files.")
    public @interface Config {

        @AttributeDefinition(name = "Validation rules file",
                description = "Absolute path to validation-rules.xml. Empty = default packaged in the bundle.")
        String validationRulesFile() default "";

        @AttributeDefinition(name = "Packaging rules file",
                description = "Absolute path to packaging-rules.xml. Empty = default packaged in the bundle.")
        String packagingRulesFile() default "";
    }

    private static final System.Logger LOG = System.getLogger(RuleRepository.class.getName());

    private final MetadataValidator validator;
    private final List<PackagingRule> packagingRules;

    @Activate
    public RuleRepository(Config config) {
        this(loadRuleSet(config.validationRulesFile()), loadPackagingRules(config.packagingRulesFile()));
        LOG.log(System.Logger.Level.INFO, "Rules loaded (validation: {0}, packaging: {1})",
                describe(config.validationRulesFile()), describe(config.packagingRulesFile()));
    }

    /** For tests and non-OSGi use. */
    public RuleRepository(RuleSet ruleSet, List<PackagingRule> packagingRules) {
        this.validator = new MetadataValidator(ruleSet);
        this.packagingRules = List.copyOf(packagingRules);
    }

    /** Rules packaged inside the bundle. */
    public static RuleRepository bundledDefaults() {
        return new RuleRepository(RuleSetLoader.load(""), PackagingRulesLoader.load(""));
    }

    public MetadataValidator validator() {
        return validator;
    }

    public List<PackagingRule> packagingRules() {
        return packagingRules;
    }

    private static RuleSet loadRuleSet(String path) {
        try {
            return RuleSetLoader.load(path);
        } catch (RuleConfigurationException e) {
            LOG.log(System.Logger.Level.ERROR, "Validation rules not loaded: " + e.getMessage());
            throw e;
        }
    }

    private static List<PackagingRule> loadPackagingRules(String path) {
        try {
            return PackagingRulesLoader.load(path);
        } catch (RuleConfigurationException e) {
            LOG.log(System.Logger.Level.ERROR, "Packaging rules not loaded: " + e.getMessage());
            throw e;
        }
    }

    private static String describe(String path) {
        return path == null || path.isBlank() ? "bundled default" : path;
    }
}
