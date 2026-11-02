package com.gsr.onboarding.integration.email;

/** Masks email addresses for logging: {@code priya.sharma@example.com -> p***@example.com}. */
public final class EmailMasking {

    private EmailMasking() {
    }

    public static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
