package dev.thilanka.site_ready.dto;

import java.util.UUID;

public record EngineerPendingResponse(
        UUID engineerId,              // null = reports with no engineer assigned
        String engineerName,
        long pendingCount,
        long oldestPendingDays,       // days the oldest pending report has been waiting
        String oldestPendingReport,   // namingKey of that report
        UUID oldestPendingReportId
) {
}
