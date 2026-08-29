package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.domain.ProcessedEventEntity;
import com.vivek.platform.subscription.domain.UsageEventEntity;
import com.vivek.platform.subscription.exception.ResourceNotFoundException;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import com.vivek.platform.subscription.repository.ProcessedEventRepository;
import com.vivek.platform.subscription.repository.UsageEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies metered usage to a tenant's ledger.
 *
 * <p>Application is idempotent: the event id is recorded in an inbox table in the same
 * transaction as the usage row, so a Kafka redelivery is a no-op rather than double-counted
 * revenue.</p>
 */
@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);

    private final UsageEventRepository usageEventRepository;
    private final OrganizationRepository organizationRepository;
    private final ProcessedEventRepository processedEventRepository;

    public UsageService(UsageEventRepository usageEventRepository,
                        OrganizationRepository organizationRepository,
                        ProcessedEventRepository processedEventRepository) {
        this.usageEventRepository = usageEventRepository;
        this.organizationRepository = organizationRepository;
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * @return the persisted usage row, or {@code null} when the event had already been applied
     */
    @Transactional
    public UsageEventEntity applyUsage(UUID eventId, UUID orgId, Integer units,
                                       Instant occurredAt, String consumerGroup) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required for idempotent processing");
        }
        if (units == null || units < 1) {
            throw new IllegalArgumentException("unitsConsumed must be at least 1, was: " + units);
        }
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate usage event eventId={} org={}", eventId, orgId);
            return null;
        }

        OrganizationEntity org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ResourceNotFoundException.organization(orgId));

        UsageEventEntity usage = new UsageEventEntity();
        usage.setEventId(eventId);
        usage.setOrganization(org);
        usage.setUnitsConsumed(units);
        usage.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());

        UsageEventEntity saved = usageEventRepository.save(usage);
        processedEventRepository.save(
                new ProcessedEventEntity(eventId, consumerGroup, Instant.now()));

        log.debug("Applied usage eventId={} org={} units={}", eventId, orgId, units);
        return saved;
    }

    @Transactional(readOnly = true)
    public long usageForPeriod(UUID orgId, BillingPeriod period) {
        return usageEventRepository.sumUnitsByOrganizationAndPeriod(orgId, period.start(), period.end());
    }
}
