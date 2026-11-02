package com.demo.itemintegration.site.dto;

import com.demo.itemintegration.site.model.SiteType;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Site as exposed by the local REST API. Uses internal field names only. */
@JsonPropertyOrder({"id", "siteId", "internalSiteId", "siteName", "siteType", "fullAddress", "addressLine1",
        "addressLine2", "addressLine3", "cityLocality", "districtCounty", "stateProvince", "postalCode",
        "country", "latitude", "longitude"})
public record SiteDto(
        Long id,
        Long siteId,
        String internalSiteId,
        String siteName,
        SiteType siteType,
        String fullAddress,
        String addressLine1,
        String addressLine2,
        String addressLine3,
        String cityLocality,
        String districtCounty,
        String stateProvince,
        String postalCode,
        String country,
        Double latitude,
        Double longitude) {
}
