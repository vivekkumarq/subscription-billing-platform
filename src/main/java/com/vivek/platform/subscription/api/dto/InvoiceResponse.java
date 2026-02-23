package com.vivek.platform.subscription.api.dto;

import java.util.UUID;

public class InvoiceResponse {

    private UUID organizationId;
    private String planType;
    private Double baseAmount;
    private Long usedUnits;
    private Integer includedUnits;
    private Double overageAmount;
    private Double totalAmount;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public Double getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(Double baseAmount) {
        this.baseAmount = baseAmount;
    }

    public Long getUsedUnits() {
        return usedUnits;
    }

    public void setUsedUnits(Long usedUnits) {
        this.usedUnits = usedUnits;
    }

    public Integer getIncludedUnits() {
        return includedUnits;
    }

    public void setIncludedUnits(Integer includedUnits) {
        this.includedUnits = includedUnits;
    }

    public Double getOverageAmount() {
        return overageAmount;
    }

    public void setOverageAmount(Double overageAmount) {
        this.overageAmount = overageAmount;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }
}