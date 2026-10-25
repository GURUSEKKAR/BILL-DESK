package com.invoice.modules.paymentlinks.service;

import com.invoice.common.exception.ApiException;
import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.entity.Invoice;
import com.invoice.modules.invoices.repository.InvoiceRepository;
import com.invoice.modules.paymentlinks.entity.PaymentLink;
import com.invoice.modules.paymentlinks.repository.PaymentLinkRepository;
import com.invoice.modules.payments.dto.CreatePaymentRequest;
import com.invoice.modules.payments.dto.PaymentResponse;
import com.invoice.modules.payments.service.PaymentService;
import com.invoice.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Manages signed payment-link tokens.
 * <p>
 * A link is a secure random token stored in the DB. Paying through it calls
 * the same {@link PaymentService#markAsPaid} logic so idempotency is free.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLinkService {

    private final PaymentLinkRepository paymentLinkRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentService paymentService;

    private final SecureRandom random = new SecureRandom();

    /**
     * Creates and persists a new payment link for the invoice.
     * The token is 32 bytes of crypto-random data, base64url-encoded.
     */
    @Transactional
    public String createLink(UUID invoiceId, UUID businessId) {
        Invoice invoice = invoiceRepository
            .findByIdAndBusinessId(invoiceId, businessId)
            .orElseThrow(() -> ApiException.notFound("Invoice"));

        if (invoice.getStatus() == InvoiceStatus.PAID ||
            invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw ApiException.conflict("Invoice is already " + invoice.getStatus());
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PaymentLink link = new PaymentLink();
        link.setInvoiceId(invoiceId);
        link.setBusinessId(businessId);
        link.setToken(token);
        link.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        paymentLinkRepository.save(link);

        log.info("Payment link created for invoice {}", invoiceId);
        return token;
    }

    /**
     * Pays an invoice via its token link — calls the same PaymentService
     * so idempotency, locking, and audit logging all work identically.
     */
    @Transactional
    public PaymentResponse payViaLink(String token, String method) {
        PaymentLink link = paymentLinkRepository.findByToken(token)
            .orElseThrow(() -> ApiException.notFound("Payment link"));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.conflict("Payment link has expired");
        }

        Invoice invoice = invoiceRepository.findById(link.getInvoiceId())
            .orElseThrow(() -> ApiException.notFound("Invoice"));

        // Populate SecurityContext so AuthContext.businessId() works in PaymentService.
        // The payer is anonymous (public link): business-wide OWNER principal with a
        // null userId so the payment's received_by is left null (no real user acted).
        AuthUser principal = new AuthUser(
            null, link.getBusinessId(),
            com.invoice.modules.auth.entity.Role.OWNER, null);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        // Idempotency key derived from link id — same link pays exactly once
        String idempotencyKey = "link-" + link.getId();
        CreatePaymentRequest req = new CreatePaymentRequest(invoice.getTotalPaise(), method);
        PaymentResponse result = paymentService.markAsPaid(
            link.getInvoiceId(), req, idempotencyKey);

        if (link.getUsedAt() == null) {
            link.setUsedAt(Instant.now());
            paymentLinkRepository.save(link);
        }

        return result;
    }
}
