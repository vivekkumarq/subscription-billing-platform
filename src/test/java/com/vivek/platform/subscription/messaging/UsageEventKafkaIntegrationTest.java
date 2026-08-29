package com.vivek.platform.subscription.messaging;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.PlanType;
import com.vivek.platform.subscription.domain.UsageEventEntity;
import com.vivek.platform.subscription.events.UsageRecordedEvent;
import com.vivek.platform.subscription.repository.*;
import com.vivek.platform.subscription.service.BillingPeriod;
import com.vivek.platform.subscription.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the real publish - consume - persist path against an in-process Kafka broker. No
 * Docker and no external broker are involved, so this runs anywhere the unit tests do.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        kraft = true,
        partitions = 1,
        topics = {KafkaTopics.USAGE_RECORDED, KafkaTopics.USAGE_RECORDED_DLT, KafkaTopics.QUOTA_THRESHOLD})
class UsageEventKafkaIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private UsageEventProducer producer;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UsageEventRepository usageEventRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private QuotaAlertRepository quotaAlertRepository;
    @Autowired
    private SubscriptionService subscriptionService;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        OrganizationEntity org = new OrganizationEntity();
        org.setName("Kafka Tenant " + UUID.randomUUID());
        org.setCreatedAt(Instant.now());
        orgId = organizationRepository.save(org).getId();
        subscriptionService.createSubscription(orgId, PlanType.FREE); // 100 included units
    }

    @Test
    @DisplayName("a published usage event is consumed and persisted against the tenant")
    void publishIsConsumedAndPersisted() {
        UUID eventId = UUID.randomUUID();
        producer.publish(new UsageRecordedEvent(eventId, orgId, 25, Instant.now()));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(usageEventRepository.existsByEventId(eventId)).isTrue());

        long total = usageEventRepository.sumUnitsByOrganizationAndPeriod(
                orgId, BillingPeriod.containing(Instant.now()).start(),
                BillingPeriod.containing(Instant.now()).end());
        assertThat(total).isEqualTo(25L);
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    /**
     * The reliability property that matters most: Kafka delivers at least once, so the same
     * event id arriving twice must not bill the tenant twice.
     */
    @Test
    @DisplayName("a redelivered event with the same id is not counted twice")
    void redeliveryIsIdempotent() {
        UUID eventId = UUID.randomUUID();
        UsageRecordedEvent event = new UsageRecordedEvent(eventId, orgId, 40, Instant.now());

        producer.publish(event);
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(usageEventRepository.existsByEventId(eventId)).isTrue());

        // Exactly the same event id, published again as a broker redelivery would be.
        producer.publish(event);
        producer.publish(event);

        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() -> {
            BillingPeriod period = BillingPeriod.containing(Instant.now());
            long total = usageEventRepository.sumUnitsByOrganizationAndPeriod(
                    orgId, period.start(), period.end());
            assertThat(total).isEqualTo(40L);
        });

        List<UsageEventEntity> rows = usageEventRepository.findAll().stream()
                .filter(row -> eventId.equals(row.getEventId()))
                .toList();
        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("crossing the plan allowance raises the 80% and 100% quota alerts exactly once")
    void quotaAlertsFireOnce() {
        // FREE includes 100 units; 90 crosses 80%, a further 30 crosses 100%.
        producer.publish(new UsageRecordedEvent(UUID.randomUUID(), orgId, 90, Instant.now()));
        BillingPeriod period = BillingPeriod.containing(Instant.now());

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(
                quotaAlertRepository.existsByOrganizationIdAndPeriodYearAndPeriodMonthAndThresholdPercent(
                        orgId, period.year(), period.month(), 80)).isTrue());

        producer.publish(new UsageRecordedEvent(UUID.randomUUID(), orgId, 30, Instant.now()));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(
                quotaAlertRepository.existsByOrganizationIdAndPeriodYearAndPeriodMonthAndThresholdPercent(
                        orgId, period.year(), period.month(), 100)).isTrue());

        assertThat(quotaAlertRepository
                .findByOrganizationIdAndPeriodYearAndPeriodMonthOrderByThresholdPercentAsc(
                        orgId, period.year(), period.month()))
                .hasSize(2);
    }
}
