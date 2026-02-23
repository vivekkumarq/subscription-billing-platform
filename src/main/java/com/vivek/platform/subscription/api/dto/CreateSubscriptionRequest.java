package com.vivek.platform.subscription.api.dto;

import com.vivek.platform.subscription.domain.PlanType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class CreateSubscriptionRequest {

    @NotNull
    private UUID organizationId;

    @NotNull
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