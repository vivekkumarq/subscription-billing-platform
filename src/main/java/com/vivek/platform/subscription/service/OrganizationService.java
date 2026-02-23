package com.vivek.platform.subscription.service;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public OrganizationEntity createOrganization(String name) {
        OrganizationEntity org = new OrganizationEntity();
        org.setName(name);
        org.setCreatedAt(Instant.now());
        return organizationRepository.save(org);
    }
}