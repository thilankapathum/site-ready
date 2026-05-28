package dev.thilanka.site_ready.dto;

import java.util.List;

public record PendingBreakdown(
        List<Breakdown> versions,
        List<Breakdown> rats,
        List<Breakdown> vendors,
        List<Breakdown> engineers
) {
}
