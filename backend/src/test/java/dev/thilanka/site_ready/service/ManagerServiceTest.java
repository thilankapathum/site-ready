package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.dto.VendorStatsResponse;
import dev.thilanka.site_ready.entity.Company;
import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.CompanyType;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import dev.thilanka.site_ready.entity.enums.UserRole;
import dev.thilanka.site_ready.repository.ManagerEngineerAssignmentRepository;
import dev.thilanka.site_ready.repository.ReportRepository;
import dev.thilanka.site_ready.repository.ReportVersionRepository;
import dev.thilanka.site_ready.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagerServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ReportVersionRepository versionRepository = mock(ReportVersionRepository.class);
    private final ManagerService managerService = new ManagerService(
            mock(ManagerEngineerAssignmentRepository.class), mock(UserRepository.class),
            reportRepository, versionRepository);

    @Test
    void vendorStatsCountResubmissionTurnaround() {
        Company zte = Company.builder().id(UUID.randomUUID()).name("ZTE").type(CompanyType.VENDOR).build();
        User vendor = User.builder().id(UUID.randomUUID()).fullName("Jane Vendor").company(zte)
                .role(UserRole.VENDOR).build();
        OffsetDateTime now = OffsetDateTime.now();

        // Report A: V1 sent back, V2 uploaded 10h later and approved
        Report a = report(ReportStatus.APPROVED, now);
        ReportVersion a1 = version(1, now.minusHours(30), ReportStatus.RESUBMISSION_REQUIRED, now.minusHours(20));
        ReportVersion a2 = version(2, now.minusHours(10), ReportStatus.APPROVED, now.minusHours(5));

        // Report B: V1 sent back 2h ago, still waiting on the vendor
        Report b = report(ReportStatus.RESUBMISSION_REQUIRED, now.minusDays(3));
        ReportVersion b1 = version(1, now.minusDays(4), ReportStatus.RESUBMISSION_REQUIRED, now.minusHours(2));

        when(reportRepository.findByCreatedByVendorIdAndDeletedFalse(vendor.getId())).thenReturn(List.of(a, b));
        when(versionRepository.findByReportIdOrderByVersionNumberDesc(a.getId())).thenReturn(List.of(a2, a1));
        when(versionRepository.findByReportIdOrderByVersionNumberDesc(b.getId())).thenReturn(List.of(b1));

        VendorStatsResponse s = managerService.buildVendorStats(vendor);

        assertEquals(2, s.totalReports());
        assertEquals(1, s.approved());
        assertEquals(1, s.resubmissionRequired());
        assertEquals(6.0, s.avgResubmissionHours(), 0.1); // (10h + 2h) / 2
        assertEquals(3L, s.oldestResubmissionDays());
        assertEquals(b.getId(), s.oldestResubmissionReportId());
    }

    @Test
    void reviewPendingGroupsByEngineerOldestFirst() {
        Company zte = Company.builder().id(UUID.randomUUID()).name("ZTE").type(CompanyType.VENDOR).build();
        User manager = User.builder().id(UUID.randomUUID()).company(zte).role(UserRole.VENDOR_MANAGER).build();
        User eng = User.builder().id(UUID.randomUUID()).fullName("Peshan").role(UserRole.ENGINEER).build();
        OffsetDateTime now = OffsetDateTime.now();

        Report e1 = report(ReportStatus.PENDING_REVIEW, now.minusDays(2));
        Report e2 = report(ReportStatus.PENDING_REVIEW, now.minusDays(5));
        e1.setAssignedEngineer(eng);
        e2.setAssignedEngineer(eng);
        Report unassigned = report(ReportStatus.PENDING_REVIEW, now.minusDays(9));

        when(reportRepository.findByVendorCompanyIdAndCurrentStatusAndDeletedFalse(zte.getId(), ReportStatus.PENDING_REVIEW))
                .thenReturn(List.of(e1, e2, unassigned));

        var rows = managerService.getVendorReviewPending(manager);

        assertEquals(2, rows.size());
        assertNull(rows.get(0).engineerId());               // oldest (9d) first
        assertEquals("Not assigned", rows.get(0).engineerName());
        assertEquals(9, rows.get(0).oldestPendingDays());
        assertEquals(eng.getId(), rows.get(1).engineerId());
        assertEquals(2, rows.get(1).pendingCount());
        assertEquals(5, rows.get(1).oldestPendingDays());
        assertEquals(e2.getId(), rows.get(1).oldestPendingReportId());
    }

    private Report report(ReportStatus status, OffsetDateTime updatedAt) {
        return Report.builder().id(UUID.randomUUID()).namingKey("SITE_P_4G-" + status)
                .currentStatus(status).updatedAt(updatedAt).build();
    }

    private ReportVersion version(int n, OffsetDateTime uploadedAt, ReportStatus review, OffsetDateTime reviewedAt) {
        return ReportVersion.builder().versionNumber(n).uploadedAt(uploadedAt)
                .reviewStatus(review).reviewedAt(reviewedAt).build();
    }
}
