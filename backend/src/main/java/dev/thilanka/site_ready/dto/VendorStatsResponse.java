package dev.thilanka.site_ready.dto;

import java.util.UUID;

public record VendorStatsResponse(
        UUID vendorId,
        String vendorName,
        String vendorEmail,
        String role,
        long totalReports,
        long pendingReview,
        long resubmissionRequired,
        long approved,
        long conditionallyApproved,
        long totalApproved,             // approved + conditionally approved
        long rejected,
        Double avgResubmissionHours,    // average hours from RESUBMISSION_REQUIRED review to next upload
        Long oldestResubmissionDays,    // days the oldest pending resubmission has been waiting
        String oldestResubmissionReport, // namingKey of that report
        UUID oldestResubmissionReportId
) {
}
