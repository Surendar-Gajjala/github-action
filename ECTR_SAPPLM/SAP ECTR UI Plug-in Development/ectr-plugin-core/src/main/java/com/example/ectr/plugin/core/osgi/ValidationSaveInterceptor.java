// VERIFIED: standard OSGi Declarative Services. The SaveInterceptor port it implements is ILLUSTRATIVE:
// a real ECTR adapter must call it from the host's actual before-save hook.
package com.example.ectr.plugin.core.osgi;

import com.example.ectr.plugin.api.model.EngineeringObject;
import com.example.ectr.plugin.api.port.EctrSession;
import com.example.ectr.plugin.api.port.SaveDecision;
import com.example.ectr.plugin.api.port.SaveInterceptor;
import com.example.ectr.plugin.api.port.UserNotifier;
import com.example.ectr.plugin.core.i18n.LocalizableMessage;
import com.example.ectr.plugin.core.i18n.LocalizableMessage.TypeLabel;
import com.example.ectr.plugin.core.i18n.Messages;
import com.example.ectr.plugin.core.validation.ValidationResult;
import com.example.ectr.plugin.core.validation.Violation;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks mandatory metadata before save and tells the user exactly what is missing.
 * <p>
 * If the check itself crashes, the save is <b>blocked</b> (fail closed): saving unchecked data
 * into PLM is worse than asking the user to retry. The user gets an incident reference that
 * matches the stack trace in the log.
 */
@Component(service = SaveInterceptor.class, configurationPid = ValidationSaveInterceptor.PID)
@Designate(ocd = ValidationSaveInterceptor.Config.class)
public final class ValidationSaveInterceptor implements SaveInterceptor {

    public static final String PID = "com.example.ectr.plugin.validation";

    @ObjectClassDefinition(name = "ECTR plug-in: save validation")
    public @interface Config {

        @AttributeDefinition(name = "Block save on violation",
                description = "true = refuse the save; false = allow it and only warn (useful during roll-out).")
        boolean blockOnViolation() default true;
    }

    private static final System.Logger LOG = System.getLogger(ValidationSaveInterceptor.class.getName());

    private final RuleRepository rules;
    private final EctrSession session;
    private final UserNotifier notifier;
    private final boolean blockOnViolation;

    @Activate
    public ValidationSaveInterceptor(@Reference RuleRepository rules,
                                     @Reference EctrSession session,
                                     @Reference UserNotifier notifier,
                                     Config config) {
        this.rules = rules;
        this.session = session;
        this.notifier = notifier;
        this.blockOnViolation = config.blockOnViolation();
    }

    @Override
    public SaveDecision beforeSave(EngineeringObject object) {
        try {
            ValidationResult result = rules.validator().validate(object);
            if (result.isValid()) {
                LOG.log(System.Logger.Level.DEBUG, "{0} passed validation", object.id());
                return SaveDecision.allow();
            }
            Messages messages = new Messages(Support.safeLocale(session));
            List<String> lines = new ArrayList<>();
            lines.add(messages.format(LocalizableMessage.of(blockOnViolation ? "save.blocked" : "save.warningOnly",
                    new TypeLabel(object.type()), object.id())));
            for (Violation v : result.violations()) {
                lines.add("  - " + messages.format(v.message()));
            }
            String text = String.join(System.lineSeparator(), lines);

            LOG.log(System.Logger.Level.INFO, "{0}: {1} validation violation(s) {2}, save {3}",
                    object.id(), result.violations().size(),
                    result.violations().stream().map(Violation::ruleId).toList(),
                    blockOnViolation ? "blocked" : "allowed (warning mode)");

            if (blockOnViolation) {
                notifier.error(text);
                return SaveDecision.reject(lines);
            }
            notifier.warn(text);
            return new SaveDecision(true, lines);
        } catch (RuntimeException e) {
            String reference = Support.newIncidentReference();
            LOG.log(System.Logger.Level.ERROR, "Validation of " + object.id() + " failed, reference " + reference, e);
            String text = new Messages(Support.safeLocale(session)).format("save.internalError", object.id(), reference);
            notifier.error(text);
            return SaveDecision.reject(List.of(text));
        }
    }
}
