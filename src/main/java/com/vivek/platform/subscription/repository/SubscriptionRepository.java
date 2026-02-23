package com.vivek.platform.subscription.repository;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByOrganizationAndActiveTrue(OrganizationEntity organization);
}