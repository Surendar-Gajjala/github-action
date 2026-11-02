package com.demo.itemintegration.site.mapper;

import static com.demo.itemintegration.common.mapping.ExternalValues.enumKey;
import static com.demo.itemintegration.common.mapping.ExternalValues.text;
import static com.demo.itemintegration.common.mapping.ExternalValues.toDecimal;
import static com.demo.itemintegration.common.mapping.ExternalValues.toLong;

import org.springframework.stereotype.Component;

import com.demo.itemintegration.external.dto.ExternalSiteRecord;
import com.demo.itemintegration.site.dto.SiteDto;
import com.demo.itemintegration.site.model.Site;
import com.demo.itemintegration.site.model.SiteType;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The boundary between the hosted server's Site representation and the internal Site
 * model. Generic value rules come from
 * {@link com.demo.itemintegration.common.mapping.ExternalValues}; Site-specific rules:
 * <ul>
 *   <li>site type: case-insensitive, {@code _}/{@code -} treated as spaces
 *       ({@code IC_ASSEMBLY} → IC Assembly); blank or unrecognised becomes {@code null}</li>
 *   <li>latitude / longitude: decimal numbers from JSON numbers or numeric strings</li>
 * </ul>
 * The hosted environment had no Site rows when this was written; the rules follow the
 * Site specification and should be re-checked once real rows exist.
 */
@Component
public class SiteMapper {

    public Site toSite(ExternalSiteRecord external) {
        return new Site(
                toLong(external.id()),
                toLong(external.siteId()),
                text(external.internalSiteId()),
                text(external.siteName()),
                toSiteType(external.siteType()),
                text(external.fullAddress()),
                text(external.addressLine1()),
                text(external.addressLine2()),
                text(external.addressLine3()),
                text(external.cityLocality()),
                text(external.districtCounty()),
                text(external.stateProvince()),
                text(external.postalCode()),
                text(external.country()),
                toDecimal(external.latitude()),
                toDecimal(external.longitude()));
    }

    public SiteDto toDto(Site site) {
        return new SiteDto(
                site.id(),
                site.siteId(),
                site.internalSiteId(),
                site.siteName(),
                site.siteType(),
                site.fullAddress(),
                site.addressLine1(),
                site.addressLine2(),
                site.addressLine3(),
                site.cityLocality(),
                site.districtCounty(),
                site.stateProvince(),
                site.postalCode(),
                site.country(),
                site.latitude(),
                site.longitude());
    }

    static SiteType toSiteType(JsonNode node) {
        String key = enumKey(node);
        if (key == null) {
            return null;
        }
        return switch (key) {
            case "FABRICATION", "FAB" -> SiteType.FABRICATION;
            case "IC ASSEMBLY" -> SiteType.IC_ASSEMBLY;
            case "FINAL ASSEMBLY" -> SiteType.FINAL_ASSEMBLY;
            case "TEST" -> SiteType.TEST;
            case "PACKAGING" -> SiteType.PACKAGING;
            case "WAREHOUSE" -> SiteType.WAREHOUSE;
            case "HQ", "HEADQUARTERS" -> SiteType.HQ;
            case "OFFICE" -> SiteType.OFFICE;
            default -> null;
        };
    }
}
