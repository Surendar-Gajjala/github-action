package com.demo.itemintegration.digikey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.demo.itemintegration.digikey.client.DigiKeyApiClient;
import com.demo.itemintegration.digikey.config.DigiKeyProperties;
import com.demo.itemintegration.digikey.dto.DigiKeyExternalProduct;
import com.demo.itemintegration.digikey.dto.DigiKeyExternalProduct.Manufacturer;
import com.demo.itemintegration.digikey.dto.DigiKeyExternalProduct.Pricing;
import com.demo.itemintegration.digikey.dto.DigiKeyExternalProduct.ProductVariation;
import com.demo.itemintegration.digikey.dto.DigiKeyKeywordResponse;
import com.demo.itemintegration.digikey.dto.DigiKeySearchRequest;
import com.demo.itemintegration.digikey.dto.DigiKeySearchResponse;
import com.demo.itemintegration.digikey.mapper.DigiKeyProductMapper;

class DigiKeyServiceTest {

    private final DigiKeyApiClient client = mock(DigiKeyApiClient.class);
    private final DigiKeyProperties properties = new DigiKeyProperties(
            "https://api.digikey.com", "id", "secret", "IN", "en", "INR", "resistor",
            Duration.ofSeconds(5), Duration.ofSeconds(30));
    private final DigiKeyService service = new DigiKeyService(client, new DigiKeyProductMapper(), properties);

    private static DigiKeyKeywordResponse oneProduct() {
        DigiKeyExternalProduct product = new DigiKeyExternalProduct(
                "RC0402FR-0710KL", new Manufacturer("YAGEO"),
                new DigiKeyExternalProduct.Description("RES 10K"), "ds", "url", 1000, 0.1,
                List.of(new ProductVariation("311-ND", List.of(new Pricing(1, 0.1)))));
        return new DigiKeyKeywordResponse(List.of(product), 1);
    }

    @Test
    void mapsSearchResultsToTheInternalModel() {
        when(client.searchKeyword(any())).thenReturn(oneProduct());

        DigiKeySearchResponse response = service.search("10k resistor", 25, 0);

        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).digiKeyPartNumber()).isEqualTo("311-ND");
        assertThat(response.products().get(0).currency()).isEqualTo("INR");
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.limit()).isEqualTo(25);
    }

    @Test
    void blankKeywordFallsBackToTheConfiguredDefault() {
        when(client.searchKeyword(any())).thenReturn(new DigiKeyKeywordResponse(List.of(), 0));

        service.search("  ", 25, 0);

        ArgumentCaptor<DigiKeySearchRequest> captor = ArgumentCaptor.forClass(DigiKeySearchRequest.class);
        org.mockito.Mockito.verify(client).searchKeyword(captor.capture());
        assertThat(captor.getValue().keywords()).isEqualTo("resistor");
    }
}
