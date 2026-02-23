package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.UsageEventEntity;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import com.vivek.platform.subscription.repository.UsageEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class UsageService {

    private final UsageEventRepository usageEventRepository;
    private final OrganizationRepository organizationRepository;

    public UsageService(UsageEventRepository usageEventRepository,
                        OrganizationRepository organizationRepository) {
        this.usageEventRepository = usageEventRepository;
        this.organizationRepository = organizationRepository;
    }

    public UsageEventEntity recordUsage(UUID orgId, Integer units) {
        OrganizationEntity org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        UsageEventEntity event = new UsageEventEntity();
        event.setOrganization(org);
        event.setUnitsConsumed(units);
        event.setTimestamp(Instant.now());

        return usageEventRepository.save(event);
    }
}