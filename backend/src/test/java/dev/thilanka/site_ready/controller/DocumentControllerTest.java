package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.repository.ReportVersionRepository;
import dev.thilanka.site_ready.service.DocumentService;
import dev.thilanka.site_ready.service.ReportAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentControllerTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final ReportVersionRepository versionRepository = mock(ReportVersionRepository.class);
    private final DocumentController controller = new DocumentController(
            documentService, null, null, null, versionRepository, null, null,
            mock(ReportAccessService.class), null);

    @Test
    void downloadsRejectVersionFromAnotherReport() {
        UUID ownReportId = UUID.randomUUID();
        Report otherReport = Report.builder().id(UUID.randomUUID()).build();
        ReportVersion otherVersion = ReportVersion.builder().id(UUID.randomUUID()).report(otherReport).build();
        when(versionRepository.findById(otherVersion.getId())).thenReturn(Optional.of(otherVersion));
        User vendor = new User();
        var req = new MockHttpServletRequest();

        assertThrows(IllegalArgumentException.class,
                () -> controller.downloadVersion(ownReportId, otherVersion.getId(), vendor, req));
        assertThrows(IllegalArgumentException.class,
                () -> controller.downloadReviewedVersion(ownReportId, otherVersion.getId(), vendor, req));
        verify(documentService, never()).downloadVersionStamped(any(), any(), any());
        verify(documentService, never()).downloadReviewedStamped(any(), any(), any());
    }
}
