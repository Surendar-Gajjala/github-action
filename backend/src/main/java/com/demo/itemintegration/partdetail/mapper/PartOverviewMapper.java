package com.demo.itemintegration.partdetail.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.demo.itemintegration.common.mapping.ExternalValues;
import com.demo.itemintegration.external.dto.ExternalObjectResponse;
import com.demo.itemintegration.itemdetail.mapper.ObjectOverviewMapper;
import com.demo.itemintegration.itemdetail.mapper.ObjectOverviewMapper.Kind;
import com.demo.itemintegration.itemdetail.mapper.ObjectOverviewMapper.SectionSpec;
import com.demo.itemintegration.itemdetail.mapper.ObjectOverviewMapper.Spec;
import com.demo.itemintegration.partdetail.dto.PartOverviewDto;

/**
 * Maps the hosted partial-object response of a part to the Overview tab. The field table
 * below is the only place that knows the external property names; properties not listed
 * are not exposed. The section-building machinery is shared with items via
 * {@link ObjectOverviewMapper}.
 */
@Component
public class PartOverviewMapper {

    private static final List<SectionSpec> SECTIONS = List.of(
            new SectionSpec("General", List.of(
                    Spec.of("part_number", "Part Number", Kind.TEXT),
                    Spec.of("manufacturer", "Manufacturer", Kind.TEXT),
                    Spec.of("mfr_nbr", "Manufacturer Number", Kind.CODES),
                    Spec.of("description", "Description", Kind.TEXT),
                    Spec.of("country_of_origin", "Country of Origin", Kind.TEXT),
                    Spec.of("sourcing_type", "Sourcing Type", Kind.CODE),
                    Spec.of("z2_properties_comparison", "Z2 Properties Comparison", Kind.TEXT))),
            new SectionSpec("Risk", List.of(
                    new Spec("availability_risk", "Availability Risk", Kind.CODE, "availability_risk_reason"),
                    new Spec("supply_chain_risk", "Supply Chain Risk", Kind.CODE, "supply_chain_risk_reason"),
                    new Spec("lifecycle.risk", "Lifecycle Risk", Kind.CODE, "lifecycle.risk_reason"),
                    new Spec("cost_risk", "Cost Risk", Kind.CODE, "cost_risk_reason"),
                    new Spec("environmental_compliance_risk", "Environmental Compliance Risk", Kind.CODE,
                            "environmental_compliance_risk_reason"))),
            new SectionSpec("Environmental Compliance", List.of(
                    Spec.of("environmental_compliance.eu_rohs", "EU RoHS", Kind.CODE),
                    Spec.of("environmental_compliance.pwb_lead_halogen", "PWB Lead / Halogen", Kind.CODE),
                    Spec.of("environmental_compliance.plastic_lead_halogen", "Plastic Lead / Halogen", Kind.CODE),
                    Spec.of("environmental_compliance.pnr", "PNR", Kind.CODE),
                    Spec.of("environmental_compliance.needs_review", "Needs Review", Kind.BOOL))),
            new SectionSpec("Usage & Impact", List.of(
                    Spec.of("used_in_products", "Used In Products", Kind.TEXT),
                    Spec.of("products_impacted", "Products Impacted", Kind.TEXT),
                    Spec.of("product_families_impacted", "Product Families Impacted", Kind.TEXT),
                    Spec.of("business_units_impacted", "Business Units Impacted", Kind.TEXT),
                    Spec.of("manufacturers_impacted", "Manufacturers Impacted", Kind.TEXT),
                    Spec.of("preferred_manufacturers_impacted", "Preferred Manufacturers Impacted", Kind.TEXT))),
            new SectionSpec("Z2 Data", List.of(
                    Spec.of("z2data.z2_match_status", "Z2 Match Status", Kind.CODE),
                    Spec.of("z2data.z2_match_type", "Z2 Match Type", Kind.CODE),
                    Spec.of("z2data.z2_last_validated_at", "Z2 Last Validated", Kind.TEXT))));

    public PartOverviewDto toOverview(ExternalObjectResponse object) {
        return new PartOverviewDto(
                object.objectId(),
                ExternalValues.text(object.value("part_number")),
                ExternalValues.text(object.value("manufacturer")),
                ExternalValues.text(object.value("description")),
                ObjectOverviewMapper.build(SECTIONS, object));
    }
}
