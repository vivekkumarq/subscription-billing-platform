package com.vivek.platform.subscription.repository;

import com.vivek.platform.subscription.domain.QuotaAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuotaAlertRepository extends JpaRepository<QuotaAlertEntity, UUID> {

    boolean existsByOrganizationIdAndPeriodYearAndPeriodMonthAndThresholdPercent(
            UUID organizationId, int periodYear, int periodMonth, int thresholdPercent);

    List<QuotaAlertEntity> findByOrganizationIdAndPeriodYearAndPeriodMonthOrderByThresholdPercentAsc(
            UUID organizationId, int periodYear, int periodMonth);
}
