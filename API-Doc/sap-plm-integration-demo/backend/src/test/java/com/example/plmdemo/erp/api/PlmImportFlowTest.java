package com.example.plmdemo.erp.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ERP path over HTTP: CAD data -> external PLM (IN_WORK) -> PLM release -> SAP transfer (MOCK).
 * Each test uses its own revision because the in-memory PLM store is shared within the Spring context.
 */
@SpringBootTest(properties = "integration.retry.backoff-millis=0")
@AutoConfigureMockMvc
class PlmImportFlowTest {

    @Autowired
    private MockMvc mvc;

    private static String cadJson(String revision, String components) {
        return """
                {
                  "source":   { "cadSystem": "DEMO-CAD", "fileName": "motor-100.asm", "exportedBy": "designer01" },
                  "assembly": { "partNumber": "MOTOR-100", "revision": "%s", "plant": "1000",
                                "changeNumber": "ECN-5001", "description": "Electric motor assembly",
                                "components": [ %s ] }
                }""".formatted(revision, components);
    }

    private static final String GOOD_COMPONENTS = """
            { "instanceId": "i-001", "partNumber": "SHAFT-101",   "quantity": 1, "unit": "EA" },
            { "instanceId": "i-002", "partNumber": "BEARING-201", "quantity": 1, "unit": "EA" },
            { "instanceId": "i-003", "partNumber": "BEARING-201", "quantity": 1, "unit": "EA" },
            { "instanceId": "i-004", "partNumber": "SKEL-100", "quantity": 1, "excludeFromBom": true }""";

    private ResultActions importCad(String json) throws Exception {
        return mvc.perform(post("/api/v1/plm/imports").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions release(String revision) throws Exception {
        return mvc.perform(post("/api/v1/plm/boms/MOTOR-100/" + revision + "/release").param("plant", "1000"));
    }

    private ResultActions transfer(String revision, String key) throws Exception {
        return mvc.perform(post("/api/v1/bom-transfers").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .content("{\"materialNumber\":\"MOTOR-100\",\"revision\":\"" + revision + "\",\"plant\":\"1000\"}"));
    }

    @Test
    void fullFlowFromCadJsonToSap() throws Exception {
        importCad(cadJson("D", GOOD_COMPONENTS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bom.releaseState").value("IN_WORK"))
                .andExpect(jsonPath("$.bom.sourceSystem").value("CAD:DEMO-CAD"))
                .andExpect(jsonPath("$.bom.items.length()").value(2))
                .andExpect(jsonPath("$.bom.items[1].componentNumber").value("BEARING-201"))
                .andExpect(jsonPath("$.bom.items[1].quantity").value(2))
                .andExpect(jsonPath("$.cadInstances").value(4))
                .andExpect(jsonPath("$.excludedInstances").value(1));

        // Not released yet: the integration layer refuses to send it to SAP.
        transfer("D", "flow-early").andExpect(status().isUnprocessableEntity());

        release("D").andExpect(status().isOk()).andExpect(jsonPath("$.releaseState").value("RELEASED"));

        transfer("D", "flow-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.targetReference", startsWith("MOCK-BOM-")));

        // A released revision cannot be overwritten or released twice.
        importCad(cadJson("D", GOOD_COMPONENTS)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLM_CONFLICT"));
        release("D").andExpect(status().isConflict());
    }

    @Test
    void reimportOfInWorkRevisionReplacesIt() throws Exception {
        importCad(cadJson("E", GOOD_COMPONENTS)).andExpect(status().isCreated());
        importCad(cadJson("E", GOOD_COMPONENTS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.replacedExisting").value(true));
    }

    @Test
    void invalidCadDataReturns422() throws Exception {
        importCad(cadJson("F", """
                { "instanceId": "i-001", "partNumber": "SHAFT-101",   "quantity": 0 },
                { "instanceId": "i-002", "partNumber": "BEARING-201", "quantity": 1, "unit": "EA" },
                { "instanceId": "i-003", "partNumber": "BEARING-201", "quantity": 1, "unit": "KG" }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CAD_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void malformedCadJsonReturns400() throws Exception {
        importCad("""
                { "source": { "cadSystem": "DEMO-CAD" },
                  "assembly": { "partNumber": "MOTOR-100", "revision": "G", "plant": "10", "components": [] } }""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("assembly.plant"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("assembly.components"))));
    }

    @Test
    void releaseOfUnknownBomReturns404() throws Exception {
        mvc.perform(post("/api/v1/plm/boms/PUMP-1/A/release").param("plant", "1000"))
                .andExpect(status().isNotFound());
    }
}
