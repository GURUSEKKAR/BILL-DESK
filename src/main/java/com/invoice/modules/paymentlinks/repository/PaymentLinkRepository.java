package com.invoice.modules.paymentlinks.repository;

import com.invoice.modules.paymentlinks.entity.PaymentLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {

    Optional<PaymentLink> findByToken(String token);
}
