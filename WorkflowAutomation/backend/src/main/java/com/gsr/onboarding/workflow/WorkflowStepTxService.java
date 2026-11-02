package com.gsr.onboarding.workflow;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.domain.entity.Employee;
import com.gsr.onboarding.domain.entity.OnboardingArtifact;
import com.gsr.onboarding.domain.entity.OnboardingWorkflow;
import com.gsr.onboarding.domain.entity.WorkflowStep;
import com.gsr.onboarding.domain.enums.ArtifactStatus;
import com.gsr.onboarding.domain.enums.ArtifactType;
import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.domain.enums.StepStatus;
import com.gsr.onboarding.domain.enums.WorkflowStatus;
import com.gsr.onboarding.exception.NotFoundException;
import com.gsr.onboarding.integration.github.GitHubIssue;
import com.gsr.onboarding.repository.EmployeeRepository;
import com.gsr.onboarding.repository.OnboardingArtifactRepository;
import com.gsr.onboarding.repository.OnboardingWorkflowRepository;
import com.gsr.onboarding.repository.WorkflowStepRepository;
import com.gsr.onboarding.service.ArtifactMetadata;
import com.gsr.onboarding.service.ArtifactMetadataCodec;
import com.gsr.onboarding.service.AuditService;
import com.gsr.onboarding.service.EmailContentService;
import com.gsr.onboarding.service.EmailContentService.ContentResult;

/**
 * Short, independent transactions around workflow steps. External calls happen between these
 * transactions, never inside them, so no database transaction is held open during network I/O.
 */
@Service
public class WorkflowStepTxService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStepTxService.class);
    private static final int MAX_MESSAGE = 2000;

    private final WorkflowStepRepository stepRepository;
    private final OnboardingWorkflowRepository workflowRepository;
    private final OnboardingArtifactRepository artifactRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkflowPolicy policy;
    private final AuditService auditService;
    private final EmailContentService contentService;
    private final ArtifactMetadataCodec metadataCodec;
    private final AppProperties.GitHub gitHubProperties;

    public WorkflowStepTxService(WorkflowStepRepository stepRepository, OnboardingWorkflowRepository workflowRepository,
                                 OnboardingArtifactRepository artifactRepository, EmployeeRepository employeeRepository,
                                 WorkflowPolicy policy, AuditService auditService, EmailContentService contentService,
                                 ArtifactMetadataCodec metadataCodec, AppProperties appProperties) {
        this.stepRepository = stepRepository;
        this.workflowRepository = workflowRepository;
        this.artifactRepository = artifactRepository;
        this.employeeRepository = employeeRepository;
        this.policy = policy;
        this.auditService = auditService;
        this.contentService = contentService;
        this.metadataCodec = metadataCodec;
        this.gitHubProperties = appProperties.github();
    }

    @Transactional(readOnly = true)
    public List<Long> findWorkflowIdsWithPendingSteps(int limit) {
        return workflowRepository.findWorkflowIdsWithPendingSteps(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowStep> findNextRunnableStep(Long workflowId) {
        return policy.nextRunnable(stepRepository.findByWorkflowIdOrderByStepOrder(workflowId));
    }

    /** Atomically moves a PENDING step to IN_PROGRESS. Returns false if another worker claimed it first. */
    @Transactional
    public boolean claim(Long stepId) {
        if (stepRepository.claim(stepId, Instant.now()) != 1) {
            return false;
        }
        WorkflowStep step = stepRepository.findById(stepId).orElseThrow();
        recompute(step.getWorkflowId());
        return true;
    }

    @Transactional
    public void complete(Long stepId, StepOutcome outcome) {
        WorkflowStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new NotFoundException("Workflow step not found"));
        step.setStatus(outcome.status());
        step.setCompletedAt(outcome.status() == StepStatus.AWAITING_APPROVAL ? null : Instant.now());
        step.setErrorMessage(truncate(outcome.message()));
        if (outcome.externalReference() != null) {
            step.setExternalReference(truncate(outcome.externalReference(), 500));
        }
        stepRepository.save(step);
        OnboardingWorkflow workflow = recompute(step.getWorkflowId());
        auditService.record("STEP_" + outcome.status(), AuditService.ENTITY_WORKFLOW, workflow.getId(),
                step.getStepName().label() + (outcome.message() == null ? "" : ": " + outcome.message()));
        log.info("Step {} finished with {} (workflow status {})", step.getStepName(), outcome.status(),
                workflow.getOverallStatus());
    }

    @Transactional(readOnly = true)
    public OnboardingWorkflow loadWorkflow(Long workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow " + workflowId + " not found"));
    }

    @Transactional(readOnly = true)
    public Employee loadEmployee(Long workflowId) {
        OnboardingWorkflow workflow = loadWorkflow(workflowId);
        return employeeRepository.findById(workflow.getEmployeeId())
                .orElseThrow(() -> new NotFoundException("Employee not found for workflow " + workflowId));
    }

    @Transactional(readOnly = true)
    public Optional<OnboardingArtifact> findArtifact(Long workflowId, ArtifactType type) {
        return artifactRepository.findByWorkflowIdAndArtifactType(workflowId, type);
    }

    /**
     * Stores the generated email and issue drafts. Artifacts that already produced an external effect
     * (email sending/sent, issue created) are never overwritten.
     */
    @Transactional
    public void saveGeneratedArtifacts(Long workflowId, Employee employee, ContentResult result) {
        var content = result.content();
        OnboardingArtifact email = artifactRepository.findByWorkflowIdAndArtifactType(workflowId, ArtifactType.WELCOME_EMAIL)
                .orElseGet(() -> newArtifact(workflowId, ArtifactType.WELCOME_EMAIL));
        if (email.getStatus() == ArtifactStatus.DRAFT) {
            email.setRecipient(employee.getEmail());
            email.setSubject(content.emailSubject());
            email.setContent(content.emailBody());
            email.setContentSource(result.source());
            email.setMetadata(metadataCodec.write(new ArtifactMetadata(content.checklist(), null, null, result.note())));
            email.setUpdatedBy(AuditService.SYSTEM_ACTOR);
            artifactRepository.save(email);
        }

        OnboardingArtifact issue = artifactRepository.findByWorkflowIdAndArtifactType(workflowId, ArtifactType.GITHUB_ISSUE)
                .orElseGet(() -> newArtifact(workflowId, ArtifactType.GITHUB_ISSUE));
        if (issue.getStatus() != ArtifactStatus.CREATED) {
            issue.setSubject(content.githubIssueTitle());
            issue.setContent(contentService.buildIssueBody(employee, workflowId, content.githubIssueSummary(),
                    content.checklist()));
            issue.setContentSource(result.source());
            issue.setMetadata(metadataCodec.write(new ArtifactMetadata(content.checklist(),
                    issueRepository(employee), employee.getGithubRepository(), result.note())));
            issue.setStatus(ArtifactStatus.DRAFT);
            issue.setUpdatedBy(AuditService.SYSTEM_ACTOR);
            artifactRepository.save(issue);
        }
    }

    /** Compare-and-set artifact status change. Returns false when the artifact was not in an expected state. */
    @Transactional
    public boolean transitionArtifact(Long artifactId, Collection<ArtifactStatus> from, ArtifactStatus to) {
        return artifactRepository.transition(artifactId, from, to, Instant.now()) == 1;
    }

    @Transactional
    public void markEmailSent(Long artifactId, String messageId) {
        OnboardingArtifact artifact = artifactRepository.findById(artifactId).orElseThrow();
        artifact.setStatus(ArtifactStatus.SENT);
        artifact.setSentAt(Instant.now());
        artifact.setExternalId(truncate(messageId, 100));
        artifact.setUpdatedBy(AuditService.SYSTEM_ACTOR);
        artifactRepository.save(artifact);
    }

    @Transactional
    public void markIssueCreated(Long artifactId, GitHubIssue issue) {
        OnboardingArtifact artifact = artifactRepository.findById(artifactId).orElseThrow();
        artifact.setStatus(ArtifactStatus.CREATED);
        artifact.setExternalId(String.valueOf(issue.number()));
        artifact.setExternalUrl(truncate(issue.htmlUrl(), 500));
        artifact.setUpdatedBy(AuditService.SYSTEM_ACTOR);
        artifactRepository.save(artifact);
    }

    /**
     * Repairs steps left IN_PROGRESS by a crash or restart. An interrupted email send cannot be safely
     * repeated automatically, so it becomes FAILED_UNCERTAIN; every other step is idempotent and is re-queued.
     */
    @Transactional
    public int recoverInterruptedSteps() {
        List<WorkflowStep> interrupted = stepRepository.findByStatus(StepStatus.IN_PROGRESS);
        for (WorkflowStep step : interrupted) {
            boolean emailInFlight = step.getStepName() == StepName.SEND_EMAIL
                    && artifactRepository.findByWorkflowIdAndArtifactType(step.getWorkflowId(), ArtifactType.WELCOME_EMAIL)
                    .map(artifact -> artifact.getStatus() == ArtifactStatus.SENDING)
                    .orElse(false);
            if (emailInFlight) {
                step.setStatus(StepStatus.FAILED_UNCERTAIN);
                step.setCompletedAt(Instant.now());
                step.setErrorMessage("The application stopped while sending the email, so delivery is unknown. "
                        + "An administrator must confirm a forced retry.");
            } else {
                step.setStatus(StepStatus.PENDING);
                step.setStartedAt(null);
            }
            stepRepository.save(step);
            recompute(step.getWorkflowId());
            auditService.record("STEP_RECOVERED", AuditService.ENTITY_WORKFLOW, step.getWorkflowId(),
                    step.getStepName().label() + " recovered as " + step.getStatus());
        }
        return interrupted.size();
    }

    /** Recalculates and stores the workflow's overall status, current step and last error. */
    @Transactional
    public OnboardingWorkflow recompute(Long workflowId) {
        OnboardingWorkflow workflow = loadWorkflow(workflowId);
        List<WorkflowStep> steps = stepRepository.findByWorkflowIdOrderByStepOrder(workflowId);
        WorkflowStatus status = policy.overallStatus(steps);
        workflow.setOverallStatus(status);
        workflow.setCurrentStep(steps.stream()
                .filter(step -> !step.getStatus().isDone())
                .map(WorkflowStep::getStepName)
                .findFirst()
                .orElse(StepName.COMPLETE_WORKFLOW));
        workflow.setLastError(steps.stream()
                .filter(step -> step.getStatus().isFailure())
                .map(WorkflowStep::getErrorMessage)
                .findFirst()
                .orElse(null));
        if (status != WorkflowStatus.COMPLETED) {
            workflow.setCompletedAt(null);
        } else if (workflow.getCompletedAt() == null) {
            workflow.setCompletedAt(Instant.now());
        }
        return workflowRepository.save(workflow);
    }

    /** Repository where onboarding issues are filed: the configured default, else the requested repository. */
    private String issueRepository(Employee employee) {
        if (notBlank(gitHubProperties.defaultOwner()) && notBlank(gitHubProperties.defaultRepository())) {
            return gitHubProperties.defaultOwner() + "/" + gitHubProperties.defaultRepository();
        }
        return employee.getGithubRepository();
    }

    private static OnboardingArtifact newArtifact(Long workflowId, ArtifactType type) {
        OnboardingArtifact artifact = new OnboardingArtifact();
        artifact.setWorkflowId(workflowId);
        artifact.setArtifactType(type);
        artifact.setStatus(ArtifactStatus.DRAFT);
        artifact.setCreatedBy(AuditService.SYSTEM_ACTOR);
        artifact.setUpdatedBy(AuditService.SYSTEM_ACTOR);
        return artifact;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    static String truncate(String value) {
        return truncate(value, MAX_MESSAGE);
    }

    static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }
}
