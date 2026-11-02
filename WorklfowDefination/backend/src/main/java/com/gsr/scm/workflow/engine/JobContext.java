package com.gsr.scm.workflow.engine;

import java.util.Map;

/** Engine-neutral view of an activated service-task or user-task job. */
public record JobContext(long jobKey, String jobType, long engineInstanceKey, String elementId, int retries,
                         Map<String, Object> variables, Map<String, String> headers) {

    public static final String VAR_WORKFLOW_INSTANCE_ID = "workflowInstanceId";
    public static final String VAR_ITEM_ID = "itemId";
    public static final String VAR_CHANGE_REQUEST_ID = "changeRequestId";
    public static final String VAR_BUSINESS_KEY = "businessKey";
    public static final String VAR_ACTOR = "actor";
    public static final String VAR_MANAGER_APPROVAL_REQUIRED = "managerApprovalRequired";
    public static final String VAR_VALIDATION_PASSED = "validationPassed";
    public static final String VAR_VALIDATION_MESSAGE = "validationMessage";
    public static final String VAR_LAST_DECISION = "lastDecision";
    public static final String VAR_LAST_DECISION_BY = "lastDecisionBy";
    public static final String VAR_REJECTION_REASON = "rejectionReason";

    /** Header names used by Camunda for job-worker based user tasks. */
    public static final String HEADER_CANDIDATE_GROUPS = "io.camunda.zeebe:candidateGroups";
    public static final String HEADER_DUE_DATE = "io.camunda.zeebe:dueDate";
    public static final String USER_TASK_JOB_TYPE = "io.camunda.zeebe:userTask";

    public String workflowInstanceId() {
        Object v = variables.get(VAR_WORKFLOW_INSTANCE_ID);
        return v == null ? null : v.toString();
    }

    public Long itemId() {
        return asLong(variables.get(VAR_ITEM_ID));
    }

    public Long changeRequestId() {
        return asLong(variables.get(VAR_CHANGE_REQUEST_ID));
    }

    public String businessKey() {
        Object v = variables.get(VAR_BUSINESS_KEY);
        return v == null ? null : v.toString();
    }

    public boolean flag(String name) {
        Object v = variables.get(name);
        return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(v.toString());
    }

    public String string(String name) {
        Object v = variables.get(name);
        return v == null ? null : v.toString();
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        return v instanceof Number n ? n.longValue() : Long.valueOf(v.toString());
    }
}
