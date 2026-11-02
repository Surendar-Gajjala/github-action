package com.demo.itemintegration.itemdetail.mapper;

import static com.demo.itemintegration.common.mapping.ExternalValues.list;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.demo.itemintegration.common.mapping.CodeLabels;
import com.demo.itemintegration.common.mapping.ExternalValues;
import com.demo.itemintegration.external.dto.ExternalObjectResponse;
import com.demo.itemintegration.itemdetail.dto.OverviewField;
import com.demo.itemintegration.itemdetail.dto.OverviewSection;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared machinery for the Overview tab of any object (item or part). A mapper supplies
 * a list of {@link SectionSpec}s naming the external properties to show and how to
 * format each; {@link #build} reads those from a hosted partial-object response and
 * produces display-ready sections. The spec tables are the only place that knows the
 * external property names; properties not listed are not exposed.
 */
public final class ObjectOverviewMapper {

    private ObjectOverviewMapper() {
    }

    /** How a raw value becomes display text. */
    public enum Kind {
        TEXT(ExternalValues::text),
        CODE(CodeLabels::label),
        BOOL(node -> {
            Boolean value = ExternalValues.toBoolean(node);
            return value == null ? null : value ? "Yes" : "No";
        }),
        CODES(node -> {
            List<String> labels = list(node, element -> CodeLabels.label(element));
            return labels.isEmpty() ? null : String.join(", ", labels);
        });

        private final Function<JsonNode, String> format;

        Kind(Function<JsonNode, String> format) {
            this.format = format;
        }
    }

    /** One field: which external property, its label, how to format it, and an optional reason property. */
    public record Spec(String external, String label, Kind kind, String reasonExternal) {
        public static Spec of(String external, String label, Kind kind) {
            return new Spec(external, label, kind, null);
        }
    }

    public record SectionSpec(String title, List<Spec> fields) {
    }

    /** Reads every spec from the object and groups the results into display-ready sections. */
    public static List<OverviewSection> build(List<SectionSpec> sections, ExternalObjectResponse object) {
        List<OverviewSection> result = new ArrayList<>(sections.size());
        for (SectionSpec section : sections) {
            List<OverviewField> fields = new ArrayList<>(section.fields().size());
            for (Spec spec : section.fields()) {
                String reason = spec.reasonExternal() == null
                        ? null
                        : ExternalValues.text(object.value(spec.reasonExternal()));
                fields.add(new OverviewField(spec.label(), spec.kind().format.apply(object.value(spec.external())), reason));
            }
            result.add(new OverviewSection(section.title(), fields));
        }
        return result;
    }
}
