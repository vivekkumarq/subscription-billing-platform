package com.vivek.platform.subscription.api;

import com.vivek.platform.subscription.api.dto.CreateOrganizationRequest;
import com.vivek.platform.subscription.domain.OrganizationEntity;
import com.vivek.platform.subscription.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public OrganizationEntity create(@RequestBody @Valid CreateOrganizationRequest request) {
        return organizationService.createOrganization(request.getName());
    }
}