package com.invoice.modules.reminders.repository;

import com.invoice.modules.reminders.entity.Reminder;
import com.invoice.modules.reminders.entity.ReminderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    /** Fetch PENDING reminders whose scheduled time has arrived. */
    List<Reminder> findByStatusAndScheduledAtBefore(ReminderStatus status, Instant now);

    /** Lock a single reminder row for the atomic claim. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reminder> findWithLockById(UUID id);

    /**
     * Cancel all PENDING reminders for an invoice in the same transaction
     * as the payment (called when marking as paid).
     */
    @Modifying
    @Query("UPDATE Reminder r SET r.status = com.invoice.modules.reminders.entity.ReminderStatus.CANCELLED, " +
           "r.updatedAt = :now " +
           "WHERE r.invoiceId = :invoiceId " +
           "AND r.status = com.invoice.modules.reminders.entity.ReminderStatus.PENDING")
    int cancelPendingForInvoice(@Param("invoiceId") UUID invoiceId,
                                @Param("now") java.time.Instant now);

    List<Reminder> findByInvoiceId(UUID invoiceId);
}
