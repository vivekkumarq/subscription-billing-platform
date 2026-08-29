package com.vivek.platform.subscription.repository;

import com.vivek.platform.subscription.domain.ProrationAdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProrationAdjustmentRepository extends JpaRepository<ProrationAdjustmentEntity, UUID> {

    List<ProrationAdjustmentEntity> findByOrganizationIdAndPeriodYearAndPeriodMonthOrderByCreatedAtAsc(
            UUID organizationId, int periodYear, int periodMonth);
}
