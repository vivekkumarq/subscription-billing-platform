package com.vivek.platform.subscription.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_usage_events_event_id", columnNames = "event_id"),
        indexes = @Index(name = "idx_usage_events_org_time", columnList = "organization_id, occurred_at"))
public class UsageEventEntity {

    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Producer-assigned identifier carried on the Kafka message. Unique so a redelivered
     * message can never be counted twice.
     */
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "units_consumed", nullable = false)
    private Integer unitsConsumed;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public void setOrganization(OrganizationEntity organization) {
        this.organization = organization;
    }

    public Integer getUnitsConsumed() {
        return unitsConsumed;
    }

    public void setUnitsConsumed(Integer unitsConsumed) {
        this.unitsConsumed = unitsConsumed;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
