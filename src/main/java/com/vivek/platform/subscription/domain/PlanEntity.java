package com.vivek.platform.subscription.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "plans")
public class PlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private PlanType type;

    @Column(nullable = false)
    private Double monthlyPrice;

    @Column(nullable = false)
    private Integer includedUnits; // e.g. API calls / seats

    public Long getId() {
        return id;
    }

    public PlanType getType() {
        return type;
    }

    public void setType(PlanType type) {
        this.type = type;
    }

    public Double getMonthlyPrice() {
        return monthlyPrice;
    }

    public void setMonthlyPrice(Double monthlyPrice) {
        this.monthlyPrice = monthlyPrice;
    }

    public Integer getIncludedUnits() {
        return includedUnits;
    }

    public void setIncludedUnits(Integer includedUnits) {
        this.includedUnits = includedUnits;
    }
}
