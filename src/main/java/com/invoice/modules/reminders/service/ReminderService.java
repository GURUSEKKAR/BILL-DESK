package com.invoice.modules.reminders.service;

import com.invoice.modules.reminders.entity.Reminder;
import com.invoice.modules.reminders.entity.ReminderStatus;
import com.invoice.modules.reminders.entity.ReminderType;
import com.invoice.modules.reminders.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Creates the 5 reminder rows when an invoice is sent.
 * Uses {@code UNIQUE(invoice_id, type)} so inserting twice is a no-op (idempotent).
 * Cancels pending reminders when an invoice is paid.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;

    /**
     * Creates all 5 reminder rows for the invoice.
     * Must be called inside the same transaction as the SENT transition.
     */
    @Transactional
    public void scheduleReminders(UUID invoiceId, UUID businessId, LocalDate dueDate) {
        create(invoiceId, businessId, ReminderType.PRE_DUE,
            atNoon(dueDate.minusDays(3)));
        create(invoiceId, businessId, ReminderType.ON_DUE,
            atNoon(dueDate));
        create(invoiceId, businessId, ReminderType.POST_3,
            atNoon(dueDate.plusDays(3)));
        create(invoiceId, businessId, ReminderType.POST_7,
            atNoon(dueDate.plusDays(7)));
        create(invoiceId, businessId, ReminderType.POST_14,
            atNoon(dueDate.plusDays(14)));
        log.info("Scheduled 5 reminders for invoice {}", invoiceId);
    }

    /**
     * Cancels all PENDING reminders for an invoice.
     * Must be called inside the same transaction as the payment.
     */
    @Transactional
    public void cancelReminders(UUID invoiceId) {
        int count = reminderRepository.cancelPendingForInvoice(invoiceId, Instant.now());
        log.info("Cancelled {} pending reminders for invoice {}", count, invoiceId);
    }

    private void create(UUID invoiceId, UUID businessId, ReminderType type, Instant scheduledAt) {
        Reminder r = new Reminder();
        r.setInvoiceId(invoiceId);
        r.setBusinessId(businessId);
        r.setType(type);
        r.setStatus(ReminderStatus.PENDING);
        r.setScheduledAt(scheduledAt);
        // UNIQUE(invoice_id, type) constraint prevents duplicates if called twice
        try {
            reminderRepository.save(r);
        } catch (Exception e) {
            log.debug("Reminder {}/{} already exists (idempotent)", invoiceId, type);
        }
    }

    private Instant atNoon(LocalDate date) {
        return date.atTime(12, 0).toInstant(ZoneOffset.UTC);
    }
}
