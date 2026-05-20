package dev.thilanka.site_ready.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UploadRequest(
        @NotBlank String siteId,
        @NotBlank String project,
        @NotBlank String rat,
        UUID assignedEngineerId
) {}
