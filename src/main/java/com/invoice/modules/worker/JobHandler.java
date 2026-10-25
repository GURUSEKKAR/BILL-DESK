package com.invoice.modules.worker;

import java.util.Map;

/**
 * Contract for all background job handlers.
 * {@link #type()} must match the string stored in {@code jobs.type}.
 */
public interface JobHandler {

    String type();

    /**
     * @param payload the deserialized JSON payload from the job row
     * @throws com.invoice.modules.jobs.service.PermanentJobException for
     *         permanent failures (no retry)
     * @throws Exception for transient failures (will be retried)
     */
    void handle(Map<String, Object> payload) throws Exception;
}
