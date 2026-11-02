package com.demo.itemintegration.site.controller;

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
import com.demo.itemintegration.site.dto.SiteDto;
import com.demo.itemintegration.site.model.SiteType;
import com.demo.itemintegration.site.service.SiteService;

@WebMvcTest(SiteController.class)
@Import(GlobalExceptionHandler.class)
class SiteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SiteService siteService;

    @Test
    void returnsSitesWithInternalFieldNamesAndLabels() throws Exception {
        SiteDto dto = new SiteDto(4332013900L, 1201L, "PEN-01", "Penang Assembly", SiteType.IC_ASSEMBLY,
                "Plot 13, Bayan Lepas", "Plot 13", null, null, "Bayan Lepas", null, "Penang", "11900", "Malaysia",
                5.2945, 100.2593);
        when(siteService.getSites(0, 25)).thenReturn(PageResponse.of(List.of(dto), 0, 25, 1L, false));

        mvc.perform(get("/api/sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[0].siteName").value("Penang Assembly"))
                .andExpect(jsonPath("$.objects[0].siteType").value("IC Assembly"))
                .andExpect(jsonPath("$.objects[0].cityLocality").value("Bayan Lepas"))
                .andExpect(jsonPath("$.objects[0].latitude").value(5.2945))
                .andExpect(jsonPath("$.totalObjects").value(1))
                .andExpect(content().string(Matchers.not(Matchers.containsString("address_"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("site__id"))));
    }

    @Test
    void returnsEmptyPageWhenThereAreNoSites() throws Exception {
        when(siteService.getSites(0, 25)).thenReturn(PageResponse.of(List.of(), 0, 25, 0L, false));

        mvc.perform(get("/api/sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.objects").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void rejectsInvalidPaging() throws Exception {
        mvc.perform(get("/api/sites").param("size", "0")).andExpect(status().isBadRequest());
    }
}
