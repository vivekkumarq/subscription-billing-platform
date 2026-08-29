package com.vivek.platform.subscription.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbox table backing idempotent Kafka consumption. Kafka delivery is at-least-once, so the
 * consumer records every event id it has applied and skips repeats.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "consumer_group", nullable = false, length = 128)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
        // required by JPA
    }

    public ProcessedEventEntity(UUID eventId, String consumerGroup, Instant processedAt) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
        this.processedAt = processedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
