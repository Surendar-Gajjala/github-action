package com.gsr.onboarding.validation;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class JoiningDateValidator implements ConstraintValidator<ValidJoiningDate, LocalDate> {

    public static final int MAX_DAYS_IN_PAST = 30;
    public static final int MAX_DAYS_IN_FUTURE = 365;

    private final Clock clock;

    public JoiningDateValidator() {
        this(Clock.systemDefaultZone());
    }

    JoiningDateValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotNull reports missing values
        }
        LocalDate today = LocalDate.now(clock);
        return !value.isBefore(today.minusDays(MAX_DAYS_IN_PAST)) && !value.isAfter(today.plusDays(MAX_DAYS_IN_FUTURE));
    }
}
