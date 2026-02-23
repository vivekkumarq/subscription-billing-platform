package com.vivek.platform.subscription.repository;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.UsageEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface UsageEventRepository extends JpaRepository<UsageEventEntity, UUID> {

    @Query("""
        SELECT COALESCE(SUM(u.unitsConsumed), 0)
        FROM UsageEventEntity u
        WHERE u.organization = :org
          AND u.timestamp BETWEEN :start AND :end
    """)
    long sumUnitsByOrganizationAndPeriod(@Param("org") OrganizationEntity organization,
                                         @Param("start") Instant start,
                                         @Param("end") Instant end);
}