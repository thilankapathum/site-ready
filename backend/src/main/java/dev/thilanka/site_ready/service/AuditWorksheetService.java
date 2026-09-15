package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.config.AppProperties;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import lombok.RequiredArgsConstructor;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * xlsx equivalent of {@link PdfStampService}'s audit-trail page: appends a worksheet
 * recording who uploaded/reviewed the file, when, and its hash, plus the report's
 * action history — mirroring the PDF audit page's layout and colors with fonts that
 * ship with any standard Office/LibreOffice install (Calibri/Consolas), since a
 * viewer's machine won't have the PDF pipeline's embedded custom fonts.
 *
 * Unlike the PDF pipeline, there is no cryptographic signature equivalent for xlsx,
 * and page-level "DOCUMENT CHANGES" diffing doesn't apply to a spreadsheet, so that
 * table is intentionally omitted here.
 */
@Service
@RequiredArgsConstructor
public class AuditWorksheetService {

    private static final String SHEET_NAME = "Version History";
    private static final String FONT_BODY = "Calibri";
    private static final String FONT_MONO = "Consolas";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");
    private static final DateTimeFormatter SHORT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppProperties appProperties;

    public byte[] appendAuditSheet(
            byte[] originalBytes, User actor, ReportVersion version,
            String sha256Hash, List<ReportVersion> allVersions
    ) throws Exception {
        String verifyUrl = appProperties.baseUrl() + "/verify/" + version.getId();
        String timestamp = OffsetDateTime.now().format(TS_FMT);

        try (XSSFWorkbook wb = (XSSFWorkbook) WorkbookFactory.create(new ByteArrayInputStream(originalBytes))) {
            stripExistingAuditSheets(wb);
            Sheet sheet = wb.createSheet(uniqueSheetName(wb));
            sheet.setColumnWidth(0, 26 * 256);
            sheet.setColumnWidth(1, 34 * 256);
            sheet.setColumnWidth(2, 20 * 256);
            sheet.setColumnWidth(3, 14 * 256);
            sheet.setColumnWidth(4, 18 * 256);
            sheet.setColumnWidth(5, 40 * 256);

            Styles styles = new Styles(wb);
            int rowNum = 0;
            rowNum = addHeaderBanner(sheet, styles, rowNum, version);
            rowNum++;
            rowNum = addCurrentVersionDetails(sheet, styles, rowNum, actor, version, sha256Hash, timestamp);
            rowNum++;
            rowNum = addActionHistory(sheet, styles, rowNum, allVersions);
            rowNum++;
            addFooter(sheet, styles, rowNum, verifyUrl);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        }
    }

    private int addHeaderBanner(Sheet sheet, Styles styles, int rowNum, ReportVersion version) {
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 5));
        setCell(sheet, rowNum, 0, "SLTMobitel SiteReady", styles.bannerTitle);
        rowNum++;

        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 5));
        setCell(sheet, rowNum, 0, "SSV DOCUMENT MANAGEMENT SYSTEM — VERSION HISTORY", styles.bannerSubtitle);
        rowNum++;

        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 5));
        String summary = String.format(
                "Site ID: %s  |  Project: %s  |  RAT: %s  |  Version: V%d  |  Status: %s",
                version.getReport().getSiteId(), version.getReport().getProject(),
                version.getReport().getRat(), version.getReport().getCurrentVersion(),
                version.getReport().getCurrentStatus().name());
        setCell(sheet, rowNum, 0, summary, styles.bannerMeta);
        rowNum++;
        return rowNum;
    }

    private int addCurrentVersionDetails(
            Sheet sheet, Styles styles, int rowNum, User actor, ReportVersion version,
            String sha256Hash, String timestamp
    ) {
        setCell(sheet, rowNum, 0, "CURRENT VERSION DETAILS", styles.sectionHeader);
        rowNum++;

        String[][] rows = {
                {"Document Version ID", version.getId().toString(), "mono"},
                {"Uploaded/Reviewed By", actor.getFullName() + " (" + actor.getCompany().getName() + ")", "text"},
                {"Role", actor.getRole().name(), "text"},
                {"Email", actor.getEmail(), "text"},
                {"Timestamp", timestamp, "text"},
                {"Original Filename", version.getOriginalFilename(), "text"},
                {"SHA-256 Hash", sha256Hash, "mono"},
        };

        for (int i = 0; i < rows.length; i++) {
            CellStyle labelStyle = (i % 2 == 0) ? styles.labelShaded : styles.labelPlain;
            CellStyle valueStyle = "mono".equals(rows[i][2])
                    ? ((i % 2 == 0) ? styles.valueMonoShaded : styles.valueMonoPlain)
                    : ((i % 2 == 0) ? styles.valueShaded : styles.valuePlain);
            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 1, 5));
            setCell(sheet, rowNum, 0, rows[i][0], labelStyle);
            setCell(sheet, rowNum, 1, rows[i][1], valueStyle);
            rowNum++;
        }
        return rowNum;
    }

    private int addActionHistory(Sheet sheet, Styles styles, int rowNum, List<ReportVersion> allVersions) {
        if (allVersions == null || allVersions.isEmpty()) {
            return rowNum;
        }
        setCell(sheet, rowNum, 0, "ACTION HISTORY", styles.sectionHeader);
        rowNum++;

        String[] headers = {"Ver.", "Date", "Actor", "Role", "Action", "Notes"};
        for (int c = 0; c < headers.length; c++) {
            setCell(sheet, rowNum, c, headers[c], styles.tableHeader);
        }
        rowNum++;

        List<ReportVersion> sorted = allVersions.stream()
                .sorted(Comparator.comparingInt(ReportVersion::getVersionNumber)).toList();

        int dataRow = 0;
        for (ReportVersion rv : sorted) {
            CellStyle rowStyle = (dataRow++ % 2 == 0) ? styles.tableRowShaded : styles.tableRowPlain;
            String uploadedAt = rv.getUploadedAt() != null ? rv.getUploadedAt().format(SHORT_FMT) : "";
            setCell(sheet, rowNum, 0, "V" + rv.getVersionNumber(), rowStyle);
            setCell(sheet, rowNum, 1, uploadedAt, rowStyle);
            setCell(sheet, rowNum, 2,
                    rv.getUploadedBy().getFullName() + " (" + rv.getUploadedBy().getCompany().getName() + ")", rowStyle);
            setCell(sheet, rowNum, 3, rv.getUploadedBy().getRole().name(), rowStyle);
            setCell(sheet, rowNum, 4, "UPLOADED", rowStyle);
            setCell(sheet, rowNum, 5, rv.getOriginalFilename(), rowStyle);
            rowNum++;

            if (rv.getReviewStatus() != null && rv.getReviewedBy() != null) {
                rowStyle = (dataRow++ % 2 == 0) ? styles.tableRowShaded : styles.tableRowPlain;
                String reviewedAt = rv.getReviewedAt() != null ? rv.getReviewedAt().format(SHORT_FMT) : "";
                String notes = rv.getReviewerNotes() != null ? rv.getReviewerNotes() : "";
                if (rv.getConditions() != null && !rv.getConditions().isBlank()) {
                    notes += " | Conditions: " + rv.getConditions();
                }
                CellStyle actionStyle = switch (rv.getReviewStatus()) {
                    case APPROVED, CONDITIONALLY_APPROVED -> styles.actionSuccess;
                    case REJECTED -> styles.actionError;
                    case RESUBMISSION_REQUIRED -> styles.actionWarning;
                    default -> styles.tableRowPlain;
                };
                setCell(sheet, rowNum, 0, "V" + rv.getVersionNumber(), rowStyle);
                setCell(sheet, rowNum, 1, reviewedAt, rowStyle);
                setCell(sheet, rowNum, 2,
                        rv.getReviewedBy().getFullName() + " (" + rv.getReviewedBy().getCompany().getName() + ")", rowStyle);
                setCell(sheet, rowNum, 3, rv.getReviewedBy().getRole().name(), rowStyle);
                setCell(sheet, rowNum, 4, rv.getReviewStatus().name(), actionStyle);
                setCell(sheet, rowNum, 5, notes, rowStyle);
                rowNum++;
            }
        }
        return rowNum;
    }

    private void addFooter(Sheet sheet, Styles styles, int rowNum, String verifyUrl) {
        Row row = sheet.createRow(rowNum);
        Cell label = row.createCell(0);
        label.setCellValue("Verify Authenticity");
        label.setCellStyle(styles.labelShaded);
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 1, 5));
        Cell link = row.createCell(1);
        link.setCellValue(verifyUrl);
        link.setCellStyle(styles.valueShaded);
        Hyperlink hyperlink = sheet.getWorkbook().getCreationHelper().createHyperlink(HyperlinkType.URL);
        hyperlink.setAddress(verifyUrl);
        link.setHyperlink(hyperlink);
        rowNum++;

        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 5));
        setCell(sheet, rowNum, 0,
                "This audit trail worksheet is automatically generated on every upload or review action, " +
                        "recording uploader/reviewer identity, timestamp, and the file's SHA-256 hash. " +
                        "Verify authenticity at: " + verifyUrl,
                styles.footerNote);
    }

    private void setCell(Sheet sheet, int rowIdx, int colIdx, String value, CellStyle style) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /**
     * Removes any audit trail sheet(s) left over from a prior stamping pass — e.g. a
     * vendor downloads the stamped V(n) file, edits it, and re-uploads it as V(n+1),
     * or an engineer re-uploads a stamped file for review — so each pass replaces the
     * sheet in place instead of accumulating a new one every step, mirroring how
     * {@link PdfStampService} strips the previous audit page before appending a fresh one.
     * Identified by name (our sheet or its numbered-collision variants) plus the banner
     * marker in cell A1, so a user's own sheet that happens to be named "Version History"
     * is left untouched.
     */
    private void stripExistingAuditSheets(Workbook wb) {
        for (int i = wb.getNumberOfSheets() - 1; i >= 0; i--) {
            Sheet sheet = wb.getSheetAt(i);
            String name = sheet.getSheetName();
            if (!(name.equals(SHEET_NAME) || name.matches(java.util.regex.Pattern.quote(SHEET_NAME) + " \\(\\d+\\)"))) {
                continue;
            }
            Row firstRow = sheet.getRow(0);
            Cell firstCell = firstRow != null ? firstRow.getCell(0) : null;
            if (firstCell != null && firstCell.getCellType() == CellType.STRING
                    && "SLTMobitel SiteReady".equals(firstCell.getStringCellValue())) {
                wb.removeSheetAt(i);
            }
        }
    }

    private String uniqueSheetName(Workbook wb) {
        String candidate = SHEET_NAME;
        int suffix = 2;
        while (wb.getSheet(candidate) != null) {
            candidate = SHEET_NAME + " (" + suffix++ + ")";
        }
        return candidate;
    }

    /** Colors mirror PdfStampService's palette; fonts use Calibri/Consolas instead of the PDF pipeline's embedded fonts. */
    private static final class Styles {
        final CellStyle bannerTitle, bannerSubtitle, bannerMeta, sectionHeader,
                labelShaded, labelPlain, valueShaded, valuePlain, valueMonoShaded, valueMonoPlain,
                tableHeader, tableRowShaded, tableRowPlain, actionSuccess, actionError, actionWarning, footerNote;

        Styles(XSSFWorkbook wb) {
            XSSFColor headerBlue = rgb(97, 115, 137);
            XSSFColor successGreen = rgb(0, 160, 70);
            XSSFColor warningAmber = rgb(200, 120, 0);
            XSSFColor errorRed = rgb(200, 30, 40);
            XSSFColor lightGray = rgb(245, 245, 244);
            XSSFColor white = rgb(255, 255, 255);
            XSSFColor darkText = rgb(26, 39, 58);
            XSSFColor mutedText = rgb(90, 90, 90);

            XSSFFont bold = font(wb, FONT_BODY, true, null);
            XSSFFont normal = font(wb, FONT_BODY, false, darkText);
            XSSFFont mono = font(wb, FONT_MONO, false, darkText);
            XSSFFont whiteBold = font(wb, FONT_BODY, true, white);
            XSSFFont whiteNormal = font(wb, FONT_BODY, false, rgb(220, 230, 240));

            bannerTitle = fillStyle(wb, headerBlue, whiteBold, 14);
            bannerSubtitle = fillStyle(wb, headerBlue, whiteBold, 10);
            bannerMeta = fillStyle(wb, headerBlue, whiteNormal, 9);

            sectionHeader = textStyle(wb, font(wb, FONT_BODY, true, headerBlue), 11);

            labelShaded = fillStyle(wb, lightGray, bold, 10);
            labelPlain = fillStyle(wb, white, bold, 10);
            valueShaded = fillStyle(wb, lightGray, normal, 10);
            valuePlain = fillStyle(wb, white, normal, 10);
            valueMonoShaded = fillStyle(wb, lightGray, mono, 10);
            valueMonoPlain = fillStyle(wb, white, mono, 10);

            tableHeader = fillStyle(wb, headerBlue, whiteBold, 10);
            tableRowShaded = fillStyle(wb, lightGray, normal, 9);
            tableRowPlain = fillStyle(wb, white, normal, 9);
            actionSuccess = fillStyle(wb, white, font(wb, FONT_BODY, true, successGreen), 9);
            actionError = fillStyle(wb, white, font(wb, FONT_BODY, true, errorRed), 9);
            actionWarning = fillStyle(wb, white, font(wb, FONT_BODY, true, warningAmber), 9);

            footerNote = textStyle(wb, font(wb, FONT_BODY, false, mutedText), 8);
        }

        private static XSSFColor rgb(int r, int g, int b) {
            return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
        }

        private static XSSFFont font(XSSFWorkbook wb, String name, boolean bold, XSSFColor color) {
            XSSFFont f = wb.createFont();
            f.setFontName(name);
            f.setBold(bold);
            if (color != null) f.setColor(color);
            return f;
        }

        private static XSSFCellStyle fillStyle(XSSFWorkbook wb, XSSFColor bg, XSSFFont font, int fontSize) {
            XSSFCellStyle style = wb.createCellStyle();
            style.setFillForegroundColor(bg);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            font.setFontHeightInPoints((short) fontSize);
            style.setFont(font);
            style.setWrapText(true);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static XSSFCellStyle textStyle(XSSFWorkbook wb, XSSFFont font, int fontSize) {
            XSSFCellStyle style = wb.createCellStyle();
            font.setFontHeightInPoints((short) fontSize);
            style.setFont(font);
            style.setWrapText(true);
            return style;
        }
    }
}
