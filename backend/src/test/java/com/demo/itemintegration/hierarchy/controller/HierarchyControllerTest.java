package com.demo.itemintegration.hierarchy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.demo.itemintegration.hierarchy.dto.HierarchyItemDto;
import com.demo.itemintegration.hierarchy.dto.HierarchyNodeDto;
import com.demo.itemintegration.hierarchy.dto.HierarchyPartDto;
import com.demo.itemintegration.hierarchy.model.NodeKind;
import com.demo.itemintegration.hierarchy.service.HierarchyService;
import com.demo.itemintegration.item.model.StructureRole;

@WebMvcTest(HierarchyController.class)
@Import(GlobalExceptionHandler.class)
class HierarchyControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private HierarchyService hierarchyService;

    private static HierarchyItemDto item(String number) {
        return new HierarchyItemDto(number, "OPTIC KIT", "20", "MAT", "Production Approved", "MAKE", "LAD", false,
                "N/A", "10GBIT", null, null, "Not Locked", "Yes", null, null, null, null, null, null, null, null,
                "NPO", true, StructureRole.BOM);
    }

    @Test
    void returnsNestedTreeWithInternalNames() throws Exception {
        HierarchyNodeDto part = new HierarchyNodeDto("1/2/9", NodeKind.PART, 9L, null, null,
                new HierarchyPartDto("FTLX8574D3BCV-IT", "FINISAR CORPORATION"), List.of());
        HierarchyNodeDto child = new HierarchyNodeDto("1/2", NodeKind.ITEM, 2L, new BigDecimal("0.001"),
                item("E70293-013"), null, List.of(part));
        HierarchyNodeDto root = new HierarchyNodeDto("1", NodeKind.ITEM, 1L, null, item("903239"), null, List.of(child));
        when(hierarchyService.getHierarchy(0, 25)).thenReturn(PageResponse.of(List.of(root), 0, 25, 292L, true));

        mvc.perform(get("/api/item-hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalObjects").value(292))
                .andExpect(jsonPath("$.objects[0].kind").value("ITEM"))
                .andExpect(jsonPath("$.objects[0].item.itemNumber").value("903239"))
                .andExpect(jsonPath("$.objects[0].item.itemStatus").value("Production Approved"))
                .andExpect(jsonPath("$.objects[0].qty").doesNotExist())
                .andExpect(jsonPath("$.objects[0].children[0].qty").value(0.001))
                .andExpect(jsonPath("$.objects[0].children[0].children[0].kind").value("PART"))
                .andExpect(jsonPath("$.objects[0].children[0].children[0].part.manufacturer").value("FINISAR CORPORATION"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("environmental_compliance"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("item_number"))));
    }

    @Test
    void productsReturnsTopLevelItemNumbersAndTotal() throws Exception {
        when(hierarchyService.getProducts(0, 1)).thenReturn(PageResponse.of(List.of("903239"), 0, 1, 292L, true));

        mvc.perform(get("/api/item-hierarchy/products").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[0]").value("903239"))
                .andExpect(jsonPath("$.totalObjects").value(292));
    }

    @Test
    void rejectsInvalidPaging() throws Exception {
        mvc.perform(get("/api/item-hierarchy/products").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/item-hierarchy").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/item-hierarchy").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void upstreamFailureUsesSharedErrorHandling() throws Exception {
        when(hierarchyService.getHierarchy(0, 25)).thenThrow(new ExternalApiException(Reason.TIMEOUT));

        mvc.perform(get("/api/item-hierarchy")).andExpect(status().isGatewayTimeout());
    }
}
