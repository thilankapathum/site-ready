package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.dto.PageDiff;
import dev.thilanka.site_ready.dto.ReportResponse;
import dev.thilanka.site_ready.dto.ReviewRequest;
import dev.thilanka.site_ready.dto.UploadRequest;
import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.AuditAction;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import dev.thilanka.site_ready.entity.enums.Responsibility;
import dev.thilanka.site_ready.entity.enums.UserRole;
import dev.thilanka.site_ready.repository.ReportRepository;
import dev.thilanka.site_ready.repository.ReportVersionRepository;
import dev.thilanka.site_ready.repository.UserRepository;
import dev.thilanka.site_ready.spec.ReportSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final ReportRepository reportRepository;
    private final ReportVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final PdfStampService pdfStampService;
    private final AuditService auditService;
    private final ReportAccessService reportAccessService;
    private final PdfDiffService pdfDiffService;

    @Transactional
    public ReportResponse uploadReport(
            MultipartFile file,
            UploadRequest request,
            User uploader,
            String ipAddress
    ) throws Exception {

        // Re-fetch uploader within this transaction to ensure all lazy associations are loaded
        User managedUploader = userRepository.findById(uploader.getId())
                .orElseThrow(() -> new IllegalArgumentException("Uploader not found"));


        // ── Filename validation ──
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("File must be a PDF.");
        }

        // Updated filename validation — now expects SITEID_PROJECT_RAT_Vn.pdf
        String nameWithoutExt = filename.replaceAll("(?i)\\.pdf$", "");
        String[] parts = nameWithoutExt.split("_");
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "Filename must follow the convention: SITEID_PROJECT_RAT_Vn.pdf " +
                            "(e.g. KY0001_Project1_4G_V1.pdf)");
        }

        String versionPart = parts[parts.length - 1];
        if (!versionPart.matches("(?i)V\\d+")) {
            throw new IllegalArgumentException(
                    "Filename version suffix is invalid. Expected V1, V2, … Got: " + versionPart);
        }

// RAT is the second-to-last segment
        String ratFromFilename = parts[parts.length - 2].toUpperCase();

        if (!ratFromFilename.equals(request.rat().toUpperCase())) {
            throw new IllegalArgumentException(String.format(
                    "RAT mismatch: filename contains '%s' but form field says '%s'. They must match.",
                    ratFromFilename, request.rat()));
        }

        int fileVersion = Integer.parseInt(versionPart.substring(1));
        String namingKey = Report.buildNamingKey(request.siteId(), request.project(), request.rat());

        if (reportRepository.existsByNamingKey(namingKey)) {
            Report existing = reportRepository.findByNamingKey(namingKey).orElseThrow();
            reportAccessService.assertCanUploadNextVersion(existing, managedUploader);

            int expectedVersion = existing.getCurrentVersion() + 1;
            if (fileVersion != expectedVersion) {
                throw new IllegalArgumentException(String.format(
                        "Version mismatch: this report is at V%d, so next upload must be V%d. Filename shows V%d.",
                        existing.getCurrentVersion(), expectedVersion, fileVersion));
            }
            if (existing.getCurrentStatus() != ReportStatus.RESUBMISSION_REQUIRED) {
                throw new IllegalStateException(
                        "Report is not in RESUBMISSION_REQUIRED state. Current status: " + existing.getCurrentStatus());
            }
        } else {
            if (fileVersion != 1) {
                throw new IllegalArgumentException(
                        "First upload for a new Site ID + Project must be V1. Filename shows V" + fileVersion + ".");
            }
        }

        byte[] originalBytes = file.getBytes();
        String sha256 = sha256Hex(originalBytes);

        // Determine or create report record
//        String namingKey = Report.buildNamingKey(request.siteId(), request.project());
        Report report;
        int versionNumber;

        if (reportRepository.existsByNamingKey(namingKey)) {
            report = reportRepository.findByNamingKey(namingKey).orElseThrow();
            versionNumber = report.getCurrentVersion() + 1;
        } else {
            User assignedEngineer = request.assignedEngineerId() != null
                    ? userRepository.findById(request.assignedEngineerId()).orElse(null)
                    : null;
            report = Report.builder()
                    .siteId(request.siteId())
                    .project(request.project())
                    .rat(request.rat())
                    .namingKey(namingKey)
                    .createdByVendor(managedUploader)
                    .vendorCompany(managedUploader.getCompany())
                    .assignedEngineer(assignedEngineer)
                    .currentStatus(ReportStatus.PENDING_REVIEW)
                    .currentVersion(1)
                    .currentResponsibility(Responsibility.ENGINEER)
                    .build();
            versionNumber = 1;
        }

        // Store original PDF
        String originalKey = storageService.buildKey(namingKey, versionNumber, "original");
        storageService.upload(originalKey, originalBytes, "application/pdf");

        // Create version record (needed for stamp — references version.getId())
        ReportVersion version = ReportVersion.builder()
                .report(report)
                .versionNumber(versionNumber)
                .originalFilename(file.getOriginalFilename())
                .originalStorageKey(originalKey)
                .sha256Hash(sha256)
                .uploadedBy(managedUploader)
                .statusAtUpload(ReportStatus.PENDING_REVIEW)
                .build();

        // Save report first so version can reference it
        report.setCurrentVersion(versionNumber);
        report.setCurrentStatus(ReportStatus.PENDING_REVIEW);
        report.setCurrentResponsibility(Responsibility.ENGINEER);
        reportRepository.save(report);
        version = versionRepository.save(version);

        // Compute page diff against previous version's original
        PageDiff pageDiff;
        if (versionNumber == 1) {
            pageDiff = PageDiff.firstVersion(countPages(originalBytes));
        } else {
            ReportVersion previousVersion = versionRepository
                    .findByReportIdAndVersionNumber(report.getId(), versionNumber - 1)
                    .orElse(null);
            if (previousVersion != null) {
                try {
                    byte[] previousOriginal = storageService.download(
                            previousVersion.getOriginalStorageKey());

                    // Per workflow: vendor always works from engineer's reviewed file.
                    // Use reviewed-stamped as annotation baseline so engineer's markups
                    // on V(n-1) are correctly subtracted from V(n)'s annotation count.
                    byte[] engineerReviewed = null;
                    if (previousVersion.getReviewedStorageKey() != null) {
                        try {
                            engineerReviewed = storageService.download(
                                    previousVersion.getReviewedStorageKey());
                        } catch (Exception ex) {
                            log.debug("Could not fetch engineer reviewed file for baseline: {}",
                                    ex.getMessage());
                        }
                    }

                    // If engineer added pages to their reviewed file, use that as the
                    // "previous" for page count comparison, not the vendor's original.
                    // This way, pages the engineer added are not flagged as vendor additions.
                    byte[] previousForPageCount = engineerReviewed != null
                            ? engineerReviewed : previousOriginal;

                    pageDiff = pdfDiffService.diff(previousForPageCount, originalBytes, engineerReviewed);

                } catch (Exception e) {
                    log.warn("Page diff failed, continuing without diff: {}", e.getMessage());
                    pageDiff = PageDiff.firstVersion(countPages(originalBytes));
                }
            } else {
                pageDiff = PageDiff.firstVersion(countPages(originalBytes));
            }
        }

// Store diff on version
        version.setPageDiff(pageDiff);
        versionRepository.save(version);

// Stamp and sign
        try {
            List<ReportVersion> allVersions = versionRepository
                    .findByReportIdOrderByVersionNumberDesc(report.getId());
            PdfStampService.StampResult stamp = pdfStampService.stampAndSign(
                    originalBytes, managedUploader, version, sha256, allVersions, pageDiff);
            String stampedKey = storageService.buildKey(namingKey, versionNumber, "stamped");
            storageService.upload(stampedKey, stamp.signedBytes(), "application/pdf");
            version.setStampedStorageKey(stampedKey);
            version.setPadesSignatureId(stamp.signatureId());
            versionRepository.save(version);
        } catch (Exception e) {
            log.error("PDF stamping failed for version {}: {}", version.getId(), e.getMessage());
            // Don't fail the upload — log and continue without stamp (investigate separately)
        }

        auditService.log(version, managedUploader, AuditAction.UPLOADED, ipAddress,
                "version", String.valueOf(versionNumber), "namingKey", namingKey);

        return ReportResponse.from(report, version);
    }

    @Transactional
    public ReportResponse reviewReport(
            UUID reportId,
            MultipartFile reviewedFile,
            ReviewRequest request,
            User engineer,
            String ipAddress
    ) throws Exception {

        // Re-fetch within transaction
        User managedEngineer = userRepository.findById(engineer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Engineer not found"));


        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        if (report.getCurrentStatus() != ReportStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Report is not pending review");
        }

        // Validate transition
        ReportStatus newStatus = request.decision();
        validateTransition(newStatus);

        // Store the engineer's reviewed PDF (with their markups) as original for this review
        byte[] reviewBytes = reviewedFile.getBytes();
        String sha256 = sha256Hex(reviewBytes);
        int currentVer = report.getCurrentVersion();
        String reviewKey = storageService.buildKey(report.getNamingKey(), currentVer, "reviewed-by-engineer");
        storageService.upload(reviewKey, reviewBytes, "application/pdf");


        // Get the current version to update
        ReportVersion version = versionRepository
                .findByReportIdAndVersionNumber(reportId, currentVer)
                .orElseThrow();

        version.setReviewedStorageKey(reviewKey);
        version.setReviewStatus(newStatus);
        version.setReviewerNotes(request.notes());
        version.setConditions(request.conditions());
        version.setReviewedBy(managedEngineer);
        version.setReviewedAt(OffsetDateTime.now());

// ── Update report status BEFORE stamping so the audit page reflects the new status ──
        report.setCurrentStatus(newStatus);
        report.setCurrentResponsibility(resolveResponsibility(newStatus));
        reportRepository.save(report);    // persist so version.getReport() returns updated state

        // Compute engineer diff: compare vendor's stamped upload vs engineer's reviewed file
        PageDiff engineerDiff = null;
        try {
            // Per workflow: engineer always works from vendor's stamped file.
            // Use stamped (not original) as baseline — it includes the audit page
            // which stripAuditPages() will remove before comparison.
            String vendorBaselineKey = version.getStampedStorageKey() != null
                    ? version.getStampedStorageKey()
                    : version.getOriginalStorageKey();
            byte[] vendorBaseline = storageService.download(vendorBaselineKey);

            // No third baseline needed — engineer is the first actor on this version
            engineerDiff = pdfDiffService.diff(vendorBaseline, reviewBytes);
        } catch (Exception e) {
            log.warn("Engineer page diff failed: {}", e.getMessage());
        }
        version.setReviewerPageDiff(engineerDiff);

// Stamp reviewed PDF
        try {
            List<ReportVersion> allVersions = versionRepository
                    .findByReportIdOrderByVersionNumberDesc(report.getId());
            PdfStampService.StampResult stamp = pdfStampService.stampAndSign(
                    reviewBytes, managedEngineer, version, sha256, allVersions, engineerDiff);
            String stampedReviewKey = storageService.buildKey(
                    report.getNamingKey(), currentVer, "reviewed-stamped");
            storageService.upload(stampedReviewKey, stamp.signedBytes(), "application/pdf");
            version.setReviewedStorageKey(stampedReviewKey);
        } catch (Exception e) {
            log.warn("Stamping reviewed PDF failed: {}", e.getMessage());
        }

        versionRepository.save(version);

// Report already saved above — no need to save again unless something else changed
// Remove the duplicate report save that was here before

        AuditAction auditAction = switch (newStatus) {
            case APPROVED -> AuditAction.APPROVED;
            case CONDITIONALLY_APPROVED -> AuditAction.CONDITIONALLY_APPROVED;
            case REJECTED -> AuditAction.REJECTED;
            case RESUBMISSION_REQUIRED -> AuditAction.RESUBMISSION_REQUIRED;
            default -> throw new IllegalStateException("Unexpected status: " + newStatus);
        };
        auditService.log(version, managedEngineer, auditAction, ipAddress,
                "decision", newStatus.name(), "notes", request.notes());

        return ReportResponse.from(report, version);
    }

    public byte[] downloadStampedPdf(UUID reportId, int versionNumber, User requestingUser, String ipAddress) {
        Report report = reportRepository.findById(reportId).orElseThrow();
        ReportVersion rv = versionRepository
                .findByReportIdAndVersionNumber(reportId, versionNumber)
                .orElseThrow();

        // Priority: reviewed-stamped > vendor-stamped > original
        // The reviewed-stamped is the most up-to-date file in the approval chain
        String key;
        if (rv.getReviewedStorageKey() != null) {
            key = rv.getReviewedStorageKey();
        } else if (rv.getStampedStorageKey() != null) {
            key = rv.getStampedStorageKey();
        } else {
            key = rv.getOriginalStorageKey();
        }

        auditService.log(rv, requestingUser, AuditAction.DOWNLOADED, ipAddress);
        return storageService.download(key);
    }


    public Page<Report> getMyReports(User user, Pageable pageable) {
        if (user.getRole() == UserRole.ENGINEER) {
            return reportRepository.findByAssignedEngineerId(user.getId(), pageable);
        }
        return reportRepository.findByCreatedByVendorId(user.getId(), pageable);
    }

    public Page<Report> search(String siteId, String project, String rat,
                               ReportStatus status, UUID engineerId,
                               UUID vendorId, UUID vendorCompanyId, Pageable pageable) {
        return reportRepository.findAll(
                ReportSpecification.filter(siteId, project, rat, status, engineerId, vendorId, vendorCompanyId),
                pageable
        );
    }

    public Page<Report> search(String siteId, String project, String rat,
                               ReportStatus status, List<UUID> engineerIds,
                               UUID vendorId, UUID vendorCompanyId, Pageable pageable) {
        return reportRepository.findAll(
                ReportSpecification.filter(siteId, project, rat, status, engineerIds, vendorId, vendorCompanyId),
                pageable
        );
    }

    public List<Report> searchAll(String siteId, String project, String rat,
                                  ReportStatus status, UUID engineerId,
                                  UUID vendorId, UUID vendorCompanyId) {
        return reportRepository.findAll(
                ReportSpecification.filter(siteId, project, rat, status, engineerId, vendorId, vendorCompanyId),
                Sort.by("siteId").ascending().and(Sort.by("project").ascending())
        );
    }

    // --- Helpers ---

    private void validateTransition(ReportStatus status) {
        if (!List.of(ReportStatus.APPROVED, ReportStatus.CONDITIONALLY_APPROVED,
                ReportStatus.REJECTED, ReportStatus.RESUBMISSION_REQUIRED).contains(status)) {
            throw new IllegalArgumentException("Invalid review decision: " + status);
        }
    }

    private Responsibility resolveResponsibility(ReportStatus status) {
        return switch (status) {
            case APPROVED, CONDITIONALLY_APPROVED, REJECTED -> Responsibility.CLOSED;
            case RESUBMISSION_REQUIRED -> Responsibility.VENDOR;
            default -> Responsibility.ENGINEER;
        };
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }

    private String blank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private int countPages(byte[] pdfBytes) {
        try (com.itextpdf.kernel.pdf.PdfReader reader =
                     new com.itextpdf.kernel.pdf.PdfReader(new java.io.ByteArrayInputStream(pdfBytes));
             com.itextpdf.kernel.pdf.PdfDocument doc =
                     new com.itextpdf.kernel.pdf.PdfDocument(reader)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            return 0;
        }
    }
}
