package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.UserRole;
import dev.thilanka.site_ready.repository.ReportRepository;
import dev.thilanka.site_ready.repository.ReportVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Centralised ownership/access checks used by all report endpoints.
 * Engineers and Admins can access any report.
 * Vendors can only access reports belonging to their own company.
 */
@Service
@RequiredArgsConstructor
public class ReportAccessService {

    private final ReportRepository reportRepository;
    private final ReportVersionRepository versionRepository;

    public void assertCanRead(UUID reportId, User user) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER || user.getRole() == UserRole.ENGINEER) return;
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new AccessDeniedException("Report not found"));
        assertVendorOwnsReport(report, user);
    }

    public void assertCanRead(Report report, User user) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER || user.getRole() == UserRole.ENGINEER) return;
        assertVendorOwnsReport(report, user);
    }

    public void assertCanUploadNextVersion(Report report, User user) {
        // Only the owning vendor company can upload subsequent versions
        if (user.getRole() == UserRole.VENDOR) {
            assertVendorOwnsReport(report, user);
        }
    }

    private void assertVendorOwnsReport(Report report, User user) {
        UUID reportCompanyId = report.getVendorCompany() != null
                ? report.getVendorCompany().getId()
                : (report.getCreatedByVendor().getCompany() != null
                ? report.getCreatedByVendor().getCompany().getId()
                : null);

        if (reportCompanyId == null ||
                !reportCompanyId.equals(user.getCompany().getId())) {
            throw new AccessDeniedException("You do not have access to this report");
        }
    }
}
