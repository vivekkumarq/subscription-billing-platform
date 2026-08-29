package com.vivek.platform.subscription.api;

import com.vivek.platform.subscription.api.dto.CreateOrganizationRequest;
import com.vivek.platform.subscription.api.dto.OrganizationResponse;
import com.vivek.platform.subscription.api.dto.QuotaStatusResponse;
import com.vivek.platform.subscription.security.TenantAccessGuard;
import com.vivek.platform.subscription.service.BillingPeriod;
import com.vivek.platform.subscription.service.OrganizationService;
import com.vivek.platform.subscription.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/organizations")
@Tag(name = "Organizations", description = "Tenant provisioning and quota status")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final QuotaService quotaService;
    private final TenantAccessGuard tenantAccessGuard;

    public OrganizationController(OrganizationService organizationService,
                                  QuotaService quotaService,
                                  TenantAccessGuard tenantAccessGuard) {
        this.organizationService = organizationService;
        this.quotaService = quotaService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Provision a tenant organization",
            description = "Requires the PLATFORM_ADMIN realm role - creating tenants is a platform "
                    + "operation, not a tenant one.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Organization created"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "Caller is not a platform admin", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "409", description = "Name already taken", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public OrganizationResponse create(@RequestBody @Valid CreateOrganizationRequest request) {
        return OrganizationResponse.from(organizationService.createOrganization(request.getName()));
    }

    @GetMapping("/{orgId}")
    @Operation(summary = "Fetch one organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "Unknown organization", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public OrganizationResponse get(@PathVariable UUID orgId) {
        tenantAccessGuard.assertCanAccess(orgId);
        return OrganizationResponse.from(organizationService.getOrganization(orgId));
    }

    @GetMapping("/{orgId}/quota")
    @Operation(summary = "Usage against the plan allowance",
            description = "Defaults to the current UTC month when year and month are omitted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quota status"),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "409", description = "Organization has no active subscription", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public QuotaStatusResponse quota(@PathVariable UUID orgId,
                                     @Parameter(description = "Billing year, defaults to the current UTC year")
                                     @RequestParam(required = false) Integer year,
                                     @Parameter(description = "Billing month 1-12, defaults to the current UTC month")
                                     @RequestParam(required = false) Integer month) {
        tenantAccessGuard.assertCanAccess(orgId);
        BillingPeriod period = (year == null || month == null)
                ? BillingPeriod.containing(Instant.now())
                : BillingPeriod.of(year, month);
        return quotaService.status(orgId, period);
    }
}
