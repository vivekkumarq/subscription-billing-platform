package com.vivek.platform.subscription.api;

import com.vivek.platform.subscription.api.dto.*;
import com.vivek.platform.subscription.security.TenantAccessGuard;
import com.vivek.platform.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
@Tag(name = "Subscriptions", description = "Subscription lifecycle: subscribe, change plan, cancel")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final TenantAccessGuard tenantAccessGuard;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  TenantAccessGuard tenantAccessGuard) {
        this.subscriptionService = subscriptionService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subscribe an organization to a plan")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subscription created"),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @Content),
            @ApiResponse(responseCode = "404", description = "Unknown organization or plan", content = @Content),
            @ApiResponse(responseCode = "409", description = "Organization already has a subscription", content = @Content)
    })
    public SubscriptionResponse subscribe(@RequestBody @Valid CreateSubscriptionRequest request) {
        tenantAccessGuard.assertCanAccess(request.getOrganizationId());
        return SubscriptionResponse.from(
                subscriptionService.createSubscription(request.getOrganizationId(), request.getPlanType()));
    }

    @GetMapping("/{orgId}")
    @Operation(summary = "Current subscription for an organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current subscription"),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @Content),
            @ApiResponse(responseCode = "409", description = "No active subscription", content = @Content)
    })
    public SubscriptionResponse current(@PathVariable UUID orgId) {
        tenantAccessGuard.assertCanAccess(orgId);
        return SubscriptionResponse.from(subscriptionService.getCurrentSubscription(orgId));
    }

    @GetMapping("/{orgId}/history")
    @Operation(summary = "Every subscription an organization has held, newest first")
    public List<SubscriptionResponse> history(@PathVariable UUID orgId) {
        tenantAccessGuard.assertCanAccess(orgId);
        return subscriptionService.listSubscriptions(orgId).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    @PostMapping("/{orgId}/change-plan")
    @Operation(summary = "Upgrade or downgrade, prorated to the day",
            description = "Credits the unused remainder of the current plan and charges the "
                    + "matching remainder of the new one. Both land on the current period's invoice.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan changed"),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @Content),
            @ApiResponse(responseCode = "404", description = "Unknown organization or plan", content = @Content),
            @ApiResponse(responseCode = "409", description = "Already on that plan, or subscription is cancelled", content = @Content)
    })
    public PlanChangeResponse changePlan(@PathVariable UUID orgId,
                                         @RequestBody @Valid ChangePlanRequest request) {
        tenantAccessGuard.assertCanAccess(orgId);
        SubscriptionService.PlanChange change =
                subscriptionService.changePlan(orgId, request.getPlanType());
        return new PlanChangeResponse(
                SubscriptionResponse.from(change.subscription()),
                change.proration().credit(),
                change.proration().charge(),
                change.proration().netAmount(),
                change.proration().remainingDays(),
                change.proration().daysInPeriod(),
                change.period().label());
    }

    @PostMapping("/{orgId}/cancel")
    @Operation(summary = "Cancel a subscription",
            description = "Without an effective date, service runs to the end of the current "
                    + "billing period so the tenant keeps what they paid for.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancellation scheduled"),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @Content),
            @ApiResponse(responseCode = "409", description = "No active subscription", content = @Content)
    })
    public SubscriptionResponse cancel(@PathVariable UUID orgId,
                                       @RequestBody(required = false) CancelSubscriptionRequest request) {
        tenantAccessGuard.assertCanAccess(orgId);
        return SubscriptionResponse.from(subscriptionService.cancel(
                orgId, request == null ? null : request.getEffectiveAt()));
    }
}
