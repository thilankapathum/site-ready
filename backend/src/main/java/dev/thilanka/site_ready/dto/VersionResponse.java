package dev.thilanka.site_ready.dto;

import dev.thilanka.site_ready.entity.ReportVersion;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VersionResponse(
        UUID id,
        int versionNumber,
        String originalFilename,
        String contentType,
        String sha256Hash,
        String padesSignatureId,
        String uploaderName,
        String uploaderCompany,
        String uploaderRole,
        OffsetDateTime uploadedAt,
        String statusAtUpload,
        String reviewStatus,
        String reviewerNotes,
        String conditions,
        String reviewerName,
        OffsetDateTime reviewedAt,
        boolean hasStampedPdf,
        boolean hasReviewedPdf,
        PageDiff pageDiff,
        PageDiff reviewerPageDiff
) {
    public static VersionResponse from(ReportVersion v) {
        return new VersionResponse(
                v.getId(),
                v.getVersionNumber(),
                v.getOriginalFilename(),
                v.getContentType(),
                v.getSha256Hash(),
                v.getPadesSignatureId(),
                v.getUploadedBy().getFullName(),
                v.getUploadedBy().getCompany().getName(),
                v.getUploadedBy().getRole().name(),
                v.getUploadedAt(),
                v.getStatusAtUpload().name(),
                v.getReviewStatus() != null ? v.getReviewStatus().name() : null,
                v.getReviewerNotes(),
                v.getConditions(),
                v.getReviewedBy() != null ? v.getReviewedBy().getFullName() : null,
                v.getReviewedAt(),
                v.getStampedStorageKey() != null,
                v.getReviewedStorageKey() != null,
                v.getPageDiff(),
                v.getReviewerPageDiff()
        );
    }
}
