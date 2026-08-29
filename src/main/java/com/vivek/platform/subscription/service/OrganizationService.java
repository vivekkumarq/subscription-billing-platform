package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.exception.DuplicateResourceException;
import com.vivek.platform.subscription.exception.ResourceNotFoundException;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /**
     * Names are unique. Duplicates used to surface as an unmapped constraint-violation 500;
     * they are now a typed 409.
     */
    @Transactional
    public OrganizationEntity createOrganization(String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new IllegalArgumentException("Organization name must not be blank");
        }
        if (organizationRepository.existsByNameIgnoreCase(trimmed)) {
            throw new DuplicateResourceException("An organization named '" + trimmed + "' already exists");
        }
        OrganizationEntity org = new OrganizationEntity();
        org.setName(trimmed);
        org.setCreatedAt(Instant.now());
        OrganizationEntity saved = organizationRepository.save(org);
        log.info("Provisioned organization id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional(readOnly = true)
    public OrganizationEntity getOrganization(UUID orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> ResourceNotFoundException.organization(orgId));
    }
}
