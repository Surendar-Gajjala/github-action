package com.demo.itemintegration.digikey.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.demo.itemintegration.digikey.dto.DigiKeyProductDto;
import com.demo.itemintegration.digikey.dto.DigiKeySearchResponse;
import com.demo.itemintegration.digikey.exception.DigiKeyApiException;
import com.demo.itemintegration.digikey.exception.DigiKeyApiException.Reason;
import com.demo.itemintegration.digikey.exception.DigiKeyExceptionHandler;
import com.demo.itemintegration.digikey.service.DigiKeyService;

@WebMvcTest(DigiKeyController.class)
@Import(DigiKeyExceptionHandler.class)
class DigiKeyControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DigiKeyService service;

    private static DigiKeyProductDto product() {
        return new DigiKeyProductDto("311-ND", "YAGEO", "RC0402FR-0710KL", "RES 10K", 1000, 0.1, "INR",
                "ds", "url", List.of());
    }

    @Test
    void searchReturnsResults() throws Exception {
        when(service.search(eq("resistor"), anyInt(), anyInt()))
                .thenReturn(new DigiKeySearchResponse(List.of(product()), 1, 25, 0));

        mvc.perform(get("/api/digikey/products/search").param("keyword", "resistor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].digiKeyPartNumber").value("311-ND"))
                .andExpect(jsonPath("$.products[0].manufacturer").value("YAGEO"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void clampsLimitToTheDigiKeyMaximumAndFloor() throws Exception {
        when(service.search(eq("resistor"), anyInt(), anyInt()))
                .thenReturn(new DigiKeySearchResponse(List.of(), 0, 50, 0));

        mvc.perform(get("/api/digikey/products/search").param("keyword", "resistor").param("limit", "100"))
                .andExpect(status().isOk());
        verify(service).search("resistor", 50, 0);

        mvc.perform(get("/api/digikey/products/search").param("keyword", "resistor").param("limit", "0"))
                .andExpect(status().isOk());
        verify(service).search("resistor", 1, 0);
    }

    @Test
    void rejectsNegativeOffset() throws Exception {
        mvc.perform(get("/api/digikey/products/search").param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productDetailByNumber() throws Exception {
        when(service.getProduct("311-ND")).thenReturn(product());

        mvc.perform(get("/api/digikey/products/311-ND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manufacturerPartNumber").value("RC0402FR-0710KL"));
    }

    @Test
    void notConfiguredIsServiceUnavailableWithCode() throws Exception {
        when(service.search(eq("resistor"), anyInt(), anyInt()))
                .thenThrow(new DigiKeyApiException(Reason.NOT_CONFIGURED));

        mvc.perform(get("/api/digikey/products/search").param("keyword", "resistor"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DIGIKEY_NOT_CONFIGURED"));
    }

    @Test
    void rateLimitedIsMappedTo429() throws Exception {
        when(service.search(eq("resistor"), anyInt(), anyInt()))
                .thenThrow(new DigiKeyApiException(Reason.RATE_LIMITED));

        mvc.perform(get("/api/digikey/products/search").param("keyword", "resistor"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DIGIKEY_RATE_LIMITED"));
    }
}
