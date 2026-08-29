package com.vivek.platform.subscription.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A persisted invoice. Invoices used to be computed on the fly and thrown away, which meant a
 * tenant could be quoted two different totals for the same closed period. Generation is now
 * idempotent per (organization, year, month) and the stored document is the source of truth.
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invoices_number", columnNames = "invoice_number"),
                @UniqueConstraint(name = "uk_invoices_org_period",
                        columnNames = {"organization_id", "period_year", "period_month"})
        })
public class InvoiceEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 64)
    private String invoiceNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    /** Exclusive upper bound of the billing period. */
    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 32)
    private PlanType planType;

    @Column(name = "included_units", nullable = false)
    private int includedUnits;

    @Column(name = "used_units", nullable = false)
    private long usedUnits;

    @Column(name = "overage_units", nullable = false)
    private long overageUnits;

    @Column(name = "overage_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal overageRate;

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "overage_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal overageAmount;

    /** Net of all proration credits and charges applied to this period. */
    @Column(name = "adjustment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal adjustmentAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLineItemEntity> lineItems = new ArrayList<>();

    public void addLineItem(InvoiceLineItemEntity item) {
        item.setInvoice(this);
        this.lineItems.add(item);
    }

    public UUID getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public void setOrganization(OrganizationEntity organization) {
        this.organization = organization;
    }

    public int getPeriodYear() {
        return periodYear;
    }

    public void setPeriodYear(int periodYear) {
        this.periodYear = periodYear;
    }

    public int getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(int periodMonth) {
        this.periodMonth = periodMonth;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public void setPlanType(PlanType planType) {
        this.planType = planType;
    }

    public int getIncludedUnits() {
        return includedUnits;
    }

    public void setIncludedUnits(int includedUnits) {
        this.includedUnits = includedUnits;
    }

    public long getUsedUnits() {
        return usedUnits;
    }

    public void setUsedUnits(long usedUnits) {
        this.usedUnits = usedUnits;
    }

    public long getOverageUnits() {
        return overageUnits;
    }

    public void setOverageUnits(long overageUnits) {
        this.overageUnits = overageUnits;
    }

    public BigDecimal getOverageRate() {
        return overageRate;
    }

    public void setOverageRate(BigDecimal overageRate) {
        this.overageRate = overageRate;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public BigDecimal getOverageAmount() {
        return overageAmount;
    }

    public void setOverageAmount(BigDecimal overageAmount) {
        this.overageAmount = overageAmount;
    }

    public BigDecimal getAdjustmentAmount() {
        return adjustmentAmount;
    }

    public void setAdjustmentAmount(BigDecimal adjustmentAmount) {
        this.adjustmentAmount = adjustmentAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public List<InvoiceLineItemEntity> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }
}
