package com.vivek.platform.subscription.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted to {@code quota-threshold-topic} the first time an organization crosses a configured
 * percentage of its plan allowance within a billing period.
 */
public class QuotaThresholdCrossedEvent {

    private UUID eventId;
    private UUID organizationId;
    private int thresholdPercent;
    private long usedUnits;
    private int includedUnits;
    private int periodYear;
    private int periodMonth;
    private Instant crossedAt;

    public QuotaThresholdCrossedEvent() {
        // required by the JSON deserializer
    }

    public QuotaThresholdCrossedEvent(UUID eventId, UUID organizationId, int thresholdPercent,
                                      long usedUnits, int includedUnits,
                                      int periodYear, int periodMonth, Instant crossedAt) {
        this.eventId = eventId;
        this.organizationId = organizationId;
        this.thresholdPercent = thresholdPercent;
        this.usedUnits = usedUnits;
        this.includedUnits = includedUnits;
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.crossedAt = crossedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
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

    public Instant getCrossedAt() {
        return crossedAt;
    }

    public void setCrossedAt(Instant crossedAt) {
        this.crossedAt = crossedAt;
    }
}
