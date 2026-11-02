package com.demo.itemintegration.partdetail.controller;

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

import com.demo.itemintegration.common.error.GlobalExceptionHandler;
import com.demo.itemintegration.external.ExternalApiException;
import com.demo.itemintegration.external.ExternalApiException.Reason;
import com.demo.itemintegration.itemdetail.dto.OverviewField;
import com.demo.itemintegration.itemdetail.dto.OverviewSection;
import com.demo.itemintegration.partdetail.PartNotFoundException;
import com.demo.itemintegration.partdetail.dto.PartOverviewDto;
import com.demo.itemintegration.partdetail.service.PartDetailsService;

@WebMvcTest(PartDetailsController.class)
@Import(GlobalExceptionHandler.class)
class PartDetailsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PartDetailsService service;

    @Test
    void overview() throws Exception {
        when(service.getOverview(4332025900L)).thenReturn(new PartOverviewDto(4332025900L, "Y5363689",
                "BRADY CORPORATION", null, List.of(new OverviewSection("Risk",
                        List.of(new OverviewField("Availability Risk", "Not Assessed", "Lifecycle unknown."))))));

        mvc.perform(get("/api/parts/4332025900/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partNumber").value("Y5363689"))
                .andExpect(jsonPath("$.manufacturer").value("BRADY CORPORATION"))
                .andExpect(jsonPath("$.sections[0].title").value("Risk"))
                .andExpect(jsonPath("$.sections[0].fields[0].reason").value("Lifecycle unknown."));
    }

    @Test
    void unknownPartIs404() throws Exception {
        when(service.getOverview(1L)).thenThrow(new PartNotFoundException(1L));

        mvc.perform(get("/api/parts/1/overview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PART_NOT_FOUND"));
    }

    @Test
    void invalidIdIs400() throws Exception {
        mvc.perform(get("/api/parts/0/overview")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/parts/abc/overview")).andExpect(status().isBadRequest());
    }

    @Test
    void upstreamFailureUsesSharedErrorHandling() throws Exception {
        when(service.getOverview(5L)).thenThrow(new ExternalApiException(Reason.TIMEOUT));

        mvc.perform(get("/api/parts/5/overview")).andExpect(status().isGatewayTimeout());
    }
}
