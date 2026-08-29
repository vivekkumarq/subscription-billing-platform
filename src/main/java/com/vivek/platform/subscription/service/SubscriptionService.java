package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.domain.*;
import com.vivek.platform.subscription.exception.InvalidSubscriptionStateException;
import com.vivek.platform.subscription.exception.NoActiveSubscriptionException;
import com.vivek.platform.subscription.exception.ResourceNotFoundException;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import com.vivek.platform.subscription.repository.PlanRepository;
import com.vivek.platform.subscription.repository.ProrationAdjustmentRepository;
import com.vivek.platform.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Subscription lifecycle: subscribe, change plan with proration, cancel with an effective date.
 *
 * <p>The README always claimed "create, upgrade, deactivate" but only create existed, and state
 * was a bare boolean that could not express a pending cancellation.</p>
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final OrganizationRepository organizationRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProrationAdjustmentRepository prorationAdjustmentRepository;
    private final ProrationService prorationService;

    public SubscriptionService(OrganizationRepository organizationRepository,
                               PlanRepository planRepository,
                               SubscriptionRepository subscriptionRepository,
                               ProrationAdjustmentRepository prorationAdjustmentRepository,
                               ProrationService prorationService) {
        this.organizationRepository = organizationRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.prorationAdjustmentRepository = prorationAdjustmentRepository;
        this.prorationService = prorationService;
    }

    @Transactional
    public SubscriptionEntity createSubscription(UUID orgId, PlanType planType) {
        OrganizationEntity org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ResourceNotFoundException.organization(orgId));
        PlanEntity plan = planRepository.findByType(planType)
                .orElseThrow(() -> ResourceNotFoundException.plan(planType));

        subscriptionRepository.findCurrent(orgId).ifPresent(existing -> {
            throw new InvalidSubscriptionStateException(
                    "Organization already has a " + existing.getStatus() + " subscription on plan "
                            + existing.getPlan().getType() + "; change or cancel it instead");
        });

        Instant now = Instant.now();
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setOrganization(org);
        subscription.setPlan(plan);
        subscription.setStartDate(now);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        SubscriptionEntity saved = subscriptionRepository.save(subscription);
        log.info("Created subscription id={} org={} plan={}", saved.getId(), orgId, planType);
        return saved;
    }

    @Transactional(readOnly = true)
    public SubscriptionEntity getCurrentSubscription(UUID orgId) {
        requireOrganization(orgId);
        return subscriptionRepository.findCurrent(orgId)
                .orElseThrow(() -> new NoActiveSubscriptionException(orgId));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionEntity> listSubscriptions(UUID orgId) {
        requireOrganization(orgId);
        return subscriptionRepository.findByOrganizationIdOrderByStartDateDesc(orgId);
    }

    /**
     * Moves the tenant to a different plan effective immediately, crediting the unused part of
     * the old plan and charging the matching part of the new one. Both amounts are parked as
     * adjustments on the current billing period and picked up by the next invoice.
     */
    @Transactional
    public PlanChange changePlan(UUID orgId, PlanType newPlanType) {
        OrganizationEntity org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ResourceNotFoundException.organization(orgId));
        SubscriptionEntity current = subscriptionRepository.findCurrent(orgId)
                .orElseThrow(() -> new NoActiveSubscriptionException(orgId));

        if (current.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new InvalidSubscriptionStateException("Cannot change the plan of a cancelled subscription");
        }
        PlanEntity newPlan = planRepository.findByType(newPlanType)
                .orElseThrow(() -> ResourceNotFoundException.plan(newPlanType));
        PlanEntity oldPlan = current.getPlan();
        if (oldPlan.getType() == newPlanType) {
            throw new InvalidSubscriptionStateException(
                    "Subscription is already on plan " + newPlanType);
        }

        Instant now = Instant.now();
        BillingPeriod period = BillingPeriod.containing(now);
        ProrationResult proration = prorationService.calculate(
                oldPlan.getMonthlyPrice(), newPlan.getMonthlyPrice(), now, period);

        recordAdjustment(org, period, InvoiceLineType.PRORATION_CREDIT,
                "Unused " + oldPlan.getType() + " plan credit ("
                        + proration.remainingDays() + "/" + proration.daysInPeriod() + " days)",
                proration.credit().negate(), now);
        recordAdjustment(org, period, InvoiceLineType.PRORATION_CHARGE,
                "Prorated " + newPlan.getType() + " plan charge ("
                        + proration.remainingDays() + "/" + proration.daysInPeriod() + " days)",
                proration.charge(), now);

        // Close the old subscription row and open a new one, so history stays auditable.
        current.setStatus(SubscriptionStatus.CANCELLED);
        current.setCancelledAt(now);
        current.setEndDate(now);
        subscriptionRepository.save(current);

        SubscriptionEntity replacement = new SubscriptionEntity();
        replacement.setOrganization(org);
        replacement.setPlan(newPlan);
        replacement.setStartDate(now);
        replacement.setStatus(SubscriptionStatus.ACTIVE);
        SubscriptionEntity saved = subscriptionRepository.save(replacement);

        log.info("Plan change org={} {} -> {} credit={} charge={} net={} period={}",
                orgId, oldPlan.getType(), newPlanType, proration.credit(), proration.charge(),
                proration.netAmount(), period.label());
        return new PlanChange(saved, proration, period);
    }

    /**
     * Requests cancellation. Service continues until the effective date - by default the end of
     * the current billing period, so the tenant keeps what they have already paid for.
     */
    @Transactional
    public SubscriptionEntity cancel(UUID orgId, Instant effectiveAt) {
        requireOrganization(orgId);
        SubscriptionEntity current = subscriptionRepository.findCurrent(orgId)
                .orElseThrow(() -> new NoActiveSubscriptionException(orgId));

        Instant now = Instant.now();
        Instant effective = effectiveAt != null ? effectiveAt : BillingPeriod.containing(now).end();
        if (effective.isBefore(current.getStartDate())) {
            throw new InvalidSubscriptionStateException(
                    "Cancellation cannot take effect before the subscription started");
        }

        current.setStatus(SubscriptionStatus.CANCELLED);
        current.setCancelledAt(now);
        current.setEndDate(effective);
        SubscriptionEntity saved = subscriptionRepository.save(current);
        log.info("Cancelled subscription id={} org={} effectiveAt={}", saved.getId(), orgId, effective);
        return saved;
    }

    /** Flags a subscription as unpaid without terminating it, for dunning workflows. */
    @Transactional
    public SubscriptionEntity markPastDue(UUID orgId) {
        requireOrganization(orgId);
        SubscriptionEntity current = subscriptionRepository.findCurrent(orgId)
                .orElseThrow(() -> new NoActiveSubscriptionException(orgId));
        if (current.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new InvalidSubscriptionStateException("Cancelled subscriptions cannot be marked past due");
        }
        current.setStatus(SubscriptionStatus.PAST_DUE);
        return subscriptionRepository.save(current);
    }

    private void recordAdjustment(OrganizationEntity org, BillingPeriod period,
                                  InvoiceLineType type, String description,
                                  BigDecimal amount, Instant createdAt) {
        if (amount.signum() == 0) {
            return;
        }
        ProrationAdjustmentEntity adjustment = new ProrationAdjustmentEntity();
        adjustment.setOrganization(org);
        adjustment.setPeriodYear(period.year());
        adjustment.setPeriodMonth(period.month());
        adjustment.setLineType(type);
        adjustment.setDescription(description);
        adjustment.setAmount(Money.round(amount));
        adjustment.setCreatedAt(createdAt);
        prorationAdjustmentRepository.save(adjustment);
    }

    private void requireOrganization(UUID orgId) {
        if (!organizationRepository.existsById(orgId)) {
            throw ResourceNotFoundException.organization(orgId);
        }
    }

    /** Result of {@link #changePlan}: the new subscription plus the proration applied. */
    public record PlanChange(SubscriptionEntity subscription,
                             ProrationResult proration,
                             BillingPeriod period) {
    }
}
