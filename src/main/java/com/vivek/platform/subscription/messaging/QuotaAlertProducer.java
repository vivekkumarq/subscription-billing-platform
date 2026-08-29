package com.vivek.platform.subscription.messaging;

import com.vivek.platform.subscription.events.QuotaThresholdCrossedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class QuotaAlertProducer {

    private static final Logger log = LoggerFactory.getLogger(QuotaAlertProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public QuotaAlertProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(QuotaThresholdCrossedEvent event) {
        kafkaTemplate.send(KafkaTopics.QUOTA_THRESHOLD, event.getOrganizationId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Alerting is best-effort: never fail the usage path because a
                        // notification could not be delivered.
                        log.error("Failed to publish quota alert for org={} threshold={}",
                                event.getOrganizationId(), event.getThresholdPercent(), ex);
                    } else {
                        log.debug("Published quota alert {}% for org={}",
                                event.getThresholdPercent(), event.getOrganizationId());
                    }
                });
    }
}
