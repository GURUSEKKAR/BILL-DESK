package com.invoice.modules.counters.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A billing counter within a business (e.g. a physical till / desk).
 * <p>
 * One business has many counters. Invoices and payments are scoped to a
 * counter; a CASHIER is pinned to exactly one. The {@code code} is a short,
 * business-unique label (C1, C2, ...) used in per-counter invoice numbers.
 */
@Entity
@Table(name = "counters")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Counter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * BCrypt hash of the counter's login password. Null = counter login
     * disabled until the owner sets a password.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
