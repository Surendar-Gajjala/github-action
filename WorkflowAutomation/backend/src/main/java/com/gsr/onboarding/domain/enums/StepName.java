package com.gsr.onboarding.domain.enums;

/** Workflow steps in execution order. */
public enum StepName {
    VALIDATE_DETAILS(1, "Employee details validated"),
    SAVE_RECORD(2, "Employee record saved"),
    GENERATE_CONTENT(3, "AI email generated"),
    SEND_EMAIL(4, "Welcome email sent"),
    CREATE_GITHUB_ISSUE(5, "GitHub onboarding issue created"),
    COMPLETE_WORKFLOW(6, "Workflow completed");

    private final int order;
    private final String label;

    StepName(int order, String label) {
        this.order = order;
        this.label = label;
    }

    public int order() {
        return order;
    }

    public String label() {
        return label;
    }
}
