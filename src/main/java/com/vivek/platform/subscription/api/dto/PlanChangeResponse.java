package com.vivek.platform.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Result of a mid-cycle plan change, including the proration applied")
public record PlanChangeResponse(
        SubscriptionResponse subscription,
        @Schema(description = "Refund for the unused remainder of the previous plan") BigDecimal prorationCredit,
        @Schema(description = "Charge for the remainder of the cycle on the new plan") BigDecimal prorationCharge,
        @Schema(description = "charge minus credit; positive means the tenant owes more") BigDecimal netAdjustment,
        int remainingDaysInPeriod,
        int daysInPeriod,
        @Schema(description = "Period the adjustment lands on, as YYYY-MM") String appliedToPeriod) {
}
