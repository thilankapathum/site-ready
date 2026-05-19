package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.dto.ReportResponse;
import dev.thilanka.site_ready.dto.ReviewRequest;

import dev.thilanka.site_ready.dto.UploadRequest;
import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import dev.thilanka.site_ready.service.DocumentService;
import dev.thilanka.site_ready.service.ExportService;
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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ReportResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("siteId") String siteId,
            @RequestPart("project") String project,
            @RequestPart(value = "assignedEngineerId", required = false) String assignedEngineerId,
            @AuthenticationPrincipal User user,
            HttpServletRequest request
    ) throws Exception {
        UUID engId = (assignedEngineerId != null && !assignedEngineerId.isBlank())
                ? UUID.fromString(assignedEngineerId) : null;
        UploadRequest uploadRequest = new UploadRequest(siteId, project, engId);
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
        byte[] data = documentService.downloadStampedPdf(reportId, version, user, getIp(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-V" + version + ".pdf\"")
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
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) UUID engineerId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Report> reports = documentService.search(siteId, project, status, engineerId, vendorId,
                PageRequest.of(page, size, Sort.by("updatedAt").descending()));
        return ResponseEntity.ok(reports.map(r -> ReportResponse.from(r, null)));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) UUID engineerId,
            @RequestParam(required = false) UUID vendorId
    ) throws Exception {
        List<Report> reports = documentService.searchAll(siteId, project, status, engineerId, vendorId);
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
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) UUID engineerId,
            @RequestParam(required = false) UUID vendorId
    ) {
        List<Report> reports = documentService.searchAll(siteId, project, status, engineerId, vendorId);
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

    private String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
