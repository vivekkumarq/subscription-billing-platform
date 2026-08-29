package com.vivek.platform.subscription.api.dto;

import com.vivek.platform.subscription.domain.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Subscribe an organization to a plan")
public class CreateSubscriptionRequest {

    @NotNull
    @Schema(description = "Organization to subscribe", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID organizationId;

    @NotNull
    @Schema(description = "Plan to subscribe to", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "BASIC")
    private PlanType planType;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public void setPlanType(PlanType planType) {
        this.planType = planType;
    }
}
