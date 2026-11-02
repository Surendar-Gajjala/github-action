package com.demo.itemintegration.part.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.common.error.GlobalExceptionHandler;
import com.demo.itemintegration.external.ExternalApiException;
import com.demo.itemintegration.external.ExternalApiException.Reason;
import com.demo.itemintegration.part.dto.PartDto;
import com.demo.itemintegration.part.model.LifecycleStatus;
import com.demo.itemintegration.part.model.SourcingType;
import com.demo.itemintegration.part.model.SupplyChainRisk;
import com.demo.itemintegration.part.service.PartService;

@WebMvcTest(PartController.class)
@Import(GlobalExceptionHandler.class)
class PartControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PartService partService;

    @Test
    void returnsPartsWithInternalFieldNamesAndLabels() throws Exception {
        PartDto dto = new PartDto(4332025100L, "FC FBMJ3216HS480NT", "TAIYO YUDEN", null, "incomparable", "China",
                SourcingType.OFF_THE_SHELF, SupplyChainRisk.NOT_ASSESSED, LifecycleStatus.LAST_TIME_BUY);
        when(partService.getParts(1, 50)).thenReturn(PageResponse.of(List.of(dto), 1, 50, 10994L, true));

        mvc.perform(get("/api/parts").param("page", "1").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[0].id").value(4332025100L))
                .andExpect(jsonPath("$.objects[0].partNumber").value("FC FBMJ3216HS480NT"))
                .andExpect(jsonPath("$.objects[0].countryOfOrigin").value("China"))
                .andExpect(jsonPath("$.objects[0].sourcingType").value("Off-the-shelf"))
                .andExpect(jsonPath("$.objects[0].supplyChainRisk").value("Not Assessed"))
                .andExpect(jsonPath("$.objects[0].lifecycleStatus").value("LastTimeBuy"))
                .andExpect(jsonPath("$.totalObjects").value(10994))
                .andExpect(jsonPath("$.totalPages").value(220))
                .andExpect(content().string(Matchers.not(Matchers.containsString("part__"))));
    }

    @Test
    void rejectsInvalidPaging() throws Exception {
        mvc.perform(get("/api/parts").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/parts").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void upstreamFailureUsesSharedErrorHandling() throws Exception {
        when(partService.getParts(0, 25)).thenThrow(new ExternalApiException(Reason.TIMEOUT));

        mvc.perform(get("/api/parts")).andExpect(status().isGatewayTimeout());
    }
}
