package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.config.AppProperties;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import lombok.RequiredArgsConstructor;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * xlsx equivalent of {@link PdfStampService}'s audit-trail page: appends a worksheet
 * recording who uploaded/reviewed the file, when, and its hash, plus a link to the
 * verify endpoint. Unlike PDF, there is no PAdES-equivalent cryptographic signature
 * applied to xlsx files.
 */
@Service
@RequiredArgsConstructor
public class AuditWorksheetService {

    private static final String SHEET_NAME = "_Audit Trail";

    private final AppProperties appProperties;

    public byte[] appendAuditSheet(
            byte[] originalBytes, User actor, ReportVersion version, String sha256Hash
    ) throws Exception {
        String verifyUrl = appProperties.baseUrl() + "/verify/" + version.getId();
        String timestamp = OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(originalBytes))) {
            Sheet sheet = wb.createSheet(uniqueSheetName(wb));

            CellStyle labelStyle = wb.createCellStyle();
            Font labelFont = wb.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);

            int rowNum = 0;
            rowNum = addRow(sheet, labelStyle, rowNum, "Document Version ID", version.getId().toString());
            rowNum = addRow(sheet, labelStyle, rowNum, "Site ID", version.getReport().getSiteId());
            rowNum = addRow(sheet, labelStyle, rowNum, "Project", version.getReport().getProject());
            rowNum = addRow(sheet, labelStyle, rowNum, "Version", "V" + version.getVersionNumber());
            rowNum = addRow(sheet, labelStyle, rowNum, "Uploaded/Reviewed By",
                    actor.getFullName() + " (" + actor.getCompany().getName() + ")");
            rowNum = addRow(sheet, labelStyle, rowNum, "Role", actor.getRole().name());
            rowNum = addRow(sheet, labelStyle, rowNum, "Timestamp", timestamp);
            rowNum = addRow(sheet, labelStyle, rowNum, "SHA-256 Hash", sha256Hash);
            addLinkRow(wb, sheet, labelStyle, rowNum, "Verify Authenticity", verifyUrl);

            sheet.setColumnWidth(0, 30 * 256);
            sheet.setColumnWidth(1, 60 * 256);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        }
    }

    private int addRow(Sheet sheet, CellStyle labelStyle, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        row.createCell(1).setCellValue(value);
        return rowNum + 1;
    }

    private void addLinkRow(Workbook wb, Sheet sheet, CellStyle labelStyle, int rowNum, String label, String url) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(url);
        Hyperlink link = wb.getCreationHelper().createHyperlink(HyperlinkType.URL);
        link.setAddress(url);
        valueCell.setHyperlink(link);
    }

    private String uniqueSheetName(Workbook wb) {
        String candidate = SHEET_NAME;
        int suffix = 2;
        while (wb.getSheet(candidate) != null) {
            candidate = SHEET_NAME + " (" + suffix++ + ")";
        }
        return candidate;
    }
}
