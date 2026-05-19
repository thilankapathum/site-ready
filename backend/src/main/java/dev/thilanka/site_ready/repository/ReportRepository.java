package dev.thilanka.site_ready.repository;

import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import dev.thilanka.site_ready.entity.enums.Responsibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> , JpaSpecificationExecutor<Report> {
    Optional<Report> findByNamingKey(String namingKey);

    boolean existsByNamingKey(String namingKey);

    // Engineer dashboard — reports assigned to me
    Page<Report> findByAssignedEngineerIdAndCurrentResponsibility(
            UUID engineerId, Responsibility responsibility, Pageable pageable);

    Page<Report> findByAssignedEngineerId(UUID engineerId, Pageable pageable);

    // Vendor dashboard — reports I created
    Page<Report> findByCreatedByVendorId(UUID vendorId, Pageable pageable);

    // Search / export — flexible filters
    @Query("""
        SELECT r FROM Report r
        WHERE (:siteId IS NULL OR LOWER(r.siteId) LIKE LOWER(CONCAT('%', :siteId, '%')))
          AND (:project IS NULL OR LOWER(r.project) LIKE LOWER(CONCAT('%', :project, '%')))
          AND (:status IS NULL OR r.currentStatus = :status)
          AND (:engineerId IS NULL OR r.assignedEngineer.id = :engineerId)
          AND (:vendorId IS NULL OR r.createdByVendor.id = :vendorId)
        ORDER BY r.updatedAt DESC
        """)
    Page<Report> search(
            @Param("siteId") String siteId,
            @Param("project") String project,
            @Param("status") ReportStatus status,
            @Param("engineerId") UUID engineerId,
            @Param("vendorId") UUID vendorId,
            Pageable pageable
    );

    // All reports for export (no pagination)
    @Query("""
            SELECT r FROM Report r
            WHERE (:siteId IS NULL OR LOWER(r.siteId) LIKE LOWER(CONCAT('%', :siteId, '%')))
              AND (:project IS NULL OR LOWER(r.project) LIKE LOWER(CONCAT('%', :project, '%')))
              AND (:status IS NULL OR r.currentStatus = :status)
              AND (:engineerId IS NULL OR r.assignedEngineer.id = :engineerId)
              AND (:vendorId IS NULL OR r.createdByVendor.id = :vendorId)
            ORDER BY r.siteId, r.project
            """)
    List<Report> searchAll(
            @Param("siteId") String siteId,
            @Param("project") String project,
            @Param("status") ReportStatus status,
            @Param("engineerId") UUID engineerId,
            @Param("vendorId") UUID vendorId
    );
}
