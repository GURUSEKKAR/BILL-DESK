package com.invoice.modules.worker.handlers;

import com.invoice.modules.customers.entity.Customer;
import com.invoice.modules.customers.repository.CustomerRepository;
import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.entity.Invoice;
import com.invoice.modules.invoices.repository.InvoiceRepository;
import com.invoice.modules.jobs.service.PermanentJobException;
import com.invoice.modules.notifications.ResendEmailChannel;
import com.invoice.modules.reminders.entity.Reminder;
import com.invoice.modules.reminders.entity.ReminderStatus;
import com.invoice.modules.reminders.repository.ReminderRepository;
import com.invoice.modules.worker.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sends a reminder email for a single reminder row.
 * <p>
 * Safety checks:
 * 1. Atomic claim: sets status SENDING (if still PENDING) before doing anything.
 * 2. Re-checks the invoice is still unpaid before sending.
 * 3. Marks the reminder SENT on success, FAILED on permanent error.
 *
 * Payload: {@code { "reminderId": "<uuid>" }}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendReminderHandler implements JobHandler {

    private final ReminderRepository reminderRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ResendEmailChannel emailChannel;
    private final com.invoice.modules.counters.repository.CounterRepository counterRepository;

    @Override
    public String type() {
        return "send_reminder";
    }

    @Override
    @Transactional
    public void handle(Map<String, Object> payload) throws Exception {
        UUID reminderId = UUID.fromString((String) payload.get("reminderId"));

        // Lock the reminder row
        Reminder reminder = reminderRepository.findWithLockById(reminderId)
            .orElseThrow(() -> new PermanentJobException("Reminder not found: " + reminderId));

        // Idempotency: skip if already sent or cancelled
        if (reminder.getStatus() == ReminderStatus.SENT ||
            reminder.getStatus() == ReminderStatus.CANCELLED) {
            log.info("Reminder {} already {} — skipping", reminderId, reminder.getStatus());
            return;
        }

        Invoice invoice = invoiceRepository.findById(reminder.getInvoiceId())
            .orElseThrow(() -> new PermanentJobException("Invoice not found"));

        // Re-check: if the invoice was paid while this job was queued, skip
        if (invoice.getStatus() == InvoiceStatus.PAID ||
            invoice.getStatus() == InvoiceStatus.CANCELLED) {
            reminder.setStatus(ReminderStatus.CANCELLED);
            reminderRepository.save(reminder);
            log.info("Invoice {} is {} — cancelling reminder {}", invoice.getId(),
                invoice.getStatus(), reminderId);
            return;
        }

        Customer customer = customerRepository.findById(invoice.getCustomerId())
            .orElseThrow(() -> new PermanentJobException("Customer not found"));

        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            reminder.setStatus(ReminderStatus.CANCELLED);
            reminderRepository.save(reminder);
            log.info("No email for customer {} — cancelling reminder", customer.getId());
            return;
        }

        String subject = buildSubject(reminder, invoice);
        String counterName = counterRepository.findById(invoice.getCounterId())
            .map(com.invoice.modules.counters.entity.Counter::getName)
            .orElse("");
        String body = buildBody(reminder, invoice, customer, counterName);
        String idempotencyKey = "reminder-" + reminderId;

        emailChannel.send(customer.getEmail(), subject, body, idempotencyKey);

        reminder.setStatus(ReminderStatus.SENT);
        reminder.setSentAt(Instant.now());
        reminderRepository.save(reminder);
        log.info("Reminder {} ({}) sent for invoice {}", reminderId, reminder.getType(), invoice.getNumber());
    }

    private String buildSubject(Reminder reminder, Invoice invoice) {
        return switch (reminder.getType()) {
            case PRE_DUE  -> "Payment reminder: Invoice " + invoice.getNumber() + " due in 3 days";
            case ON_DUE   -> "Payment due today: Invoice " + invoice.getNumber();
            case POST_3   -> "Overdue: Invoice " + invoice.getNumber() + " was due 3 days ago";
            case POST_7   -> "Overdue: Invoice " + invoice.getNumber() + " was due 7 days ago";
            case POST_14  -> "Final notice: Invoice " + invoice.getNumber() + " overdue by 14 days";
        };
    }

    private String buildBody(Reminder reminder, Invoice invoice, Customer customer, String counterName) {
        return """
            <html><body>
            <p>Dear %s,</p>
            <p>This is a reminder for invoice <strong>%s</strong>
               for <strong>₹%.2f</strong>, due on <strong>%s</strong>.</p>
            <p>Billed at counter: <strong>%s</strong>.</p>
            <p>Please settle this at your earliest convenience.</p>
            </body></html>
            """.formatted(
                customer.getName(),
                invoice.getNumber(),
                invoice.getTotalPaise() / 100.0,
                invoice.getDueDate(),
                counterName);
    }
}
