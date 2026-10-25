package com.invoice.modules.notifications;

/**
 * Strategy interface for all notification channels.
 * Phase 2 ships EmailChannel. WhatsApp/SMS can be added later.
 */
public interface NotificationChannel {

    /**
     * Send a notification.
     *
     * @param to             recipient address (email, phone, etc.)
     * @param subject        subject line or message title
     * @param body           HTML or plain-text body
     * @param idempotencyKey provider-level idempotency key (prevents double-send)
     * @throws com.invoice.modules.jobs.service.PermanentJobException if the
     *         error is permanent (e.g. invalid address) — no retry
     * @throws Exception for transient errors that should be retried
     */
    void send(String to, String subject, String body, String idempotencyKey) throws Exception;
}
