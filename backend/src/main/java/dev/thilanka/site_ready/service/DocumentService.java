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
import java.util.Arrays;
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
    private final AuditWorksheetService auditWorksheetService;
    private final AuditService auditService;
    private final ReportAccessService reportAccessService;
    private final PdfDiffService pdfDiffService;

    /** File types accepted for report uploads and reviews. */
    private enum DocType {
        PDF(".pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F'}),
        XLSX(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B, 0x03, 0x04});

        private final String extension;
        private final String contentType;
        private final byte[] magicBytes;

        DocType(String extension, String contentType, byte[] magicBytes) {
            this.extension = extension;
            this.contentType = contentType;
            this.magicBytes = magicBytes;
        }

        String extension() { return extension; }
        String contentType() { return contentType; }
    }

    /**
     * Validates the uploaded file's extension against the supported types and sniffs
     * its magic bytes to catch a mismatched/spoofed extension — trusting the client-supplied
     * extension alone would let a renamed file bypass the upload pipeline entirely.
     */
    private DocType detectAndValidate(String filename, byte[] bytes) {
        if (filename == null) {
            throw new IllegalArgumentException("File must be a PDF or Excel (.xlsx) file.");
        }
        String lower = filename.toLowerCase();
        DocType type = Arrays.stream(DocType.values())
                .filter(t -> lower.endsWith(t.extension()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "File must be a PDF or Excel (.xlsx) file."));
        if (bytes.length < type.magicBytes.length
                || !Arrays.equals(bytes, 0, type.magicBytes.length,
                        type.magicBytes, 0, type.magicBytes.length)) {
            throw new IllegalArgumentException(
                    "File extension is " + type.extension() + " but its content does not match a valid file.");
        }
        return type;
    }

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


        // ── File-type detection & validation ──
        String filename = file.getOriginalFilename();
        byte[] originalBytes = file.getBytes();
        DocType docType = detectAndValidate(filename, originalBytes);

        // Filename convention: SITEID_PROJECT_RAT_Vn.<ext>
        String nameWithoutExt = filename.substring(0, filename.length() - docType.extension().length());
        String[] parts = nameWithoutExt.split("_");
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "Filename must follow the convention: SITEID_PROJECT_RAT_Vn" + docType.extension() +
                            " (e.g. KY0001_Project1_4G_V1" + docType.extension() + ")");
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

        if (reportRepository.existsByNamingKeyAndDeletedFalse(namingKey)) {
            Report existing = reportRepository.findByNamingKeyAndDeletedFalse(namingKey).orElseThrow();
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

        String sha256 = sha256Hex(originalBytes);

        // Determine or create report record
//        String namingKey = Report.buildNamingKey(request.siteId(), request.project());
        Report report;
        int versionNumber;

        if (reportRepository.existsByNamingKeyAndDeletedFalse(namingKey)) {
            report = reportRepository.findByNamingKeyAndDeletedFalse(namingKey).orElseThrow();
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

        // Save report first so it has an id (used to key storage objects) and version can reference it
        report.setCurrentVersion(versionNumber);
        report.setCurrentStatus(ReportStatus.PENDING_REVIEW);
        report.setCurrentResponsibility(Responsibility.ENGINEER);
        reportRepository.save(report);

        // Store original file
        String originalKey = storageService.buildKey(report.getId(), versionNumber, "original", docType.extension());
        storageService.upload(originalKey, originalBytes, docType.contentType());

        // Create version record (needed for stamp — references version.getId())
        ReportVersion version = ReportVersion.builder()
                .report(report)
                .versionNumber(versionNumber)
                .originalFilename(file.getOriginalFilename())
                .originalStorageKey(originalKey)
                .contentType(docType.contentType())
                .sha256Hash(sha256)
                .uploadedBy(managedUploader)
                .statusAtUpload(ReportStatus.PENDING_REVIEW)
                .build();

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

// Stamp and sign (PDF) or append audit worksheet (xlsx — no cryptographic signature equivalent)
        try {
            if (docType == DocType.PDF) {
                List<ReportVersion> allVersions = versionRepository
                        .findByReportIdOrderByVersionNumberDesc(report.getId());
                PdfStampService.StampResult stamp = pdfStampService.stampAndSign(
                        originalBytes, managedUploader, version, sha256, allVersions, pageDiff);
                String stampedKey = storageService.buildKey(report.getId(), versionNumber, "stamped", docType.extension());
                storageService.upload(stampedKey, stamp.signedBytes(), docType.contentType());
                version.setStampedStorageKey(stampedKey);
                version.setPadesSignatureId(stamp.signatureId());
            } else {
                byte[] audited = auditWorksheetService.appendAuditSheet(
                        originalBytes, managedUploader, version, sha256);
                String stampedKey = storageService.buildKey(report.getId(), versionNumber, "stamped", docType.extension());
                storageService.upload(stampedKey, audited, docType.contentType());
                version.setStampedStorageKey(stampedKey);
            }
            versionRepository.save(version);
        } catch (Exception e) {
            log.error("Audit stamping failed for version {}", version.getId(), e);
            // Don't fail the upload — log and continue without stamp; download will retry lazily
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

        // Get the current version to update
        int currentVer = report.getCurrentVersion();
        ReportVersion version = versionRepository
                .findByReportIdAndVersionNumber(reportId, currentVer)
                .orElseThrow();

        // Store the engineer's reviewed file (with their markups) as original for this review.
        // Must be the same file type as the vendor's upload for this version.
        byte[] reviewBytes = reviewedFile.getBytes();
        DocType docType = detectAndValidate(reviewedFile.getOriginalFilename(), reviewBytes);
        if (!docType.contentType().equals(version.getContentType())) {
            throw new IllegalArgumentException(
                    "Reviewed file must be the same file type as the vendor's upload for this version.");
        }
        String sha256 = sha256Hex(reviewBytes);
        String reviewKey = storageService.buildKey(report.getId(), currentVer, "reviewed-by-engineer", docType.extension());
        storageService.upload(reviewKey, reviewBytes, docType.contentType());

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

// Stamp reviewed file (PDF) or append audit worksheet (xlsx)
        try {
            String stampedReviewKey = storageService.buildKey(
                    report.getId(), currentVer, "reviewed-stamped", docType.extension());
            if (docType == DocType.PDF) {
                List<ReportVersion> allVersions = versionRepository
                        .findByReportIdOrderByVersionNumberDesc(report.getId());
                PdfStampService.StampResult stamp = pdfStampService.stampAndSign(
                        reviewBytes, managedEngineer, version, sha256, allVersions, engineerDiff);
                storageService.upload(stampedReviewKey, stamp.signedBytes(), docType.contentType());
            } else {
                byte[] audited = auditWorksheetService.appendAuditSheet(
                        reviewBytes, managedEngineer, version, sha256);
                storageService.upload(stampedReviewKey, audited, docType.contentType());
            }
            version.setReviewedStorageKey(stampedReviewKey);
        } catch (Exception e) {
            log.error("Stamping reviewed file failed for version {}", version.getId(), e);
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

    @Transactional
    public byte[] downloadStampedPdf(UUID reportId, int versionNumber, User requestingUser, String ipAddress) {
        ReportVersion rv = versionRepository
                .findByReportIdAndVersionNumber(reportId, versionNumber)
                .orElseThrow();

        // Priority: reviewed-stamped > vendor-stamped > original
        // The reviewed-stamped is the most up-to-date file in the approval chain
        byte[] data = resolveStamped(rv, rv.getReviewedStorageKey() != null);

        auditService.log(rv, requestingUser, AuditAction.DOWNLOADED, ipAddress);
        return data;
    }

    @Transactional
    public byte[] downloadVersionStamped(UUID versionId, User requestingUser, String ipAddress) {
        ReportVersion rv = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        byte[] data = resolveStamped(rv, false);
        auditService.log(rv, requestingUser, AuditAction.DOWNLOADED, ipAddress);
        return data;
    }

    /** Returns null if the version has not been reviewed yet. */
    @Transactional
    public byte[] downloadReviewedStamped(UUID versionId, User requestingUser, String ipAddress) {
        ReportVersion rv = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        if (rv.getReviewedStorageKey() == null) {
            return null;
        }
        byte[] data = resolveStamped(rv, true);
        auditService.log(rv, requestingUser, AuditAction.DOWNLOADED, ipAddress);
        return data;
    }

    /**
     * Returns the stamped bytes for a version's vendor-upload or review file.
     * If the stored key points at an unstamped file (a prior stamping attempt failed
     * or never ran), stamps it now on demand, persists the result, and updates the
     * version so future downloads and the audit trail stay consistent.
     */
    private byte[] resolveStamped(ReportVersion rv, boolean review) {
        DocType docType = DocType.PDF.contentType().equals(rv.getContentType()) ? DocType.PDF : DocType.XLSX;
        String key = review ? rv.getReviewedStorageKey() : rv.getStampedStorageKey();
        if (key == null && !review) {
            key = rv.getOriginalStorageKey();
        }
        if (key != null && key.endsWith("stamped" + docType.extension())) {
            return storageService.download(key);
        }

        try {
            byte[] source = storageService.download(key);
            User actor = review ? rv.getReviewedBy() : rv.getUploadedBy();
            PageDiff diff = review ? rv.getReviewerPageDiff() : rv.getPageDiff();
            byte[] resultBytes;
            if (docType == DocType.PDF) {
                List<ReportVersion> allVersions = versionRepository
                        .findByReportIdOrderByVersionNumberDesc(rv.getReport().getId());
                PdfStampService.StampResult stamp = pdfStampService.stampAndSign(
                        source, actor, rv, rv.getSha256Hash(), allVersions, diff);
                resultBytes = stamp.signedBytes();
            } else {
                resultBytes = auditWorksheetService.appendAuditSheet(
                        source, actor, rv, rv.getSha256Hash());
            }
            String newKey = storageService.buildKey(
                    rv.getReport().getId(), rv.getVersionNumber(),
                    review ? "reviewed-stamped" : "stamped", docType.extension());
            storageService.upload(newKey, resultBytes, docType.contentType());
            if (review) {
                rv.setReviewedStorageKey(newKey);
            } else {
                rv.setStampedStorageKey(newKey);
            }
            versionRepository.save(rv);
            return resultBytes;
        } catch (Exception e) {
            log.warn("On-demand stamping failed for version {}, serving unstamped file", rv.getId(), e);
            return storageService.download(key);
        }
    }


    public Page<Report> getMyReports(User user, Pageable pageable) {
        if (user.getRole() == UserRole.ENGINEER) {
            return reportRepository.findByAssignedEngineerIdAndDeletedFalse(user.getId(), pageable);
        }
        return reportRepository.findByCreatedByVendorIdAndDeletedFalse(user.getId(), pageable);
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
