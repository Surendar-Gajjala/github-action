package com.example.plmdemo.cad.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CAD path over HTTP: CAD JSON -> simulated ECTR steps -> mock SAP backend. */
@SpringBootTest
@AutoConfigureMockMvc
class EctrFlowTest {

    @Autowired
    private MockMvc mvc;

    private static String cadJson(String assembly, String revision) {
        return """
                {
                  "source":   { "cadSystem": "DEMO-CAD", "fileName": "%1$s.asm", "exportedBy": "designer01" },
                  "assembly": { "partNumber": "%1$s", "revision": "%2$s", "plant": "1000",
                                "changeNumber": "ECN-5001", "description": "Electric motor assembly",
                                "components": [
                    { "instanceId": "i-001", "partNumber": "SHAFT-101",   "revision": "A", "quantity": 1, "unit": "EA", "fileName": "shaft-101.prt" },
                    { "instanceId": "i-002", "partNumber": "BEARING-201", "revision": "C", "quantity": 1, "unit": "EA", "fileName": "bearing-201.prt" },
                    { "instanceId": "i-003", "partNumber": "BEARING-201", "revision": "C", "quantity": 1, "unit": "EA", "fileName": "bearing-201.prt" },
                    { "instanceId": "i-004", "partNumber": "SKEL-100",    "revision": "A", "quantity": 1, "fileName": "skel-100.prt", "excludeFromBom": true }
                  ] }
                }""".formatted(assembly, revision);
    }

    private String start(String assembly, String revision) throws Exception {
        String body = mvc.perform(post("/api/v1/ectr/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(cadJson(assembly, revision)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.sessionId");
    }

    private ResultActions step(String id, String step) throws Exception {
        return mvc.perform(post("/api/v1/ectr/sessions/" + id + "/" + step));
    }

    @Test
    void fullEctrFlowCreatesDirsBodMaterialsAndBom() throws Exception {
        String id = start("ECTR-MOTOR-1", "D");

        mvc.perform(get("/api/v1/ectr/sessions/" + id))
                .andExpect(jsonPath("$.status").value("DOCUMENTS_CREATED"))
                .andExpect(jsonPath("$.nextStep").value("checkin"))
                .andExpect(jsonPath("$.mode").value("MOCK"))
                .andExpect(jsonPath("$.documents.length()").value(4))          // assembly + 3 distinct parts
                .andExpect(jsonPath("$.documents[0].dirKey", startsWith("CAD/")))
                .andExpect(jsonPath("$.documents[0].assembly").value(true))
                .andExpect(jsonPath("$.documentStructure.length()").value(0))
                .andExpect(jsonPath("$.steps[0].connectorCall").value("createDocuments(files, metadata)"));

        step(id, "checkin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.documents[1].originalId", startsWith("MOCK-CS-")))
                .andExpect(jsonPath("$.documentStructure.length()").value(3))  // BoD keeps the skeleton
                .andExpect(jsonPath("$.documentStructure[1].partNumber").value("BEARING-201"))
                .andExpect(jsonPath("$.documentStructure[1].quantity").value(2))
                .andExpect(jsonPath("$.documentStructure[2].bomRelevant").value(false));

        step(id, "materials")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials.length()").value(3))          // assembly, shaft, bearing; no skeleton
                .andExpect(jsonPath("$.documents[0].linkedMaterial").value("ECTR-MOTOR-1"));

        step(id, "bom")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOM_MAINTAINED"))
                .andExpect(jsonPath("$.nextStep").doesNotExist())
                .andExpect(jsonPath("$.bom.created").value(true))
                .andExpect(jsonPath("$.bom.items.length()").value(2))
                .andExpect(jsonPath("$.bom.items[0].itemNumber").value("0010"))
                .andExpect(jsonPath("$.bom.items[1].componentMaterial").value("BEARING-201"))
                .andExpect(jsonPath("$.bom.items[1].quantity").value(2))
                .andExpect(jsonPath("$.steps.length()").value(4))
                .andExpect(jsonPath("$.steps[3].connectorCall").value("cdeskBomWizard(part, materialHead, items)"));
    }

    @Test
    void stepsMustRunInOrder() throws Exception {
        String id = start("ECTR-MOTOR-2", "A");

        step(id, "bom").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ECTR_STEP_ORDER"));
        step(id, "checkin").andExpect(status().isOk());
        step(id, "checkin").andExpect(status().isConflict());
    }

    @Test
    void secondRunReusesDocumentsAndUpdatesBom() throws Exception {
        String first = start("ECTR-MOTOR-3", "A");
        step(first, "checkin");
        step(first, "materials");
        step(first, "bom");

        String second = start("ECTR-MOTOR-3", "A");
        mvc.perform(get("/api/v1/ectr/sessions/" + second))
                .andExpect(jsonPath("$.documents[0].reused").value(true))
                .andExpect(jsonPath("$.steps[0].messages", hasItem(startsWith("DIR "))));
        step(second, "checkin");
        step(second, "materials").andExpect(jsonPath("$.materials[0].created").value(false));
        step(second, "bom").andExpect(jsonPath("$.bom.created").value(false));
    }

    @Test
    void invalidCadAndUnknownSession() throws Exception {
        mvc.perform(post("/api/v1/ectr/sessions").contentType(MediaType.APPLICATION_JSON).content("""
                        { "source": { "cadSystem": "DEMO-CAD" },
                          "assembly": { "partNumber": "M-1", "revision": "A", "plant": "1000",
                            "components": [ { "partNumber": "S-1", "quantity": 0 } ] } }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CAD_VALIDATION_FAILED"));

        mvc.perform(get("/api/v1/ectr/sessions/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ECTR_SESSION_NOT_FOUND"));
    }
}
