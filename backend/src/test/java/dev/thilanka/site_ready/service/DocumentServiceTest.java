package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.dto.ReportResponse;
import dev.thilanka.site_ready.dto.ReviewRequest;
import dev.thilanka.site_ready.dto.UploadRequest;
import dev.thilanka.site_ready.entity.Company;
import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.CompanyType;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import dev.thilanka.site_ready.entity.enums.UserRole;
import dev.thilanka.site_ready.repository.ReportRepository;
import dev.thilanka.site_ready.repository.ReportVersionRepository;
import dev.thilanka.site_ready.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ReportVersionRepository versionRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @Mock private PdfStampService pdfStampService;
    @Mock private AuditWorksheetService auditWorksheetService;
    @Mock private AuditService auditService;
    @Mock private ReportAccessService reportAccessService;
    @Mock private PdfDiffService pdfDiffService;

    private DocumentService documentService;
    private User uploader;

    private static final byte[] XLSX_MAGIC = {0x50, 0x4B, 0x03, 0x04, 0, 0};
    private static final byte[] PDF_MAGIC = "%PDF-1.7 fake content".getBytes();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        documentService = new DocumentService(
                reportRepository, versionRepository, userRepository, storageService,
                pdfStampService, auditWorksheetService, auditService, reportAccessService, pdfDiffService);

        Company company = Company.builder().id(UUID.randomUUID()).name("Acme").shortName("ACME")
                .type(CompanyType.VENDOR).build();
        uploader = User.builder().id(UUID.randomUUID()).fullName("Jane Vendor").company(company)
                .role(UserRole.VENDOR).build();

        when(userRepository.findById(uploader.getId())).thenReturn(Optional.of(uploader));
        when(reportRepository.existsByNamingKeyAndDeletedFalse(anyString())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(versionRepository.save(any(ReportVersion.class))).thenAnswer(inv -> {
            ReportVersion v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });
        when(storageService.buildKey(any(), anyInt(), anyString(), anyString()))
                .thenAnswer(inv -> String.format("reports/%s/V%d/%s%s",
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
    }

    @Test
    void uploadReport_xlsxWithValidMagicBytes_succeeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "KY0001_Proj1_4G_V1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", XLSX_MAGIC);
        UploadRequest request = new UploadRequest("KY0001", "Proj1", "4G", null);
        when(auditWorksheetService.appendAuditSheet(any(), any(), any(), any())).thenReturn(new byte[]{1});

        ReportResponse response = documentService.uploadReport(file, request, uploader, "127.0.0.1");

        assertEquals("KY0001_Proj1_4G", response.namingKey());
        assertNull(response.padesSignatureId());
        verify(auditWorksheetService).appendAuditSheet(any(), any(), any(), any());
        verify(pdfStampService, never()).stampAndSign(any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadReport_xlsxExtensionWithNonZipContent_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "KY0001_Proj1_4G_V1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not a zip".getBytes());
        UploadRequest request = new UploadRequest("KY0001", "Proj1", "4G", null);

        assertThrows(IllegalArgumentException.class, () ->
                documentService.uploadReport(file, request, uploader, "127.0.0.1"));
    }

    @Test
    void uploadReport_unsupportedExtension_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "KY0001_Proj1_4G_V1.docx",
                "application/msword", PDF_MAGIC);
        UploadRequest request = new UploadRequest("KY0001", "Proj1", "4G", null);

        assertThrows(IllegalArgumentException.class, () ->
                documentService.uploadReport(file, request, uploader, "127.0.0.1"));
    }

    @Test
    void uploadReport_pdfWithValidMagicBytes_stillSucceeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "KY0001_Proj1_4G_V1.pdf", "application/pdf", PDF_MAGIC);
        UploadRequest request = new UploadRequest("KY0001", "Proj1", "4G", null);
        when(pdfStampService.stampAndSign(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PdfStampService.StampResult(new byte[]{1}, "SSV-ABC123"));
        when(versionRepository.findByReportIdOrderByVersionNumberDesc(any()))
                .thenReturn(List.of());

        ReportResponse response = documentService.uploadReport(file, request, uploader, "127.0.0.1");

        assertEquals("SSV-ABC123", response.padesSignatureId());
        verify(auditWorksheetService, never()).appendAuditSheet(any(), any(), any(), any());
    }

    @Test
    void reviewReport_xlsxMatchingVendorUploadType_succeeds() throws Exception {
        UUID reportId = UUID.randomUUID();
        Report report = Report.builder().id(reportId).siteId("KY0001").project("Proj1").rat("4G")
                .createdByVendor(uploader)
                .currentStatus(ReportStatus.PENDING_REVIEW).currentVersion(1).build();
        ReportVersion version = ReportVersion.builder().id(UUID.randomUUID()).report(report)
                .versionNumber(1).uploadedBy(uploader)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .build();

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(versionRepository.findByReportIdAndVersionNumber(reportId, 1)).thenReturn(Optional.of(version));
        when(userRepository.findById(uploader.getId())).thenReturn(Optional.of(uploader));
        when(storageService.download(any())).thenReturn(new byte[]{1});
        when(auditWorksheetService.appendAuditSheet(any(), any(), any(), any())).thenReturn(new byte[]{1});

        MockMultipartFile reviewedFile = new MockMultipartFile(
                "file", "KY0001_Proj1_4G_V1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", XLSX_MAGIC);
        ReviewRequest request = new ReviewRequest(ReportStatus.APPROVED, "looks good", null);

        ReportResponse response = documentService.reviewReport(reportId, reviewedFile, request, uploader, "127.0.0.1");

        assertEquals("APPROVED", response.reviewStatus());
        verify(auditWorksheetService, times(1)).appendAuditSheet(any(), any(), any(), any());
        verify(pdfStampService, never()).stampAndSign(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reviewReport_typeMismatchWithVendorUpload_rejected() {
        UUID reportId = UUID.randomUUID();
        Report report = Report.builder().id(reportId).siteId("KY0001").project("Proj1").rat("4G")
                .currentStatus(ReportStatus.PENDING_REVIEW).currentVersion(1).build();
        ReportVersion version = ReportVersion.builder().id(UUID.randomUUID()).report(report)
                .versionNumber(1).uploadedBy(uploader).contentType("application/pdf").build();

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(versionRepository.findByReportIdAndVersionNumber(reportId, 1)).thenReturn(Optional.of(version));
        when(userRepository.findById(uploader.getId())).thenReturn(Optional.of(uploader));

        MockMultipartFile reviewedFile = new MockMultipartFile(
                "file", "KY0001_Proj1_4G_V1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", XLSX_MAGIC);
        ReviewRequest request = new ReviewRequest(ReportStatus.APPROVED, null, null);

        assertThrows(IllegalArgumentException.class, () ->
                documentService.reviewReport(reportId, reviewedFile, request, uploader, "127.0.0.1"));
    }
}
