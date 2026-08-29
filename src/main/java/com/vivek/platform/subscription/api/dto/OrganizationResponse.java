package com.vivek.platform.subscription.api.dto;

import com.vivek.platform.subscription.domain.OrganizationEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Controllers return this instead of the JPA entity, so lazy associations are never serialised
 * and the persistence model can change without breaking the wire contract.
 */
@Schema(description = "A tenant organization")
public record OrganizationResponse(UUID id, String name, Instant createdAt) {

    public static OrganizationResponse from(OrganizationEntity entity) {
        return new OrganizationResponse(entity.getId(), entity.getName(), entity.getCreatedAt());
    }
}
