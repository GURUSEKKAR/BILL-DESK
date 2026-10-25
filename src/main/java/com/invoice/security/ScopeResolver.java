package com.invoice.security;

import com.invoice.common.exception.ApiException;
import com.invoice.modules.auth.entity.Role;
import com.invoice.modules.auth.entity.User;
import com.invoice.modules.auth.repository.UserRepository;
import com.invoice.modules.counters.repository.CounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The one place that turns "who is calling + what counter did they ask for"
 * into an effective {@link Scope}. Every scoped read/write resolves here so
 * the rules live in exactly one spot.
 *
 * <ul>
 *   <li><b>CASHIER</b>: always their own counter. Any requested counterId is
 *       ignored (not an error — silently overridden).</li>
 *   <li><b>OWNER</b>: uses the requested counterId when present (validated to
 *       belong to the business), otherwise business-wide (null).</li>
 * </ul>
 *
 * <p>For sensitive actions the caller should use {@link #resolveForWrite} /
 * {@link #requireActiveUser()}, which re-load the user from the database so a
 * stale JWT (role/counter changed, or the user deactivated after the token was
 * issued) cannot be used.
 */
@Component
@RequiredArgsConstructor
public class ScopeResolver {

    private final UserRepository userRepository;
    private final CounterRepository counterRepository;

    /**
     * Resolve a read scope from the current JWT principal.
     *
     * @param requestedCounterId the counterId query param (may be null); honoured
     *                           only for an OWNER and only if it belongs to the business.
     */
    public Scope resolve(UUID requestedCounterId) {
        AuthUser principal = AuthContext.current();
        UUID businessId = principal.getBusinessId();

        if (principal.getRole() == Role.CASHIER) {
            // Cashier is pinned; ignore whatever counter was requested.
            return new Scope(businessId, principal.getCounterId());
        }

        // OWNER
        if (requestedCounterId == null) {
            return new Scope(businessId, null); // business-wide
        }
        // Validate the requested counter belongs to this business (else 404).
        counterRepository.findByIdAndBusinessId(requestedCounterId, businessId)
            .orElseThrow(() -> ApiException.notFound("Counter"));
        return new Scope(businessId, requestedCounterId);
    }

    /**
     * Resolve a scope for a write (invoice/payment creation).
     * <p>
     * CASHIER: their own counter (from the signed token), re-validated for
     * liveness. OWNER: must pass an explicit, business-owned counterId.
     */
    public Scope resolveForWrite(UUID requestedCounterId) {
        AuthUser principal = requireActivePrincipal();
        UUID businessId = principal.getBusinessId();

        if (principal.getRole() == Role.CASHIER) {
            return new Scope(businessId, principal.getCounterId());
        }

        // OWNER must specify a concrete counter to write to.
        if (requestedCounterId == null) {
            throw ApiException.badRequest("counterId is required when an owner creates this record");
        }
        counterRepository.findByIdAndBusinessId(requestedCounterId, businessId)
            .orElseThrow(() -> ApiException.notFound("Counter"));
        return new Scope(businessId, requestedCounterId);
    }

    /**
     * Validate the current session against live state and return the backing
     * user. The JWT's role/counter are authoritative (signed); this guards
     * against liveness changes since the token was minted:
     * <ul>
     *   <li>the backing user still exists and is active;</li>
     *   <li>for a CASHIER/counter session, the counter still exists and is active.</li>
     * </ul>
     */
    public User requireActiveUser() {
        requireActivePrincipal();
        return userRepository.findById(AuthContext.current().getUserId())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    private AuthUser requireActivePrincipal() {
        AuthUser principal = AuthContext.current();

        User user = userRepository.findById(principal.getUserId())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User is deactivated");
        }

        if (principal.getRole() == Role.CASHIER) {
            UUID counterId = principal.getCounterId();
            if (counterId == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid cashier session");
            }
            var counter = counterRepository.findByIdAndBusinessId(counterId, principal.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Counter no longer exists"));
            if (!counter.isActive()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Counter is deactivated");
            }
            // If this is a per-person cashier user, they must still be pinned here.
            if (user.getRole() == Role.CASHIER
                && !java.util.Objects.equals(user.getCounterId(), counterId)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Session is stale; please sign in again");
            }
        } else {
            // OWNER session must still map to a user who can act business-wide.
            if (user.getRole() != Role.OWNER) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Session is stale; please sign in again");
            }
        }
        return principal;
    }
}
