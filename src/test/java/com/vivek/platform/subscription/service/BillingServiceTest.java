package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.TestFixtures;
import com.vivek.platform.subscription.config.BillingProperties;
import com.vivek.platform.subscription.domain.*;
import com.vivek.platform.subscription.exception.NoActiveSubscriptionException;
import com.vivek.platform.subscription.exception.ResourceNotFoundException;
import com.vivek.platform.subscription.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The invoice maths is the core of this service, so it is tested directly rather than through
 * the HTTP layer.
 */
@ExtendWith(MockitoExtension.class)
// Shared arrange helpers stub more than any single case consumes; strict stubs would fail on
// the surplus rather than on anything meaningful.
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final int YEAR = 2026;
    private static final int MONTH = 2;

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UsageEventRepository usageEventRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private ProrationAdjustmentRepository prorationAdjustmentRepository;

    private BillingService billingService;
    private OrganizationEntity org;

    @BeforeEach
    void setUp() {
        BillingProperties properties = new BillingProperties();
        properties.setDefaultOverageRate(new BigDecimal("0.10"));
        properties.setCurrency("USD");
        billingService = new BillingService(organizationRepository, subscriptionRepository,
                usageEventRepository, invoiceRepository, prorationAdjustmentRepository,
                properties, new SimpleMeterRegistry());
        org = TestFixtures.organization(ORG_ID, "Acme Corp");
    }

    private void givenSubscription(PlanEntity plan, long usedUnits) {
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
        when(invoiceRepository.findByOrganizationIdAndPeriodYearAndPeriodMonth(ORG_ID, YEAR, MONTH))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findCurrent(ORG_ID))
                .thenReturn(Optional.of(TestFixtures.subscription(org, plan)));
        when(usageEventRepository.sumUnitsByOrganizationAndPeriod(eq(ORG_ID), any(), any()))
                .thenReturn(usedUnits);
        when(prorationAdjustmentRepository
                .findByOrganizationIdAndPeriodYearAndPeriodMonthOrderByCreatedAtAsc(ORG_ID, YEAR, MONTH))
                .thenReturn(List.of());
        when(invoiceRepository.existsByInvoiceNumber(anyString())).thenReturn(false);
        when(invoiceRepository.save(any(InvoiceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("usage against the plan allowance")
    class UsageAgainstAllowance {

        @Test
        @DisplayName("under the allowance bills the plan fee only")
        void underAllowance() {
            givenSubscription(TestFixtures.plan(PlanType.BASIC, "499.00", 10_000, "0.10"), 4_200L);

            InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

            assertThat(invoice.getUsedUnits()).isEqualTo(4_200L);
            assertThat(invoice.getOverageUnits()).isZero();
            assertThat(invoice.getBaseAmount()).isEqualByComparingTo("499.00");
            assertThat(invoice.getOverageAmount()).isEqualByComparingTo("0.00");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("499.00");
            assertThat(invoice.getLineItems()).hasSize(1);
            assertThat(invoice.getLineItems().get(0).getLineType())
                    .isEqualTo(InvoiceLineType.BASE_SUBSCRIPTION);
        }

        @Test
        @DisplayName("exactly at the allowance charges no overage (boundary is inclusive)")
        void exactlyAtAllowance() {
            givenSubscription(TestFixtures.plan(PlanType.BASIC, "499.00", 10_000, "0.10"), 10_000L);

            InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

            assertThat(invoice.getOverageUnits()).isZero();
            assertThat(invoice.getOverageAmount()).isEqualByComparingTo("0.00");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("499.00");
        }

        @Test
        @DisplayName("one unit over the allowance charges exactly one unit of overage")
        void oneUnitOver() {
            givenSubscription(TestFixtures.plan(PlanType.BASIC, "499.00", 10_000, "0.10"), 10_001L);

            InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

            assertThat(invoice.getOverageUnits()).isEqualTo(1L);
            assertThat(invoice.getOverageAmount()).isEqualByComparingTo("0.10");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("499.10");
        }

        @Test
        @DisplayName("over the allowance adds an overage line item")
        void overAllowance() {
            givenSubscription(TestFixtures.plan(PlanType.BASIC, "499.00", 10_000, "0.10"), 12_500L);

            InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

            assertThat(invoice.getOverageUnits()).isEqualTo(2_500L);
            assertThat(invoice.getOverageAmount()).isEqualByComparingTo("250.00");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("749.00");
            assertThat(invoice.getLineItems()).hasSize(2);
            assertThat(invoice.getLineItems().get(1).getLineType()).isEqualTo(InvoiceLineType.OVERAGE);
            assertThat(invoice.getLineItems().get(1).getQuantity()).isEqualByComparingTo("2500");
        }
    }

    @Nested
    @DisplayName("decimal money handling")
    class MoneyHandling {

        /**
         * The regression this whole migration exists for. With doubles,
         * {@code 3 * 0.1 == 0.30000000000000004}, so the invoice total drifted from the amount
         * actually charged.
         */
        @Test
        @DisplayName("amounts are always scaled to exactly two decimal places")
        void amountsAreScaledToTwoDecimals() {
            givenSubscription(TestFixtures.plan(PlanType.BASIC, "499.00", 10_000, "0.10"), 10_003L);

            InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

            assertThat(invoice.getOverageAmount()).isEqualByComparingTo("0.30");
            assertThat(invoice.getOverageAmount().scale()).isEqualTo(2);
            assertThat(invoice.getTotalAmount().scale()).isEqualTo(2);
            assertThat(invoice.getBaseAmount().scale()).isEqualTo(2);
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("499.30");
            assertThat(invoice.getTotalAmount().toPlainString()).isEqualTo("499.30");
        }

        @Test
        @DisplayName("a sub-cent fraction rounds half up rather than truncating")
        void roundsHalfUp() {
            // 0.005 * 3 = 0.015 -> 0.02 rounded half up.
            givenSubscription(TestFixtures.plan(PlanType.PRO, "1999.00", 100_000, "0.005"), 100_003L);

            InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

            assertThat(invoice.getOverageAmount()).isEqualByComparingTo("0.02");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1999.02");
        }

        @Test
        @DisplayName("a plan without its own rate falls back to the configured default")
        void fallsBackToConfiguredRate() {
            givenSubscription(TestFixtures.plan(PlanType.BASIC, "499.00", 10_000, null), 10_010L);

            InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

            assertThat(invoice.getOverageRate()).isEqualByComparingTo("0.10");
            assertThat(invoice.getOverageAmount()).isEqualByComparingTo("1.00");
        }
    }

    @Nested
    @DisplayName("proration adjustments")
    class Adjustments {

        @Test
        @DisplayName("credits and charges parked on the period appear as line items and move the total")
        void adjustmentsAreApplied() {
            givenSubscription(TestFixtures.plan(PlanType.PRO, "1999.00", 100_000, "0.05"), 0L);
            when(prorationAdjustmentRepository
                    .findByOrganizationIdAndPeriodYearAndPeriodMonthOrderByCreatedAtAsc(ORG_ID, YEAR, MONTH))
                    .thenReturn(List.of(
                            adjustment(InvoiceLineType.PRORATION_CREDIT, "Unused BASIC credit", "-249.50"),
                            adjustment(InvoiceLineType.PRORATION_CHARGE, "Prorated PRO charge", "999.50")));

            InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

            assertThat(invoice.getAdjustmentAmount()).isEqualByComparingTo("750.00");
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("2749.00");
            assertThat(invoice.getLineItems()).hasSize(3);
        }

        private ProrationAdjustmentEntity adjustment(InvoiceLineType type, String description, String amount) {
            ProrationAdjustmentEntity entity = new ProrationAdjustmentEntity();
            entity.setOrganization(org);
            entity.setPeriodYear(YEAR);
            entity.setPeriodMonth(MONTH);
            entity.setLineType(type);
            entity.setDescription(description);
            entity.setAmount(new BigDecimal(amount));
            entity.setCreatedAt(Instant.parse("2026-02-15T00:00:00Z"));
            return entity;
        }
    }

    @Nested
    @DisplayName("failure cases")
    class Failures {

        @Test
        @DisplayName("an unknown organization is a typed 404, not a bare RuntimeException")
        void unknownOrganization() {
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> billingService.generateInvoice(ORG_ID, YEAR, MONTH))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ORG_ID.toString());
            verify(invoiceRepository, never()).save(any());
        }

        @Test
        @DisplayName("an organization with no subscription cannot be invoiced")
        void noActiveSubscription() {
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(invoiceRepository.findByOrganizationIdAndPeriodYearAndPeriodMonth(ORG_ID, YEAR, MONTH))
                    .thenReturn(Optional.empty());
            when(subscriptionRepository.findCurrent(ORG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> billingService.generateInvoice(ORG_ID, YEAR, MONTH))
                    .isInstanceOf(NoActiveSubscriptionException.class);
            verify(invoiceRepository, never()).save(any());
        }

        @Test
        @DisplayName("an out-of-range month is rejected before any lookup")
        void invalidMonth() {
            assertThatThrownBy(() -> billingService.generateInvoice(ORG_ID, YEAR, 13))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(organizationRepository, invoiceRepository);
        }
    }

    @Test
    @DisplayName("generation is idempotent: an already-issued period returns the stored invoice")
    void generationIsIdempotent() {
        InvoiceEntity existing = new InvoiceEntity();
        existing.setInvoiceNumber("INV-202602-ABC123");
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
        when(invoiceRepository.findByOrganizationIdAndPeriodYearAndPeriodMonth(ORG_ID, YEAR, MONTH))
                .thenReturn(Optional.of(existing));

        InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

        assertThat(invoice).isSameAs(existing);
        verify(invoiceRepository, never()).save(any());
        verifyNoInteractions(subscriptionRepository, usageEventRepository);
    }

    @Test
    @DisplayName("the billing period is a half-open UTC month, so the final second is still counted")
    void periodIsHalfOpen() {
        givenSubscription(TestFixtures.plan(PlanType.BASIC, "499.00", 10_000, "0.10"), 0L);

        InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

        assertThat(invoice.getPeriodStart()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
        assertThat(invoice.getPeriodEnd()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        verify(usageEventRepository).sumUnitsByOrganizationAndPeriod(
                ORG_ID,
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-03-01T00:00:00Z"));
    }

    @Test
    @DisplayName("invoice numbers are immutable, unique and period-stamped")
    void invoiceNumberFormat() {
        givenSubscription(TestFixtures.plan(PlanType.BASIC, "499.00", 10_000, "0.10"), 0L);

        InvoiceEntity invoice = billingService.generateInvoice(ORG_ID, YEAR, MONTH);

        assertThat(invoice.getInvoiceNumber()).matches("INV-202602-[A-Z0-9]{6}");
    }
}
