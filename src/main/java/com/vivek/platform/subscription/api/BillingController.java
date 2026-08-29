package com.vivek.platform.subscription.api;

import com.vivek.platform.subscription.api.dto.InvoiceResponse;
import com.vivek.platform.subscription.security.TenantAccessGuard;
import com.vivek.platform.subscription.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/invoices")
@Tag(name = "Billing", description = "Invoice generation and retrieval")
public class BillingController {

    private final BillingService billingService;
    private final TenantAccessGuard tenantAccessGuard;

    public BillingController(BillingService billingService, TenantAccessGuard tenantAccessGuard) {
        this.billingService = billingService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    /**
     * Generation is a POST because it creates a stored document. The old {@code GET /invoices/{id}}
     * both computed and returned an invoice, which made a read verb produce state.
     */
    @PostMapping("/{orgId}/generate")
    @Operation(summary = "Generate the invoice for a billing period",
            description = "Idempotent: calling it again for the same organization and period "
                    + "returns the invoice already on file rather than issuing a second one.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice issued or already on file"),
            @ApiResponse(responseCode = "400", description = "Invalid period", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @Content),
            @ApiResponse(responseCode = "404", description = "Unknown organization", content = @Content),
            @ApiResponse(responseCode = "409", description = "No active subscription to bill", content = @Content)
    })
    public InvoiceResponse generate(@PathVariable UUID orgId,
                                    @Parameter(description = "Billing year", example = "2026")
                                    @RequestParam int year,
                                    @Parameter(description = "Billing month, 1-12", example = "2")
                                    @RequestParam int month) {
        tenantAccessGuard.assertCanAccess(orgId);
        return InvoiceResponse.from(billingService.generateInvoice(orgId, year, month));
    }

    @GetMapping("/{orgId}")
    @Operation(summary = "List issued invoices, newest period first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoices on file"),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @Content),
            @ApiResponse(responseCode = "404", description = "Unknown organization", content = @Content)
    })
    public List<InvoiceResponse> list(@PathVariable UUID orgId) {
        tenantAccessGuard.assertCanAccess(orgId);
        return billingService.listInvoices(orgId).stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping("/{orgId}/{invoiceNumber}")
    @Operation(summary = "Retrieve one issued invoice by its number")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice"),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to this organization", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such invoice for this organization", content = @Content)
    })
    public InvoiceResponse get(@PathVariable UUID orgId, @PathVariable String invoiceNumber) {
        tenantAccessGuard.assertCanAccess(orgId);
        return InvoiceResponse.from(billingService.getInvoice(orgId, invoiceNumber));
    }
}
