package com.vivek.platform.subscription.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Usage reported by a tenant, published to {@code usage-recorded-topic}.
 *
 * <p>{@code eventId} is assigned by the producer and is what makes consumption idempotent:
 * Kafka delivers at least once, and without a stable id a redelivered message inflated the
 * tenant's usage total (and therefore their invoice).</p>
 */
public class UsageRecordedEvent {

    private UUID eventId;
    private UUID organizationId;
    private Integer units;
    private Instant occurredAt;

    public UsageRecordedEvent() {
        // required by the JSON deserializer
    }

    public UsageRecordedEvent(UUID eventId, UUID organizationId, Integer units, Instant occurredAt) {
        this.eventId = eventId;
        this.organizationId = organizationId;
        this.units = units;
        this.occurredAt = occurredAt;
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

    public Integer getUnits() {
        return units;
    }

    public void setUnits(Integer units) {
        this.units = units;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    @Override
    public String toString() {
        return "UsageRecordedEvent{eventId=" + eventId
                + ", organizationId=" + organizationId
                + ", units=" + units
                + ", occurredAt=" + occurredAt + '}';
    }
}
