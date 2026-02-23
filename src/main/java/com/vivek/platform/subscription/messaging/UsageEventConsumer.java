package com.vivek.platform.subscription.messaging;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.UsageEventEntity;
import com.vivek.platform.subscription.events.UsageRecordedEvent;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import com.vivek.platform.subscription.repository.UsageEventRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UsageEventConsumer {

    private final UsageEventRepository usageEventRepository;
    private final OrganizationRepository organizationRepository;

    public UsageEventConsumer(UsageEventRepository usageEventRepository,
                              OrganizationRepository organizationRepository) {
        this.usageEventRepository = usageEventRepository;
        this.organizationRepository = organizationRepository;
    }

    @KafkaListener(topics = "usage-recorded-topic", groupId = "subscription-service")
    public void handleUsageRecorded(UsageRecordedEvent event) {
        OrganizationEntity org = organizationRepository.findById(event.getOrganizationId())
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        UsageEventEntity usage = new UsageEventEntity();
        usage.setOrganization(org);
        usage.setUnitsConsumed(event.getUnits());
        usage.setTimestamp(Instant.now());

        usageEventRepository.save(usage);

        System.out.println("📥 Usage event consumed and persisted for org=" + event.getOrganizationId());
    }
}