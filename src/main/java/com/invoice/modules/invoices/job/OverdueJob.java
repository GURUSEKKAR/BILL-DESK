package com.invoice.modules.invoices.job;

import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.entity.Invoice;
import com.invoice.modules.invoices.repository.InvoiceRepository;
import com.invoice.modules.invoices.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that moves SENT invoices past their due date to OVERDUE.
 * <p>
 * Each invoice is processed in its own transaction (via {@link InvoiceService#markOverdue})
 * so one failure doesn't roll back the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueJob {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;

    @Scheduled(cron = "${app.overdue.cron:0 0 1 * * *}")
    public void scheduled() {
        runOnce();
    }

    /**
     * @return number of invoices transitioned to OVERDUE
     */
    public int runOnce() {
        log.info("Running overdue detection job");
        List<Invoice> candidates = invoiceRepository.findOverdueCandidates(
            InvoiceStatus.SENT, LocalDate.now());
        log.info("Found {} overdue candidates", candidates.size());

        int updated = 0;
        for (Invoice invoice : candidates) {
            try {
                invoiceService.markOverdue(invoice.getId(), invoice.getBusinessId());
                updated++;
            } catch (Exception e) {
                log.error("Failed to mark invoice {} as overdue", invoice.getId(), e);
            }
        }
        log.info("Marked {} invoices as OVERDUE", updated);
        return updated;
    }
}
