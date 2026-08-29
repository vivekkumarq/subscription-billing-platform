package com.vivek.platform.subscription.repository;

import com.vivek.platform.subscription.domain.SubscriptionEntity;
import com.vivek.platform.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    /**
     * Fetches the current subscription together with its plan and organization in one query.
     * Without the entity graph, {@code getPlan()} triggers a second select per subscription
     * (and a third for the organization) - the classic N+1 that showed up whenever billing
     * walked more than one subscription.
     */
    @EntityGraph(attributePaths = {"plan", "organization"})
    @Query("""
        SELECT s FROM SubscriptionEntity s
        WHERE s.organization.id = :orgId
          AND s.status <> com.vivek.platform.subscription.domain.SubscriptionStatus.CANCELLED
        ORDER BY s.startDate DESC
    """)
    List<SubscriptionEntity> findCurrentByOrganizationId(@Param("orgId") UUID organizationId);

    /** At most one subscription should be non-cancelled per tenant; take the newest defensively. */
    default Optional<SubscriptionEntity> findCurrent(UUID organizationId) {
        return findCurrentByOrganizationId(organizationId).stream().findFirst();
    }

    @EntityGraph(attributePaths = {"plan", "organization"})
    List<SubscriptionEntity> findByOrganizationIdOrderByStartDateDesc(UUID organizationId);

    @EntityGraph(attributePaths = {"plan", "organization"})
    List<SubscriptionEntity> findByOrganizationIdAndStatus(UUID organizationId, SubscriptionStatus status);
}
