package dev.thilanka.site_ready.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.signatures.*;
import dev.thilanka.site_ready.config.AppProperties;
import dev.thilanka.site_ready.dto.PageDiff;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import com.itextpdf.layout.element.Image;

import java.util.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfStampService {

    private final AppProperties appProperties;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public StampResult stampAndSign(
            byte[] originalBytes,
            User uploader,
            ReportVersion version,
            String sha256Hash,
            List<ReportVersion> allVersions,
            PageDiff pageDiff             // ← new parameter
    ) throws Exception {
        byte[] withAuditPage = appendAuditPage(
                originalBytes, uploader, version, sha256Hash, allVersions, pageDiff);
        String signatureId = "SSV-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase();
        byte[] signedBytes = applyPadesSignature(withAuditPage, signatureId);
        return new StampResult(signedBytes, signatureId);
    }

    /**
     * Appends a fresh audit trail page to the PDF.
     * Detects whether the source PDF is already PAdES-signed and uses
     * append mode if so, avoiding cross-reference corruption.
     */
    private byte[] appendAuditPage(
            byte[] original, User uploader, ReportVersion version,
            String sha256Hash, List<ReportVersion> allVersions, PageDiff pageDiff
    ) throws Exception {

        String verifyUrl = appProperties.baseUrl() + "/verify/" + version.getId();
        String timestamp = OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"));

        boolean isSigned = isPdfSigned(original);

        if (isSigned) {
            return appendAuditPageAppendMode(
                    original, uploader, version, sha256Hash, allVersions, pageDiff, verifyUrl, timestamp);
        } else {
            return appendAuditPageFullRewrite(
                    original, uploader, version, sha256Hash, allVersions, pageDiff, verifyUrl, timestamp);
        }
    }

    private boolean isPdfSigned(byte[] pdfBytes) {
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
             PdfDocument doc = new PdfDocument(reader)) {
            SignatureUtil signatureUtil = new SignatureUtil(doc);
            return !signatureUtil.getSignatureNames().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * For unsigned PDFs — full rewrite with new audit page appended.
     */
    private byte[] appendAuditPageFullRewrite(
            byte[] original, User uploader, ReportVersion version,
            String sha256Hash, List<ReportVersion> allVersions, PageDiff pageDiff,
            String verifyUrl, String timestamp
    ) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfReader reader = new PdfReader(new ByteArrayInputStream(original));
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdfDoc = new PdfDocument(reader, writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(36, 36, 36, 36);
//        document.setPageNumber(pdfDoc.getNumberOfPages());
        document.add(new AreaBreak(com.itextpdf.layout.properties.AreaBreakType.LAST_PAGE));
        document.add(new AreaBreak(com.itextpdf.layout.properties.AreaBreakType.NEXT_PAGE));
        addAuditContent(document, uploader, version, sha256Hash, allVersions, pageDiff, verifyUrl, timestamp);
        document.close();
        return out.toByteArray();
    }

    /**
     * For already-signed PDFs — uses append mode to avoid invalidating existing signature.
     * Adds a new page at the end without touching the signed content.
     */
    private byte[] appendAuditPageAppendMode(
            byte[] original, User uploader, ReportVersion version,
            String sha256Hash, List<ReportVersion> allVersions, PageDiff pageDiff,
            String verifyUrl, String timestamp
    ) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfReader reader = new PdfReader(new ByteArrayInputStream(original));
        // useAppendMode() — does not alter any existing page or object
        PdfWriter writer = new PdfWriter(out, new WriterProperties().useSmartMode());
        StampingProperties sp = new StampingProperties().useAppendMode();
        PdfDocument pdfDoc = new PdfDocument(reader, writer, sp);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(36, 36, 36, 36);
//        document.setPageNumber(pdfDoc.getNumberOfPages());
        document.add(new AreaBreak(com.itextpdf.layout.properties.AreaBreakType.LAST_PAGE));
        document.add(new AreaBreak(com.itextpdf.layout.properties.AreaBreakType.NEXT_PAGE));
        addAuditContent(document, uploader, version, sha256Hash, allVersions, pageDiff, verifyUrl, timestamp);
        document.close();
        return out.toByteArray();
    }

    /**
     * Writes all audit trail content into the document.
     * Shared between full-rewrite and append-mode paths.
     */
    private void addAuditContent(
            Document document, User uploader, ReportVersion version,
            String sha256Hash, List<ReportVersion> allVersions, PageDiff pageDiff,
            String verifyUrl, String timestamp
    ) throws Exception {

        DeviceRgb headerBlue  = new DeviceRgb(15, 76, 129);
        DeviceRgb successGreen = new DeviceRgb(22, 163, 74);
        DeviceRgb warningAmber = new DeviceRgb(217, 119, 6);
        DeviceRgb errorRed     = new DeviceRgb(220, 38, 38);
        DeviceRgb lightGray    = new DeviceRgb(245, 247, 250);
        DeviceRgb white        = new DeviceRgb(255, 255, 255);
        DeviceRgb darkText     = new DeviceRgb(30, 30, 30);
        DeviceRgb amber        = new DeviceRgb(180, 100, 0);

        PdfFont bold   = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont mono   = PdfFontFactory.createFont(StandardFonts.COURIER);

        // ── Header banner ──
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth().setMarginBottom(8);
        Cell headerCell = new Cell()
                .setBackgroundColor(headerBlue).setPadding(10).setBorder(Border.NO_BORDER)
                .add(new Paragraph("SSV DOCUMENT MANAGEMENT SYSTEM — AUDIT TRAIL")
                        .setFont(bold).setFontSize(11).setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER).setMarginBottom(2))
                .add(new Paragraph(String.format(
                        "Site: %s  |  Project: %s  |  RAT: %s  |  Version: V%d  |  Status: %s",
                        version.getReport().getSiteId(),
                        version.getReport().getProject(),
                        version.getReport().getRat(),
                        version.getReport().getCurrentVersion(),
                        version.getReport().getCurrentStatus().name()))
                        .setFont(normal).setFontSize(8)
                        .setFontColor(new DeviceRgb(180, 210, 240))
                        .setTextAlignment(TextAlignment.CENTER));
        headerTable.addCell(headerCell);
        document.add(headerTable);

        // ── Current version metadata ──
        document.add(new Paragraph("CURRENT VERSION DETAILS")
                .setFont(bold).setFontSize(8).setFontColor(headerBlue).setMarginBottom(3));

        Table metaTable = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .useAllAvailableWidth().setMarginBottom(10);

        String[][] metaRows = {
                {"Document Version ID", version.getId().toString()},
                {"Uploaded By",         uploader.getFullName() + " (" + uploader.getCompany().getName() + ")"},
                {"Uploader Role",       uploader.getRole().name()},
                {"Uploader Email",      uploader.getEmail()},
                {"Upload Timestamp",    timestamp},
                {"Original Filename",   version.getOriginalFilename()},
                {"SHA-256 Hash",        sha256Hash},
        };

        for (int i = 0; i < metaRows.length; i++) {
            DeviceRgb bg = (i % 2 == 0) ? lightGray : white;
            metaTable.addCell(new Cell().setBackgroundColor(bg).setPadding(4).setBorder(Border.NO_BORDER)
                    .add(new Paragraph(metaRows[i][0]).setFont(bold).setFontSize(7.5f)));
            PdfFont vf = metaRows[i][0].contains("Hash") || metaRows[i][0].contains("ID") ? mono : normal;
            metaTable.addCell(new Cell().setBackgroundColor(bg).setPadding(4).setBorder(Border.NO_BORDER)
                    .add(new Paragraph(metaRows[i][1]).setFont(vf).setFontSize(7f)));
        }
        document.add(metaTable);

        // ── Page change summary (only for V2+) ──
        if (pageDiff != null && pageDiff.totalPagesOld() > 0) {
            addPageDiffSection(document, pageDiff, bold, normal, mono,
                    headerBlue, successGreen, warningAmber, errorRed, amber, lightGray, white, darkText);
        }

        // ── Complete action history ──
        document.add(new Paragraph("COMPLETE ACTION HISTORY")
                .setFont(bold).setFontSize(8).setFontColor(headerBlue).setMarginBottom(3));

        Table histTable = new Table(UnitValue.createPercentArray(new float[]{8, 18, 18, 14, 18, 24}))
                .useAllAvailableWidth().setMarginBottom(10);

        String[] colHeaders = {"Ver.", "Date", "Actor", "Role", "Action", "Notes"};
        for (String h : colHeaders) {
            histTable.addHeaderCell(new Cell()
                    .setBackgroundColor(headerBlue).setPadding(4).setBorder(Border.NO_BORDER)
                    .add(new Paragraph(h).setFont(bold).setFontSize(7).setFontColor(ColorConstants.WHITE)));
        }

        List<ReportVersion> sorted = allVersions.stream()
                .sorted(Comparator.comparingInt(ReportVersion::getVersionNumber)).toList();

        int rowNum = 0;
        for (ReportVersion rv : sorted) {
            DeviceRgb rowBg = (rowNum++ % 2 == 0) ? lightGray : white;
            String uploadedAt = rv.getUploadedAt() != null
                    ? rv.getUploadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "";
            String[] uploadRow = {
                    "V" + rv.getVersionNumber(), uploadedAt,
                    rv.getUploadedBy().getFullName(), rv.getUploadedBy().getRole().name(),
                    "UPLOADED", rv.getOriginalFilename()
            };
            for (String val : uploadRow) {
                histTable.addCell(new Cell().setBackgroundColor(rowBg).setPadding(3).setBorder(Border.NO_BORDER)
                        .add(new Paragraph(val).setFont(normal).setFontSize(6.5f)));
            }

            if (rv.getReviewStatus() != null && rv.getReviewedBy() != null) {
                rowBg = (rowNum++ % 2 == 0) ? lightGray : white;
                String reviewedAt = rv.getReviewedAt() != null
                        ? rv.getReviewedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "";
                String notes = rv.getReviewerNotes() != null ? rv.getReviewerNotes() : "";
                if (rv.getConditions() != null && !rv.getConditions().isBlank()) {
                    notes += " | Conditions: " + rv.getConditions();
                }
                DeviceRgb actionColor = switch (rv.getReviewStatus()) {
                    case APPROVED, CONDITIONALLY_APPROVED -> successGreen;
                    case REJECTED                         -> errorRed;
                    case RESUBMISSION_REQUIRED            -> warningAmber;
                    default                               -> darkText;
                };
                String[] reviewRow = {
                        "V" + rv.getVersionNumber(), reviewedAt,
                        rv.getReviewedBy().getFullName(), rv.getReviewedBy().getRole().name(),
                        rv.getReviewStatus().name(),
                        notes.length() > 80 ? notes.substring(0, 77) + "..." : notes
                };
                for (int i = 0; i < reviewRow.length; i++) {
                    histTable.addCell(new Cell().setBackgroundColor(rowBg).setPadding(3).setBorder(Border.NO_BORDER)
                            .add(new Paragraph(reviewRow[i])
                                    .setFont(i == 4 ? bold : normal).setFontSize(6.5f)
                                    .setFontColor(i == 4 ? actionColor : darkText)));
                }
            }
        }
        document.add(histTable);

        // ── QR + legal notice ──
        byte[] qrBytes = generateQrCode(verifyUrl, 80);
        Image qrImage = new Image(ImageDataFactory.create(qrBytes)).setWidth(60).setHeight(60);
        Table footerTable = new Table(UnitValue.createPercentArray(new float[]{15, 85})).useAllAvailableWidth();
        footerTable.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(4).add(qrImage));
        footerTable.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .add(new Paragraph(
                        "This page is automatically generated and maintained by the SSV Document Management System. " +
                                "A PAdES-B digital signature covers the entire document including this page. " +
                                "Verify authenticity at: " + verifyUrl)
                        .setFont(normal).setFontSize(6.5f).setFontColor(new DeviceRgb(90, 90, 90))));
        document.add(footerTable);
    }

    /**
     * Renders the page change summary section on the audit page.
     */
    private void addPageDiffSection(
            Document document, PageDiff diff,
            PdfFont bold, PdfFont normal, PdfFont mono,
            DeviceRgb headerBlue, DeviceRgb successGreen, DeviceRgb warningAmber,
            DeviceRgb errorRed, DeviceRgb amber, DeviceRgb lightGray,
            DeviceRgb white, DeviceRgb darkText
    ) {
        // Section header
        document.add(new Paragraph("DOCUMENT CHANGE ANALYSIS (vs. PREVIOUS VERSION)")
                .setFont(bold).setFontSize(8).setFontColor(headerBlue).setMarginBottom(3));

        // Warning banner if deletions detected
        if (diff.hasDeletions()) {
            Table warningTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .useAllAvailableWidth().setMarginBottom(6);
            warningTable.addCell(new Cell()
                    .setBackgroundColor(new DeviceRgb(254, 226, 226))
                    .setPadding(6).setBorder(Border.NO_BORDER)
                    .add(new Paragraph("⚠  PAGE DELETIONS DETECTED — Engineer verification required")
                            .setFont(bold).setFontSize(8).setFontColor(errorRed)));
            document.add(warningTable);
        }

        // Change summary table
        Table diffTable = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
                .useAllAvailableWidth().setMarginBottom(10);

        // Page count row
        addDiffRow(diffTable, "Total pages (previous → current)",
                diff.totalPagesOld() + " → " + diff.totalPagesNew(),
                diff.totalPagesNew() > diff.totalPagesOld() ? successGreen :
                        diff.totalPagesNew() < diff.totalPagesOld() ? errorRed : darkText,
                0, lightGray, white, bold, normal);

        // Added pages
        if (!diff.addedPages().isEmpty()) {
            addDiffRow(diffTable, "✓  Pages added",
                    "Page" + (diff.addedPages().size() != 1 ? "s" : "") + " " +
                            formatPageList(diff.addedPages()),
                    successGreen, 1, lightGray, white, bold, normal);
        }

        // Deleted pages
        if (!diff.deletedPages().isEmpty()) {
            addDiffRow(diffTable, "✗  Pages deleted",
                    "Page" + (diff.deletedPages().size() != 1 ? "s" : "") + " " +
                            formatPageList(diff.deletedPages()),
                    errorRed, 2, lightGray, white, bold, normal);
        }

        // Modified pages
        if (!diff.modifiedPages().isEmpty()) {
            addDiffRow(diffTable, "~  Pages with content changes",
                    "Page" + (diff.modifiedPages().size() != 1 ? "s" : "") + " " +
                            formatPageList(diff.modifiedPages()) +
                            " (may be PDF re-export artefact — verify manually)",
                    warningAmber, 3, lightGray, white, bold, normal);
        }

        // New annotations
        if (!diff.newAnnotationsByPage().isEmpty()) {
            StringBuilder annotDesc = new StringBuilder();
            diff.newAnnotationsByPage().forEach((page, count) ->
                    annotDesc.append("p").append(page).append(": ").append(count)
                            .append(" new; "));
            String desc = annotDesc.toString();
            if (desc.endsWith("; ")) desc = desc.substring(0, desc.length() - 2);
            addDiffRow(diffTable, "✎  New annotations/comments",
                    desc, amber, 4, lightGray, white, bold, normal);
        }

        // No changes
        if (diff.addedPages().isEmpty() && diff.deletedPages().isEmpty()
                && diff.modifiedPages().isEmpty() && diff.newAnnotationsByPage().isEmpty()) {
            addDiffRow(diffTable, "No structural changes detected",
                    "Document content unchanged from previous version",
                    darkText, 0, lightGray, white, bold, normal);
        }

        document.add(diffTable);
    }

    private void addDiffRow(
            Table table, String label, String value, DeviceRgb valueColor,
            int rowIndex, DeviceRgb lightGray, DeviceRgb white,
            PdfFont bold, PdfFont normal
    ) {
        DeviceRgb bg = (rowIndex % 2 == 0) ? lightGray : white;
        table.addCell(new Cell().setBackgroundColor(bg).setPadding(4).setBorder(Border.NO_BORDER)
                .add(new Paragraph(label).setFont(bold).setFontSize(7.5f)));
        table.addCell(new Cell().setBackgroundColor(bg).setPadding(4).setBorder(Border.NO_BORDER)
                .add(new Paragraph(value).setFont(normal).setFontSize(7f).setFontColor(valueColor)));
    }

    private String formatPageList(List<Integer> pages) {
        List<Integer> sorted = new ArrayList<>(pages);
        Collections.sort(sorted);
        if (sorted.size() <= 6) return sorted.toString().replace("[","").replace("]","");
        return sorted.subList(0, 5).toString().replace("[","").replace("]","")
                + " … (+" + (sorted.size() - 5) + " more)";
    }

    private byte[] applyPadesSignature(byte[] pdfBytes, String signatureId) throws Exception {
        AppProperties.SigningProperties sp = appProperties.signing();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream ksStream = new FileInputStream(sp.keystorePath())) {
            ks.load(ksStream, sp.keystorePassword().toCharArray());
        }
        PrivateKey privateKey = (PrivateKey) ks.getKey(sp.alias(), sp.keystorePassword().toCharArray());
        Certificate[] chain = ks.getCertificateChain(sp.alias());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
        StampingProperties stampProps = new StampingProperties().useAppendMode();
        PdfSigner signer = new PdfSigner(reader, out, stampProps);
        signer.setFieldName(signatureId);
        signer.getSignatureAppearance()
                .setReason("SSV DMS — Document received and registered")
                .setLocation("Colombo, Sri Lanka");
        IExternalSignature pks = new PrivateKeySignature(
                privateKey, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
        signer.signDetached(new BouncyCastleDigest(), pks, chain,
                null, null, null, 0, PdfSigner.CryptoStandard.CADES);
        return out.toByteArray();
    }

    private byte[] generateQrCode(String content, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    public record StampResult(byte[] signedBytes, String signatureId) {}
}
