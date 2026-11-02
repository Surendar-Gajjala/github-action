// VERIFIED: plain Java.
package com.example.ectr.plugin.core.validation;

import com.example.ectr.plugin.core.i18n.LocalizableMessage;

/**
 * One failed rule on one object.
 *
 * @param objectId    object that failed
 * @param ruleId      stable id of the rule, useful in logs and tests
 * @param propertyKey logical property that is wrong, or {@code null} for object-level rules
 * @param message     untranslated user message
 */
public record Violation(String objectId, String ruleId, String propertyKey, LocalizableMessage message) {
}
