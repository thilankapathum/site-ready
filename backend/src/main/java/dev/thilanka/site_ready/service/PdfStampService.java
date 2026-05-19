package dev.thilanka.site_ready.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.layout.Document;
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
     * @param originalBytes  Raw bytes of the uploaded PDF
     * @param uploader       The user who uploaded this version
     * @param version        The ReportVersion metadata (id, namingKey, versionNumber, sha256Hash)
     * @param sha256Hash     SHA-256 hex of the originalBytes (computed before calling this)
     * @return Stamped + signed PDF bytes
     */
    public StampResult stampAndSign(
            byte[] originalBytes,
            User uploader,
            ReportVersion version,
            String sha256Hash
    ) throws Exception {
        // Step 1: Append audit trail page
        byte[] withAuditPage = appendAuditPage(originalBytes, uploader, version, sha256Hash);

        // Step 2: Apply PAdES-B-LT digital signature
        String signatureId = "SSV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        byte[] signedBytes = applyPadesSignature(withAuditPage, signatureId);

        return new StampResult(signedBytes, signatureId);
    }

    private byte[] appendAuditPage(byte[] original, User uploader, ReportVersion version, String sha256Hash)
            throws Exception {

        String verifyUrl = appProperties.baseUrl() + "/verify/" + version.getId();
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfReader reader = new PdfReader(new ByteArrayInputStream(original));
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdfDoc = new PdfDocument(reader, writer);
        Document document = new Document(pdfDoc);

        // Add new page at the end
        pdfDoc.addNewPage(PageSize.A4);
        int lastPage = pdfDoc.getNumberOfPages();
//        document.setPageNumber(lastPage);

        DeviceRgb headerColor = new DeviceRgb(15, 76, 129);   // Dark telecom blue
        DeviceRgb lightGray = new DeviceRgb(245, 245, 245);
        DeviceRgb borderColor = new DeviceRgb(200, 200, 200);

        // ---- Header banner ----
        Table header = new Table(UnitValue.createPercentArray(new float[]{1})).useAllAvailableWidth();
        Cell headerCell = new Cell()
                .setBackgroundColor(headerColor)
                .setPadding(16)
                .add(new Paragraph("SSV DOCUMENT MANAGEMENT SYSTEM — AUDIT TRAIL")
                        .setFontColor(ColorConstants.WHITE)
                        .setBold()
                        .setFontSize(14)
                        .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph("SYSTEM-GENERATED — DO NOT ALTER")
                        .setFontColor(new DeviceRgb(180, 210, 240))
                        .setFontSize(9)
                        .setTextAlignment(TextAlignment.CENTER));
        header.addCell(headerCell);

        // ---- Metadata table ----
        Table meta = new Table(UnitValue.createPercentArray(new float[]{35, 65})).useAllAvailableWidth();
        String[][] rows = {
                {"Document ID",      version.getId().toString()},
                {"Site ID",          version.getReport().getSiteId()},
                {"Project",          version.getReport().getProject()},
                {"Version",          "V" + version.getVersionNumber()},
                {"Uploader Name",    uploader.getFullName()},
                {"Uploader Company", uploader.getCompany()},
                {"Uploader Role",    uploader.getRole().name()},
                {"Uploader Email",   uploader.getEmail()},
                {"Upload Timestamp", timestamp},
                {"SHA-256 Hash",     sha256Hash},
        };
        for (String[] row : rows) {
            meta.addCell(new Cell().setBackgroundColor(lightGray)
                    .setPadding(5).add(new Paragraph(row[0]).setBold().setFontSize(9)));
            meta.addCell(new Cell().setPadding(5)
                    .add(new Paragraph(row[1]).setFontSize(9).setFont(
                            com.itextpdf.kernel.font.PdfFontFactory.createFont(
                                    com.itextpdf.io.font.constants.StandardFonts.COURIER))));
        }

        // ---- Legal notice ----
        Paragraph legal = new Paragraph(
                "This page was automatically appended by the SSV Document Management System upon receipt of the above document. " +
                        "The SHA-256 hash above uniquely identifies the content of the original uploaded file. " +
                        "Any alteration to the document will invalidate this hash. " +
                        "A digital signature (PAdES-B-LT) has been applied to the complete document including this audit page. " +
                        "Scan the QR code or visit the verification URL to confirm authenticity."
        ).setFontSize(8).setTextAlignment(TextAlignment.JUSTIFIED)
                .setMarginTop(8).setFontColor(new DeviceRgb(80, 80, 80));

        // ---- QR Code ----
        byte[] qrBytes = generateQrCode(verifyUrl, 120);
        Image qrImage = new Image(com.itextpdf.io.image.ImageDataFactory.create(qrBytes));
        qrImage.setWidth(80).setHeight(80);

        Paragraph verifyPara = new Paragraph("Verify at: " + verifyUrl)
                .setFontSize(7).setFontColor(new DeviceRgb(80, 80, 80));

        // Add all elements to the last page
        document.add(new AreaBreak(com.itextpdf.layout.properties.AreaBreakType.NEXT_PAGE));
        document.add(header.setMarginBottom(10));
        document.add(meta.setMarginBottom(10));
        document.add(legal);
        document.add(qrImage.setMarginTop(10));
        document.add(verifyPara);

        document.close();
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

    public record StampResult(byte[] signedBytes, String signatureId) {}
}
