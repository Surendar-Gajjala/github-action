package com.gsr.onboarding.domain.enums;

/** Requested GitHub repository role. Recorded on the onboarding issue only; never provisioned automatically. */
public enum AccessLevel {
    READ, TRIAGE, WRITE, MAINTAIN, ADMIN
}
