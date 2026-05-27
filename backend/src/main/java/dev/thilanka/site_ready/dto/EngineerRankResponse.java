package dev.thilanka.site_ready.dto;

public record EngineerRankResponse(
        Integer rank,                    // 1-based rank, null if no reviews yet
        int totalRanked,                 // engineers with at least one review
        int totalEngineers,              // all active engineers
        Double myAvgReviewHours,         // this engineer's average
        String engineerAboveName,        // name of engineer ranked just above
        Double engineerAboveAvgHours,    // their average
        String engineerBelowName,        // name of engineer ranked just below
        Double engineerBelowAvgHours,
        Integer percentile               // top X% — e.g. 80 means faster than 80% of peers
) {}
