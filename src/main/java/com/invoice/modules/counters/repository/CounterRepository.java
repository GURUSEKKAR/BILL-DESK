package com.invoice.modules.counters.repository;

import com.invoice.modules.counters.entity.Counter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CounterRepository extends JpaRepository<Counter, UUID> {

    List<Counter> findByBusinessIdOrderByCodeAsc(UUID businessId);

    Optional<Counter> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<Counter> findByBusinessIdAndCode(UUID businessId, String code);

    boolean existsByBusinessIdAndCode(UUID businessId, String code);

    long countByBusinessId(UUID businessId);
}
