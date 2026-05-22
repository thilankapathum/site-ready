package dev.thilanka.site_ready.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import dev.thilanka.site_ready.dto.PageDiff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
public class PdfDiffService {

    /**
     * Compares two PDF byte arrays and returns a structural diff.
     *
     * @param previousBytes  Original bytes of the previous version (pre-stamp)
     * @param currentBytes   Original bytes of the current version (pre-stamp)
     * @return PageDiff describing structural changes
     */
    public PageDiff diff(byte[] previousBytes, byte[] currentBytes) {
        try {
            List<String> prevHashes  = getPageHashes(previousBytes);
            List<String> currHashes  = getPageHashes(currentBytes);
            Map<Integer, Integer> prevAnnotCounts = getAnnotationCounts(previousBytes);
            Map<Integer, Integer> currAnnotCounts = getAnnotationCounts(currentBytes);

            int prevTotal = prevHashes.size();
            int currTotal = currHashes.size();

            // Build a set of all previous hashes for fast lookup
            Set<String> prevHashSet = new HashSet<>(prevHashes);
            Set<String> currHashSet = new HashSet<>(currHashes);

            // Added pages: pages in current whose hash doesn't appear in previous at all
            List<Integer> added = new ArrayList<>();
            for (int i = 0; i < currHashes.size(); i++) {
                if (!prevHashSet.contains(currHashes.get(i))) {
                    added.add(i + 1); // 1-based
                }
            }

            // Deleted pages: pages in previous whose hash doesn't appear in current at all
            List<Integer> deleted = new ArrayList<>();
            for (int i = 0; i < prevHashes.size(); i++) {
                if (!currHashSet.contains(prevHashes.get(i))) {
                    deleted.add(i + 1); // 1-based
                }
            }

            // Modified pages: same position in both, different hash, but hash
            // exists somewhere in current (so it's not just deleted)
            // We compare position-by-position for pages that exist in both
            List<Integer> modified = new ArrayList<>();
            int compareLen = Math.min(prevHashes.size(), currHashes.size());
            for (int i = 0; i < compareLen; i++) {
                String prevHash = prevHashes.get(i);
                String currHash = currHashes.get(i);
                if (!prevHash.equals(currHash)) {
                    // Only flag as modified if this page wasn't already counted
                    // as deleted (to avoid double-counting)
                    if (!deleted.contains(i + 1) && !added.contains(i + 1)) {
                        modified.add(i + 1);
                    }
                }
            }

            // New annotations: pages where annotation count increased
            Map<Integer, Integer> newAnnotations = new LinkedHashMap<>();
            for (int pageNum = 1; pageNum <= currTotal; pageNum++) {
                int prevCount = prevAnnotCounts.getOrDefault(pageNum, 0);
                int currCount = currAnnotCounts.getOrDefault(pageNum, 0);
                if (currCount > prevCount) {
                    newAnnotations.put(pageNum, currCount - prevCount);
                }
            }

            boolean hasDeletions     = !deleted.isEmpty();
            boolean hasModifications = !modified.isEmpty();

            String summary = buildSummary(added, deleted, modified, newAnnotations,
                    prevTotal, currTotal);

            return new PageDiff(
                    prevTotal, currTotal,
                    Collections.unmodifiableList(added),
                    Collections.unmodifiableList(deleted),
                    Collections.unmodifiableList(modified),
                    Collections.unmodifiableMap(newAnnotations),
                    hasDeletions, hasModifications,
                    summary
            );

        } catch (Exception e) {
            log.warn("Page diff failed: {}", e.getMessage());
            // Return a safe fallback — don't fail the upload
            return new PageDiff(
                    0, 0, List.of(), List.of(), List.of(), Map.of(),
                    false, false,
                    "Change analysis unavailable for this version."
            );
        }
    }

    // ── Helpers ──

    private List<String> getPageHashes(byte[] pdfBytes) throws Exception {
        List<String> hashes = new ArrayList<>();
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
             PdfDocument doc = new PdfDocument(reader)) {

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            int pageCount = doc.getNumberOfPages();

            for (int i = 1; i <= pageCount; i++) {
                PdfPage page = doc.getPage(i);
                // Hash the raw content stream bytes of the page
                // This captures text, images, and graphics but not metadata
                byte[] contentBytes = extractPageContentBytes(page);
                md.reset();
                byte[] hash = md.digest(contentBytes);
                hashes.add(HexFormat.of().formatHex(hash));
            }
        }
        return hashes;
    }

    private byte[] extractPageContentBytes(PdfPage page) {
        try {
            // Get the concatenated content stream bytes
            // PdfPage.getContentBytes() returns all content streams merged
            byte[] content = page.getContentBytes();
            // Also include page dimensions in the hash to detect page resizing
            com.itextpdf.kernel.geom.Rectangle mediaBox = page.getMediaBox();
            String dims = String.format("%.2fx%.2f", mediaBox.getWidth(), mediaBox.getHeight());
            byte[] dimBytes = dims.getBytes();
            byte[] combined = new byte[content.length + dimBytes.length];
            System.arraycopy(content, 0, combined, 0, content.length);
            System.arraycopy(dimBytes, 0, combined, content.length, dimBytes.length);
            return combined;
        } catch (Exception e) {
            // Fall back to page dictionary hash if content stream unavailable
            return page.getPdfObject().toString().getBytes();
        }
    }

    private Map<Integer, Integer> getAnnotationCounts(byte[] pdfBytes) throws Exception {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
             PdfDocument doc = new PdfDocument(reader)) {

            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                PdfPage page = doc.getPage(i);
                List<PdfAnnotation> annotations = page.getAnnotations();
                // Only count visible markup annotations, not form fields or links
                long markupCount = annotations.stream()
                        .filter(a -> isMarkupAnnotation(a.getSubtype()))
                        .count();
                if (markupCount > 0) {
                    counts.put(i, (int) markupCount);
                }
            }
        }
        return counts;
    }

    private boolean isMarkupAnnotation(com.itextpdf.kernel.pdf.PdfName subtype) {
        if (subtype == null) return false;
        return subtype.equals(PdfName.Text)           // Sticky note
                || subtype.equals(PdfName.FreeText)       // Text box / callout
                || subtype.equals(PdfName.Highlight)      // Highlight
                || subtype.equals(PdfName.Underline)      // Underline
                || subtype.equals(PdfName.StrikeOut)      // Strikethrough
                || subtype.equals(PdfName.Squiggly)       // Squiggly underline
                || subtype.equals(PdfName.Ink)            // Freehand drawing
                || subtype.equals(PdfName.Square)         // Rectangle
                || subtype.equals(PdfName.Circle)         // Circle/oval
                || subtype.equals(PdfName.Polygon)        // Polygon shape
                || subtype.equals(PdfName.PolyLine)       // Polyline
                || subtype.equals(PdfName.Stamp)          // Rubber stamp
                || subtype.equals(PdfName.Caret);         // Caret (insertion point)
    }

    private String buildSummary(
            List<Integer> added, List<Integer> deleted, List<Integer> modified,
            Map<Integer, Integer> newAnnotations, int prevTotal, int currTotal
    ) {
        List<String> parts = new ArrayList<>();

        int pagesDelta = currTotal - prevTotal;
        if (pagesDelta > 0) {
            parts.add(pagesDelta + " page" + (pagesDelta != 1 ? "s" : "") + " added");
        } else if (pagesDelta < 0) {
            parts.add(Math.abs(pagesDelta) + " page" + (Math.abs(pagesDelta) != 1 ? "s" : "") + " removed");
        }

        if (!added.isEmpty() && pagesDelta <= 0) {
            parts.add("new content on page" + (added.size() != 1 ? "s" : "") + " " + formatPageList(added));
        }
        if (!deleted.isEmpty()) {
            parts.add("content removed from page" + (deleted.size() != 1 ? "s" : "") + " " + formatPageList(deleted));
        }
        if (!modified.isEmpty()) {
            parts.add("content changed on page" + (modified.size() != 1 ? "s" : "") + " " + formatPageList(modified));
        }
        if (!newAnnotations.isEmpty()) {
            int total = newAnnotations.values().stream().mapToInt(Integer::intValue).sum();
            parts.add(total + " new annotation" + (total != 1 ? "s" : "") + " on page"
                    + (newAnnotations.size() != 1 ? "s" : "") + " "
                    + formatPageList(new ArrayList<>(newAnnotations.keySet())));
        }

        if (parts.isEmpty()) {
            return "No structural changes detected (total pages: " + currTotal + ")";
        }

        return String.join("; ", parts) + ".";
    }

    private String formatPageList(List<Integer> pages) {
        if (pages.isEmpty()) return "";
        List<Integer> sorted = new ArrayList<>(pages);
        Collections.sort(sorted);
        if (sorted.size() <= 5) {
            return sorted.toString().replace("[", "").replace("]", "");
        }
        // Abbreviate long lists
        return sorted.subList(0, 4).toString().replace("[", "").replace("]", "")
                + " … (+" + (sorted.size() - 4) + " more)";
    }
}
