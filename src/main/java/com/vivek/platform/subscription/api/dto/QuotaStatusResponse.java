package com.vivek.platform.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Usage against the plan allowance for a billing period")
public record QuotaStatusResponse(
        UUID organizationId,
        String planType,
        int periodYear,
        int periodMonth,
        long usedUnits,
        int includedUnits,
        long remainingUnits,
        @Schema(description = "Usage as a percentage of the allowance, may exceed 100")
        BigDecimal usagePercent,
        @Schema(description = "True once consumption passes the plan allowance") boolean overQuota,
        @Schema(description = "Estimated overage cost accrued so far this period") BigDecimal projectedOverageAmount,
        String currency,
        @Schema(description = "Thresholds already alerted on in this period") List<Integer> thresholdsCrossed) {
}
