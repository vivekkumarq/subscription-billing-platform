package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.api.dto.InvoiceResponse;
import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.SubscriptionEntity;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import com.vivek.platform.subscription.repository.SubscriptionRepository;
import com.vivek.platform.subscription.repository.UsageEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class BillingService {

    private static final double OVERAGE_RATE_PER_UNIT = 0.10;

    private final OrganizationRepository organizationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageEventRepository usageEventRepository;

    public BillingService(OrganizationRepository organizationRepository,
                          SubscriptionRepository subscriptionRepository,
                          UsageEventRepository usageEventRepository) {
        this.organizationRepository = organizationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageEventRepository = usageEventRepository;
    }

    public InvoiceResponse generateInvoice(UUID orgId, int year, int month) {
        OrganizationEntity org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        SubscriptionEntity activeSub = subscriptionRepository.findByOrganizationAndActiveTrue(org)
                .orElseThrow(() -> new RuntimeException("No active subscription"));

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        Instant startTs = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endTs = end.atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        long usedUnits = usageEventRepository.sumUnitsByOrganizationAndPeriod(org, startTs, endTs);

        int includedUnits = activeSub.getPlan().getIncludedUnits();
        double baseAmount = activeSub.getPlan().getMonthlyPrice();

        long extraUnits = Math.max(0, usedUnits - includedUnits);
        double overageAmount = extraUnits * OVERAGE_RATE_PER_UNIT;

        InvoiceResponse invoice = new InvoiceResponse();
        invoice.setOrganizationId(orgId);
        invoice.setPlanType(activeSub.getPlan().getType().name());
        invoice.setBaseAmount(baseAmount);
        invoice.setUsedUnits(usedUnits);
        invoice.setIncludedUnits(includedUnits);
        invoice.setOverageAmount(overageAmount);
        invoice.setTotalAmount(baseAmount + overageAmount);

        return invoice;
    }
}