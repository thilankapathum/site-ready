package dev.thilanka.site_ready.dto;

import dev.thilanka.site_ready.entity.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
        @NotNull ReportStatus decision,
        String notes,
        String conditions   // only relevant for CONDITIONALLY_APPROVED
) {}
