package com.example.ectr.dms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full HTTP round trips against the mock SAP backend. */
@SpringBootTest(properties = {
        "mock.sap.latency=0ms",
        "app.storage.root=target/test-originals"
})
@AutoConfigureMockMvc
class DocumentApiIntegrationTest {

    private static final String VALID = """
            {
              "documentType": "DRW",
              "documentPart": "000",
              "documentVersion": "00",
              "description": "Brake caliper drawing",
              "descriptions": [ { "languageIso": "DE", "description": "Bremssattel Zeichnung" } ],
              "objectLinks":  [ { "objectType": "MARA", "objectKey": "MAT-4711" } ]
            }""";

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private JsonNode createDocument() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static String keyPath(JsonNode created) {
        JsonNode d = created.get("document");
        return "/api/v1/documents/" + d.get("documentType").asText() + "/" + d.get("documentNumber").asText() + "/"
                + d.get("documentPart").asText() + "/" + d.get("documentVersion").asText();
    }

    @Test
    void createReturns201WithLocationAndKey() throws Exception {
        mvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON).content(VALID)
                        .header("X-Correlation-Id", "cad-test-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/v1/documents/DRW/")))
                .andExpect(header().string("X-Correlation-Id", "cad-test-1"))
                .andExpect(jsonPath("$.state").value("COMMITTED"))
                .andExpect(jsonPath("$.document.documentType").value("DRW"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());
    }

    @Test
    void invalidRequestListsEveryField() throws Exception {
        String invalid = """
                { "documentType": "drawing", "description": "", "descriptions": [ { "languageIso": "german", "description": "x" } ] }""";
        mvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem:ectr-dms:validation"))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void sapRejectionReturns422WithSapMessages() throws Exception {
        mvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("\"DRW\"", "\"ZZZ\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:problem:ectr-dms:sap-rejected"))
                .andExpect(jsonPath("$.sapMessages[0].type").value("E"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());
    }

    @Test
    void createdDocumentCanBeReadAndUnknownIs404() throws Exception {
        JsonNode created = createDocument();
        mvc.perform(get(keyPath(created)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Brake caliper drawing"));
        mvc.perform(get("/api/v1/documents/DRW/0/000/00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:problem:ectr-dms:not-found"));
    }

    @Test
    void transactionStatusShowsHistory() throws Exception {
        JsonNode created = createDocument();
        mvc.perform(get("/api/v1/transactions/" + created.get("transactionId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMMITTED"))
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.history", hasSize(4)));
    }

    @Test
    void idempotencyKeyReplaysFirstResult() throws Exception {
        MvcResult first = mvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON).content(VALID)
                        .header("Idempotency-Key", "job-777"))
                .andExpect(status().isCreated()).andReturn();
        String txId = json.readTree(first.getResponse().getContentAsString()).get("transactionId").asText();

        mvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON).content(VALID)
                        .header("Idempotency-Key", "job-777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.transactionId").value(txId));
    }

    @Test
    void pdfUploadAndStreamingDownload() throws Exception {
        String base = keyPath(createDocument());
        MockMultipartFile file = new MockMultipartFile("file", "caliper.pdf", "application/octet-stream", PDF);

        MvcResult upload = mvc.perform(multipart(base + "/originals").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileType").value("PDF"))
                .andExpect(jsonPath("$.size").value(PDF.length))
                .andExpect(jsonPath("$.sapCheckin").value("STORED_IN_MIDDLEWARE_ONLY"))
                .andReturn();
        JsonNode body = json.readTree(upload.getResponse().getContentAsString());

        MvcResult download = mvc.perform(get(base + "/originals/" + body.get("fileId").asText()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("ETag", "\"" + body.get("sha256").asText() + "\""))
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(PDF);

        mvc.perform(get(base + "/originals")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void wrongFileContentOrNameIsRejected() throws Exception {
        String base = keyPath(createDocument());
        mvc.perform(multipart(base + "/originals").file(new MockMultipartFile("file", "notes.pdf", null,
                        "just text".getBytes(StandardCharsets.US_ASCII))))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type").value("urn:problem:ectr-dms:invalid-file"));
        mvc.perform(multipart(base + "/originals").file(new MockMultipartFile("file", "caliper.dxf", null, PDF)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadForUnknownDocumentIs404() throws Exception {
        mvc.perform(multipart("/api/v1/documents/DRW/0/000/00/originals")
                        .file(new MockMultipartFile("file", "a.pdf", null, PDF)))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthIncludesSap() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.sap.status").value("UP"));
    }
}
