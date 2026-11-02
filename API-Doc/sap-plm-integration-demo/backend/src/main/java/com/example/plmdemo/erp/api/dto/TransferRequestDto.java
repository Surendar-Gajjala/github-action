package com.example.plmdemo.erp.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** External request contract for POST /api/v1/bom-transfers. */
public record TransferRequestDto(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9][A-Z0-9_\\-]*",
                message = "must contain only upper-case letters, digits, '-' or '_'")
        String materialNumber,

        @NotBlank @Size(max = 2) @Pattern(regexp = "[A-Z0-9]+", message = "must be 1-2 upper-case letters or digits")
        String revision,

        @NotBlank @Pattern(regexp = "[A-Z0-9]{4}", message = "must be exactly 4 upper-case letters or digits")
        String plant) {
}
