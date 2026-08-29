package com.vivek.platform.subscription.repository;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    Optional<OrganizationEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
