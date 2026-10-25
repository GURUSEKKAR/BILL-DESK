package com.invoice.modules.paymentlinks.controller;

import com.invoice.modules.paymentlinks.service.PaymentLinkService;
import com.invoice.modules.payments.dto.PaymentResponse;
import com.invoice.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentLinkController {

    private final PaymentLinkService paymentLinkService;

    /** Create a payment link for an invoice. */
    @PostMapping("/invoices/{id}/payment-link")
    public Map<String, String> create(@PathVariable UUID id) {
        String token = paymentLinkService.createLink(id, AuthContext.businessId());
        return Map.of("token", token, "url", "/pay/" + token);
    }

    /**
     * Public endpoint — no auth required.
     * The token itself is the authentication.
     */
    @PostMapping("/pay/{token}")
    public PaymentResponse pay(@PathVariable String token,
                               @RequestParam(defaultValue = "ONLINE") String method) {
        return paymentLinkService.payViaLink(token, method);
    }
}
