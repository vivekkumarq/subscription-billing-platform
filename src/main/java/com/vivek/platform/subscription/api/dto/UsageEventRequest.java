package com.vivek.platform.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Usage reported by a tenant for metering")
public class UsageEventRequest {

    @NotNull
    @Schema(description = "Organization consuming the units", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID organizationId;

    /**
     * {@code @NotNull} matters here: the field was only annotated {@code @Min(1)}, which passes
     * for null, so a body without the field reached the Kafka producer and blew up downstream.
     */
    @NotNull
    @Min(1)
    @Schema(description = "Units consumed, must be at least 1", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "50")
    private Integer unitsConsumed;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public Integer getUnitsConsumed() {
        return unitsConsumed;
    }

    public void setUnitsConsumed(Integer unitsConsumed) {
        this.unitsConsumed = unitsConsumed;
    }
}
