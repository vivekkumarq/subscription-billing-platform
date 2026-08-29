package com.vivek.platform.subscription.api;

import com.vivek.platform.subscription.api.dto.UsageAcceptedResponse;
import com.vivek.platform.subscription.api.dto.UsageEventRequest;
import com.vivek.platform.subscription.events.UsageRecordedEvent;
import com.vivek.platform.subscription.messaging.KafkaTopics;
import com.vivek.platform.subscription.messaging.UsageEventProducer;
import com.vivek.platform.subscription.security.TenantAccessGuard;
import com.vivek.platform.subscription.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/usage-events")
@Tag(name = "Usage", description = "Asynchronous usage metering")
public class UsageController {

    private final UsageEventProducer producer;
    private final OrganizationService organizationService;
    private final TenantAccessGuard tenantAccessGuard;

    public UsageController(UsageEventProducer producer,
                           OrganizationService organizationService,
                           TenantAccessGuard tenantAccessGuard) {
        this.producer = producer;
        this.organizationService = organizationService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Record usage for a tenant",
            description = "Publishes to the usage topic and returns immediately. The event id in "
                    + "the response is the idempotency key the consumer dedupes on.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Accepted for asynchronous processing"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @Content),
            @ApiResponse(responseCode = "404", description = "Unknown organization", content = @Content)
    })
    public UsageAcceptedResponse record(@RequestBody @Valid UsageEventRequest request) {
        tenantAccessGuard.assertCanAccess(request.getOrganizationId());
        // Reject an unknown tenant here rather than letting the message become a poison record
        // that only fails once it reaches the consumer.
        organizationService.getOrganization(request.getOrganizationId());

        UsageRecordedEvent event = new UsageRecordedEvent(
                UUID.randomUUID(),
                request.getOrganizationId(),
                request.getUnitsConsumed(),
                Instant.now());
        producer.publish(event);

        return new UsageAcceptedResponse(
                event.getEventId(),
                event.getOrganizationId(),
                event.getUnits(),
                event.getOccurredAt(),
                KafkaTopics.USAGE_RECORDED);
    }
}
