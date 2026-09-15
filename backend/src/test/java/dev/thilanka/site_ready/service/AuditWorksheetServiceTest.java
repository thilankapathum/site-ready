package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.config.AppProperties;
import dev.thilanka.site_ready.entity.Company;
import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.CompanyType;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import dev.thilanka.site_ready.entity.enums.UserRole;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

        byte[] result = service.appendAuditSheet(original, actor, version, "abc123hash");

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(result))) {
            assertEquals(2, wb.getNumberOfSheets());
            Sheet audit = wb.getSheet("_Audit Trail");
            assertNotNull(audit);

            boolean foundHash = false;
            boolean foundLink = false;
            for (Row row : audit) {
                if ("SHA-256 Hash".equals(row.getCell(0).getStringCellValue())) {
                    assertEquals("abc123hash", row.getCell(1).getStringCellValue());
                    foundHash = true;
                }
                if ("Verify Authenticity".equals(row.getCell(0).getStringCellValue())) {
                    assertNotNull(row.getCell(1).getHyperlink());
                    foundLink = true;
                }
            }
            assertTrue(foundHash, "expected a SHA-256 Hash row");
            assertTrue(foundLink, "expected a Verify Authenticity hyperlink row");
        }
    }

    @Test
    void fallsBackToNumberedSheetNameOnCollision() throws Exception {
        byte[] original = blankWorkbookBytes("_Audit Trail");
        ReportVersion version = buildVersion();

        byte[] result = service.appendAuditSheet(original, version.getUploadedBy(), version, "hash");

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(result))) {
            assertNotNull(wb.getSheet("_Audit Trail"));
            assertNotNull(wb.getSheet("_Audit Trail (2)"));
        }
    }

    @Test
    void throwsOnMalformedWorkbook() {
        byte[] garbage = new byte[]{1, 2, 3, 4, 5};
        ReportVersion version = buildVersion();
        assertThrows(Exception.class, () ->
                service.appendAuditSheet(garbage, version.getUploadedBy(), version, "hash"));
    }
}
