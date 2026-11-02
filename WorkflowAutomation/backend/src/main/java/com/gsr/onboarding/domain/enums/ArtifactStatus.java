package com.gsr.onboarding.domain.enums;

public enum ArtifactStatus {
    /** Generated, not yet approved or sent. */
    DRAFT,
    /** Reviewed and approved by an HR user. */
    APPROVED,
    /** A send attempt has started; if this state survives a crash the delivery outcome is unknown. */
    SENDING,
    SENT,
    SEND_FAILED,
    /** External resource (GitHub issue) created. */
    CREATED,
    FAILED
}
