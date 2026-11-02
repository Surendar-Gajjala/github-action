package com.gsr.onboarding.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class JoiningDateValidatorTest {

    private final JoiningDateValidator validator =
            new JoiningDateValidator(Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void acceptsDatesInsideTheWindow() {
        assertThat(validator.isValid(LocalDate.of(2026, 10, 26), null)).isTrue();
        assertThat(validator.isValid(LocalDate.of(2026, 9, 26), null)).isTrue();
        assertThat(validator.isValid(LocalDate.of(2027, 10, 26), null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void rejectsDatesOutsideTheWindow() {
        assertThat(validator.isValid(LocalDate.of(2026, 9, 25), null)).isFalse();
        assertThat(validator.isValid(LocalDate.of(2027, 10, 27), null)).isFalse();
    }
}
