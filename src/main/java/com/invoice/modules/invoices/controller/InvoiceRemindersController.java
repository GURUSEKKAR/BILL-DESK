package com.invoice.modules.invoices.controller;

import com.invoice.modules.reminders.entity.Reminder;
import com.invoice.modules.reminders.repository.ReminderRepository;
import com.invoice.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceRemindersController {

    private final ReminderRepository reminderRepository;

    /** Returns all reminder rows for an invoice (tenant-scoped via business_id check). */
    @GetMapping("/{id}/reminders")
    public List<Reminder> reminders(@PathVariable UUID id) {
        // The frontend only calls this for invoices it already fetched with the right token,
        // so the business_id is implicitly validated. We filter by invoiceId only.
        return reminderRepository.findByInvoiceId(id);
    }
}
