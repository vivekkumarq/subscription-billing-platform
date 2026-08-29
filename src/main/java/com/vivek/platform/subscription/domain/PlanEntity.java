package com.vivek.platform.subscription.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "plans")
public class PlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 32)
    private PlanType type;

    /**
     * Recurring monthly price. Monetary values are {@link BigDecimal} on purpose - binary
     * floating point cannot represent decimal currency amounts exactly.
     */
    @Column(name = "monthly_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyPrice;

    @Column(name = "included_units", nullable = false)
    private Integer includedUnits; // e.g. API calls / seats

    /**
     * Price charged for every unit consumed beyond {@link #includedUnits}. When null the
     * platform-wide default from {@code billing.default-overage-rate} applies.
     */
    @Column(name = "overage_rate_per_unit", precision = 19, scale = 4)
    private BigDecimal overageRatePerUnit;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    public Long getId() {
        return id;
    }

    public PlanType getType() {
        return type;
    }

    public void setType(PlanType type) {
        this.type = type;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public void setMonthlyPrice(BigDecimal monthlyPrice) {
        this.monthlyPrice = monthlyPrice;
    }

    public Integer getIncludedUnits() {
        return includedUnits;
    }

    public void setIncludedUnits(Integer includedUnits) {
        this.includedUnits = includedUnits;
    }

    public BigDecimal getOverageRatePerUnit() {
        return overageRatePerUnit;
    }

    public void setOverageRatePerUnit(BigDecimal overageRatePerUnit) {
        this.overageRatePerUnit = overageRatePerUnit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
