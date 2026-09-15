package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.config.AppProperties;
import dev.thilanka.site_ready.entity.Company;
import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.CompanyType;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import dev.thilanka.site_ready.entity.enums.UserRole;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditWorksheetServiceTest {

    private final AuditWorksheetService service =
            new AuditWorksheetService(new AppProperties(null, null, null, "http://localhost:8080"));

    private byte[] blankWorkbookBytes(String... existingSheetNames) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("Data");
            for (String name : existingSheetNames) {
                wb.createSheet(name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private ReportVersion buildVersion() {
        Company company = Company.builder().name("Acme").shortName("ACME").type(CompanyType.VENDOR).build();
        User uploader = User.builder().fullName("Jane Vendor").company(company).role(UserRole.VENDOR).build();
        Report report = Report.builder().siteId("KY0001").project("Project1").rat("4G")
                .currentStatus(ReportStatus.PENDING_REVIEW).currentVersion(1).build();
        return ReportVersion.builder()
                .id(UUID.randomUUID())
                .report(report)
                .versionNumber(1)
                .uploadedBy(uploader)
                .build();
    }

    @Test
    void appendsAuditSheetWithExpectedFields() throws Exception {
        byte[] original = blankWorkbookBytes();
        ReportVersion version = buildVersion();
        User actor = version.getUploadedBy();

        byte[] result = service.appendAuditSheet(original, actor, version, "abc123hash", List.of(version));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(result))) {
            assertEquals(2, wb.getNumberOfSheets());
            Sheet audit = wb.getSheet("Version History");
            assertNotNull(audit);

            boolean foundHash = false;
            boolean foundLink = false;
            boolean foundActionHistoryHeader = false;
            for (Row row : audit) {
                Cell first = row.getCell(0);
                if (first == null || first.getCellType() != CellType.STRING) continue;
                if ("SHA-256 Hash".equals(first.getStringCellValue())) {
                    assertEquals("abc123hash", row.getCell(1).getStringCellValue());
                    foundHash = true;
                }
                if ("Verify Authenticity".equals(first.getStringCellValue())) {
                    assertNotNull(row.getCell(1).getHyperlink());
                    foundLink = true;
                }
                if ("ACTION HISTORY".equals(first.getStringCellValue())) {
                    foundActionHistoryHeader = true;
                }
            }
            assertTrue(foundHash, "expected a SHA-256 Hash row");
            assertTrue(foundLink, "expected a Verify Authenticity hyperlink row");
            assertTrue(foundActionHistoryHeader, "expected an ACTION HISTORY section");
        }
    }

    @Test
    void fallsBackToNumberedSheetNameOnCollisionWithUnrelatedSheet() throws Exception {
        // A pre-existing sheet that merely happens to be named "Version History" but has no
        // banner marker (i.e. it's the user's own data, not a prior audit stamp) must be
        // left alone, so the fresh audit sheet falls back to a numbered name.
        byte[] original = blankWorkbookBytes("Version History");
        ReportVersion version = buildVersion();

        byte[] result = service.appendAuditSheet(
                original, version.getUploadedBy(), version, "hash", List.of(version));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(result))) {
            assertNotNull(wb.getSheet("Version History"));
            assertNotNull(wb.getSheet("Version History (2)"));
        }
    }

    @Test
    void reStampingReplacesPriorAuditSheetInPlace() throws Exception {
        // Simulates a vendor downloading the stamped file and re-uploading it for the
        // next version (or an engineer re-uploading it for review): the bytes handed in
        // already contain a prior audit trail sheet, which must be replaced, not stacked.
        byte[] original = blankWorkbookBytes();
        ReportVersion version = buildVersion();
        User actor = version.getUploadedBy();

        byte[] firstPass = service.appendAuditSheet(original, actor, version, "hash-v1", List.of(version));
        byte[] secondPass = service.appendAuditSheet(firstPass, actor, version, "hash-v2", List.of(version));

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(secondPass))) {
            assertEquals(2, wb.getNumberOfSheets(), "expected the original data sheet plus exactly one audit sheet");
            assertNotNull(wb.getSheet("Version History"));
            assertNull(wb.getSheet("Version History (2)"));

            Sheet audit = wb.getSheet("Version History");
            boolean foundLatestHash = false;
            for (Row row : audit) {
                Cell first = row.getCell(0);
                if (first != null && first.getCellType() == CellType.STRING
                        && "SHA-256 Hash".equals(first.getStringCellValue())) {
                    assertEquals("hash-v2", row.getCell(1).getStringCellValue());
                    foundLatestHash = true;
                }
            }
            assertTrue(foundLatestHash, "expected the replaced sheet to carry the latest hash");
        }
    }

    @Test
    void throwsOnMalformedWorkbook() {
        byte[] garbage = new byte[]{1, 2, 3, 4, 5};
        ReportVersion version = buildVersion();
        assertThrows(Exception.class, () ->
                service.appendAuditSheet(garbage, version.getUploadedBy(), version, "hash", List.of(version)));
    }
}
