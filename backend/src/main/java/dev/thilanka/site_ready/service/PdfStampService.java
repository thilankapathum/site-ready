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
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.signatures.*;
import dev.thilanka.site_ready.config.AppProperties;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import com.itextpdf.layout.element.Image;

import java.util.Comparator;

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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfStampService {

    private final AppProperties appProperties;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Appends a tamper-evident audit trail page to the PDF, then applies a PAdES digital signature.
     *
     * @param originalBytes Raw bytes of the uploaded PDF
     * @param uploader      The user who uploaded this version
     * @param version       The ReportVersion metadata (id, namingKey, versionNumber, sha256Hash)
     * @param sha256Hash    SHA-256 hex of the originalBytes (computed before calling this)
     * @return Stamped + signed PDF bytes
     */
    public StampResult stampAndSign(
            byte[] originalBytes,
            User uploader,
            ReportVersion version,
            String sha256Hash,
            List<ReportVersion> allVersions   // ← full history including current
    ) throws Exception {
        byte[] withAuditPage = appendOrReplaceAuditPage(originalBytes, uploader, version, sha256Hash, allVersions);
        String signatureId = "SSV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        byte[] signedBytes = applyPadesSignature(withAuditPage, signatureId);
        return new StampResult(signedBytes, signatureId);
    }

    private byte[] appendOrReplaceAuditPage(
            byte[] original, User uploader, ReportVersion version,
            String sha256Hash, List<ReportVersion> allVersions
    ) throws Exception {

        byte[] baseBytes = stripLastAuditPage(original);

        String verifyUrl = appProperties.baseUrl() + "/verify/" + version.getId();
        String timestamp = OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfReader reader = new PdfReader(new ByteArrayInputStream(baseBytes));
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdfDoc = new PdfDocument(reader, writer);

        //  CORRECT APPROACH:
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(36, 36, 36, 36);

        document.add(new AreaBreak(com.itextpdf.layout.properties.AreaBreakType.LAST_PAGE));
        document.add(new AreaBreak(com.itextpdf.layout.properties.AreaBreakType.NEXT_PAGE));

        DeviceRgb headerBlue = new DeviceRgb(15, 76, 129);
        DeviceRgb successGreen = new DeviceRgb(22, 163, 74);
        DeviceRgb warningAmber = new DeviceRgb(217, 119, 6);
        DeviceRgb errorRed = new DeviceRgb(220, 38, 38);
        DeviceRgb lightGray = new DeviceRgb(245, 247, 250);
        DeviceRgb white = new DeviceRgb(255, 255, 255);
        DeviceRgb darkText = new DeviceRgb(30, 30, 30);

        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont mono = PdfFontFactory.createFont(StandardFonts.COURIER);

        // ── Header banner ──
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth()
                .setMarginBottom(8);
        Cell headerCell = new Cell()
                .setBackgroundColor(headerBlue)
                .setPadding(10)
                .setBorder(Border.NO_BORDER)
                .add(new Paragraph("SSV DOCUMENT MANAGEMENT SYSTEM — AUDIT TRAIL")
                        .setFont(bold).setFontSize(11)
                        .setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(2))
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
                .setFont(bold).setFontSize(8)
                .setFontColor(headerBlue)
                .setMarginBottom(3));

        Table metaTable = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .useAllAvailableWidth()
                .setMarginBottom(10);

        String[][] metaRows = {
                {"Document Version ID", version.getId().toString()},
                {"Uploaded By", uploader.getFullName() + " (" + uploader.getCompany() + ")"},
                {"Uploader Role", uploader.getRole().name()},
                {"Uploader Email", uploader.getEmail()},
                {"Upload Timestamp", timestamp},
                {"Original Filename", version.getOriginalFilename()},
                {"SHA-256 Hash", sha256Hash},
        };

        for (int i = 0; i < metaRows.length; i++) {
            DeviceRgb bg = (i % 2 == 0) ? lightGray : white;
            metaTable.addCell(new Cell()
                    .setBackgroundColor(bg).setPadding(4).setBorder(Border.NO_BORDER)
                    .add(new Paragraph(metaRows[i][0]).setFont(bold).setFontSize(7.5f)));
            PdfFont valFont = metaRows[i][0].contains("Hash") || metaRows[i][0].contains("ID") ? mono : normal;
            metaTable.addCell(new Cell()
                    .setBackgroundColor(bg).setPadding(4).setBorder(Border.NO_BORDER)
                    .add(new Paragraph(metaRows[i][1]).setFont(valFont).setFontSize(7f)));
        }
        document.add(metaTable);

        // ── Version history table ──
        document.add(new Paragraph("COMPLETE ACTION HISTORY")
                .setFont(bold).setFontSize(8)
                .setFontColor(headerBlue)
                .setMarginBottom(3));

        Table histTable = new Table(UnitValue.createPercentArray(new float[]{8, 18, 18, 14, 18, 24}))
                .useAllAvailableWidth()
                .setMarginBottom(10);

        String[] colHeaders = {"Ver.", "Date", "Actor", "Role", "Action", "Notes"};
        for (String h : colHeaders) {
            histTable.addHeaderCell(new Cell()
                    .setBackgroundColor(headerBlue)
                    .setPadding(4).setBorder(Border.NO_BORDER)
                    .add(new Paragraph(h).setFont(bold).setFontSize(7)
                            .setFontColor(ColorConstants.WHITE)));
        }

        List<ReportVersion> sorted = allVersions.stream()
                .sorted(Comparator.comparingInt(ReportVersion::getVersionNumber))
                .toList();

        int rowNum = 0;
        for (ReportVersion rv : sorted) {
            // Upload row
            DeviceRgb rowBg = (rowNum++ % 2 == 0) ? lightGray : white;
            String uploadedAt = rv.getUploadedAt() != null
                    ? rv.getUploadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : "";
            String[] uploadRow = {
                    "V" + rv.getVersionNumber(), uploadedAt,
                    rv.getUploadedBy().getFullName(), rv.getUploadedBy().getRole().name(),
                    "UPLOADED", rv.getOriginalFilename()
            };
            for (String val : uploadRow) {
                histTable.addCell(new Cell()
                        .setBackgroundColor(rowBg).setPadding(3).setBorder(Border.NO_BORDER)
                        .add(new Paragraph(val).setFont(normal).setFontSize(6.5f)));
            }

            // Review row
            if (rv.getReviewStatus() != null && rv.getReviewedBy() != null) {
                rowBg = (rowNum++ % 2 == 0) ? lightGray : white;
                String reviewedAt = rv.getReviewedAt() != null
                        ? rv.getReviewedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "";
                String notes = rv.getReviewerNotes() != null ? rv.getReviewerNotes() : "";
                if (rv.getConditions() != null && !rv.getConditions().isBlank()) {
                    notes += " | Conditions: " + rv.getConditions();
                }
                DeviceRgb actionColor = switch (rv.getReviewStatus()) {
                    case APPROVED, CONDITIONALLY_APPROVED -> successGreen;
                    case REJECTED -> errorRed;
                    case RESUBMISSION_REQUIRED -> warningAmber;
                    default -> darkText;
                };
                String[] reviewRow = {
                        "V" + rv.getVersionNumber(), reviewedAt,
                        rv.getReviewedBy().getFullName(), rv.getReviewedBy().getRole().name(),
                        rv.getReviewStatus().name(),
                        notes.length() > 80 ? notes.substring(0, 77) + "..." : notes
                };
                for (int i = 0; i < reviewRow.length; i++) {
                    histTable.addCell(new Cell()
                            .setBackgroundColor(rowBg).setPadding(3).setBorder(Border.NO_BORDER)
                            .add(new Paragraph(reviewRow[i])
                                    .setFont(i == 4 ? bold : normal)
                                    .setFontSize(6.5f)
                                    .setFontColor(i == 4 ? actionColor : darkText)));
                }
            }
        }
        document.add(histTable);

        // ── QR + legal notice ──
        byte[] qrBytes = generateQrCode(verifyUrl, 80);
        Image qrImage = new Image(ImageDataFactory.create(qrBytes))
                .setWidth(60).setHeight(60);

        Table footerTable = new Table(UnitValue.createPercentArray(new float[]{15, 85}))
                .useAllAvailableWidth();
        footerTable.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(4).add(qrImage));
        footerTable.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .add(new Paragraph(
                        "This page is automatically generated and maintained by the SSV Document Management System. " +
                                "It is replaced in full on every upload or review action — there is always exactly one audit page. " +
                                "The SHA-256 hash uniquely identifies the original uploaded file content. " +
                                "A PAdES-B digital signature covers the entire document including this page. " +
                                "Verify authenticity at: " + verifyUrl)
                        .setFont(normal).setFontSize(6.5f)
                        .setFontColor(new DeviceRgb(90, 90, 90))));
        document.add(footerTable);

        document.close();
        return out.toByteArray();
    }

    private byte[] stripLastAuditPage(byte[] pdfBytes) throws Exception {
        PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
        PdfDocument pdfDoc = new PdfDocument(reader);
        int totalPages = pdfDoc.getNumberOfPages();

        if (totalPages <= 1) {
            pdfDoc.close();
            return pdfBytes;
        }

        // Check if last page contains our audit marker
        PdfPage lastPage = pdfDoc.getPage(totalPages);
        String lastPageText = PdfTextExtractor.getTextFromPage(lastPage);
        pdfDoc.close();

        if (!lastPageText.contains("SSV DOCUMENT MANAGEMENT SYSTEM")) {
            return pdfBytes;
        }


        // Remove last page
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfReader reader2 = new PdfReader(new ByteArrayInputStream(pdfBytes));
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdfDoc2 = new PdfDocument(reader2, writer);
        pdfDoc2.removePage(pdfDoc2.getNumberOfPages());
        pdfDoc2.close();
        return out.toByteArray();
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
        PdfSignatureAppearance appearance = signer.getSignatureAppearance();
        appearance.setReason("SSV DMS — Document received and registered")
                .setLocation("Colombo, Sri Lanka");

        IExternalSignature pks = new PrivateKeySignature(privateKey, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
        IExternalDigest digest = new BouncyCastleDigest();

        signer.signDetached(digest, pks, chain, null, null, null, 0, PdfSigner.CryptoStandard.CADES);

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

    public record StampResult(byte[] signedBytes, String signatureId) {
    }
}
