package com.gsr.scm.common;

import java.util.List;

/** Application exception hierarchy; each type maps to one HTTP status in {@link GlobalExceptionHandler}. */
public final class Exceptions {

    private Exceptions() {
    }

    /** 404 */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }

        public static NotFoundException of(String type, Object id) {
            return new NotFoundException(type + " " + id + " was not found");
        }
    }

    /** 409 - state conflict (duplicate key, stale version, item under review, duplicate submission...). */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /** 422 - request is well-formed but violates a business rule. */
    public static class BusinessRuleException extends RuntimeException {
        private final List<String> details;

        public BusinessRuleException(String message) {
            this(message, List.of());
        }

        public BusinessRuleException(String message, List<String> details) {
            super(message);
            this.details = details;
        }

        public List<String> getDetails() {
            return details;
        }
    }

    /** 422 - a lifecycle transition that is not configured/enabled. */
    public static class LifecycleTransitionException extends BusinessRuleException {
        public LifecycleTransitionException(String message) {
            super(message);
        }
    }

    /** 403 - authenticated but not permitted for this specific resource (e.g. task for another role). */
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }

    /** 503 - workflow engine could not be reached or rejected the command. */
    public static class EngineUnavailableException extends RuntimeException {
        public EngineUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
