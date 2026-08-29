package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.config.BillingProperties;
import com.vivek.platform.subscription.domain.*;
import com.vivek.platform.subscription.exception.NoActiveSubscriptionException;
import com.vivek.platform.subscription.exception.ResourceNotFoundException;
import com.vivek.platform.subscription.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Invoice generation.
 *
 * <p>Two things changed materially here. Money is {@link BigDecimal} end to end - the previous
 * {@code double} arithmetic could not represent decimal currency and produced totals like
 * {@code 1999.0000000000002}. And invoices are now persisted with a line-item breakdown and an
 * immutable invoice number, so a closed period always reports the same figures.</p>
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVOICE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int INVOICE_SUFFIX_LENGTH = 6;
    private static final int INVOICE_NUMBER_ATTEMPTS = 5;

    private final OrganizationRepository organizationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageEventRepository usageEventRepository;
    private final InvoiceRepository invoiceRepository;
    private final ProrationAdjustmentRepository prorationAdjustmentRepository;
    private final BillingProperties billingProperties;
    private final Counter invoicesGeneratedCounter;
    private final Counter revenueCounter;

    public BillingService(OrganizationRepository organizationRepository,
                          SubscriptionRepository subscriptionRepository,
                          UsageEventRepository usageEventRepository,
                          InvoiceRepository invoiceRepository,
                          ProrationAdjustmentRepository prorationAdjustmentRepository,
                          BillingProperties billingProperties,
                          MeterRegistry meterRegistry) {
        this.organizationRepository = organizationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageEventRepository = usageEventRepository;
        this.invoiceRepository = invoiceRepository;
        this.prorationAdjustmentRepository = prorationAdjustmentRepository;
        this.billingProperties = billingProperties;
        this.invoicesGeneratedCounter = Counter.builder("billing.invoices.generated")
                .description("Invoices generated and persisted")
                .register(meterRegistry);
        this.revenueCounter = Counter.builder("billing.revenue.total")
                .description("Cumulative invoiced revenue, in the configured billing currency")
                .tag("currency", billingProperties.getCurrency())
                .register(meterRegistry);
    }

    /**
     * Generates and stores the invoice for a period, or returns the stored one if this period
     * has already been invoiced. Idempotent by (organization, year, month).
     */
    @Transactional
    public InvoiceEntity generateInvoice(UUID orgId, int year, int month) {
        BillingPeriod period = BillingPeriod.of(year, month);

        OrganizationEntity org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ResourceNotFoundException.organization(orgId));

        return invoiceRepository
                .findByOrganizationIdAndPeriodYearAndPeriodMonth(orgId, year, month)
                .map(existing -> {
                    log.debug("Returning already-issued invoice {} for org={} period={}",
                            existing.getInvoiceNumber(), orgId, period.label());
                    return existing;
                })
                .orElseGet(() -> issueInvoice(org, period));
    }

    private InvoiceEntity issueInvoice(OrganizationEntity org, BillingPeriod period) {
        UUID orgId = org.getId();
        SubscriptionEntity subscription = subscriptionRepository.findCurrent(orgId)
                .orElseThrow(() -> new NoActiveSubscriptionException(orgId));

        PlanEntity plan = subscription.getPlan();
        int includedUnits = plan.getIncludedUnits();
        BigDecimal baseAmount = Money.round(plan.getMonthlyPrice());
        BigDecimal overageRate = resolveOverageRate(plan);

        long usedUnits = usageEventRepository
                .sumUnitsByOrganizationAndPeriod(orgId, period.start(), period.end());
        long overageUnits = Math.max(0L, usedUnits - includedUnits);
        BigDecimal overageAmount = Money.multiply(overageRate, overageUnits);

        List<ProrationAdjustmentEntity> adjustments = prorationAdjustmentRepository
                .findByOrganizationIdAndPeriodYearAndPeriodMonthOrderByCreatedAtAsc(
                        orgId, period.year(), period.month());
        BigDecimal adjustmentAmount = adjustments.stream()
                .map(ProrationAdjustmentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        adjustmentAmount = Money.round(adjustmentAmount);

        BigDecimal total = Money.sum(baseAmount, overageAmount, adjustmentAmount);

        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setInvoiceNumber(nextInvoiceNumber(period));
        invoice.setOrganization(org);
        invoice.setPeriodYear(period.year());
        invoice.setPeriodMonth(period.month());
        invoice.setPeriodStart(period.start());
        invoice.setPeriodEnd(period.end());
        invoice.setPlanType(plan.getType());
        invoice.setIncludedUnits(includedUnits);
        invoice.setUsedUnits(usedUnits);
        invoice.setOverageUnits(overageUnits);
        invoice.setOverageRate(overageRate);
        invoice.setBaseAmount(baseAmount);
        invoice.setOverageAmount(overageAmount);
        invoice.setAdjustmentAmount(adjustmentAmount);
        invoice.setTotalAmount(total);
        invoice.setCurrency(plan.getCurrency() != null ? plan.getCurrency() : billingProperties.getCurrency());
        invoice.setIssuedAt(Instant.now());

        invoice.addLineItem(new InvoiceLineItemEntity(
                InvoiceLineType.BASE_SUBSCRIPTION,
                plan.getType().name() + " plan, " + period.label(),
                BigDecimal.ONE,
                baseAmount,
                baseAmount));

        if (overageUnits > 0) {
            invoice.addLineItem(new InvoiceLineItemEntity(
                    InvoiceLineType.OVERAGE,
                    "Overage: " + overageUnits + " units beyond the " + includedUnits + " included",
                    BigDecimal.valueOf(overageUnits),
                    overageRate,
                    overageAmount));
        }

        for (ProrationAdjustmentEntity adjustment : adjustments) {
            invoice.addLineItem(new InvoiceLineItemEntity(
                    adjustment.getLineType(),
                    adjustment.getDescription(),
                    BigDecimal.ONE,
                    adjustment.getAmount(),
                    adjustment.getAmount()));
        }

        InvoiceEntity saved = invoiceRepository.save(invoice);

        invoicesGeneratedCounter.increment();
        if (total.signum() > 0) {
            revenueCounter.increment(total.doubleValue());
        }
        log.info("Issued invoice {} for org={} period={} used={} overage={} total={} {}",
                saved.getInvoiceNumber(), orgId, period.label(), usedUnits, overageUnits,
                total, saved.getCurrency());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InvoiceEntity> listInvoices(UUID orgId) {
        if (!organizationRepository.existsById(orgId)) {
            throw ResourceNotFoundException.organization(orgId);
        }
        return invoiceRepository.findByOrganizationIdOrderByPeriodYearDescPeriodMonthDesc(orgId);
    }

    @Transactional(readOnly = true)
    public InvoiceEntity getInvoice(UUID orgId, String invoiceNumber) {
        return invoiceRepository.findByOrganizationIdAndInvoiceNumber(orgId, invoiceNumber)
                .orElseThrow(() -> ResourceNotFoundException.invoice(invoiceNumber));
    }

    /** Plan-level rate wins; otherwise the configured platform default applies. */
    BigDecimal resolveOverageRate(PlanEntity plan) {
        BigDecimal planRate = plan.getOverageRatePerUnit();
        return planRate != null ? planRate : billingProperties.getDefaultOverageRate();
    }

    private String nextInvoiceNumber(BillingPeriod period) {
        for (int attempt = 0; attempt < INVOICE_NUMBER_ATTEMPTS; attempt++) {
            String candidate = "INV-" + period.year() + String.format("%02d", period.month())
                    + "-" + randomSuffix();
            if (!invoiceRepository.existsByInvoiceNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique invoice number");
    }

    private static String randomSuffix() {
        StringBuilder sb = new StringBuilder(INVOICE_SUFFIX_LENGTH);
        for (int i = 0; i < INVOICE_SUFFIX_LENGTH; i++) {
            sb.append(INVOICE_ALPHABET.charAt(RANDOM.nextInt(INVOICE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
