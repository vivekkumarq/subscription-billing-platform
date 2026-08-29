package com.vivek.platform.subscription.api.dto;

import com.vivek.platform.subscription.domain.InvoiceLineItemEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "One priced line on an invoice")
public record InvoiceLineItemResponse(
        String lineType,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount) {

    public static InvoiceLineItemResponse from(InvoiceLineItemEntity item) {
        return new InvoiceLineItemResponse(
                item.getLineType().name(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getAmount());
    }
}
