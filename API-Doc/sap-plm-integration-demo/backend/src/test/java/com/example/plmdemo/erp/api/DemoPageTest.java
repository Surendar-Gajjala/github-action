package com.example.plmdemo.erp.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints used by the demo page (static/index.html). */
@SpringBootTest
@AutoConfigureMockMvc
class DemoPageTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rootServesDemoPage() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("SAP BOM Transfer</h1>")));
    }

    @Test
    void infoShowsMockBackends() throws Exception {
        mvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayMode").value("MOCK"))
                .andExpect(jsonPath("$.ectrBackend").value("MOCK"));
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void sourceBomIsReadable() throws Exception {
        mvc.perform(get("/api/v1/plm/boms/MOTOR-100/B").param("plant", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseState").value("RELEASED"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].componentNumber").value("BEARING-201"));

        mvc.perform(get("/api/v1/plm/boms/PUMP-1/A").param("plant", "1000"))
                .andExpect(status().isNotFound());
    }
}
