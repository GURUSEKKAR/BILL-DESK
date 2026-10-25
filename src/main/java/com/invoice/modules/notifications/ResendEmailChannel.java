package com.invoice.modules.notifications;

import com.invoice.modules.jobs.service.PermanentJobException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import jakarta.mail.internet.MimeMessage;
import java.util.Map;

/**
 * Sends email via Resend API (https://resend.com).
 * Free tier: 3,000 emails/month, 100/day.
 * No SMTP config needed — just a REST API call.
 *
 * Set RESEND_API_KEY in your environment.
 * If not set, falls back to SMTP (via the configured {@link JavaMailSender}),
 * which is what local dev and the integration tests (GreenMail) rely on.
 */
@Slf4j
@Service
public class ResendEmailChannel implements NotificationChannel {

    private final String apiKey;
    private final String fromAddress;
    private final RestClient restClient;
    private final org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSender;

    public ResendEmailChannel(
            @Value("${app.resend.api-key:}") String apiKey,
            @Value("${app.resend.from:noreply@invoice.local}") String fromAddress,
            org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSender) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.mailSender = mailSender;
        this.restClient = RestClient.builder()
            .baseUrl("https://api.resend.com")
            .build();
    }

    @Override
    public void send(String to, String subject, String body, String idempotencyKey) throws Exception {
        if (to == null || !to.contains("@")) {
            throw new PermanentJobException("Invalid email address: " + to);
        }

        // No API key configured -> deliver via SMTP (dev/test).
        if (apiKey == null || apiKey.isBlank()) {
            sendViaSmtp(to, subject, body);
            return;
        }

        try {
            var payload = Map.of(
                "from", fromAddress,
                "to",   new String[]{ to },
                "subject", subject,
                "html", body
            );

            var spec = restClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload);

            if (idempotencyKey != null) {
                spec.header("Idempotency-Key", idempotencyKey);
            }

            spec.retrieve().toBodilessEntity();
            log.info("Email sent via Resend to={} subject='{}'", to, subject);

        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            // 422 = invalid address — permanent
            if (code == 422 || code == 400) {
                throw new PermanentJobException(
                    "Permanent Resend error " + code + " for " + to + ": " + e.getResponseBodyAsString(), e);
            }
            // 429 = rate limit, 5xx = server error — retry
            throw new RuntimeException("Resend error " + code + ": " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * SMTP fallback used when no Resend API key is set (local dev + tests).
     * If no mail sender is available at all, logs and no-ops.
     */
    private void sendViaSmtp(String to, String subject, String body) throws Exception {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("[DEV] No mail sender configured — email skipped to={} subject='{}'", to, subject);
            return;
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setTo(to);
        helper.setFrom(fromAddress.contains("<")
            ? fromAddress.replaceAll(".*<(.+)>.*", "$1")
            : fromAddress);
        helper.setSubject(subject);
        helper.setText(body, true);
        sender.send(message);
        log.info("Email sent via SMTP to={} subject='{}'", to, subject);
    }
}
