package com.invoice.modules.payments.repository;

import com.invoice.modules.payments.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Total collected for a (business, counter) within [from, to).
     * counterId null = all counters in the business.
     */
    @Query("SELECT COALESCE(SUM(p.amountPaise), 0) FROM Payment p " +
           "WHERE p.businessId = :businessId " +
           "AND (:counterId IS NULL OR p.counterId = :counterId) " +
           "AND p.paidAt >= :from AND p.paidAt < :to")
    Long collectedBetween(@Param("businessId") UUID businessId,
                          @Param("counterId") UUID counterId,
                          @Param("from") Instant from,
                          @Param("to") Instant to);

    /**
     * Collected grouped by payment method for a (business, counter) in [from, to).
     * Returns rows of [method, sum].
     */
    @Query("SELECT p.method, COALESCE(SUM(p.amountPaise), 0) FROM Payment p " +
           "WHERE p.businessId = :businessId " +
           "AND (:counterId IS NULL OR p.counterId = :counterId) " +
           "AND p.paidAt >= :from AND p.paidAt < :to " +
           "GROUP BY p.method")
    List<Object[]> collectedByMethodBetween(@Param("businessId") UUID businessId,
                                            @Param("counterId") UUID counterId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);
}
