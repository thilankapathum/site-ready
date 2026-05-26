package dev.thilanka.site_ready.dto;

import java.util.UUID;

public record EngineerStatsResponse(
        UUID engineerId,
        String engineerName,
        String engineerEmail,
        String companyName,
        long totalReports,
        long pendingReview,
        long resubmissionRequired,
        long approved,
        long conditionallyApproved,
        long totalApproved,          // approved + conditionally approved
        long rejected,
        Double avgReviewHours,       // average hours from vendor upload to engineer review
        Long longestPendingDays,     // days the oldest currently-pending report has been waiting
        String longestPendingReport  // namingKey of the longest-pending report
) {
}
