package com.invoice.modules.payments.service;

import com.invoice.common.audit.AuditLogService;
import com.invoice.common.exception.ApiException;
import com.invoice.modules.invoices.InvoiceStateMachine;
import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.entity.Invoice;
import com.invoice.modules.invoices.repository.InvoiceRepository;
import com.invoice.modules.invoices.service.InvoiceService;
import com.invoice.modules.payments.dto.CreatePaymentRequest;
import com.invoice.modules.payments.dto.PaymentResponse;
import com.invoice.modules.payments.entity.Payment;
import com.invoice.modules.payments.repository.PaymentRepository;
import com.invoice.modules.reminders.service.ReminderService;
import com.invoice.security.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Records a payment and transitions the invoice to PAID in a single transaction.
 * <p>
 * The {@code Idempotency-Key} header prevents double payment: if the same key
 * arrives twice, the second request returns the original payment instead of
 * creating a duplicate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogService auditLogService;
    private final ReminderService reminderService;

    @Transactional
    public PaymentResponse markAsPaid(UUID invoiceId,
                                      CreatePaymentRequest request,
                                      String idempotencyKey) {
        UUID businessId = AuthContext.businessId();
        String key = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : null;

        // 1. Fast path: if we've already processed this key, return the original result
        if (key != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        // 2. Lock the invoice row (SELECT ... FOR UPDATE)
        Invoice invoice = invoiceService.getForUpdate(invoiceId, businessId);

        // Scope guard: a CASHIER may only pay their own counter's invoices.
        UUID callerCounter = AuthContext.counterId();
        if (callerCounter != null && !callerCounter.equals(invoice.getCounterId())) {
            throw ApiException.notFound("Invoice");
        }

        // 3. Handle the race: another request with the same key may have just committed
        if (invoice.getStatus() == InvoiceStatus.PAID && key != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        // 4. Validate the transition is legal (SENT/OVERDUE -> PAID)
        InvoiceStatus from = invoice.getStatus();
        InvoiceStateMachine.validate(from, InvoiceStatus.PAID);

        // 5. Insert the payment (unique constraint on idempotency_key catches duplicates)
        Payment payment = new Payment();
        payment.setInvoiceId(invoiceId);
        payment.setBusinessId(businessId);
        payment.setCounterId(invoice.getCounterId()); // payment inherits invoice's counter
        payment.setReceivedBy(AuthContext.userId());
        payment.setAmountPaise(request.amountPaise());
        payment.setMethod(request.method());
        if (key != null) {
            payment.setIdempotencyKey(key);
        }

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            // Lost the race to a concurrent request with the same key
            if (key != null) {
                return paymentRepository.findByIdempotencyKey(key)
                    .map(this::toResponse)
                    .orElseThrow(() -> e);
            }
            throw e;
        }

        // 6. Transition to PAID (same transaction, same locked row)
        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);
        auditLogService.record(businessId, invoice.getCounterId(), "invoice", invoiceId,
            from.name(), InvoiceStatus.PAID.name());

        // 7. Cancel any pending reminders in the same transaction
        reminderService.cancelReminders(invoiceId);

        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(), payment.getInvoiceId(),
            payment.getAmountPaise(), payment.getMethod(),
            payment.getPaidAt());
    }
}
