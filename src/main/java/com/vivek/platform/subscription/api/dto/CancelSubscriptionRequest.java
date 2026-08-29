package com.vivek.platform.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Cancel a subscription, optionally at a future effective date")
public class CancelSubscriptionRequest {

    @Schema(description = "When service should stop. Defaults to the end of the current billing "
            + "period, so the tenant keeps what they already paid for.",
            example = "2026-03-01T00:00:00Z")
    private Instant effectiveAt;

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
    }
}
