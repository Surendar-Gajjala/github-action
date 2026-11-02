package com.example.plmdemo.cad.api.dto;

import com.example.plmdemo.cad.model.CadAssembly;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * External request contract for POST /api/v1/cad/imports.
 * DEMO FORMAT: a simplified CAD assembly export, not an SAP ECTR or CAD vendor format.
 */
public record CadExportDto(@NotNull @Valid Source source, @NotNull @Valid Assembly assembly) {

    private static final String PART = "[A-Za-z0-9][A-Za-z0-9_\\-]*";

    public record Source(
            @NotBlank @Size(max = 40) String cadSystem,
            @Size(max = 200) String fileName,
            @Size(max = 40) String exportedAt,
            @Size(max = 40) String exportedBy) {
    }

    public record Assembly(
            @NotBlank @Size(max = 40) @Pattern(regexp = PART, message = "must contain only letters, digits, '-' or '_'")
            String partNumber,
            @NotBlank @Size(max = 2) @Pattern(regexp = "[A-Z0-9]+", message = "must be 1-2 upper-case letters or digits")
            String revision,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{4}", message = "must be exactly 4 upper-case letters or digits")
            String plant,
            @Size(max = 20) String changeNumber,
            @Size(max = 80) String description,
            Map<String, Object> attributes,
            @NotNull @Size(min = 1, max = 500) List<@Valid Component> components) {
    }

    public record Component(
            @Size(max = 40) String instanceId,
            @NotBlank @Size(max = 40) @Pattern(regexp = PART, message = "must contain only letters, digits, '-' or '_'")
            String partNumber,
            @Size(max = 2) String revision,
            @Size(max = 80) String description,
            BigDecimal quantity,
            @Size(max = 3) String unit,
            @Size(max = 200) String fileName,
            Boolean excludeFromBom) {
    }

    public CadAssembly toDomain() {
        return new CadAssembly(source.cadSystem(), source.fileName(), source.exportedBy(),
                assembly.partNumber(), assembly.revision(), assembly.plant(), assembly.changeNumber(),
                assembly.description(),
                assembly.components().stream()
                        .map(c -> new CadAssembly.Component(c.instanceId(), c.partNumber(), c.revision(),
                                c.description(), c.quantity(), c.unit(), c.fileName(),
                                Boolean.TRUE.equals(c.excludeFromBom())))
                        .toList());
    }
}
