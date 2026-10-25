package com.invoice.modules.auth.repository;

import com.invoice.modules.auth.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    Optional<Business> findByOwnerUserId(UUID ownerUserId);

    Optional<Business> findByLoginCode(String loginCode);

    boolean existsByLoginCode(String loginCode);
}
