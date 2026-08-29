package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.domain.*;
import com.vivek.platform.subscription.exception.InvalidSubscriptionStateException;
import com.vivek.platform.subscription.messaging.KafkaTopics;
import com.vivek.platform.subscription.repository.InvoiceRepository;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end domain flow against a real (in-memory) database and the Flyway-managed schema:
 * provision a tenant, subscribe, meter usage, invoice, change plan.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        kraft = true,
        partitions = 1,
        topics = {KafkaTopics.USAGE_RECORDED, KafkaTopics.USAGE_RECORDED_DLT, KafkaTopics.QUOTA_THRESHOLD})
class BillingLifecycleIntegrationTest {

    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private SubscriptionService subscriptionService;
    @Autowired
    private UsageService usageService;
    @Autowired
    private BillingService billingService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = organizationService.createOrganization("Lifecycle Tenant " + UUID.randomUUID()).getId();
    }

    @Test
    @DisplayName("subscribe, meter usage over the allowance, then invoice the period")
    void endToEndInvoice() {
        subscriptionService.createSubscription(orgId, PlanType.BASIC);

        BillingPeriod period = BillingPeriod.of(2026, 2);
        usageService.applyUsage(UUID.randomUUID(), orgId, 10_000,
                Instant.parse("2026-02-10T12:00:00Z"), "test");
        usageService.applyUsage(UUID.randomUUID(), orgId, 2_500,
                Instant.parse("2026-02-20T12:00:00Z"), "test");

        InvoiceEntity invoice = billingService.generateInvoice(orgId, period.year(), period.month());

        assertThat(invoice.getUsedUnits()).isEqualTo(12_500L);
        assertThat(invoice.getOverageUnits()).isEqualTo(2_500L);
        assertThat(invoice.getBaseAmount()).isEqualByComparingTo("499.00");
        assertThat(invoice.getOverageAmount()).isEqualByComparingTo("250.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("749.00");
        assertThat(invoice.getLineItems()).hasSize(2);
        assertThat(invoiceRepository.existsByInvoiceNumber(invoice.getInvoiceNumber())).isTrue();
    }

    /**
     * The final second of the period used to be excluded, because the query bounded the range
     * with {@code BETWEEN ... AND 23:59:59} instead of a half-open interval.
     */
    @Test
    @DisplayName("usage in the last second of the month is still billed")
    void usageAtPeriodBoundaryIsCounted() {
        subscriptionService.createSubscription(orgId, PlanType.BASIC);

        usageService.applyUsage(UUID.randomUUID(), orgId, 10_500,
                Instant.parse("2026-02-28T23:59:59.750Z"), "test");

        InvoiceEntity invoice = billingService.generateInvoice(orgId, 2026, 2);

        assertThat(invoice.getUsedUnits()).isEqualTo(10_500L);
        assertThat(invoice.getOverageUnits()).isEqualTo(500L);
    }

    @Test
    @DisplayName("usage in the following month does not leak into this month's invoice")
    void usageOutsidePeriodIsExcluded() {
        subscriptionService.createSubscription(orgId, PlanType.BASIC);

        usageService.applyUsage(UUID.randomUUID(), orgId, 500,
                Instant.parse("2026-02-15T00:00:00Z"), "test");
        usageService.applyUsage(UUID.randomUUID(), orgId, 9_000,
                Instant.parse("2026-03-01T00:00:00Z"), "test");

        InvoiceEntity invoice = billingService.generateInvoice(orgId, 2026, 2);

        assertThat(invoice.getUsedUnits()).isEqualTo(500L);
    }

    @Test
    @DisplayName("regenerating a period returns the invoice already on file")
    void invoiceGenerationIsIdempotent() {
        subscriptionService.createSubscription(orgId, PlanType.BASIC);

        InvoiceEntity first = billingService.generateInvoice(orgId, 2026, 2);
        InvoiceEntity second = billingService.generateInvoice(orgId, 2026, 2);

        assertThat(second.getInvoiceNumber()).isEqualTo(first.getInvoiceNumber());
        assertThat(billingService.listInvoices(orgId)).hasSize(1);
    }

    @Test
    @DisplayName("a mid-cycle upgrade lands on the invoice as credit and charge line items")
    void planChangeProratesOntoTheInvoice() {
        subscriptionService.createSubscription(orgId, PlanType.BASIC);

        SubscriptionService.PlanChange change = subscriptionService.changePlan(orgId, PlanType.PRO);
        BillingPeriod period = change.period();

        assertThat(change.subscription().getPlan().getType()).isEqualTo(PlanType.PRO);
        assertThat(change.subscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscriptionService.listSubscriptions(orgId)).hasSize(2);

        InvoiceEntity invoice = billingService.generateInvoice(orgId, period.year(), period.month());

        assertThat(invoice.getPlanType()).isEqualTo(PlanType.PRO);
        assertThat(invoice.getAdjustmentAmount())
                .isEqualByComparingTo(change.proration().netAmount());
        assertThat(invoice.getLineItems())
                .extracting(InvoiceLineItemEntity::getLineType)
                .contains(InvoiceLineType.PRORATION_CREDIT, InvoiceLineType.PRORATION_CHARGE);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(
                invoice.getBaseAmount()
                        .add(invoice.getOverageAmount())
                        .add(invoice.getAdjustmentAmount()));
    }

    @Test
    @DisplayName("cancelling defaults to the end of the current period and is terminal")
    void cancellationUsesEffectiveDate() {
        subscriptionService.createSubscription(orgId, PlanType.BASIC);

        SubscriptionEntity cancelled = subscriptionService.cancel(orgId, null);

        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getEndDate())
                .isEqualTo(BillingPeriod.containing(Instant.now()).end());
        assertThat(cancelled.isBillableAt(Instant.now())).isTrue();
        assertThat(cancelled.isBillableAt(cancelled.getEndDate().plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("double-subscribing is rejected rather than silently replacing the plan")
    void doubleSubscribeIsRejected() {
        subscriptionService.createSubscription(orgId, PlanType.BASIC);

        assertThatThrownBy(() -> subscriptionService.createSubscription(orgId, PlanType.PRO))
                .isInstanceOf(InvalidSubscriptionStateException.class);
    }

    @Test
    @DisplayName("a duplicate usage event id is applied once, however many times it arrives")
    void duplicateUsageEventIsIgnored() {
        subscriptionService.createSubscription(orgId, PlanType.BASIC);
        UUID eventId = UUID.randomUUID();
        Instant at = Instant.parse("2026-02-10T12:00:00Z");

        assertThat(usageService.applyUsage(eventId, orgId, 100, at, "test")).isNotNull();
        assertThat(usageService.applyUsage(eventId, orgId, 100, at, "test")).isNull();

        assertThat(usageService.usageForPeriod(orgId, BillingPeriod.of(2026, 2))).isEqualTo(100L);
        assertThat(organizationRepository.existsById(orgId)).isTrue();
    }
}
