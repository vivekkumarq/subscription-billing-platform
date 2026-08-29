package com.vivek.platform.subscription.messaging;

public final class KafkaTopics {

    public static final String USAGE_RECORDED = "usage-recorded-topic";

    /** Where {@code DeadLetterPublishingRecoverer} parks usage events that never succeeded. */
    public static final String USAGE_RECORDED_DLT = "usage-recorded-topic.DLT";

    public static final String QUOTA_THRESHOLD = "quota-threshold-topic";

    public static final String CONSUMER_GROUP = "subscription-service";

    private KafkaTopics() {
    }
}
