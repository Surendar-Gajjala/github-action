package com.gsr.onboarding.dto;

/** Validation patterns shared by request DTOs. The React form mirrors these rules. */
public final class ValidationPatterns {

    public static final String PERSON_NAME = "^\\p{L}[\\p{L} .'-]*$";

    public static final String EMAIL = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    /** GitHub usernames: alphanumeric or single hyphens, no leading/trailing hyphen, max 39 chars. */
    public static final String GITHUB_USERNAME = "^(?=.{1,39}$)[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9]))*$";

    public static final String GITHUB_REPOSITORY = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9._-]{1,100}$";

    /** Subjects must not contain line breaks (prevents mail header injection). */
    public static final String SINGLE_LINE = "^[^\\r\\n]*$";

    private ValidationPatterns() {
    }
}
