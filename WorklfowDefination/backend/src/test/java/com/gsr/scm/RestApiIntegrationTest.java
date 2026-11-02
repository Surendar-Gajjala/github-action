package com.gsr.scm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RestApiIntegrationTest extends IntegrationTestBase {

    private String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    @Test
    void requiresAuthenticationWithJsonError() throws Exception {
        mvc.perform(get("/api/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void itemCrudThroughApi() throws Exception {
        String number = unique("API");
        MvcResult created = mvc.perform(post("/api/items").with(httpBasic("author", "author"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("itemNumber", number, "name", "Pump", "partIds",
                                ids("PART-101", "PART-102")))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.parts.length()").value(2))
                .andReturn();
        JsonNode item = json.readTree(created.getResponse().getContentAsString());
        long id = item.get("id").asLong();

        mvc.perform(get("/api/items/{id}/parts", id).with(httpBasic("engineer", "engineer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partNumber").value("PART-101"));

        mvc.perform(put("/api/items/{id}/parts", id).with(httpBasic("author", "author"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("partIds", ids("PART-103"), "version", item.get("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts.length()").value(1));

        mvc.perform(get("/api/items").param("q", number).param("sort", "itemNumber,asc")
                        .with(httpBasic("author", "author")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].partCount").value(1));

        mvc.perform(get("/api/items/{id}/workflow-history", id).with(httpBasic("author", "author")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("author"));
    }

    @Test
    void validationAndNotFoundErrorsAreStructured() throws Exception {
        mvc.perform(post("/api/items").with(httpBasic("author", "author"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemNumber\":\"\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
        mvc.perform(post("/api/parts").with(httpBasic("author", "author"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"partNumber\":\"bad number!\",\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("partNumber"));
        mvc.perform(get("/api/items/999999").with(httpBasic("author", "author")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Item 999999 was not found"));
        mvc.perform(get("/api/items").param("size", "1000").with(httpBasic("author", "author")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void partApiAndDuplicates() throws Exception {
        String number = unique("P");
        mvc.perform(post("/api/parts").with(httpBasic("author", "author")).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("partNumber", number, "name", "Valve", "partType", "MECHANICAL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.part.partNumber").value(number));
        mvc.perform(post("/api/parts").with(httpBasic("author", "author")).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("partNumber", number, "name", "Valve"))))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/parts").param("q", "bearing").with(httpBasic("manager", "manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].partNumber").value("PART-102"));
    }

    @Test
    void rolesAreEnforced() throws Exception {
        mvc.perform(post("/api/items").with(httpBasic("engineer", "engineer"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemNumber\":\"X1\",\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/workflows/definitions/ITEM_REVISION_RELEASE/publish").with(httpBasic("author", "author")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/workflows/tasks/1/approve").with(httpBasic("author", "author"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/me").with(httpBasic("admin", "admin")))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles.length()").value(2));
    }

    @Test
    void workflowEndpoints() throws Exception {
        mvc.perform(get("/api/workflows/definitions").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='ITEM_LIFECYCLE_APPROVAL')]").exists());
        mvc.perform(get("/api/workflows/definitions/ITEM_LIFECYCLE_APPROVAL/versions").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/workflows/engine").with(httpBasic("author", "author")))
                .andExpect(jsonPath("$.engine").value("DEV_ADAPTER"))
                .andExpect(jsonPath("$.realCamunda").value(false));
        mvc.perform(get("/api/workflows/lifecycle-transitions").with(httpBasic("author", "author")))
                .andExpect(jsonPath("$.length()").value(7));
        mvc.perform(post("/api/workflows/definitions/validate").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("bpmnXml", "<x/>", "key", "X"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        ensurePublished("ITEM_LIFECYCLE_APPROVAL");
        long itemId = createItem("PART-101").id();
        MvcResult started = mvc.perform(post("/api/workflows/instances").with(httpBasic("author", "author"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("itemId", itemId))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.definitionVersion").isNumber())
                .andReturn();
        String instanceId = json.readTree(started.getResponse().getContentAsString()).get("id").asText();
        awaitEngine();

        MvcResult tasks = mvc.perform(get("/api/workflows/tasks").with(httpBasic("engineer", "engineer")))
                .andExpect(status().isOk()).andReturn();
        JsonNode task = null;
        for (JsonNode t : json.readTree(tasks.getResponse().getContentAsString()).get("content")) {
            if (t.get("workflowInstanceId").asText().equals(instanceId)) {
                task = t;
            }
        }
        assertThat(task).isNotNull();
        assertThat(task.get("candidateGroup").asText()).isEqualTo("ENGINEERING");

        mvc.perform(get("/api/workflows/tasks").with(httpBasic("manager", "manager")))
                .andExpect(jsonPath("$.content[?(@.workflowInstanceId=='" + instanceId + "')]").isEmpty());
        mvc.perform(post("/api/workflows/tasks/{id}/reject", task.get("id").asLong()).with(httpBasic("engineer", "engineer"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/workflows/tasks/{id}/changes", task.get("id").asLong()).with(httpBasic("engineer", "engineer")))
                .andExpect(jsonPath("$.partsAdded[0].partNumber").value("PART-101"));
        mvc.perform(post("/api/workflows/tasks/{id}/approve", task.get("id").asLong()).with(httpBasic("engineer", "engineer"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"fine\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        awaitEngine();

        mvc.perform(get("/api/workflows/instances/{id}", instanceId).with(httpBasic("admin", "admin")))
                .andExpect(jsonPath("$.instance.status").value("COMPLETED"))
                .andExpect(jsonPath("$.tasks.length()").value(1));
        mvc.perform(get("/api/workflows/instances/{id}/history", instanceId).with(httpBasic("admin", "admin")))
                .andExpect(jsonPath("$[?(@.action=='REVISION_RELEASED')]").exists());
        mvc.perform(get("/api/workflows/history").param("q", instanceId).with(httpBasic("admin", "admin")))
                .andExpect(status().isOk());
    }
}
