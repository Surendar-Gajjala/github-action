package com.gsr.scm.config;

import java.util.Set;

/**
 * Application roles. Approval-task candidate groups in BPMN ({@code zeebe:assignmentDefinition candidateGroups})
 * map 1:1 onto these role names, e.g. candidate group ENGINEERING requires ROLE_ENGINEERING.
 */
public final class Roles {

    public static final String AUTHOR = "AUTHOR";
    public static final String ENGINEERING = "ENGINEERING";
    public static final String MANAGER = "MANAGER";
    public static final String ADMIN = "ADMIN";

    /** Candidate groups that may be used on approval tasks. */
    public static final Set<String> APPROVAL_GROUPS = Set.of(ENGINEERING, MANAGER);

    private Roles() {
    }
}
