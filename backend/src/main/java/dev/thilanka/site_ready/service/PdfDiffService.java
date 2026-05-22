package dev.thilanka.site_ready.service;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import dev.thilanka.site_ready.dto.PageDiff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
public class PdfDiffService {

    /**
     * Diffs two PDFs, subtracting a known "baseline" annotation count
     * (e.g. engineer's annotations already present in the document the vendor downloaded).
     *
     * @param previousOriginalBytes  Vendor's original of V(n-1) — clean upload
     * @param currentBytes           Vendor's new upload V(n)
     * @param engineerReviewedBytes  Engineer's reviewed file of V(n-1), or null if not available
     */

    public PageDiff diff(byte[] previousOriginalBytes, byte[] currentBytes,
                         byte[] engineerReviewedBytes) {
        try {
            // Strip any existing audit trail pages before diffing
            // so system-generated pages don't appear as added/deleted content
            byte[] prevClean = stripAuditPages(previousOriginalBytes);
            byte[] currClean = stripAuditPages(currentBytes);
            byte[] baseClean = engineerReviewedBytes != null
                    ? stripAuditPages(engineerReviewedBytes) : null;

            List<String> prevHashes = getPageHashes(prevClean);
            List<String> currHashes = getPageHashes(currClean);

            Map<Integer, Integer> baselineAnnotCounts = baseClean != null
                    ? getAnnotationCounts(baseClean)
                    : getAnnotationCounts(prevClean);

            Map<Integer, Integer> currAnnotCounts = getAnnotationCounts(currClean);

            int prevTotal = prevHashes.size();
            int currTotal = currHashes.size();

            Set<String> prevHashSet = new HashSet<>(prevHashes);
            Set<String> currHashSet = new HashSet<>(currHashes);

            List<Integer> added = new ArrayList<>();
            for (int i = 0; i < currHashes.size(); i++) {
                if (!prevHashSet.contains(currHashes.get(i))) added.add(i + 1);
            }

            List<Integer> deleted = new ArrayList<>();
            for (int i = 0; i < prevHashes.size(); i++) {
                if (!currHashSet.contains(prevHashes.get(i))) deleted.add(i + 1);
            }

            List<Integer> modified = new ArrayList<>();
            int compareLen = Math.min(prevHashes.size(), currHashes.size());
            for (int i = 0; i < compareLen; i++) {
                if (!prevHashes.get(i).equals(currHashes.get(i))
                        && !deleted.contains(i + 1) && !added.contains(i + 1)) {
                    modified.add(i + 1);
                }
            }

            // New annotations = current count minus baseline (engineer's + vendor's previous)
            // This isolates ONLY annotations the vendor added in this submission
            Map<Integer, Integer> newAnnotations = new LinkedHashMap<>();
            for (int pageNum = 1; pageNum <= currTotal; pageNum++) {
                int baselineCount = baselineAnnotCounts.getOrDefault(pageNum, 0);
                int currCount     = currAnnotCounts.getOrDefault(pageNum, 0);
                int vendorNew     = currCount - baselineCount;
                if (vendorNew > 0) {
                    newAnnotations.put(pageNum, vendorNew);
                }
            }

            boolean hasDeletions     = !deleted.isEmpty();
            boolean hasModifications = !modified.isEmpty();
            String summary = buildSummary(added, deleted, modified, newAnnotations, prevTotal, currTotal);

            return new PageDiff(prevTotal, currTotal,
                    Collections.unmodifiableList(added),
                    Collections.unmodifiableList(deleted),
                    Collections.unmodifiableList(modified),
                    Collections.unmodifiableMap(newAnnotations),
                    hasDeletions, hasModifications, summary);

        } catch (Exception e) {
            log.warn("Page diff failed: {}", e.getMessage());
            return new PageDiff(0, 0, List.of(), List.of(), List.of(), Map.of(),
                    false, false, "Change analysis unavailable for this version.");
        }
    }

    // Keep the old 2-argument overload for backward compatibility
    public PageDiff diff(byte[] previousBytes, byte[] currentBytes) {
        return diff(previousBytes, currentBytes, null);
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

    /**
     * Removes all trailing pages that contain the SSV audit trail marker.
     * Handles multiple consecutive audit pages (e.g. vendor + engineer audit pages).
     */
    private byte[] stripAuditPages(byte[] pdfBytes) {
        try {
            byte[] current = pdfBytes;
            // Keep stripping from the end until no more audit pages found
            for (int i = 0; i < 5; i++) { // max 5 iterations as safety limit
                byte[] stripped = stripLastAuditPageIfPresent(current);
                if (stripped == current) break; // no audit page was stripped
                current = stripped;
            }
            return current;
        } catch (Exception e) {
            log.debug("stripAuditPages failed, using original: {}", e.getMessage());
            return pdfBytes;
        }
    }

    private byte[] stripLastAuditPageIfPresent(byte[] pdfBytes) throws Exception {
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
             PdfDocument pdfDoc = new PdfDocument(reader)) {
            int totalPages = pdfDoc.getNumberOfPages();
            if (totalPages <= 1) return pdfBytes;
            PdfPage lastPage = pdfDoc.getPage(totalPages);
            String lastPageText = com.itextpdf.kernel.pdf.canvas.parser
                    .PdfTextExtractor.getTextFromPage(lastPage);
            if (!lastPageText.contains("SSV DOCUMENT MANAGEMENT SYSTEM")) {
                return pdfBytes; // signal: nothing was stripped
            }
        }
        // Strip the audit page
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfReader reader2 = new PdfReader(new ByteArrayInputStream(pdfBytes));
        reader2.setUnethicalReading(true);
        try (PdfDocument pdfDoc2 = new PdfDocument(reader2, new PdfWriter(out))) {
            pdfDoc2.removePage(pdfDoc2.getNumberOfPages());
        }
        return out.toByteArray();
    }
}
