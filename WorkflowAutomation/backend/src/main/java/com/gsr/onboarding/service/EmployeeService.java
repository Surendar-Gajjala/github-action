package com.gsr.onboarding.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsr.onboarding.domain.entity.Employee;
import com.gsr.onboarding.domain.entity.OnboardingWorkflow;
import com.gsr.onboarding.domain.entity.WorkflowStep;
import com.gsr.onboarding.domain.enums.StepName;
import com.gsr.onboarding.domain.enums.StepStatus;
import com.gsr.onboarding.dto.EmployeeRequest;
import com.gsr.onboarding.dto.EmployeeResponses.EmployeeCreatedResponse;
import com.gsr.onboarding.dto.EmployeeResponses.EmployeeDetailResponse;
import com.gsr.onboarding.dto.EmployeeResponses.EmployeeSummaryResponse;
import com.gsr.onboarding.dto.PageResponse;
import com.gsr.onboarding.exception.ConflictException;
import com.gsr.onboarding.exception.NotFoundException;
import com.gsr.onboarding.mapper.EmployeeMapper;
import com.gsr.onboarding.repository.EmployeeRepository;
import com.gsr.onboarding.repository.OnboardingWorkflowRepository;
import com.gsr.onboarding.repository.WorkflowStepRepository;
import com.gsr.onboarding.workflow.EmployeeOnboardingWorkflowService;
import com.gsr.onboarding.workflow.WorkflowPolicy;

@Service
public class EmployeeService {

    public static final int MAX_PAGE_SIZE = 100;

    private final EmployeeRepository employeeRepository;
    private final OnboardingWorkflowRepository workflowRepository;
    private final WorkflowStepRepository stepRepository;
    private final EmployeeOnboardingWorkflowService workflowService;
    private final WorkflowPolicy policy;
    private final EmployeeMapper employeeMapper;

    public EmployeeService(EmployeeRepository employeeRepository, OnboardingWorkflowRepository workflowRepository,
                           WorkflowStepRepository stepRepository, EmployeeOnboardingWorkflowService workflowService,
                           WorkflowPolicy policy, EmployeeMapper employeeMapper) {
        this.employeeRepository = employeeRepository;
        this.workflowRepository = workflowRepository;
        this.stepRepository = stepRepository;
        this.workflowService = workflowService;
        this.policy = policy;
        this.employeeMapper = employeeMapper;
    }

    /**
     * Creates the employee and starts onboarding. Repeating a request with the same idempotency key returns the
     * original result instead of creating a duplicate. Deliberately not transactional: the workflow service owns
     * the transaction, so a constraint violation can be handled here after its rollback.
     */
    public EmployeeCreatedResponse create(EmployeeRequest request, String idempotencyKey) {
        Employee employee = normalise(employeeMapper.toEntity(request));
        employee.setIdempotencyKey(idempotencyKey);

        if (idempotencyKey != null) {
            var existing = employeeRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), employee.getEmail());
            }
        }
        if (employeeRepository.existsByEmail(employee.getEmail())) {
            throw duplicateEmail();
        }
        try {
            OnboardingWorkflow workflow = workflowService.start(employee);
            return new EmployeeCreatedResponse(workflow.getEmployeeId(), employee.getEmployeeNumber(),
                    workflow.getId(), workflow.getOverallStatus(), false);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request won the race on the email or idempotency-key unique constraint.
            if (idempotencyKey != null) {
                var existing = employeeRepository.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    return replay(existing.get(), employee.getEmail());
                }
            }
            throw duplicateEmail();
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeSummaryResponse> list(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Employee> employees = employeeRepository.search(searchPattern(query), pageable);

        List<Long> employeeIds = employees.map(Employee::getId).toList();
        Map<Long, OnboardingWorkflow> workflows = employeeIds.isEmpty() ? Map.of()
                : workflowRepository.findByEmployeeIdIn(employeeIds).stream()
                .collect(Collectors.toMap(OnboardingWorkflow::getEmployeeId, Function.identity()));
        Map<Long, List<WorkflowStep>> steps = workflows.isEmpty() ? Map.of()
                : stepRepository.findByWorkflowIdInOrderByStepOrder(
                        workflows.values().stream().map(OnboardingWorkflow::getId).toList())
                .stream().collect(Collectors.groupingBy(WorkflowStep::getWorkflowId));

        List<EmployeeSummaryResponse> rows = employees.stream().map(employee -> {
            OnboardingWorkflow workflow = workflows.get(employee.getId());
            List<WorkflowStep> workflowSteps = workflow == null ? List.of() : steps.getOrDefault(workflow.getId(), List.of());
            return new EmployeeSummaryResponse(
                    employee.getId(),
                    employee.getEmployeeNumber(),
                    employee.getFullName(),
                    employee.getEmail(),
                    employee.getDepartment(),
                    employee.getJoiningDate(),
                    workflow == null ? null : workflow.getOverallStatus(),
                    stepStatus(workflowSteps, StepName.SEND_EMAIL),
                    stepStatus(workflowSteps, StepName.CREATE_GITHUB_ISSUE),
                    workflowSteps.stream().anyMatch(policy::isRetryable),
                    employee.getCreatedAt());
        }).toList();
        return PageResponse.of(employees, rows);
    }

    @Transactional(readOnly = true)
    public EmployeeDetailResponse get(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
        return new EmployeeDetailResponse(employeeMapper.toResponse(employee), workflowService.getWorkflow(id));
    }

    private EmployeeCreatedResponse replay(Employee existing, String requestedEmail) {
        if (!existing.getEmail().equals(requestedEmail)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                    "This idempotency key was already used for a different employee.");
        }
        OnboardingWorkflow workflow = workflowRepository.findByEmployeeId(existing.getId())
                .orElseThrow(() -> new NotFoundException("Workflow not found for employee " + existing.getId()));
        return new EmployeeCreatedResponse(existing.getId(), existing.getEmployeeNumber(), workflow.getId(),
                workflow.getOverallStatus(), true);
    }

    private static ConflictException duplicateEmail() {
        return new ConflictException("DUPLICATE_EMPLOYEE", "An employee with this work email already exists.");
    }

    private static StepStatus stepStatus(List<WorkflowStep> steps, StepName name) {
        return steps.stream().filter(s -> s.getStepName() == name).map(WorkflowStep::getStatus).findFirst().orElse(null);
    }

    /** Builds a LIKE pattern; wildcard characters typed by the user are removed rather than interpreted. */
    static String searchPattern(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        String cleaned = query.strip().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "").replace("\\", "");
        return "%" + cleaned + "%";
    }

    private static Employee normalise(Employee employee) {
        employee.setFirstName(employee.getFirstName().strip());
        employee.setLastName(employee.getLastName().strip());
        employee.setEmail(employee.getEmail().strip().toLowerCase(Locale.ROOT));
        employee.setJobTitle(employee.getJobTitle().strip());
        employee.setDepartment(employee.getDepartment().strip());
        employee.setReportingManager(employee.getReportingManager().strip());
        employee.setGithubUsername(employee.getGithubUsername().strip());
        employee.setGithubRepository(employee.getGithubRepository().strip());
        return employee;
    }
}
