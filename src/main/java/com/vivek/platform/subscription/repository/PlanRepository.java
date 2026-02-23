package com.vivek.platform.subscription.repository;

import com.vivek.platform.subscription.domain.PlanEntity;
import com.vivek.platform.subscription.domain.PlanType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanRepository extends JpaRepository<PlanEntity, Long> {
    Optional<PlanEntity> findByType(PlanType type);
}
