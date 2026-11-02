package com.example.plmdemo.erp.validation;

import com.example.plmdemo.erp.domain.ReleaseState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.example.plmdemo.erp.TestBoms.item;
import static com.example.plmdemo.erp.TestBoms.motor100;
import static com.example.plmdemo.erp.TestBoms.releasedMotor100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BomValidatorTest {

    private final BomValidator validator = new BomValidator();

    @Test
    void releasedBomPasses() {
        assertThatCode(() -> validator.validate(releasedMotor100())).doesNotThrowAnyException();
    }

    // Release validation
    @ParameterizedTest
    @EnumSource(value = ReleaseState.class, names = {"IN_WORK", "OBSOLETE"})
    void nonReleasedBomIsRejected(ReleaseState state) {
        var bom = motor100(state, item("SHAFT-101", "1"));

        assertThatThrownBy(() -> validator.validate(bom))
                .isInstanceOf(BomValidationException.class)
                .satisfies(e -> assertThat(((BomValidationException) e).getViolations())
                        .anyMatch(v -> v.contains("only RELEASED")));
    }

    // Invalid quantities
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.5"})
    void zeroOrNegativeQuantityIsRejected(String qty) {
        var bom = motor100(ReleaseState.RELEASED, item("SHAFT-101", qty));

        assertThatThrownBy(() -> validator.validate(bom))
                .isInstanceOf(BomValidationException.class)
                .hasMessageContaining("invalid quantity");
    }

    @Test
    void missingQuantityIsRejected() {
        var bom = motor100(ReleaseState.RELEASED, item("SHAFT-101", null));

        assertThatThrownBy(() -> validator.validate(bom)).hasMessageContaining("invalid quantity");
    }

    @Test
    void tooManyDecimalsIsRejected() {
        var bom = motor100(ReleaseState.RELEASED, item("SHAFT-101", "1.2345"));

        assertThatThrownBy(() -> validator.validate(bom)).hasMessageContaining("decimals");
    }

    @Test
    void duplicateComponentAndSelfReferenceAreRejected() {
        var bom = motor100(ReleaseState.RELEASED,
                item("SHAFT-101", "1"), item("SHAFT-101", "1"), item("MOTOR-100", "1"));

        assertThatThrownBy(() -> validator.validate(bom))
                .isInstanceOf(BomValidationException.class)
                .satisfies(e -> assertThat(((BomValidationException) e).getViolations())
                        .anyMatch(v -> v.contains("more than once"))
                        .anyMatch(v -> v.contains("own header")));
    }

    @Test
    void emptyBomIsRejected() {
        var bom = motor100(ReleaseState.RELEASED);

        assertThatThrownBy(() -> validator.validate(bom)).hasMessageContaining("no items");
    }
}
