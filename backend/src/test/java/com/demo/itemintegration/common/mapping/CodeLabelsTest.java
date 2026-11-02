package com.demo.itemintegration.common.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

class CodeLabelsTest {

    @ParameterizedTest
    @CsvSource({
            "PRODN_APPROVED, Production Approved", "UN_QUAL, Unqualified", "conditional, Conditional", "PRELIMINARY, Preliminary",
            "N_A, N/A", "NOT_LOCKED, Not Locked", "NO_DATA, No Data", "YES, Yes", "EXCL_ITEM, Excluded Item"
    })
    void labelsKnownCodes(String code, String label) {
        assertThat(CodeLabels.label(TextNode.valueOf(code))).isEqualTo(label);
    }

    @Test
    void unknownCodesPassThroughUnchanged() {
        assertThat(CodeLabels.label(TextNode.valueOf("Y_E"))).isEqualTo("Y_E");
        assertThat(CodeLabels.label(TextNode.valueOf(" EOL "))).isEqualTo("EOL");
    }

    @Test
    void blankBecomesNull() {
        assertThat(CodeLabels.label(TextNode.valueOf(""))).isNull();
        assertThat(CodeLabels.label(NullNode.getInstance())).isNull();
        assertThat(CodeLabels.label(null)).isNull();
    }
}
