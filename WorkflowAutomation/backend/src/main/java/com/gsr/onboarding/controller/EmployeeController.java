package com.gsr.onboarding.controller;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsr.onboarding.config.SecurityConfig;
import com.gsr.onboarding.dto.ArtifactResponses.EmailApprovalRequest;
import com.gsr.onboarding.dto.ArtifactResponses.EmailPreviewResponse;
import com.gsr.onboarding.dto.ArtifactResponses.GitHubIssueResponse;
import com.gsr.onboarding.dto.EmployeeRequest;
import com.gsr.onboarding.dto.EmployeeResponses.EmployeeCreatedResponse;
import com.gsr.onboarding.dto.EmployeeResponses.EmployeeDetailResponse;
import com.gsr.onboarding.dto.EmployeeResponses.EmployeeSummaryResponse;
import com.gsr.onboarding.dto.PageResponse;
import com.gsr.onboarding.dto.WorkflowResponse;
import com.gsr.onboarding.service.EmployeeService;
import com.gsr.onboarding.workflow.EmployeeOnboardingWorkflowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees", description = "Employee onboarding requests and workflow tracking")
public class EmployeeController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final EmployeeService employeeService;
    private final EmployeeOnboardingWorkflowService workflowService;

    public EmployeeController(EmployeeService employeeService, EmployeeOnboardingWorkflowService workflowService) {
        this.employeeService = employeeService;
        this.workflowService = workflowService;
    }

    @PostMapping
    @Operation(summary = "Validate and create an employee onboarding request",
            description = "Saves the employee and starts the workflow asynchronously. Returns 202 with the workflow ID; "
                    + "poll GET /api/employees/{id}/workflow for progress. Repeating a request with the same "
                    + "Idempotency-Key returns the original result with 200.")
    public ResponseEntity<EmployeeCreatedResponse> create(
            @Valid @RequestBody EmployeeRequest request,
            @Parameter(description = "Client-generated unique key (e.g. a UUID) that makes the request safe to repeat")
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false)
            @Size(min = 8, max = 100) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String idempotencyKey) {
        EmployeeCreatedResponse created = employeeService.create(request, idempotencyKey);
        HttpStatus status = created.duplicateRequest() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .location(URI.create("/api/employees/" + created.employeeId()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List employees with search and pagination",
            description = "Searches name, email, employee ID and department. Results are newest first.")
    public PageResponse<EmployeeSummaryResponse> list(
            @RequestParam(name = "q", required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(EmployeeService.MAX_PAGE_SIZE) int size) {
        return employeeService.list(query, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get employee details with the workflow summary")
    public EmployeeDetailResponse get(@PathVariable Long id) {
        return employeeService.get(id);
    }

    @GetMapping("/{id}/workflow")
    @Operation(summary = "Get workflow status, step details and execution history")
    public WorkflowResponse workflow(@PathVariable Long id) {
        return workflowService.getWorkflow(id);
    }

    @PostMapping("/{id}/workflow/retry")
    @Operation(summary = "Retry failed workflow steps",
            description = "Re-queues FAILED steps. Steps with an uncertain outcome (interrupted email delivery) require "
                    + "force=true, which is restricted to administrators because it may send a duplicate email.")
    public ResponseEntity<WorkflowResponse> retry(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "false") boolean force,
                                                  Authentication authentication) {
        if (force && !hasRole(authentication, SecurityConfig.ROLE_ADMIN)) {
            throw new AccessDeniedException("Forced retry requires the ADMIN role");
        }
        return ResponseEntity.accepted().body(workflowService.retry(id, force));
    }

    @GetMapping("/{id}/email-preview")
    @Operation(summary = "Get the generated welcome email")
    public EmailPreviewResponse emailPreview(@PathVariable Long id) {
        return workflowService.getEmailPreview(id);
    }

    @PostMapping("/{id}/email/approve")
    @Operation(summary = "Approve (and optionally edit) the welcome email, then send it",
            description = "Only valid when email approval is enabled and the email is awaiting approval.")
    public ResponseEntity<EmailPreviewResponse> approveEmail(@PathVariable Long id,
                                                             @Valid @RequestBody(required = false) EmailApprovalRequest request) {
        return ResponseEntity.accepted().body(workflowService.approveEmail(id, request));
    }

    @GetMapping("/{id}/github-issue")
    @Operation(summary = "Get the GitHub onboarding issue")
    public GitHubIssueResponse gitHubIssue(@PathVariable Long id) {
        return workflowService.getGitHubIssue(id);
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }
}
