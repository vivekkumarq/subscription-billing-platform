package com.vivek.platform.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Provision a new tenant organization")
public class CreateOrganizationRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Unique organization name", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Acme Corp")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
