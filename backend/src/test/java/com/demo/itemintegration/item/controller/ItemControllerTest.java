package com.demo.itemintegration.item.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.demo.itemintegration.common.error.GlobalExceptionHandler;
import com.demo.itemintegration.external.ExternalApiException;
import com.demo.itemintegration.external.ExternalApiException.Reason;
import com.demo.itemintegration.item.dto.ItemDto;
import com.demo.itemintegration.common.dto.PageResponse;
import com.demo.itemintegration.item.model.AvailabilityRisk;
import com.demo.itemintegration.item.model.StructureRole;
import com.demo.itemintegration.item.service.ItemService;

@WebMvcTest(ItemController.class)
@Import(GlobalExceptionHandler.class)
class ItemControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ItemService itemService;

    @Test
    void returnsItemsWithInternalFieldNames() throws Exception {
        ItemDto dto = new ItemDto(42L, "ITM-1", "Widget", "A", "Hardware", true, StructureRole.ITEM,
                List.of("Acme"), List.of(true), AvailabilityRisk.NOT_ASSESSED, "P1", 2, "Released");
        when(itemService.getItems(0, 25)).thenReturn(PageResponse.of(List.of(dto), 0, 25, 1L, false));

        mvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.objects[0].id").value(42))
                .andExpect(jsonPath("$.objects[0].itemNumber").value("ITM-1"))
                .andExpect(jsonPath("$.objects[0].isProduct").value(true))
                .andExpect(jsonPath("$.objects[0].structureRole").value("ITEM"))
                .andExpect(jsonPath("$.objects[0].odmName[0]").value("Acme"))
                .andExpect(jsonPath("$.objects[0].availabilityRisk").value("NOT ASSESSED"))
                .andExpect(jsonPath("$.objects[0].productFamiliesImpacted").value(2))
                .andExpect(content().string(Matchers.not(Matchers.containsString("item__"))));
    }

    @Test
    void passesRequestedPageAndReturnsPagingInfo() throws Exception {
        when(itemService.getItems(4, 50)).thenReturn(PageResponse.of(List.of(), 4, 50, 5631L, true));

        mvc.perform(get("/api/items").param("page", "4").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(4))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalObjects").value(5631))
                .andExpect(jsonPath("$.totalPages").value(113))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void rejectsInvalidPaging() throws Exception {
        mvc.perform(get("/api/items").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/items").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/items").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void upstreamUnauthorizedBecomesBadGatewayWithoutDetails() throws Exception {
        when(itemService.getItems(0, 25)).thenThrow(new ExternalApiException(Reason.UNAUTHORIZED, 401, null));

        mvc.perform(get("/api/items"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ITEM_SOURCE_ACCESS_DENIED"))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("bearer"))));
    }

    @Test
    void rateLimitPassesRetryAfter() throws Exception {
        when(itemService.getItems(0, 25))
                .thenThrow(new ExternalApiException(Reason.RATE_LIMITED, 429, Duration.ofSeconds(30)));

        mvc.perform(get("/api/items"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void timeoutBecomesGatewayTimeout() throws Exception {
        when(itemService.getItems(0, 25)).thenThrow(new ExternalApiException(Reason.TIMEOUT));

        mvc.perform(get("/api/items")).andExpect(status().isGatewayTimeout());
    }
}
