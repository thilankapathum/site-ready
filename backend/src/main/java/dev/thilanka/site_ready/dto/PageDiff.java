package dev.thilanka.site_ready.dto;

import java.util.List;
import java.util.Map;

public record PageDiff(
        int totalPagesOld,
        int totalPagesNew,
        List<Integer> addedPages,          // 1-based page numbers new in this version
        List<Integer> deletedPages,        // 1-based page numbers present in previous, missing now
        List<Integer> modifiedPages,       // 1-based pages with changed content hash
        Map<Integer, Integer> newAnnotationsByPage,  // page → count of new annotations
        boolean hasDeletions,
        boolean hasModifications,
        String summary                     // human-readable one-liner for audit page
) {
    public static PageDiff firstVersion(int totalPages) {
        return new PageDiff(
                0, totalPages,
                List.of(), List.of(), List.of(), Map.of(),
                false, false,
                "Initial submission — " + totalPages + " page" + (totalPages != 1 ? "s" : "")
        );
    }
}
