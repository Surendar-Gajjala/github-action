package com.demo.itemintegration.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One Site row exactly as the hosted server names it (the aliases in
 * {@code site-query.json}). External field names live only here. Values are kept as
 * raw JSON so the mapper can normalise inconsistent types without failing the request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalSiteRecord(
        @JsonProperty("site_id") JsonNode siteId,
        @JsonProperty("internal_site_id") JsonNode internalSiteId,
        @JsonProperty("site_name") JsonNode siteName,
        @JsonProperty("site_type") JsonNode siteType,
        @JsonProperty("address_full_address") JsonNode fullAddress,
        @JsonProperty("address_address_line_1") JsonNode addressLine1,
        @JsonProperty("address_address_line_2") JsonNode addressLine2,
        @JsonProperty("address_address_line_3") JsonNode addressLine3,
        @JsonProperty("address_city_locality") JsonNode cityLocality,
        @JsonProperty("address_district_county") JsonNode districtCounty,
        @JsonProperty("address_state_province") JsonNode stateProvince,
        @JsonProperty("address_postal_code") JsonNode postalCode,
        @JsonProperty("address_country") JsonNode country,
        @JsonProperty("address_latitude") JsonNode latitude,
        @JsonProperty("address_longitude") JsonNode longitude,
        @JsonProperty("site__id") JsonNode id) {
}
