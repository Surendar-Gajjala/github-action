package com.example.plmdemo.erp.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP with the default MOCK PLM source and MOCK SAP gateway. */
@SpringBootTest(properties = "integration.retry.backoff-millis=0")
@AutoConfigureMockMvc
class BomTransferControllerTest {

    private static final String MOTOR_100_B = """
            {"materialNumber":"MOTOR-100","revision":"B","plant":"1000"}""";

    @Autowired
    private MockMvc mvc;

    @Test
    void transferSucceedsThenDuplicateReturnsSameTransfer() throws Exception {
        MvcResult first = mvc.perform(post("/api/v1/bom-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "api-test-1")
                        .header("X-Correlation-ID", "corr-api-1")
                        .content(MOTOR_100_B))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", "corr-api-1"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.gatewayMode").value("MOCK"))
                .andExpect(jsonPath("$.targetReference", startsWith("MOCK-BOM-")))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn();

        String transferId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.transferId");

        mvc.perform(post("/api/v1/bom-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "api-test-1")
                        .content(MOTOR_100_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.transferId").value(transferId));

        mvc.perform(get("/api/v1/bom-transfers/" + transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void unreleasedBomReturns422() throws Exception {
        mvc.perform(post("/api/v1/bom-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialNumber":"MOTOR-100","revision":"C","plant":"1000"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOM_VALIDATION_FAILED"));
    }

    @Test
    void invalidQuantitiesReturn422() throws Exception {
        mvc.perform(post("/api/v1/bom-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialNumber":"MOTOR-900","revision":"A","plant":"1000"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void malformedInputReturns400() throws Exception {
        mvc.perform(post("/api/v1/bom-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialNumber":"motor 100","revision":"B","plant":"10"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.details", hasItem(startsWith("plant:"))))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void unknownBomReturns404() throws Exception {
        mvc.perform(post("/api/v1/bom-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialNumber":"PUMP-1","revision":"A","plant":"1000"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOM_NOT_FOUND"));
    }
}
