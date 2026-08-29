package com.vivek.platform.subscription.api.dto;

import com.vivek.platform.subscription.domain.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Move an existing subscription to a different plan, prorated to the day")
public class ChangePlanRequest {

    @NotNull
    @Schema(description = "Plan to move to", requiredMode = Schema.RequiredMode.REQUIRED, example = "PRO")
    private PlanType planType;

    public PlanType getPlanType() {
        return planType;
    }

    public void setPlanType(PlanType planType) {
        this.planType = planType;
    }
}
