package com.example.ectr.dms.sap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BapiReturnTest {

    @ParameterizedTest
    @CsvSource({"E,true", "A,true", "W,false", "S,false", "I,false"})
    void onlyErrorAndAbortAreErrors(String type, boolean error) {
        assertThat(new BapiReturn(type, "X", "001", "msg").isError()).isEqualTo(error);
    }

    @Test
    void emptyStructureMeansNoMessage() {
        assertThat(BapiReturn.fromStructure(Map.of())).isEmpty();
        assertThat(BapiReturn.fromStructure(Map.of("TYPE", " ", "MESSAGE", ""))).isEmpty();
    }

    @Test
    void tableRowsAreTrimmedAndFiltered() {
        List<BapiReturn> messages = BapiReturn.fromTable(List.of(
                Map.of("TYPE", "W", "MESSAGE", " warning "),
                Map.of("TYPE", "", "MESSAGE", ""),
                Map.of("TYPE", "E", "ID", "26", "NUMBER", "001", "MESSAGE", "error")));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).message()).isEqualTo("warning");
        assertThat(BapiReturn.errors(messages)).extracting(BapiReturn::id).containsExactly("26");
    }
}
