package com.vivek.platform.subscription.config;

import com.vivek.platform.subscription.events.UsageRecordedEvent;
import com.vivek.platform.subscription.exception.ResourceNotFoundException;
import com.vivek.platform.subscription.messaging.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

/**
 * Kafka reliability wiring: bounded retry with exponential backoff, a dead-letter topic for
 * records that can never succeed, and topic provisioning so a fresh broker works out of the box.
 *
 * <p>Without an explicit error handler, a listener that throws is retried forever by the default
 * container behaviour, and a single poison record blocks its partition indefinitely.</p>
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private static final long INITIAL_BACKOFF_MS = 500L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 8_000L;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    @Bean
    NewTopic usageRecordedTopic() {
        return TopicBuilder.name(KafkaTopics.USAGE_RECORDED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic usageRecordedDltTopic() {
        return TopicBuilder.name(KafkaTopics.USAGE_RECORDED_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic quotaThresholdTopic() {
        return TopicBuilder.name(KafkaTopics.QUOTA_THRESHOLD).partitions(3).replicas(1).build();
    }

    /**
     * Wraps the JSON deserializer in {@link ErrorHandlingDeserializer} so a malformed payload
     * surfaces as a failed record the error handler can dead-letter, instead of killing the
     * consumer thread before any listener code runs.
     */
    @Bean
    ConsumerFactory<String, UsageRecordedEvent> usageConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, UsageRecordedEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.vivek.platform.subscription.events");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, UsageRecordedEvent> usageKafkaListenerContainerFactory(
            ConsumerFactory<String, UsageRecordedEvent> usageConsumerFactory,
            DefaultErrorHandler usageErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, UsageRecordedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(usageConsumerFactory);
        factory.setCommonErrorHandler(usageErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler usageErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Route failures to <original-topic>.DLT, keeping the original partition.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_BACKOFF_MS, BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(MAX_BACKOFF_MS);
        backOff.setMaxAttempts(MAX_RETRY_ATTEMPTS);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Retrying these can never help: the payload itself is wrong. Dead-letter immediately.
        handler.addNotRetryableExceptions(
                ResourceNotFoundException.class,
                IllegalArgumentException.class,
                org.springframework.kafka.support.serializer.DeserializationException.class,
                org.springframework.messaging.converter.MessageConversionException.class);

        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retrying usage event from {}-{}@{} (attempt {}): {}",
                        record.topic(), record.partition(), record.offset(),
                        deliveryAttempt, ex.getMessage()));
        return handler;
    }
}
