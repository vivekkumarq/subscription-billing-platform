package com.vivek.platform.subscription.events;

import java.util.UUID;

public class UsageRecordedEvent {

    private UUID organizationId;
    private Integer units;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public Integer getUnits() {
        return units;
    }

    public void setUnits(Integer units) {
        this.units = units;
    }
}