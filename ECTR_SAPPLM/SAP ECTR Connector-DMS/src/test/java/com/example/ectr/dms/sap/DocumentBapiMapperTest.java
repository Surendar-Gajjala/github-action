package com.example.ectr.dms.sap;

import com.example.ectr.dms.domain.DocumentKey;
import com.example.ectr.dms.domain.DocumentMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentBapiMapperTest {

    private final DocumentBapiMapper mapper = new DocumentBapiMapper();

    @Test
    void createMapsHeaderDescriptionsAndLinks() {
        DocumentMetadata meta = new DocumentMetadata("DRW", null, "000", "00", "Bracket drawing", "",
                List.of(new DocumentMetadata.Description("DE", "Halterzeichnung")),
                List.of(new DocumentMetadata.ObjectLink("MARA", "MAT-4711")));

        RfcCall call = mapper.createDocument(meta);

        assertThat(call.functionName()).isEqualTo("BAPI_DOCUMENT_CREATE2");
        // number and laboratory are blank -> omitted so that SAP applies its defaults (internal numbering)
        assertThat(call.importStructure("DOCUMENTDATA")).containsExactly(
                Map.entry("DOCUMENTTYPE", "DRW"),
                Map.entry("DOCUMENTPART", "000"),
                Map.entry("DOCUMENTVERSION", "00"),
                Map.entry("DESCRIPTION", "Bracket drawing"));
        assertThat(call.table("DOCUMENTDESCRIPTIONS"))
                .containsExactly(Map.of("LANGUAGE_ISO", "DE", "DESCRIPTION", "Halterzeichnung"));
        assertThat(call.table("OBJECTLINKS")).containsExactly(Map.of("OBJECTTYPE", "MARA", "OBJECTKEY", "MAT-4711"));
    }

    @Test
    void createdKeyIsReadFromExports() {
        RfcResult result = new RfcResult(Map.of("DOCUMENTTYPE", "DRW", "DOCUMENTNUMBER", " 10000001 ",
                "DOCUMENTPART", "000", "DOCUMENTVERSION", "00"), Map.of());
        assertThat(mapper.createdKey(result)).isEqualTo(new DocumentKey("DRW", "10000001", "000", "00"));
    }

    @Test
    void commitWaitsForUpdateTask() {
        RfcCall commit = mapper.commit();
        assertThat(commit.functionName()).isEqualTo("BAPI_TRANSACTION_COMMIT");
        assertThat(commit.importValue("WAIT")).isEqualTo("X");
    }

    @Test
    void getDetailUsesScalarKeyFields() {
        RfcCall call = mapper.getDetail(new DocumentKey("DRW", "1", "000", "00"));
        assertThat(call.functionName()).isEqualTo("BAPI_DOCUMENT_GETDETAIL2");
        assertThat(call.imports()).containsEntry("DOCUMENTNUMBER", "1").containsEntry("DOCUMENTPART", "000");
    }
}
