package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageServiceKeyTest {

    private final StorageService service = new StorageService(null,
            new AppProperties(null, new AppProperties.MinioProperties(null, null, null, "ssv"), null, null));

    @Test
    void buildKey_roundTripsPdfAndXlsxExtensions() {
        UUID reportId = UUID.randomUUID();
        assertEquals("reports/" + reportId + "/V1/stamped.pdf",
                service.buildKey(reportId, 1, "stamped", ".pdf"));
        assertEquals("reports/" + reportId + "/V2/stamped.xlsx",
                service.buildKey(reportId, 2, "stamped", ".xlsx"));
    }
}
