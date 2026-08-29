package com.vivek.platform.subscription.api.dto;

import com.vivek.platform.subscription.domain.InvoiceEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Invoice as returned by the API. Every monetary field is a {@link BigDecimal} scaled to two
 * decimal places - the previous {@code Double} fields could not represent currency exactly and
 * produced totals such as {@code 499.00000000000006}.
 */
@Schema(description = "A generated invoice for one organization and billing period")
public record InvoiceResponse(
        String invoiceNumber,
        UUID organizationId,
        int periodYear,
        int periodMonth,
        Instant periodStart,
        @Schema(description = "Exclusive end of the billing period") Instant periodEnd,
        String planType,
        int includedUnits,
        long usedUnits,
        long overageUnits,
        BigDecimal overageRate,
        BigDecimal baseAmount,
        BigDecimal overageAmount,
        @Schema(description = "Net proration credits and charges for the period") BigDecimal adjustmentAmount,
        BigDecimal totalAmount,
        String currency,
        Instant issuedAt,
        List<InvoiceLineItemResponse> lineItems) {

    public static InvoiceResponse from(InvoiceEntity invoice) {
        return new InvoiceResponse(
                invoice.getInvoiceNumber(),
                invoice.getOrganization().getId(),
                invoice.getPeriodYear(),
                invoice.getPeriodMonth(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getPlanType().name(),
                invoice.getIncludedUnits(),
                invoice.getUsedUnits(),
                invoice.getOverageUnits(),
                invoice.getOverageRate(),
                invoice.getBaseAmount(),
                invoice.getOverageAmount(),
                invoice.getAdjustmentAmount(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getIssuedAt(),
                invoice.getLineItems().stream().map(InvoiceLineItemResponse::from).toList());
    }
}
