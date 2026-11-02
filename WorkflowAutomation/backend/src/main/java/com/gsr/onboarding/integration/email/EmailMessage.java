package com.gsr.onboarding.integration.email;

/** Outgoing email with HTML and plain-text alternatives. */
public record EmailMessage(String to, String subject, String textBody, String htmlBody, String reference) {

    /** Excludes the bodies so email content is never written to logs by accident. */
    @Override
    public String toString() {
        return "EmailMessage[to=" + EmailMasking.mask(to) + ", subjectLength=" + (subject == null ? 0 : subject.length())
                + ", reference=" + reference + "]";
    }
}
