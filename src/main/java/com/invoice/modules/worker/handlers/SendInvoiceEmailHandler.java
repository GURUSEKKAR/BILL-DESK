package com.invoice.modules.worker.handlers;

import com.invoice.modules.customers.entity.Customer;
import com.invoice.modules.customers.repository.CustomerRepository;
import com.invoice.modules.invoices.entity.Invoice;
import com.invoice.modules.invoices.repository.InvoiceRepository;
import com.invoice.modules.jobs.service.PermanentJobException;
import com.invoice.modules.notifications.ResendEmailChannel;
import com.invoice.modules.worker.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Sends the invoice notification email to the customer (HTML, no PDF).
 * Payload: {@code { "invoiceId": "<uuid>" }}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendInvoiceEmailHandler implements JobHandler {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ResendEmailChannel emailChannel;

    @Override
    public String type() {
        return "send_invoice_email";
    }

    @Override
    public void handle(Map<String, Object> payload) throws Exception {
        UUID invoiceId = UUID.fromString((String) payload.get("invoiceId"));

        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new PermanentJobException("Invoice not found: " + invoiceId));

        Customer customer = customerRepository.findById(invoice.getCustomerId())
            .orElseThrow(() -> new PermanentJobException("Customer not found: " + invoice.getCustomerId()));

        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.info("Customer {} has no email, skipping invoice email for {}", customer.getId(), invoiceId);
            return;
        }

        String subject = "Invoice " + invoice.getNumber() + " from your vendor";
        String idempotencyKey = "invoice-email-" + invoiceId;
        String body = buildBody(invoice, customer);

        emailChannel.send(customer.getEmail(), subject, body, idempotencyKey);
        log.info("Invoice email sent for {} to {}", invoice.getNumber(), customer.getEmail());
    }

    private String buildBody(Invoice invoice, Customer customer) {
        return """
            <html><body>
            <p>Dear %s,</p>
            <p>This is your invoice <strong>%s</strong>
               for <strong>₹%.2f</strong>, due on <strong>%s</strong>.</p>
            <p>You can pay online using the link that will be sent to you shortly.</p>
            <p>Thank you for your business.</p>
            </body></html>
            """.formatted(
                customer.getName(),
                invoice.getNumber(),
                invoice.getTotalPaise() / 100.0,
                invoice.getDueDate());
    }
}
