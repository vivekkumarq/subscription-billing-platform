package com.vivek.platform.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Acknowledgement that a usage event was accepted for asynchronous processing")
public record UsageAcceptedResponse(
        @Schema(description = "Idempotency key for this usage event") UUID eventId,
        UUID organizationId,
        Integer unitsConsumed,
        Instant occurredAt,
        @Schema(description = "Kafka topic the event was published to") String topic) {
}
