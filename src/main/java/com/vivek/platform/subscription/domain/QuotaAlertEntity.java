package com.vivek.platform.subscription.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that an organization crossed a quota threshold in a period, so each threshold
 * alerts exactly once per period instead of on every subsequent usage event.
 */
@Entity
@Table(name = "quota_alerts",
        uniqueConstraints = @UniqueConstraint(name = "uk_quota_alerts_org_period_threshold",
                columnNames = {"organization_id", "period_year", "period_month", "threshold_percent"}))
public class QuotaAlertEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "threshold_percent", nullable = false)
    private int thresholdPercent;

    @Column(name = "used_units", nullable = false)
    private long usedUnits;

    @Column(name = "included_units", nullable = false)
    private int includedUnits;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    public UUID getId() {
        return id;
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

    public int getThresholdPercent() {
        return thresholdPercent;
    }

    public void setThresholdPercent(int thresholdPercent) {
        this.thresholdPercent = thresholdPercent;
    }

    public long getUsedUnits() {
        return usedUnits;
    }

    public void setUsedUnits(long usedUnits) {
        this.usedUnits = usedUnits;
    }

    public int getIncludedUnits() {
        return includedUnits;
    }

    public void setIncludedUnits(int includedUnits) {
        this.includedUnits = includedUnits;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }
}
