package com.gsr.onboarding.integration.openai;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.gsr.onboarding.dto.ValidationPatterns;

/**
 * Structured output expected from the language model. Instances are untrusted until they pass
 * Bean Validation and the extra content checks in {@code EmailContentService}.
 */
public record GeneratedOnboardingContent(
        @NotBlank @Size(max = 150) @Pattern(regexp = ValidationPatterns.SINGLE_LINE)
        String emailSubject,

        @NotBlank @Size(min = 40, max = 4000)
        String emailBody,

        @NotNull @Size(min = 3, max = 12)
        List<@NotBlank @Size(max = 200) @Pattern(regexp = ValidationPatterns.SINGLE_LINE) String> checklist,

        @NotBlank @Size(max = 150) @Pattern(regexp = ValidationPatterns.SINGLE_LINE)
        String githubIssueTitle,

        @NotBlank @Size(max = 3000)
        String githubIssueSummary) {

    /** JSON schema sent to the OpenAI structured-output API. Keep in sync with the record components. */
    public static final String JSON_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["emailSubject", "emailBody", "checklist", "githubIssueTitle", "githubIssueSummary"],
              "properties": {
                "emailSubject": {"type": "string", "description": "Single-line welcome email subject"},
                "emailBody": {"type": "string", "description": "Plain-text welcome email body"},
                "checklist": {"type": "array", "items": {"type": "string"}, "description": "5-10 onboarding checklist items"},
                "githubIssueTitle": {"type": "string", "description": "Single-line GitHub onboarding issue title"},
                "githubIssueSummary": {"type": "string", "description": "Short markdown summary for the onboarding issue"}
              }
            }
            """;
}
