package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.dto.ReportResponse;
import dev.thilanka.site_ready.dto.ReviewRequest;

import dev.thilanka.site_ready.dto.UploadRequest;
import dev.thilanka.site_ready.dto.VersionResponse;
import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.AuditAction;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import dev.thilanka.site_ready.entity.enums.UserRole;
import dev.thilanka.site_ready.repository.ReportRepository;
import dev.thilanka.site_ready.repository.ReportVersionRepository;
import dev.thilanka.site_ready.repository.UserRepository;
import dev.thilanka.site_ready.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ExportService exportService;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReportVersionRepository versionRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final ReportAccessService reportAccessService;
    private final ManagerService managerService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ReportResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("siteId") String siteId,
            @RequestPart("project") String project,
            @RequestPart("rat") String rat,
            @RequestPart(value = "assignedEngineerId", required = false) String assignedEngineerId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) throws Exception {
        UUID engId = (assignedEngineerId != null && !assignedEngineerId.isBlank())
                ? UUID.fromString(assignedEngineerId) : null;
        UploadRequest uploadRequest = new UploadRequest(siteId, project, rat.toUpperCase(), engId);
        ReportResponse response = documentService.uploadReport(file, uploadRequest, user, getIp(request));
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{reportId}/review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<ReportResponse> review(
            @PathVariable UUID reportId,
            @RequestPart("file") MultipartFile reviewedFile,
            @RequestPart("decision") String decision,
            @RequestPart(value = "notes", required = false) String notes,
            @RequestPart(value = "conditions", required = false) String conditions,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) throws Exception {
        ReviewRequest reviewRequest = new ReviewRequest(
                ReportStatus.valueOf(decision), notes, conditions);
        ReportResponse response = documentService.reviewReport(reportId, reviewedFile, reviewRequest, user, getIp(request));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{reportId}/download/{version}")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID reportId,
            @PathVariable int version,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        reportAccessService.assertCanRead(reportId, user);
        byte[] data = documentService.downloadStampedPdf(reportId, version, user, getIp(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report-V" + version + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    @GetMapping("/my")
    public ResponseEntity<Page<ReportResponse>> myReports(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Report> reports = documentService.getMyReports(user,
                PageRequest.of(page, size, Sort.by("updatedAt").descending()));
        return ResponseEntity.ok(reports.map(r -> ReportResponse.from(r, null)));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ReportResponse>> search(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String rat,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) UUID engineerId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        // Vendors can only see their own company's reports — enforce this server-side
        UUID vendorCompanyId = null;
        if (currentUser.getRole() == UserRole.VENDOR) {
            vendorCompanyId = currentUser.getCompany().getId();
        }

        Page<Report> reports = documentService.search(
                siteId, project, rat, status, engineerId, vendorId, vendorCompanyId,
                PageRequest.of(page, size, Sort.by("updatedAt").descending()));
        return ResponseEntity.ok(reports.map(r -> ReportResponse.from(r, null)));
    }


    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String rat,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) UUID engineerId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) UUID vendorCompanyId,
            @AuthenticationPrincipal User currentUser
    ) throws Exception {

        UUID derivedVendorCompanyId = currentUser.getRole() == UserRole.VENDOR
                ? currentUser.getCompany().getId() : null;

        List<Report> reports = documentService.searchAll(siteId, project, rat, status, engineerId, vendorId, derivedVendorCompanyId);
        byte[] data = exportService.exportExcel(reports);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ssv-reports.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String rat,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) UUID engineerId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) UUID vendorCompanyId,
            @AuthenticationPrincipal User currentUser
    ) {
        UUID derivedVendorCompanyId = currentUser.getRole() == UserRole.VENDOR
                ? currentUser.getCompany().getId() : null;
        List<Report> reports = documentService.searchAll(siteId, project, rat, status, engineerId, vendorId, derivedVendorCompanyId);
        byte[] data = exportService.exportCsv(reports);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ssv-reports.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    // Public verification endpoint — used by QR codes in stamped PDFs
    @GetMapping("/verify/{versionId}")
    public ResponseEntity<String> verify(@PathVariable UUID versionId) {
        // Returns basic tamper-check info — extend as needed
        return ResponseEntity.ok("Verification endpoint for version: " + versionId);
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ReportResponse> getReport(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal User user
    ) {
        reportAccessService.assertCanRead(reportId, user);
        Report report = reportRepository.findById(reportId).orElseThrow();
        ReportVersion latest = versionRepository
                .findByReportIdOrderByVersionNumberDesc(reportId)
                .stream().findFirst().orElse(null);
        return ResponseEntity.ok(ReportResponse.from(report, latest));
    }

    @GetMapping("/{reportId}/versions")
    public ResponseEntity<List<VersionResponse>> getVersions(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal User user
    ) {
        reportAccessService.assertCanRead(reportId, user);
        List<ReportVersion> versions = versionRepository
                .findByReportIdOrderByVersionNumberDesc(reportId);
        return ResponseEntity.ok(versions.stream().map(VersionResponse::from).toList());
    }

    @GetMapping("/{reportId}/download-version/{versionId}")
    public ResponseEntity<byte[]> downloadVersion(
            @PathVariable UUID reportId,
            @PathVariable UUID versionId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        reportAccessService.assertCanRead(reportId, user);
        ReportVersion rv = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        byte[] data = documentService.downloadVersionStamped(versionId, user, getIp(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + rv.getReport().getNamingKey() + "_V" + rv.getVersionNumber() + "_stamped.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }


    @PatchMapping("/{reportId}/assign-engineer")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ENGINEER') or hasRole('VENDOR')")
    public ResponseEntity<ReportResponse> assignEngineer(
            @PathVariable UUID reportId,
            @RequestParam(required = false) UUID engineerId,
            @AuthenticationPrincipal User user
    ) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        // Vendors can only assign engineers to their own reports
        if (user.getRole() == UserRole.VENDOR) {
            reportAccessService.assertCanRead(report, user);
            // Only allow assignment if version is 1
            // AND if the report is still pending (not yet reviewed)
            if (report.getCurrentStatus() != ReportStatus.PENDING_REVIEW) {
                throw new IllegalStateException(
                        "Engineer can only be reassigned while the report is pending review.");
            }
            if (report.getCurrentVersion() != 1) {
                throw new IllegalStateException(
                        "Engineer can only be reassigned before assigned engineer reviews V1.");
            }
        }

        User engineer = engineerId != null
                ? userRepository.findById(engineerId)
                .orElseThrow(() -> new IllegalArgumentException("Engineer not found"))
                : null;
        report.setAssignedEngineer(engineer);
        reportRepository.save(report);
        System.out.println("4");

        ReportVersion latest = versionRepository
                .findByReportIdOrderByVersionNumberDesc(reportId)
                .stream().findFirst().orElse(null);
        System.out.println("5");
        return ResponseEntity.ok(ReportResponse.from(report, latest));
    }



    @GetMapping("/{reportId}/download-version/{versionId}/reviewed")
    public ResponseEntity<byte[]> downloadReviewedVersion(
            @PathVariable UUID reportId,
            @PathVariable UUID versionId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) {
        reportAccessService.assertCanRead(reportId, user);
        ReportVersion rv = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        byte[] data = documentService.downloadReviewedStamped(versionId, user, getIp(request));
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + rv.getReport().getNamingKey()
                                + "_V" + rv.getVersionNumber() + "_reviewed.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    @GetMapping("/queue/search")
    public ResponseEntity<Page<ReportResponse>> searchReviewQueue(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        List<UUID> targetEngineerIds = null;

        // Isolate scope natively based on who is logged in
        if (currentUser.getRole() == UserRole.ENGINEER) {
            targetEngineerIds = List.of(currentUser.getId());
        } else if (currentUser.getRole() == UserRole.MANAGER || currentUser.getRole() == UserRole.ADMIN) {
            targetEngineerIds = managerService.getEngineerIdsForManager(currentUser.getId());
            if (targetEngineerIds.isEmpty()) {
                return ResponseEntity.ok(Page.empty(PageRequest.of(page, size)));
            }
        }

        UUID vendorCompanyId = null;
        if (currentUser.getRole() == UserRole.VENDOR) {
            vendorCompanyId = currentUser.getCompany().getId();
        }

        // Call documentService using the collection-based engineer criteria
        Page<Report> reports = documentService.search(
                null, null, null, status, targetEngineerIds, null, vendorCompanyId,
                PageRequest.of(page, size, Sort.by("updatedAt").descending()));

        return ResponseEntity.ok(reports.map(r -> ReportResponse.from(r, null)));
    }

    private String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
