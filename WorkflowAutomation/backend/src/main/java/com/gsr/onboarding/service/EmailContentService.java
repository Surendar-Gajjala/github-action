package com.gsr.onboarding.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.domain.entity.Employee;
import com.gsr.onboarding.domain.enums.ContentSource;
import com.gsr.onboarding.exception.IntegrationException;
import com.gsr.onboarding.integration.openai.GeneratedOnboardingContent;
import com.gsr.onboarding.integration.openai.OnboardingPromptInput;
import com.gsr.onboarding.integration.openai.OpenAIClient;

/**
 * Produces the welcome email and onboarding issue content. AI output is treated as untrusted:
 * it is schema-validated, checked for links/markup, normalised, and always HTML-escaped when rendered.
 * When OpenAI is unavailable or its output is rejected, a configurable fallback template is used.
 */
@Service
public class EmailContentService {

    private static final Logger log = LoggerFactory.getLogger(EmailContentService.class);

    private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.)");
    private static final Pattern MARKUP = Pattern.compile("<\\s*[a-zA-Z/!]");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{|\\}\\}");
    private static final Pattern LIST_PREFIX = Pattern.compile("^\\s*(?:[-*]\\s*)?(?:\\[\\s?[xX]?\\s?]\\s*)?(?:\\d+[.)]\\s*)?");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final String MARKER_PREFIX = "onboarding-workflow:";

    static final List<String> FALLBACK_CHECKLIST = List.of(
            "Complete HR paperwork and review the employee handbook",
            "Set up your laptop, email and single sign-on",
            "Set up your development environment",
            "Meet your reporting manager and onboarding buddy",
            "Join your team channels and recurring meetings",
            "Review the team documentation");

    /** Generated content plus where it came from. {@code note} explains why fallback content was used. */
    public record ContentResult(GeneratedOnboardingContent content, ContentSource source, String note) {
    }

    private final OpenAIClient openAIClient;
    private final Validator validator;
    private final AppProperties.OpenAi properties;
    private final String fallbackTemplate;

    public EmailContentService(OpenAIClient openAIClient, Validator validator, AppProperties appProperties,
                               ResourceLoader resourceLoader) {
        this.openAIClient = openAIClient;
        this.validator = validator;
        this.properties = appProperties.openai();
        this.fallbackTemplate = loadTemplate(resourceLoader, properties.fallbackTemplate());
    }

    public ContentResult generate(Employee employee) {
        OnboardingPromptInput input = OnboardingPromptInput.from(employee, properties.companyName());
        try {
            GeneratedOnboardingContent raw = openAIClient.generateOnboardingContent(input);
            GeneratedOnboardingContent content = normalise(raw);
            List<String> problems = validate(content, employee);
            if (!problems.isEmpty()) {
                throw new IntegrationException("AI output failed validation: " + String.join("; ", problems), true);
            }
            return new ContentResult(content, ContentSource.AI, null);
        } catch (IntegrationException e) {
            if (!properties.fallbackEnabled()) {
                throw e;
            }
            log.warn("OpenAI content unavailable, using fallback template: {}", e.getMessage());
            return new ContentResult(fallback(employee), ContentSource.FALLBACK,
                    "OpenAI unavailable, fallback template used: " + e.getMessage());
        }
    }

    /** Returns human-readable reasons why the content must not be used; empty when valid. */
    public List<String> validate(GeneratedOnboardingContent content, Employee employee) {
        List<String> problems = new ArrayList<>();
        if (content == null) {
            problems.add("no content");
            return problems;
        }
        for (ConstraintViolation<GeneratedOnboardingContent> violation : validator.validate(content)) {
            problems.add(violation.getPropertyPath() + " " + violation.getMessage());
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        String allText = String.join("\n", content.emailSubject(), content.emailBody(),
                String.join("\n", content.checklist()), content.githubIssueTitle(), content.githubIssueSummary());
        if (URL.matcher(allText).find()) {
            problems.add("content must not contain links");
        }
        if (MARKUP.matcher(allText).find()) {
            problems.add("content must not contain HTML or markup");
        }
        if (PLACEHOLDER.matcher(allText).find()) {
            problems.add("content contains unresolved placeholders");
        }
        if (!content.emailBody().contains(employee.getFirstName())) {
            problems.add("email body must address the employee by first name");
        }
        return problems;
    }

    public GeneratedOnboardingContent fallback(Employee employee) {
        String company = properties.companyName();
        String body = fallbackTemplate
                .replace("{{firstName}}", employee.getFirstName())
                .replace("{{company}}", company)
                .replace("{{department}}", employee.getDepartment())
                .replace("{{jobTitle}}", employee.getJobTitle())
                .replace("{{joiningDate}}", employee.getJoiningDate().format(DATE))
                .replace("{{reportingManager}}", employee.getReportingManager())
                .trim();
        return new GeneratedOnboardingContent(
                "Welcome to " + company + ", " + employee.getFirstName() + "!",
                body,
                FALLBACK_CHECKLIST,
                "Onboarding: " + employee.getFullName() + " (" + employee.getJobTitle() + ")",
                employee.getFirstName() + " joins the " + employee.getDepartment() + " team as "
                        + employee.getJobTitle() + " on " + employee.getJoiningDate() + ".");
    }

    public String renderPlainText(String body, List<String> checklist) {
        StringBuilder text = new StringBuilder(body.strip()).append("\n\nYour onboarding checklist:\n");
        checklist.forEach(item -> text.append(" - ").append(item).append('\n'));
        return text.toString();
    }

    /** Renders HTML from plain text. Every user- or AI-supplied value is HTML-escaped. */
    public String renderHtml(String body, List<String> checklist) {
        StringBuilder html = new StringBuilder(
                "<!DOCTYPE html><html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2933;line-height:1.5\">");
        for (String paragraph : body.strip().split("\\R\\s*\\R")) {
            html.append("<p>").append(HtmlUtils.htmlEscape(paragraph.strip()).replaceAll("\\R", "<br>")).append("</p>");
        }
        html.append("<h3 style=\"margin-top:24px\">Your onboarding checklist</h3><ul>");
        checklist.forEach(item -> html.append("<li>").append(HtmlUtils.htmlEscape(item)).append("</li>"));
        html.append("</ul></body></html>");
        return html.toString();
    }

    public static String marker(Long workflowId) {
        return "<!-- " + MARKER_PREFIX + workflowId + " -->";
    }

    /** Builds the onboarding issue markdown. AI-provided text is neutralised so it cannot mention users or inject markup. */
    public String buildIssueBody(Employee employee, Long workflowId, String summary, List<String> checklist) {
        StringBuilder md = new StringBuilder();
        md.append(marker(workflowId)).append("\n\n");
        md.append("## Onboarding: ").append(markdown(employee.getFullName())).append("\n\n");
        md.append("| Field | Value |\n|---|---|\n");
        row(md, "Employee ID", employee.getEmployeeNumber());
        row(md, "Name", employee.getFullName());
        row(md, "Role", employee.getJobTitle());
        row(md, "Department", employee.getDepartment());
        row(md, "Joining date", employee.getJoiningDate().toString());
        row(md, "Reporting manager", employee.getReportingManager());
        row(md, "GitHub username", "`" + employee.getGithubUsername() + "`");
        row(md, "Requested repository", "`" + employee.getGithubRepository() + "`");
        row(md, "Requested access level", employee.getAccessLevel().name());
        md.append("\n### Summary\n\n").append(markdown(summary)).append("\n\n");
        md.append("### Onboarding tasks\n\n");
        md.append("- [ ] Repository access: grant `").append(employee.getAccessLevel().name()).append("` on `")
                .append(employee.getGithubRepository()).append("` after approval by a repository administrator\n");
        md.append("- [ ] Development environment setup\n");
        md.append("- [ ] Team introductions\n");
        md.append("- [ ] Documentation review\n\n");
        md.append("### Personalised checklist\n\n");
        checklist.forEach(item -> md.append("- [ ] ").append(markdown(item)).append('\n'));
        md.append("\n> **Note:** This issue does not grant any access. Repository permissions must be provisioned ")
                .append("separately with explicit authorization and audit logging.\n");
        return md.toString();
    }

    private static void row(StringBuilder md, String field, String value) {
        String safe = value.startsWith("`") ? value : markdown(value);
        md.append("| ").append(field).append(" | ").append(safe.replace("|", "\\|")).append(" |\n");
    }

    /** Neutralises @mentions, HTML comments and tags in untrusted text destined for GitHub markdown. */
    static String markdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("@", "@​")
                .strip();
    }

    private GeneratedOnboardingContent normalise(GeneratedOnboardingContent raw) {
        if (raw == null) {
            return null;
        }
        Set<String> items = new LinkedHashSet<>();
        if (raw.checklist() != null) {
            for (String item : raw.checklist()) {
                if (item != null) {
                    String cleaned = LIST_PREFIX.matcher(item).replaceFirst("").strip();
                    if (!cleaned.isEmpty()) {
                        items.add(cleaned);
                    }
                }
            }
        }
        return new GeneratedOnboardingContent(
                strip(raw.emailSubject()),
                strip(raw.emailBody()),
                List.copyOf(items),
                strip(raw.githubIssueTitle()),
                strip(raw.githubIssueSummary()));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String loadTemplate(ResourceLoader loader, String location) {
        try (InputStream in = loader.getResource(location).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Fallback email template not found: " + location, e);
        }
    }
}
