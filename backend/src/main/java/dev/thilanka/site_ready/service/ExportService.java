package dev.thilanka.site_ready.service;


import dev.thilanka.site_ready.entity.Report;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] exportExcel(List<Report> reports) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("SSV Reports");
            sheet.setDefaultColumnWidth(20);

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Title row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("SSV Document Management System — Report Export");
            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

            // Column headers
            String[] headers = {
                    "Site ID", "Project", "Version", "Status", "Responsibility",
                    "Assigned Engineer", "Vendor", "Created At", "Last Updated", "Naming Key"
            };
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowIdx = 3;
            CellStyle altStyle = wb.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (Report r : reports) {
                Row row = sheet.createRow(rowIdx++);
                if (rowIdx % 2 == 0) {
                    for (int c = 0; c < headers.length; c++) row.createCell(c).setCellStyle(altStyle);
                }
                setCellValue(row, 0, r.getSiteId());
                setCellValue(row, 1, r.getProject());
                setCellValue(row, 2, "V" + r.getCurrentVersion());
                setCellValue(row, 3, r.getCurrentStatus().name());
                setCellValue(row, 4, r.getCurrentResponsibility().name());
                setCellValue(row, 5, r.getAssignedEngineer() != null ? r.getAssignedEngineer().getFullName() : "Unassigned");
                setCellValue(row, 6, r.getCreatedByVendor().getFullName() + " (" + r.getCreatedByVendor().getCompany() + ")");
                setCellValue(row, 7, r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : "");
                setCellValue(row, 8, r.getUpdatedAt() != null ? r.getUpdatedAt().format(FMT) : "");
                setCellValue(row, 9, r.getNamingKey());
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportCsv(List<Report> reports) {
        StringBuilder sb = new StringBuilder();
        sb.append("Site ID,Project,Version,Status,Responsibility,Assigned Engineer,Vendor,Created At,Last Updated,Naming Key\n");
        for (Report r : reports) {
            sb.append(csv(r.getSiteId())).append(",")
                    .append(csv(r.getProject())).append(",")
                    .append("V").append(r.getCurrentVersion()).append(",")
                    .append(r.getCurrentStatus()).append(",")
                    .append(r.getCurrentResponsibility()).append(",")
                    .append(csv(r.getAssignedEngineer() != null ? r.getAssignedEngineer().getFullName() : "Unassigned")).append(",")
                    .append(csv(r.getCreatedByVendor().getFullName())).append(",")
                    .append(r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : "").append(",")
                    .append(r.getUpdatedAt() != null ? r.getUpdatedAt().format(FMT) : "").append(",")
                    .append(csv(r.getNamingKey())).append("\n");
        }
        return sb.toString().getBytes();
    }

    private void setCellValue(Row row, int col, String value) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
    }

    private String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
