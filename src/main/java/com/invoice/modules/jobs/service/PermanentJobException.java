package com.invoice.modules.jobs.service;

/**
 * Thrown by a job handler when the error is permanent (e.g. invalid email
 * address). The job goes directly to FAILED — no retries.
 */
public class PermanentJobException extends Exception {

    public PermanentJobException(String message) {
        super(message);
    }

    public PermanentJobException(String message, Throwable cause) {
        super(message, cause);
    }
}
