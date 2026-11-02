package com.demo.itemintegration.site.model;

/**
 * The application's internal Site. Held in memory only; never persisted.
 * {@code siteId} is the business site identifier; {@code id} is the hosted object
 * identifier (Long, as hosted identifiers exceed the int range).
 */
public record Site(
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
