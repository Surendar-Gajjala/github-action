package com.demo.itemintegration.digikey.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.demo.itemintegration.digikey.dto.DigiKeyExternalProduct;
import com.demo.itemintegration.digikey.dto.DigiKeyProductDto;
import com.fasterxml.jackson.databind.ObjectMapper;

class DigiKeyProductMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final DigiKeyProductMapper mapper = new DigiKeyProductMapper();

    private static final String PRODUCT = """
            {"ManufacturerProductNumber": "RC0402FR-0710KL",
             "Manufacturer": {"Name": "YAGEO"},
             "Description": {"ProductDescription": "RES 10K OHM 1% 1/16W 0402"},
             "DatasheetUrl": "https://example.com/ds.pdf",
             "ProductUrl": "https://www.digikey.com/x",
             "QuantityAvailable": 500000,
             "UnitPrice": 0.1,
             "ProductVariations": [
               {"DigiKeyProductNumber": "311-10.0KLRCT-ND",
                "StandardPricing": [{"BreakQuantity": 1, "UnitPrice": 0.1}, {"BreakQuantity": 100, "UnitPrice": 0.02}]}
             ]}
            """;

    @Test
    void mapsExternalProductToInternalDto() throws Exception {
        DigiKeyProductDto dto = mapper.toDto(json.readValue(PRODUCT, DigiKeyExternalProduct.class), "INR");

        assertThat(dto.digiKeyPartNumber()).isEqualTo("311-10.0KLRCT-ND");
        assertThat(dto.manufacturer()).isEqualTo("YAGEO");
        assertThat(dto.manufacturerPartNumber()).isEqualTo("RC0402FR-0710KL");
        assertThat(dto.description()).isEqualTo("RES 10K OHM 1% 1/16W 0402");
        assertThat(dto.quantityAvailable()).isEqualTo(500000);
        assertThat(dto.unitPrice()).isEqualTo(0.1);
        assertThat(dto.currency()).isEqualTo("INR");
        assertThat(dto.datasheetUrl()).isEqualTo("https://example.com/ds.pdf");
        assertThat(dto.productUrl()).isEqualTo("https://www.digikey.com/x");
        assertThat(dto.priceBreaks()).hasSize(2);
        assertThat(dto.priceBreaks().get(1).breakQuantity()).isEqualTo(100);
        assertThat(dto.priceBreaks().get(1).unitPrice()).isEqualTo(0.02);
    }

    @Test
    void toleratesMissingNestedFields() throws Exception {
        DigiKeyProductDto dto = mapper.toDto(
                json.readValue("{\"ManufacturerProductNumber\": \"X\"}", DigiKeyExternalProduct.class), "USD");

        assertThat(dto.manufacturerPartNumber()).isEqualTo("X");
        assertThat(dto.digiKeyPartNumber()).isNull();
        assertThat(dto.manufacturer()).isNull();
        assertThat(dto.description()).isNull();
        assertThat(dto.priceBreaks()).isEmpty();
    }
}
