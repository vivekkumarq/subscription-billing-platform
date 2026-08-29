package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.api.dto.QuotaStatusResponse;
import com.vivek.platform.subscription.config.BillingProperties;
import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.PlanEntity;
import com.vivek.platform.subscription.domain.QuotaAlertEntity;
import com.vivek.platform.subscription.domain.SubscriptionEntity;
import com.vivek.platform.subscription.events.QuotaThresholdCrossedEvent;
import com.vivek.platform.subscription.exception.NoActiveSubscriptionException;
import com.vivek.platform.subscription.exception.ResourceNotFoundException;
import com.vivek.platform.subscription.messaging.QuotaAlertProducer;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import com.vivek.platform.subscription.repository.QuotaAlertRepository;
import com.vivek.platform.subscription.repository.SubscriptionRepository;
import com.vivek.platform.subscription.repository.UsageEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks consumption against the plan allowance and raises an alert the first time an
 * organization crosses each configured threshold within a billing period.
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OrganizationRepository organizationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageEventRepository usageEventRepository;
    private final QuotaAlertRepository quotaAlertRepository;
    private final QuotaAlertProducer quotaAlertProducer;
    private final BillingProperties billingProperties;
    private final Counter quotaAlertsCounter;

    public QuotaService(OrganizationRepository organizationRepository,
                        SubscriptionRepository subscriptionRepository,
                        UsageEventRepository usageEventRepository,
                        QuotaAlertRepository quotaAlertRepository,
                        QuotaAlertProducer quotaAlertProducer,
                        BillingProperties billingProperties,
                        MeterRegistry meterRegistry) {
        this.organizationRepository = organizationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageEventRepository = usageEventRepository;
        this.quotaAlertRepository = quotaAlertRepository;
        this.quotaAlertProducer = quotaAlertProducer;
        this.billingProperties = billingProperties;
        this.quotaAlertsCounter = Counter.builder("billing.quota.alerts")
                .description("Quota threshold alerts emitted")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public QuotaStatusResponse status(UUID orgId, BillingPeriod period) {
        if (!organizationRepository.existsById(orgId)) {
            throw ResourceNotFoundException.organization(orgId);
        }
        SubscriptionEntity subscription = subscriptionRepository.findCurrent(orgId)
                .orElseThrow(() -> new NoActiveSubscriptionException(orgId));
        PlanEntity plan = subscription.getPlan();

        long used = usageEventRepository
                .sumUnitsByOrganizationAndPeriod(orgId, period.start(), period.end());
        int included = plan.getIncludedUnits();
        long remaining = Math.max(0L, included - used);
        long overage = Math.max(0L, used - included);
        BigDecimal rate = plan.getOverageRatePerUnit() != null
                ? plan.getOverageRatePerUnit()
                : billingProperties.getDefaultOverageRate();

        List<Integer> crossed = quotaAlertRepository
                .findByOrganizationIdAndPeriodYearAndPeriodMonthOrderByThresholdPercentAsc(
                        orgId, period.year(), period.month())
                .stream()
                .map(QuotaAlertEntity::getThresholdPercent)
                .toList();

        return new QuotaStatusResponse(
                orgId,
                plan.getType().name(),
                period.year(),
                period.month(),
                used,
                included,
                remaining,
                percentOf(used, included),
                used > included,
                Money.multiply(rate, overage),
                plan.getCurrency() != null ? plan.getCurrency() : billingProperties.getCurrency(),
                crossed);
    }

    /**
     * Called after usage is applied. Emits one event per newly crossed threshold.
     *
     * <p>Runs in its own transaction: a failure to raise an alert must never roll back the usage
     * that has already been accepted.</p>
     *
     * @return the thresholds that fired on this call
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Integer> evaluateThresholds(UUID orgId, Instant at) {
        BillingPeriod period = BillingPeriod.containing(at);
        List<Integer> fired = new ArrayList<>();

        SubscriptionEntity subscription = subscriptionRepository.findCurrent(orgId).orElse(null);
        if (subscription == null) {
            log.debug("No current subscription for org={}, skipping quota evaluation", orgId);
            return fired;
        }
        OrganizationEntity org = subscription.getOrganization();
        int included = subscription.getPlan().getIncludedUnits();
        if (included <= 0) {
            return fired;
        }

        long used = usageEventRepository
                .sumUnitsByOrganizationAndPeriod(orgId, period.start(), period.end());
        BigDecimal percent = percentOf(used, included);

        for (Integer threshold : billingProperties.getQuotaAlertThresholds()) {
            if (threshold == null || percent.compareTo(BigDecimal.valueOf(threshold)) < 0) {
                continue;
            }
            if (quotaAlertRepository
                    .existsByOrganizationIdAndPeriodYearAndPeriodMonthAndThresholdPercent(
                            orgId, period.year(), period.month(), threshold)) {
                continue;
            }
            if (persistAlert(org, period, threshold, used, included, at)) {
                fired.add(threshold);
                quotaAlertsCounter.increment();
                quotaAlertProducer.publish(new QuotaThresholdCrossedEvent(
                        UUID.randomUUID(), orgId, threshold, used, included,
                        period.year(), period.month(), at));
                log.info("Quota alert: org={} crossed {}% ({}/{} units) in {}",
                        orgId, threshold, used, included, period.label());
            }
        }
        return fired;
    }

    private boolean persistAlert(OrganizationEntity org, BillingPeriod period, int threshold,
                                 long used, int included, Instant at) {
        QuotaAlertEntity alert = new QuotaAlertEntity();
        alert.setOrganization(org);
        alert.setPeriodYear(period.year());
        alert.setPeriodMonth(period.month());
        alert.setThresholdPercent(threshold);
        alert.setUsedUnits(used);
        alert.setIncludedUnits(included);
        alert.setTriggeredAt(at);
        try {
            quotaAlertRepository.saveAndFlush(alert);
            return true;
        } catch (DataIntegrityViolationException ex) {
            // Another consumer thread raised the same alert first; the unique constraint is the
            // authority, so this instance simply stays quiet.
            log.debug("Quota alert {}% already recorded for org={} period={}",
                    threshold, org.getId(), period.label());
            return false;
        }
    }

    static BigDecimal percentOf(long used, int included) {
        if (included <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(used)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(included), 2, RoundingMode.HALF_UP);
    }
}
