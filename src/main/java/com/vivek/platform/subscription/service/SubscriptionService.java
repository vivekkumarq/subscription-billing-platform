package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.PlanEntity;
import com.vivek.platform.subscription.domain.PlanType;
import com.vivek.platform.subscription.domain.SubscriptionEntity;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import com.vivek.platform.subscription.repository.PlanRepository;
import com.vivek.platform.subscription.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SubscriptionService {

    private final OrganizationRepository organizationRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(OrganizationRepository organizationRepository,
                               PlanRepository planRepository,
                               SubscriptionRepository subscriptionRepository) {
        this.organizationRepository = organizationRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    public SubscriptionEntity createSubscription(String orgId, PlanType planType) {
        OrganizationEntity org = organizationRepository.findById(java.util.UUID.fromString(orgId))
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        PlanEntity plan = planRepository.findByType(planType)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        subscriptionRepository.findByOrganizationAndActiveTrue(org)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    existing.setEndDate(Instant.now());
                    subscriptionRepository.save(existing);
                });

        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setOrganization(org);
        subscription.setPlan(plan);
        subscription.setStartDate(Instant.now());
        subscription.setActive(true);

        return subscriptionRepository.save(subscription);
    }
}