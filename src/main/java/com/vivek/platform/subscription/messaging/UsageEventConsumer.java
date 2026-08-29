package com.vivek.platform.subscription.messaging;

import com.vivek.platform.subscription.domain.UsageEventEntity;
import com.vivek.platform.subscription.events.UsageRecordedEvent;
import com.vivek.platform.subscription.service.QuotaService;
import com.vivek.platform.subscription.service.UsageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Consumes usage events and applies them to the tenant ledger.
 *
 * <p>Three reliability properties this listener relies on:</p>
 * <ul>
 *   <li>Idempotency - {@link UsageService} records the event id, so a redelivery is dropped
 *       rather than double-counted.</li>
 *   <li>Retry with backoff - configured centrally by the container's error handler.</li>
 *   <li>Dead-lettering - permanently failing records are routed to
 *       {@code usage-recorded-topic.DLT} instead of blocking the partition forever.</li>
 * </ul>
 */
@Component
public class UsageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageEventConsumer.class);

    private final UsageService usageService;
    private final QuotaService quotaService;
    private final Counter consumedCounter;
    private final Counter duplicateCounter;

    public UsageEventConsumer(UsageService usageService,
                              QuotaService quotaService,
                              MeterRegistry meterRegistry) {
        this.usageService = usageService;
        this.quotaService = quotaService;
        this.consumedCounter = Counter.builder("usage.events.consumed")
                .description("Usage events consumed and persisted")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("usage.events.duplicate")
                .description("Usage events skipped because they had already been applied")
                .register(meterRegistry);
    }

    @KafkaListener(topics = KafkaTopics.USAGE_RECORDED,
            groupId = KafkaTopics.CONSUMER_GROUP,
            containerFactory = "usageKafkaListenerContainerFactory")
    public void handleUsageRecorded(ConsumerRecord<String, UsageRecordedEvent> record) {
        UsageRecordedEvent event = record.value();
        if (event == null) {
            log.warn("Discarding tombstone on {} partition={} offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        UsageEventEntity persisted = usageService.applyUsage(
                event.getEventId(),
                event.getOrganizationId(),
                event.getUnits(),
                event.getOccurredAt(),
                KafkaTopics.CONSUMER_GROUP);

        if (persisted == null) {
            duplicateCounter.increment();
            return;
        }

        consumedCounter.increment();
        log.info("Usage applied: org={} units={} eventId={} offset={}",
                event.getOrganizationId(), event.getUnits(), event.getEventId(), record.offset());

        Instant at = persisted.getOccurredAt() != null ? persisted.getOccurredAt() : Instant.now();
        List<Integer> fired = quotaService.evaluateThresholds(event.getOrganizationId(), at);
        if (!fired.isEmpty()) {
            log.info("Quota thresholds crossed for org={}: {}", event.getOrganizationId(), fired);
        }
    }

    /** Terminal handler for records that exhausted their retries. */
    @KafkaListener(topics = KafkaTopics.USAGE_RECORDED_DLT,
            groupId = KafkaTopics.CONSUMER_GROUP + "-dlt",
            containerFactory = "usageKafkaListenerContainerFactory")
    public void handleDeadLetter(ConsumerRecord<String, UsageRecordedEvent> record) {
        log.error("Usage event dead-lettered: key={} partition={} offset={} value={}",
                record.key(), record.partition(), record.offset(), record.value());
    }
}
