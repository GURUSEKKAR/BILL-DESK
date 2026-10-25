package com.invoice.modules.payments.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "counter_id", nullable = false)
    private UUID counterId;

    /** The user who recorded the payment. */
    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false)
    private String method;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @CreatedDate
    @Column(name = "paid_at", updatable = false)
    private Instant paidAt;
}
