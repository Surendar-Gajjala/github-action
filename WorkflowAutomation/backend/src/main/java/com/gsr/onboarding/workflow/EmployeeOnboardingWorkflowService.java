package com.gsr.onboarding.workflow;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.domain.entity.Employee;
import com.gsr.onboarding.domain.entity.OnboardingArtifact;
import com.gsr.onboarding.domain.entity.OnboardingWorkflow;
import com.gsr.onboarding.domain.entity.WorkflowStep;
import com.gsr.onboarding.domain.enums.ArtifactStatus;
import com.gsr.onboarding.domain.enums.ArtifactType;
import com.gsr.onboarding.domain.enums.ContentSource;
import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.domain.enums.StepStatus;
import com.gsr.onboarding.domain.enums.WorkflowStatus;
import com.gsr.onboarding.dto.ArtifactResponses.EmailApprovalRequest;
import com.gsr.onboarding.dto.ArtifactResponses.EmailPreviewResponse;
import com.gsr.onboarding.dto.ArtifactResponses.GitHubIssueResponse;
import com.gsr.onboarding.dto.WorkflowResponse;
import com.gsr.onboarding.exception.ConflictException;
import com.gsr.onboarding.exception.NotFoundException;
import com.gsr.onboarding.mapper.WorkflowMapper;
import com.gsr.onboarding.repository.EmployeeRepository;
import com.gsr.onboarding.repository.OnboardingArtifactRepository;
import com.gsr.onboarding.repository.OnboardingWorkflowRepository;
import com.gsr.onboarding.repository.WorkflowStepRepository;
import com.gsr.onboarding.service.ArtifactMetadata;
import com.gsr.onboarding.service.ArtifactMetadataCodec;
import com.gsr.onboarding.service.AuditService;

/**
 * Orchestrates the onboarding workflow:
 * validate → save employee and workflow → generate content (OpenAI) → send email → create GitHub issue → complete.
 * <p>
 * Starting, retrying and approving only change durable state and publish a {@link WorkflowScheduledEvent};
 * the {@link WorkflowJobRunner} performs the external calls asynchronously.
 */
@Service
public class EmployeeOnboardingWorkflowService {

    private final EmployeeRepository employeeRepository;
    private final OnboardingWorkflowRepository workflowRepository;
    private final WorkflowStepRepository stepRepository;
    private final OnboardingArtifactRepository artifactRepository;
    private final WorkflowStepTxService stepTxService;
    private final WorkflowPolicy policy;
    private final AuditService auditService;
    private final WorkflowMapper workflowMapper;
    private final ArtifactMetadataCodec metadataCodec;
    private final ApplicationEventPublisher events;
    private final AppProperties.Workflow workflowProperties;

    public EmployeeOnboardingWorkflowService(EmployeeRepository employeeRepository,
                                             OnboardingWorkflowRepository workflowRepository,
                                             WorkflowStepRepository stepRepository,
                                             OnboardingArtifactRepository artifactRepository,
                                             WorkflowStepTxService stepTxService,
                                             WorkflowPolicy policy,
                                             AuditService auditService,
                                             WorkflowMapper workflowMapper,
                                             ArtifactMetadataCodec metadataCodec,
                                             ApplicationEventPublisher events,
                                             AppProperties appProperties) {
        this.employeeRepository = employeeRepository;
        this.workflowRepository = workflowRepository;
        this.stepRepository = stepRepository;
        this.artifactRepository = artifactRepository;
        this.stepTxService = stepTxService;
        this.policy = policy;
        this.auditService = auditService;
        this.workflowMapper = workflowMapper;
        this.metadataCodec = metadataCodec;
        this.events = events;
        this.workflowProperties = appProperties.workflow();
    }

    /**
     * Saves a validated employee together with its workflow and step records in one transaction,
     * then schedules asynchronous processing once the transaction commits.
     */
    @Transactional
    public OnboardingWorkflow start(Employee employee) {
        String actor = AuditService.currentActor();
        employee.setEmployeeNumber(String.format("EMP-%06d", employeeRepository.nextEmployeeNumber()));
        employee.setCreatedBy(actor);
        employee.setUpdatedBy(actor);
        Employee saved = employeeRepository.saveAndFlush(employee);

        Instant now = Instant.now();
        OnboardingWorkflow workflow = new OnboardingWorkflow();
        workflow.setEmployeeId(saved.getId());
        workflow.setOverallStatus(WorkflowStatus.IN_PROGRESS);
        workflow.setCurrentStep(StepName.GENERATE_CONTENT);
        workflow.setEmailApprovalRequired(workflowProperties.emailApprovalRequired());
        workflow.setStartedAt(now);
        workflow = workflowRepository.save(workflow);

        for (StepName name : StepName.values()) {
            WorkflowStep step = WorkflowStep.pending(workflow.getId(), name);
            if (name == StepName.VALIDATE_DETAILS || name == StepName.SAVE_RECORD) {
                step.setStatus(StepStatus.SUCCESS);
                step.setAttempts(1);
                step.setStartedAt(now);
                step.setCompletedAt(now);
                step.setExternalReference(name == StepName.SAVE_RECORD ? saved.getEmployeeNumber() : null);
            }
            stepRepository.save(step);
        }

        auditService.record("EMPLOYEE_CREATED", AuditService.ENTITY_WORKFLOW, workflow.getId(),
                "Employee " + saved.getEmployeeNumber() + " submitted for onboarding");
        events.publishEvent(new WorkflowScheduledEvent(workflow.getId()));
        return workflow;
    }

    /**
     * Re-queues failed steps. Steps whose outcome is uncertain (an interrupted email send) are only
     * re-queued when {@code force} is set, which the controller restricts to administrators.
     */
    @Transactional
    public WorkflowResponse retry(Long employeeId, boolean force) {
        OnboardingWorkflow workflow = workflowFor(employeeId);
        List<WorkflowStep> steps = stepRepository.findByWorkflowIdOrderByStepOrder(workflow.getId());

        List<WorkflowStep> failed = steps.stream().filter(s -> s.getStatus() == StepStatus.FAILED).toList();
        List<WorkflowStep> uncertain = steps.stream().filter(s -> s.getStatus() == StepStatus.FAILED_UNCERTAIN).toList();
        if (failed.isEmpty() && uncertain.isEmpty()) {
            throw new ConflictException("NOTHING_TO_RETRY", "There are no failed steps to retry.");
        }
        if (failed.isEmpty() && !force) {
            throw new ConflictException("FORCE_REQUIRED", "The email delivery outcome is unknown. An administrator "
                    + "must confirm a forced retry, which may send a duplicate email.");
        }
        List<WorkflowStep> toRetry = force ? steps.stream().filter(s -> s.getStatus().isFailure()).toList() : failed;
        for (WorkflowStep step : toRetry) {
            if (step.getAttempts() >= policy.maxAttempts()) {
                throw new ConflictException("MAX_ATTEMPTS_REACHED", step.getStepName().label()
                        + " has reached the maximum of " + policy.maxAttempts() + " attempts.");
            }
        }
        for (WorkflowStep step : toRetry) {
            if (step.getStatus() == StepStatus.FAILED_UNCERTAIN) {
                artifactRepository.findByWorkflowIdAndArtifactType(workflow.getId(), ArtifactType.WELCOME_EMAIL)
                        .ifPresent(email -> stepTxService.transitionArtifact(email.getId(),
                                EnumSet.of(ArtifactStatus.SENDING), ArtifactStatus.SEND_FAILED));
            }
            step.setStatus(StepStatus.PENDING);
            step.setCompletedAt(null);
            step.setErrorMessage(null);
            stepRepository.save(step);
        }
        stepTxService.recompute(workflow.getId());
        auditService.record(force ? "WORKFLOW_FORCED_RETRY" : "WORKFLOW_RETRY", AuditService.ENTITY_WORKFLOW,
                workflow.getId(), "Retry requested for: " + String.join(", ",
                        toRetry.stream().map(s -> s.getStepName().label()).toList()));
        events.publishEvent(new WorkflowScheduledEvent(workflow.getId()));
        return getWorkflow(employeeId);
    }

    /** Applies optional HR edits, approves the welcome email and lets the workflow send it. */
    @Transactional
    public EmailPreviewResponse approveEmail(Long employeeId, EmailApprovalRequest request) {
        OnboardingWorkflow workflow = workflowFor(employeeId);
        WorkflowStep sendStep = stepRepository.findByWorkflowIdOrderByStepOrder(workflow.getId()).stream()
                .filter(s -> s.getStepName() == StepName.SEND_EMAIL)
                .findFirst()
                .orElseThrow();
        if (sendStep.getStatus() != StepStatus.AWAITING_APPROVAL) {
            throw new ConflictException("NOT_AWAITING_APPROVAL", "The welcome email is not waiting for approval.");
        }
        OnboardingArtifact email = artifactRepository
                .findByWorkflowIdAndArtifactType(workflow.getId(), ArtifactType.WELCOME_EMAIL)
                .filter(a -> a.getStatus() == ArtifactStatus.DRAFT)
                .orElseThrow(() -> new ConflictException("NOT_AWAITING_APPROVAL", "No draft email to approve."));

        boolean edited = false;
        if (request != null && request.subject() != null && !request.subject().strip().equals(email.getSubject())) {
            email.setSubject(request.subject().strip());
            edited = true;
        }
        if (request != null && request.body() != null && !request.body().strip().equals(email.getContent())) {
            email.setContent(request.body().strip());
            edited = true;
        }
        if (edited) {
            email.setContentSource(ContentSource.EDITED);
        }
        email.setStatus(ArtifactStatus.APPROVED);
        email.setUpdatedBy(AuditService.currentActor());
        artifactRepository.save(email);

        sendStep.setStatus(StepStatus.PENDING);
        sendStep.setErrorMessage(null);
        stepRepository.save(sendStep);
        stepTxService.recompute(workflow.getId());

        auditService.record("EMAIL_APPROVED", AuditService.ENTITY_WORKFLOW, workflow.getId(),
                edited ? "Welcome email edited and approved" : "Welcome email approved without changes");
        events.publishEvent(new WorkflowScheduledEvent(workflow.getId()));
        return toEmailPreview(workflow, Optional.of(email), sendStep);
    }

    @Transactional(readOnly = true)
    public WorkflowResponse getWorkflow(Long employeeId) {
        OnboardingWorkflow workflow = workflowFor(employeeId);
        List<WorkflowStep> steps = stepRepository.findByWorkflowIdOrderByStepOrder(workflow.getId());
        return toResponse(workflow, steps, true);
    }

    @Transactional(readOnly = true)
    public EmailPreviewResponse getEmailPreview(Long employeeId) {
        OnboardingWorkflow workflow = workflowFor(employeeId);
        WorkflowStep sendStep = step(workflow.getId(), StepName.SEND_EMAIL);
        return toEmailPreview(workflow,
                artifactRepository.findByWorkflowIdAndArtifactType(workflow.getId(), ArtifactType.WELCOME_EMAIL),
                sendStep);
    }

    @Transactional(readOnly = true)
    public GitHubIssueResponse getGitHubIssue(Long employeeId) {
        OnboardingWorkflow workflow = workflowFor(employeeId);
        WorkflowStep issueStep = step(workflow.getId(), StepName.CREATE_GITHUB_ISSUE);
        Optional<OnboardingArtifact> artifact =
                artifactRepository.findByWorkflowIdAndArtifactType(workflow.getId(), ArtifactType.GITHUB_ISSUE);
        if (artifact.isEmpty()) {
            return GitHubIssueResponse.notAvailable(issueStep.getStatus(), issueStep.getErrorMessage());
        }
        OnboardingArtifact issue = artifact.get();
        ArtifactMetadata metadata = metadataCodec.read(issue.getMetadata());
        return new GitHubIssueResponse(true, metadata.repository(), metadata.requestedRepository(),
                issue.getSubject(), issue.getContent(), metadata.checklist(),
                issue.getExternalId() == null ? null : Integer.valueOf(issue.getExternalId()),
                issue.getExternalUrl(), issue.getStatus(), issueStep.getStatus(), issueStep.getErrorMessage());
    }

    /** Builds the API view of a workflow. History is only loaded for the detail view. */
    public WorkflowResponse toResponse(OnboardingWorkflow workflow, List<WorkflowStep> steps, boolean includeHistory) {
        boolean retryable = steps.stream().anyMatch(policy::isRetryable);
        boolean requiresForce = steps.stream().anyMatch(s -> s.getStatus() == StepStatus.FAILED_UNCERTAIN);
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getEmployeeId(),
                workflow.getOverallStatus(),
                workflow.getCurrentStep(),
                workflow.isEmailApprovalRequired(),
                workflow.getStartedAt(),
                workflow.getCompletedAt(),
                workflow.getLastError(),
                retryable,
                requiresForce,
                workflowMapper.toStepResponses(steps),
                includeHistory
                        ? workflowMapper.toHistoryEntries(auditService.history(AuditService.ENTITY_WORKFLOW, workflow.getId()))
                        : List.of());
    }

    private EmailPreviewResponse toEmailPreview(OnboardingWorkflow workflow, Optional<OnboardingArtifact> artifact,
                                                WorkflowStep sendStep) {
        if (artifact.isEmpty()) {
            return EmailPreviewResponse.notAvailable(sendStep.getStatus(), workflow.isEmailApprovalRequired());
        }
        OnboardingArtifact email = artifact.get();
        ArtifactMetadata metadata = metadataCodec.read(email.getMetadata());
        boolean awaiting = sendStep.getStatus() == StepStatus.AWAITING_APPROVAL && email.getStatus() == ArtifactStatus.DRAFT;
        return new EmailPreviewResponse(true, email.getRecipient(), email.getSubject(), email.getContent(),
                metadata.checklist(), email.getStatus(), email.getContentSource(), sendStep.getStatus(),
                workflow.isEmailApprovalRequired(), awaiting, email.getSentAt());
    }

    private OnboardingWorkflow workflowFor(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new NotFoundException("Employee " + employeeId + " not found");
        }
        return workflowRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new NotFoundException("No onboarding workflow found for employee " + employeeId));
    }

    private WorkflowStep step(Long workflowId, StepName name) {
        return stepRepository.findByWorkflowIdOrderByStepOrder(workflowId).stream()
                .filter(s -> s.getStepName() == name)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Workflow step " + name + " not found"));
    }
}
