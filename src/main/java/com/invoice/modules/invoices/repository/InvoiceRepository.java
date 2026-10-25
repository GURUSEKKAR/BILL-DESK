package com.invoice.modules.invoices.repository;

import com.invoice.modules.dashboard.dto.CustomerOutstanding;
import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.entity.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>,
                                           JpaSpecificationExecutor<Invoice> {

    /**
     * Loads an invoice with a pessimistic write lock (SELECT ... FOR UPDATE)
     * so concurrent requests can't race on a transition.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.businessId = :businessId")
    Optional<Invoice> findByIdForUpdate(@Param("id") UUID id,
                                        @Param("businessId") UUID businessId);

    /** Loads an invoice together with its line items in a single query. */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items " +
           "WHERE i.id = :id AND i.businessId = :businessId")
    Optional<Invoice> findWithItemsByIdAndBusinessId(@Param("id") UUID id,
                                                     @Param("businessId") UUID businessId);

    /**
     * Scoped detail lookup: an OWNER (counterId null) may read any counter's
     * invoice; a CASHIER (counterId set) may read only their own counter's.
     */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items " +
           "WHERE i.id = :id AND i.businessId = :businessId " +
           "AND (:counterId IS NULL OR i.counterId = :counterId)")
    Optional<Invoice> findWithItemsScoped(@Param("id") UUID id,
                                          @Param("businessId") UUID businessId,
                                          @Param("counterId") UUID counterId);

    // ---- Dashboard aggregation queries (counter-scoped) ----

    @Query("SELECT COALESCE(SUM(i.totalPaise), 0) FROM Invoice i " +
           "WHERE i.businessId = :businessId AND i.status IN :statuses " +
           "AND (:counterId IS NULL OR i.counterId = :counterId)")
    Long sumByStatuses(@Param("businessId") UUID businessId,
                      @Param("counterId") UUID counterId,
                      @Param("statuses") Collection<InvoiceStatus> statuses);

    @Query("SELECT COUNT(i) FROM Invoice i " +
           "WHERE i.businessId = :businessId AND i.status IN :statuses " +
           "AND (:counterId IS NULL OR i.counterId = :counterId)")
    Long countByStatuses(@Param("businessId") UUID businessId,
                         @Param("counterId") UUID counterId,
                         @Param("statuses") Collection<InvoiceStatus> statuses);

    @Query("""
        SELECT new com.invoice.modules.dashboard.dto.CustomerOutstanding(
            c.id, c.name, COALESCE(SUM(i.totalPaise), 0))
        FROM Invoice i, com.invoice.modules.customers.entity.Customer c
        WHERE i.customerId = c.id
          AND i.businessId = :businessId
          AND i.status IN :statuses
          AND (:counterId IS NULL OR i.counterId = :counterId)
        GROUP BY c.id, c.name
        ORDER BY SUM(i.totalPaise) DESC
        """)
    List<CustomerOutstanding> outstandingPerCustomer(@Param("businessId") UUID businessId,
                                                     @Param("counterId") UUID counterId,
                                                     @Param("statuses") Collection<InvoiceStatus> statuses);

    /** Sum of totals for a (business, counter, status set), issued today. */
    @Query("SELECT COALESCE(SUM(i.totalPaise), 0) FROM Invoice i " +
           "WHERE i.businessId = :businessId AND i.counterId = :counterId " +
           "AND i.status IN :statuses AND i.issueDate = :day")
    Long sumByCounterStatusesOnDay(@Param("businessId") UUID businessId,
                                   @Param("counterId") UUID counterId,
                                   @Param("statuses") Collection<InvoiceStatus> statuses,
                                   @Param("day") LocalDate day);

    /** Outstanding (business-wide, all counters) for one customer — for balance lookup. */
    @Query("SELECT COALESCE(SUM(i.totalPaise), 0) FROM Invoice i " +
           "WHERE i.businessId = :businessId AND i.customerId = :customerId " +
           "AND i.status IN :statuses")
    Long outstandingForCustomer(@Param("businessId") UUID businessId,
                                @Param("customerId") UUID customerId,
                                @Param("statuses") Collection<InvoiceStatus> statuses);

    /** Distinct customers that have at least one invoice (any status) on a counter. */
    @Query("SELECT COUNT(DISTINCT i.customerId) FROM Invoice i " +
           "WHERE i.businessId = :businessId AND i.counterId = :counterId")
    Long distinctCustomerCount(@Param("businessId") UUID businessId,
                               @Param("counterId") UUID counterId);

    /** Plain (no lock) tenant-scoped lookup. */
    Optional<Invoice> findByIdAndBusinessId(UUID id, UUID businessId);

    // ---- Overdue detection ----

    @Query("SELECT i FROM Invoice i WHERE i.status = :status AND i.dueDate < :date")
    List<Invoice> findOverdueCandidates(@Param("status") InvoiceStatus status,
                                        @Param("date") LocalDate date);
}
