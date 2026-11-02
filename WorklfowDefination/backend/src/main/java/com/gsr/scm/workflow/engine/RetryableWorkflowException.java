package com.gsr.scm.workflow.engine;

/** A transient worker failure (e.g. data not yet committed, optimistic-lock clash); the engine should retry. */
public class RetryableWorkflowException extends RuntimeException {

    public RetryableWorkflowException(String message) {
        super(message);
    }

    public RetryableWorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
