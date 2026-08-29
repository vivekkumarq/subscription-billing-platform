package com.vivek.platform.subscription.repository;

import com.vivek.platform.subscription.domain.UsageEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface UsageEventRepository extends JpaRepository<UsageEventEntity, UUID> {

    /**
     * Aggregates in the database rather than loading every usage row into memory, and takes the
     * organization id rather than a managed entity so callers do not have to hydrate one first.
     *
     * <p>The period is half-open: {@code [start, end)}. The previous version used
     * {@code BETWEEN start AND end} with an end bound of 23:59:59 on the last day of the month,
     * which silently dropped any usage recorded in the final second of the period.</p>
     */
    @Query("""
        SELECT COALESCE(SUM(u.unitsConsumed), 0)
        FROM UsageEventEntity u
        WHERE u.organization.id = :orgId
          AND u.occurredAt >= :start
          AND u.occurredAt < :end
    """)
    long sumUnitsByOrganizationAndPeriod(@Param("orgId") UUID organizationId,
                                         @Param("start") Instant start,
                                         @Param("end") Instant end);

    boolean existsByEventId(UUID eventId);
}
