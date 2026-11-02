package com.gsr.onboarding.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Joining date must be no more than {@link JoiningDateValidator#MAX_DAYS_IN_PAST} days in the past
 * and no more than {@link JoiningDateValidator#MAX_DAYS_IN_FUTURE} days in the future.
 */
@Documented
@Constraint(validatedBy = JoiningDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidJoiningDate {

    String message() default "must be within the last 30 days or the next 365 days";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
