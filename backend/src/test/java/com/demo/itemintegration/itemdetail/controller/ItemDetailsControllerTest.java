package com.demo.itemintegration.itemdetail.controller;

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
import com.demo.itemintegration.itemdetail.ItemNotFoundException;
import com.demo.itemintegration.itemdetail.dto.ItemOverviewDto;
import com.demo.itemintegration.itemdetail.dto.OverviewField;
import com.demo.itemintegration.itemdetail.dto.OverviewSection;
import com.demo.itemintegration.itemdetail.service.ItemDetailsService;
import com.demo.itemintegration.part.dto.PartDto;
import com.demo.itemintegration.part.model.LifecycleStatus;
import com.demo.itemintegration.part.model.SourcingType;
import com.demo.itemintegration.part.model.SupplyChainRisk;

@WebMvcTest(ItemDetailsController.class)
@Import(GlobalExceptionHandler.class)
class ItemDetailsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ItemDetailsService service;

    @Test
    void overview() throws Exception {
        when(service.getOverview(4332025200L)).thenReturn(new ItemOverviewDto(4332025200L, "A93548-290", "RES D",
                "01", "RESISTOR_DISCRETE", "Conditional", List.of(new OverviewSection("Risk",
                        List.of(new OverviewField("Cost Risk", "Not Assessed", "No active source"))))));

        mvc.perform(get("/api/items/4332025200/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemNumber").value("A93548-290"))
                .andExpect(jsonPath("$.sections[0].title").value("Risk"))
                .andExpect(jsonPath("$.sections[0].fields[0].reason").value("No active source"));
    }

    @Test
    void sourcesUseThePartContract() throws Exception {
        when(service.getSources(4332025200L)).thenReturn(List.of(new PartDto(2L, "RK73H1ETTP1693F",
                "KOA SPEER ELECTRONICS", null, null, null, SourcingType.OFF_THE_SHELF, SupplyChainRisk.MEDIUM,
                LifecycleStatus.UNKNOWN)));

        mvc.perform(get("/api/items/4332025200/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partNumber").value("RK73H1ETTP1693F"))
                .andExpect(jsonPath("$[0].sourcingType").value("Off-the-shelf"));
    }

    @Test
    void unknownItemIs404() throws Exception {
        when(service.getOverview(1L)).thenThrow(new ItemNotFoundException(1L));

        mvc.perform(get("/api/items/1/overview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
    }

    @Test
    void invalidIdIs400() throws Exception {
        mvc.perform(get("/api/items/0/overview")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/items/abc/sources")).andExpect(status().isBadRequest());
    }

    @Test
    void upstreamFailureUsesSharedErrorHandling() throws Exception {
        when(service.getSources(5L)).thenThrow(new ExternalApiException(Reason.TIMEOUT));

        mvc.perform(get("/api/items/5/sources")).andExpect(status().isGatewayTimeout());
    }
}
