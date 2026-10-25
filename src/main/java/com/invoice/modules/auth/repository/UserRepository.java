package com.invoice.modules.auth.repository;

import com.invoice.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    List<User> findByBusinessIdOrderByCreatedAtAsc(UUID businessId);

    Optional<User> findByIdAndBusinessId(UUID id, UUID businessId);
}
