package dev.thilanka.site_ready.service;

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

    @Transactional
    public ReportResponse uploadReport(
            MultipartFile file,
            UploadRequest request,
            User uploader,
            String ipAddress
    ) throws Exception {

        // ── Filename validation ──
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("File must be a PDF.");
        }

        // Pattern: SITEID_PROJECT_Vn.pdf (case-insensitive version suffix)
        String nameWithoutExt = filename.replaceAll("(?i)\\.pdf$", "");
        String[] parts = nameWithoutExt.split("_");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Filename must follow the convention: SITEID_PROJECT_Vn.pdf  (e.g. KY0001_4G-upgrade-26_V1.pdf)");
        }

        String versionPart = parts[parts.length - 1]; // last segment is Vn
        if (!versionPart.matches("(?i)V\\d+")) {
            throw new IllegalArgumentException(
                    "Filename version suffix is invalid. Expected format: V1, V2, … Got: " + versionPart);
        }

        int fileVersion = Integer.parseInt(versionPart.substring(1));
        String namingKey = Report.buildNamingKey(request.siteId(), request.project());

        if (reportRepository.existsByNamingKey(namingKey)) {
            Report existing = reportRepository.findByNamingKey(namingKey).orElseThrow();
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
                    .namingKey(namingKey)
                    .createdByVendor(uploader)
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
                .uploadedBy(uploader)
                .statusAtUpload(ReportStatus.PENDING_REVIEW)
                .build();

        // Save report first so version can reference it
        report.setCurrentVersion(versionNumber);
        report.setCurrentStatus(ReportStatus.PENDING_REVIEW);
        report.setCurrentResponsibility(Responsibility.ENGINEER);
        reportRepository.save(report);
        version = versionRepository.save(version);

        // Stamp and sign the PDF
        try {
//            PdfStampService.StampResult stamp = pdfStampService.stampAndSign(originalBytes, uploader, version, sha256);
            List<ReportVersion> allVersions = versionRepository.findByReportIdOrderByVersionNumberDesc(report.getId());
            PdfStampService.StampResult stamp = pdfStampService.stampAndSign(originalBytes, uploader, version, sha256, allVersions);
            String stampedKey = storageService.buildKey(namingKey, versionNumber, "stamped");
            storageService.upload(stampedKey, stamp.signedBytes(), "application/pdf");
            version.setStampedStorageKey(stampedKey);
            version.setPadesSignatureId(stamp.signatureId());
            versionRepository.save(version);
        } catch (Exception e) {
            log.error("PDF stamping failed for version {}: {}", version.getId(), e.getMessage());
            // Don't fail the upload — log and continue without stamp (investigate separately)
        }

        auditService.log(version, uploader, AuditAction.UPLOADED, ipAddress,
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

        // Store on the version entity so it's retrievable
        version.setReviewedStorageKey(reviewKey);

        version.setReviewStatus(newStatus);
        version.setReviewerNotes(request.notes());
        version.setConditions(request.conditions());
        version.setReviewedBy(engineer);
        version.setReviewedAt(OffsetDateTime.now());

        // Stamp engineer's reviewed PDF as well
        try {
            List<ReportVersion> allVersions = versionRepository.findByReportIdOrderByVersionNumberDesc(report.getId());
            PdfStampService.StampResult stamp = pdfStampService.stampAndSign(reviewBytes, engineer, version, sha256, allVersions);
            String stampedReviewKey = storageService.buildKey(report.getNamingKey(), currentVer, "reviewed-stamped");
            storageService.upload(stampedReviewKey, stamp.signedBytes(), "application/pdf");
            version.setReviewedStorageKey(stampedReviewKey);   // serve the stamped version

        } catch (Exception e) {
            log.warn("Stamping reviewed PDF failed: {}", e.getMessage());
            // fall back to un-stamped but still store it
            version.setReviewedStorageKey(reviewKey);
        }

        // Update workflow state
        report.setCurrentStatus(newStatus);
        report.setCurrentResponsibility(resolveResponsibility(newStatus));
        versionRepository.save(version);
        reportRepository.save(report);

        AuditAction auditAction = switch (newStatus) {
            case APPROVED -> AuditAction.APPROVED;
            case CONDITIONALLY_APPROVED -> AuditAction.CONDITIONALLY_APPROVED;
            case REJECTED -> AuditAction.REJECTED;
            case RESUBMISSION_REQUIRED -> AuditAction.RESUBMISSION_REQUIRED;
            default -> throw new IllegalStateException("Unexpected status: " + newStatus);
        };
        auditService.log(version, engineer, auditAction, ipAddress,
                "decision", newStatus.name(), "notes", request.notes());

        return ReportResponse.from(report, version);
    }

    public byte[] downloadStampedPdf(UUID reportId, int version, User requestingUser, String ipAddress) {
        Report report = reportRepository.findById(reportId).orElseThrow();
        ReportVersion rv = versionRepository.findByReportIdAndVersionNumber(reportId, version).orElseThrow();
        String key = rv.getStampedStorageKey() != null ? rv.getStampedStorageKey() : rv.getOriginalStorageKey();
        auditService.log(rv, requestingUser, AuditAction.DOWNLOADED, ipAddress);
        return storageService.download(key);
    }

    public Page<Report> getMyReports(User user, Pageable pageable) {
        if (user.getRole() == UserRole.ENGINEER) {
            return reportRepository.findByAssignedEngineerId(user.getId(), pageable);
        }
        return reportRepository.findByCreatedByVendorId(user.getId(), pageable);
    }

//    public Page<Report> search(String siteId, String project, ReportStatus status,
//                               UUID engineerId, UUID vendorId, Pageable pageable) {
//        return reportRepository.search(
//                blank(siteId), blank(project), status, engineerId, vendorId, pageable);
//    }
//
//    public List<Report> searchAll(String siteId, String project, ReportStatus status,
//                                  UUID engineerId, UUID vendorId) {
//        return reportRepository.searchAll(
//                blank(siteId), blank(project), status, engineerId, vendorId);
//    }


    public Page<Report> search(String siteId, String project, ReportStatus status,
                               UUID engineerId, UUID vendorId, Pageable pageable) {
        return reportRepository.findAll(
                ReportSpecification.filter(siteId, project, status, engineerId, vendorId),
                pageable
        );
    }

    public List<Report> searchAll(String siteId, String project, ReportStatus status,
                                  UUID engineerId, UUID vendorId) {
        return reportRepository.findAll(
                ReportSpecification.filter(siteId, project, status, engineerId, vendorId),
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
}
