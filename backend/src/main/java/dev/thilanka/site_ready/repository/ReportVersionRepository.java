package dev.thilanka.site_ready.repository;

import dev.thilanka.site_ready.entity.ReportVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportVersionRepository extends JpaRepository<ReportVersion, UUID> {
    List<ReportVersion> findByReportIdOrderByVersionNumberDesc(UUID reportId);
    Optional<ReportVersion> findByReportIdAndVersionNumber(UUID reportId, int versionNumber);
    long countByReportId(UUID reportId);
}
