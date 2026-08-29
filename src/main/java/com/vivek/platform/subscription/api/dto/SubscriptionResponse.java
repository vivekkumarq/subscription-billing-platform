package com.vivek.platform.subscription.api.dto;

import com.vivek.platform.subscription.domain.SubscriptionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A tenant subscription and the plan it resolves to")
public record SubscriptionResponse(
        UUID id,
        UUID organizationId,
        String planType,
        BigDecimal monthlyPrice,
        int includedUnits,
        String currency,
        @Schema(description = "ACTIVE, CANCELLED or PAST_DUE") String status,
        Instant startDate,
        @Schema(description = "Effective end of service, null while open-ended") Instant endDate,
        Instant cancelledAt) {

    public static SubscriptionResponse from(SubscriptionEntity entity) {
        return new SubscriptionResponse(
                entity.getId(),
                entity.getOrganization().getId(),
                entity.getPlan().getType().name(),
                entity.getPlan().getMonthlyPrice(),
                entity.getPlan().getIncludedUnits(),
                entity.getPlan().getCurrency(),
                entity.getStatus().name(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getCancelledAt());
    }
}
