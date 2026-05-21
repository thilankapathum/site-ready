package dev.thilanka.site_ready.dto;

import dev.thilanka.site_ready.entity.enums.CompanyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompanyRequest(
        @NotBlank String name,
        @NotBlank String shortName,
        @NotNull CompanyType type,
        String country,
        String contactEmail,
        String notes
) {}
