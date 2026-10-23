package dev.thilanka.site_ready.dto;

import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public result of the QR-code verification endpoint. Deliberately omits personal names,
 * reviewer notes and storage details — anyone holding the document can call it.
 * match is null when no file was submitted; otherwise one of
 * STAMPED_UPLOAD, REVIEWED, ORIGINAL or NONE (file does not match any stored copy).
 */
public record VerificationResponse(
        UUID versionId,
        String siteId,
        String project,
        String rat,
        int versionNumber,
        int latestVersion,
        String currentStatus,
        String vendorCompany,
        OffsetDateTime uploadedAt,
        String sha256Hash,
        String padesSignatureId,
        String reviewStatus,
        OffsetDateTime reviewedAt,
        String match
) {
    public static VerificationResponse from(ReportVersion v, String match) {
        Report r = v.getReport();
        return new VerificationResponse(
                v.getId(),
                r.getSiteId(),
                r.getProject(),
                r.getRat(),
                v.getVersionNumber(),
                r.getCurrentVersion(),
                r.getCurrentStatus().name(),
                r.getVendorCompany() != null ? r.getVendorCompany().getName() : null,
                v.getUploadedAt(),
                v.getSha256Hash(),
                v.getPadesSignatureId(),
                v.getReviewStatus() != null ? v.getReviewStatus().name() : null,
                v.getReviewedAt(),
                match
        );
    }
}
