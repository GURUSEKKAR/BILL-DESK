package com.invoice.common.audit;

import com.invoice.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes audit-log entries for state-machine transitions.
 * <p>
 * Called inside the caller's transaction, so the audit row and the state
 * change commit (or roll back) together.
 * <p>
 * Phase 3: every entry records <b>who</b> did it ({@code actorUserId}) and at
 * <b>which counter</b> ({@code counterId}) — the answer to "who did this, and
 * at which counter?". The counter may be null for business-wide/system actions.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /** Full form: explicit actor + counter. */
    public void record(UUID businessId, UUID counterId, String entity, UUID entityId,
                       String fromState, String toState, UUID actorUserId) {
        AuditLog entry = new AuditLog();
        entry.setBusinessId(businessId);
        entry.setCounterId(counterId);
        entry.setEntity(entity);
        entry.setEntityId(entityId);
        entry.setFromState(fromState);
        entry.setToState(toState);
        entry.setActorUserId(actorUserId);
        auditLogRepository.save(entry);
    }

    /** Convenience: uses the current authenticated user as the actor. */
    public void record(UUID businessId, UUID counterId, String entity, UUID entityId,
                       String fromState, String toState) {
        UUID actor = null;
        try {
            actor = AuthContext.userId();
        } catch (Exception ignored) {
            // system-initiated transitions (e.g. overdue job) have no actor
        }
        record(businessId, counterId, entity, entityId, fromState, toState, actor);
    }
}
