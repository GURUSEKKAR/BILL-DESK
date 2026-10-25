package com.invoice.modules.users.service;

import com.invoice.common.exception.ApiException;
import com.invoice.modules.auth.entity.Role;
import com.invoice.modules.auth.entity.User;
import com.invoice.modules.auth.repository.UserRepository;
import com.invoice.modules.counters.entity.Counter;
import com.invoice.modules.counters.repository.CounterRepository;
import com.invoice.modules.users.dto.*;
import com.invoice.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * OWNER-only management of cashier accounts. Role enforcement lives on the
 * controller; everything here is business-scoped via {@link AuthContext}.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final CounterRepository counterRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createCashier(CreateUserRequest request) {
        UUID businessId = AuthContext.businessId();

        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("Email already registered");
        }
        // The counter must belong to the owner's business.
        Counter counter = counterRepository
            .findByIdAndBusinessId(request.counterId(), businessId)
            .orElseThrow(() -> ApiException.notFound("Counter"));

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setBusinessId(businessId);
        user.setRole(Role.CASHIER);
        user.setCounterId(counter.getId());
        user.setActive(true);
        user = userRepository.save(user);
        return toResponse(user);
    }

    public List<UserResponse> list() {
        return userRepository.findByBusinessIdOrderByCreatedAtAsc(AuthContext.businessId())
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        UUID businessId = AuthContext.businessId();
        User user = userRepository.findByIdAndBusinessId(id, businessId)
            .orElseThrow(() -> ApiException.notFound("User"));

        // Owners cannot be reassigned to a counter or deactivated through here.
        if (user.getRole() == Role.OWNER) {
            throw ApiException.badRequest("Cannot modify an owner account");
        }

        if (request.counterId() != null) {
            Counter counter = counterRepository
                .findByIdAndBusinessId(request.counterId(), businessId)
                .orElseThrow(() -> ApiException.notFound("Counter"));
            user.setCounterId(counter.getId());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        user = userRepository.save(user);
        return toResponse(user);
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getRole().name(),
            u.getCounterId(), u.isActive(), u.getCreatedAt());
    }
}
