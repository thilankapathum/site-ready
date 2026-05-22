package dev.thilanka.site_ready.dto;

import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        String siteId,
        String project,
        String rat,
        String namingKey,
        int currentVersion,
        String currentStatus,
        String currentResponsibility,
        String assignedEngineerName,
        UUID assignedEngineerId,
        UUID vendorId,
        String vendorName,
        String vendorCompany,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        UUID latestVersionId,
        String sha256Hash,
        String padesSignatureId,
        OffsetDateTime uploadedAt,
        String reviewStatus,
        String reviewerNotes,
        String conditions,
        String reviewerName
) {
    public static ReportResponse from(Report r, ReportVersion v) {
        return new ReportResponse(
                r.getId(),
                r.getSiteId(),
                r.getProject(),
                r.getRat(),
                r.getNamingKey(),
                r.getCurrentVersion(),
                r.getCurrentStatus().name(),
                r.getCurrentResponsibility().name(),
                r.getAssignedEngineer() != null ? r.getAssignedEngineer().getFullName() : null,
                r.getAssignedEngineer() != null ? r.getAssignedEngineer().getId() : null,
                r.getCreatedByVendor().getId(),
                r.getCreatedByVendor().getFullName(),
                r.getCreatedByVendor().getCompany().getName(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                v != null ? v.getId() : null,
                v != null ? v.getSha256Hash() : null,
                v != null ? v.getPadesSignatureId() : null,
                v != null ? v.getUploadedAt() : null,
                v != null && v.getReviewStatus() != null ? v.getReviewStatus().name() : null,
                v != null ? v.getReviewerNotes() : null,
                v != null ? v.getConditions() : null,
                v != null && v.getReviewedBy() != null ? v.getReviewedBy().getFullName() : null
        );
    }
}
