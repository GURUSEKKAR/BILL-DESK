package com.invoice.modules.counters.service;

import com.invoice.common.exception.ApiException;
import com.invoice.modules.counters.dto.*;
import com.invoice.modules.counters.entity.Counter;
import com.invoice.modules.counters.repository.CounterRepository;
import com.invoice.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Counter CRUD. All methods are business-scoped via {@link AuthContext}.
 * Role enforcement (OWNER-only) lives on the controller with {@code @PreAuthorize}.
 */
@Service
@RequiredArgsConstructor
public class CounterService {

    private final CounterRepository counterRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CounterResponse create(CreateCounterRequest request) {
        UUID businessId = AuthContext.businessId();
        String code = request.code().trim();
        if (counterRepository.existsByBusinessIdAndCode(businessId, code)) {
            throw ApiException.conflict("Counter code already exists: " + code);
        }
        Counter counter = new Counter();
        counter.setBusinessId(businessId);
        counter.setName(request.name().trim());
        counter.setCode(code);
        counter.setActive(true);
        if (request.password() != null && !request.password().isBlank()) {
            counter.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        counter = counterRepository.save(counter);
        return toResponse(counter);
    }

    public List<CounterResponse> list() {
        return counterRepository.findByBusinessIdOrderByCodeAsc(AuthContext.businessId())
            .stream().map(this::toResponse).toList();
    }

    public CounterResponse get(UUID id) {
        return toResponse(getOwned(id));
    }

    @Transactional
    public CounterResponse update(UUID id, UpdateCounterRequest request) {
        Counter counter = getOwned(id);
        if (request.name() != null) counter.setName(request.name().trim());
        if (request.active() != null) counter.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) {
            counter.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        counter = counterRepository.save(counter);
        return toResponse(counter);
    }

    private Counter getOwned(UUID id) {
        return counterRepository.findByIdAndBusinessId(id, AuthContext.businessId())
            .orElseThrow(() -> ApiException.notFound("Counter"));
    }

    private CounterResponse toResponse(Counter c) {
        return new CounterResponse(c.getId(), c.getName(), c.getCode(),
            c.isActive(), c.getPasswordHash() != null, c.getCreatedAt());
    }
}
