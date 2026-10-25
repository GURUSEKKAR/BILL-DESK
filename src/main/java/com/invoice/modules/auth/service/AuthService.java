package com.invoice.modules.auth.service;

import com.invoice.common.exception.ApiException;
import com.invoice.modules.auth.dto.*;
import com.invoice.modules.auth.entity.Business;
import com.invoice.modules.auth.entity.Role;
import com.invoice.modules.auth.entity.User;
import com.invoice.modules.auth.repository.BusinessRepository;
import com.invoice.modules.auth.repository.UserRepository;
import com.invoice.modules.counters.entity.Counter;
import com.invoice.modules.counters.repository.CounterRepository;
import com.invoice.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final CounterRepository counterRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I,O,0,1

    /**
     * Creates the owner user and their business (+ default counter) in one
     * transaction. The business gets a unique, human-typable login code.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("Email already registered");
        }

        // The users <-> businesses FKs are DEFERRABLE INITIALLY DEFERRED, so we
        // can insert the business with a not-yet-existing owner and fix it up
        // after the user is saved. Let Hibernate generate both ids naturally.
        Business business = new Business();
        business.setOwnerUserId(UUID.randomUUID()); // placeholder, set properly below
        business.setName(request.businessName());
        business.setLoginCode(resolveLoginCode(request.businessCode()));
        business = businessRepository.saveAndFlush(business);

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setBusinessId(business.getId());
        user.setRole(Role.OWNER);
        user.setCounterId(null);
        user.setActive(true);
        user = userRepository.saveAndFlush(user);

        // Point the business at its real owner now that the user row exists.
        business.setOwnerUserId(user.getId());
        business = businessRepository.save(business);

        // Every new business gets a default "Main" (C1) counter.
        Counter main = new Counter();
        main.setBusinessId(business.getId());
        main.setName("Main");
        main.setCode("C1");
        main.setActive(true);
        counterRepository.save(main);

        return ownerResponse(user, business);
    }

    /**
     * Mode 2 — admin login. Email + admin password -> OWNER session.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User is deactivated");
        }

        Business business = businessRepository.findById(user.getBusinessId())
            .orElseThrow(() -> ApiException.notFound("Business"));

        // A per-person CASHIER user signing in through the admin form resolves
        // to their pinned counter; an OWNER gets a business-wide session.
        if (user.getRole() == Role.CASHIER) {
            Counter counter = counterRepository
                .findByIdAndBusinessId(user.getCounterId(), business.getId())
                .orElseThrow(() -> ApiException.notFound("Counter"));
            return cashierResponse(user, business, counter);
        }
        return ownerResponse(user, business);
    }

    /**
     * Mode 1 — counter login. Business login code + counter code + the counter's
     * own password -> CASHIER session pinned to that counter. The session is
     * backed by the business owner's user id (counters are not people), but the
     * token's role is CASHIER and it is scoped to the counter.
     */
    public AuthResponse counterLogin(CounterLoginRequest request) {
        Business business = businessRepository.findByLoginCode(normalizeCode(request.businessLoginCode()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid business or counter"));

        Counter counter = counterRepository
            .findByBusinessIdAndCode(business.getId(), request.counterCode().trim())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid business or counter"));

        if (!counter.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Counter is deactivated");
        }
        if (counter.getPasswordHash() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                "Counter login is not enabled; ask the owner to set a counter password");
        }
        if (!passwordEncoder.matches(request.counterPassword(), counter.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid business or counter");
        }

        User owner = userRepository.findById(business.getOwnerUserId())
            .orElseThrow(() -> ApiException.notFound("Business owner"));

        return cashierResponse(owner, business, counter);
    }

    /**
     * Exchanges a valid refresh token for a new pair, re-loading live state so
     * a deactivated user/counter cannot be refreshed.
     */
    public AuthResponse refresh(RefreshRequest request) {
        try {
            Claims claims = jwtService.parse(request.refreshToken());
            if (!"REFRESH".equals(claims.get("type"))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Not a refresh token");
            }
            UUID userId = UUID.fromString(claims.getSubject());
            String roleClaim = claims.get("role", String.class);
            String counterClaim = claims.get("counterId", String.class);

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
            if (!user.isActive()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "User is deactivated");
            }

            Business business = businessRepository.findById(user.getBusinessId())
                .orElseThrow(() -> ApiException.notFound("Business"));

            // Counter (CASHIER) session: re-validate the counter is still live.
            if (Role.CASHIER.name().equals(roleClaim) && counterClaim != null) {
                Counter counter = counterRepository
                    .findByIdAndBusinessId(UUID.fromString(counterClaim), business.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Counter no longer exists"));
                if (!counter.isActive()) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "Counter is deactivated");
                }
                return cashierResponse(user, business, counter);
            }
            return ownerResponse(user, business);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
    }

    // ---- helpers ------------------------------------------------------------

    /** Owner-only: read current business settings. */
    public BusinessResponse getBusiness() {
        Business business = businessRepository.findById(
                com.invoice.security.AuthContext.businessId())
            .orElseThrow(() -> ApiException.notFound("Business"));
        return new BusinessResponse(business.getId(), business.getName(), business.getLoginCode());
    }

    /** Owner-only: update business name and/or login code. */
    @Transactional
    public BusinessResponse updateBusiness(UpdateBusinessRequest request) {
        Business business = businessRepository.findById(
                com.invoice.security.AuthContext.businessId())
            .orElseThrow(() -> ApiException.notFound("Business"));

        if (request.name() != null && !request.name().isBlank()) {
            business.setName(request.name().trim());
        }
        if (request.loginCode() != null && !request.loginCode().isBlank()) {
            String normalized = normalizeCode(request.loginCode());
            if (!normalized.equals(business.getLoginCode())
                && businessRepository.existsByLoginCode(normalized)) {
                throw ApiException.conflict("Business code already in use: " + normalized);
            }
            business.setLoginCode(normalized);
        }
        business = businessRepository.save(business);
        return new BusinessResponse(business.getId(), business.getName(), business.getLoginCode());
    }

    /** Use the admin-chosen code (validated, normalized) or generate one. */
    private String resolveLoginCode(String requested) {
        if (requested == null || requested.isBlank()) {
            return generateUniqueLoginCode();
        }
        String normalized = normalizeCode(requested);
        if (businessRepository.existsByLoginCode(normalized)) {
            throw ApiException.conflict("Business code already in use: " + normalized);
        }
        return normalized;
    }

    /** Codes are stored/compared uppercased so login is case-insensitive. */
    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private AuthResponse ownerResponse(User user, Business business) {
        JwtService.TokenPair tokens = jwtService.generateTokens(
            user.getId(), business.getId(), Role.OWNER.name(), null);
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(),
            business.getId(), business.getName(), business.getLoginCode(),
            Role.OWNER.name(), null, null);
    }

    private AuthResponse cashierResponse(User backingUser, Business business, Counter counter) {
        JwtService.TokenPair tokens = jwtService.generateTokens(
            backingUser.getId(), business.getId(), Role.CASHIER.name(), counter.getId());
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(),
            business.getId(), business.getName(), business.getLoginCode(),
            Role.CASHIER.name(), counter.getId(), counter.getCode());
    }

    private String generateUniqueLoginCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!businessRepository.existsByLoginCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not allocate a unique business login code");
    }
}
