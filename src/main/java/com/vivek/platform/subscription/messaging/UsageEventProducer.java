package com.vivek.platform.subscription.messaging;

import com.vivek.platform.subscription.events.UsageRecordedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class UsageEventProducer {

    private static final Logger log = LoggerFactory.getLogger(UsageEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter publishedCounter;
    private final Counter publishFailedCounter;

    public UsageEventProducer(KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishedCounter = Counter.builder("usage.events.published")
                .description("Usage events published to Kafka")
                .register(meterRegistry);
        this.publishFailedCounter = Counter.builder("usage.events.publish.failed")
                .description("Usage events that could not be published")
                .register(meterRegistry);
    }

    /**
     * Publishes keyed by organization id, so all usage for one tenant lands on one partition and
     * is therefore consumed in order.
     */
    public CompletableFuture<Void> publish(UsageRecordedEvent event) {
        String key = event.getOrganizationId().toString();
        return kafkaTemplate.send(KafkaTopics.USAGE_RECORDED, key, event)
                .thenAccept(result -> {
                    publishedCounter.increment();
                    log.debug("Published usage event {} to {}", event.getEventId(), KafkaTopics.USAGE_RECORDED);
                })
                .exceptionally(ex -> {
                    publishFailedCounter.increment();
                    log.error("Failed to publish usage event {}", event.getEventId(), ex);
                    return null;
                });
    }
}
