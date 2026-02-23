package com.vivek.platform.subscription.messaging;

import com.vivek.platform.subscription.events.UsageRecordedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class UsageEventProducer {

    private final KafkaTemplate<String, UsageRecordedEvent> kafkaTemplate;

    public UsageEventProducer(KafkaTemplate<String, UsageRecordedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UsageRecordedEvent event) {
        kafkaTemplate.send("usage-recorded-topic", event.getOrganizationId().toString(), event);
    }
}