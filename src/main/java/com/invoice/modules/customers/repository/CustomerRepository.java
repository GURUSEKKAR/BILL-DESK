package com.invoice.modules.customers.repository;

import com.invoice.modules.customers.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Every query method takes businessId so tenant isolation is enforced at the
 * repository level. {@link #findByIdAndBusinessId} returns empty when a
 * cross-tenant id is requested — the caller surfaces a 404.
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    /**
     * Pessimistic lock on the customer row (SELECT ... FOR UPDATE) — used by the
     * credit-limit check so two counters billing the same customer in parallel
     * are serialized. Always lock the customer BEFORE the invoice to keep a
     * consistent lock order and avoid deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id AND c.businessId = :businessId")
    Optional<Customer> findByIdForUpdate(@Param("id") UUID id,
                                         @Param("businessId") UUID businessId);

    Page<Customer> findByBusinessIdOrderByIdDesc(UUID businessId, Pageable pageable);

    @Query("""
        SELECT c FROM Customer c
        WHERE c.businessId = :businessId
          AND LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
        ORDER BY c.id DESC
        """)
    Page<Customer> searchByName(@Param("businessId") UUID businessId,
                                @Param("search") String search,
                                Pageable pageable);
}
