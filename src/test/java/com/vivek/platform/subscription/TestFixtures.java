package com.vivek.platform.subscription;

import com.vivek.platform.subscription.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Builders shared across unit tests, so each test only states what it actually cares about. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static OrganizationEntity organization(UUID id, String name) {
        OrganizationEntity org = new OrganizationEntity();
        org.setId(id);
        org.setName(name);
        org.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return org;
    }

    public static PlanEntity plan(PlanType type, String monthlyPrice, int includedUnits, String overageRate) {
        PlanEntity plan = new PlanEntity();
        plan.setType(type);
        plan.setMonthlyPrice(new BigDecimal(monthlyPrice));
        plan.setIncludedUnits(includedUnits);
        plan.setOverageRatePerUnit(overageRate == null ? null : new BigDecimal(overageRate));
        plan.setCurrency("USD");
        return plan;
    }

    public static SubscriptionEntity subscription(OrganizationEntity org, PlanEntity plan) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setOrganization(org);
        subscription.setPlan(plan);
        subscription.setStartDate(Instant.parse("2026-01-01T00:00:00Z"));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        return subscription;
    }
}
