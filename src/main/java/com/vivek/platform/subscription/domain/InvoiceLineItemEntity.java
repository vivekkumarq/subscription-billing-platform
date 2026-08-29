package com.vivek.platform.subscription.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_items",
        indexes = @Index(name = "idx_invoice_line_items_invoice", columnList = "invoice_id"))
public class InvoiceLineItemEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private InvoiceEntity invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 32)
    private InvoiceLineType lineType;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected InvoiceLineItemEntity() {
        // required by JPA
    }

    public InvoiceLineItemEntity(InvoiceLineType lineType, String description,
                                 BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
        this.lineType = lineType;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public InvoiceEntity getInvoice() {
        return invoice;
    }

    void setInvoice(InvoiceEntity invoice) {
        this.invoice = invoice;
    }

    public InvoiceLineType getLineType() {
        return lineType;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
