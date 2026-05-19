package dev.thilanka.site_ready.repository;

import dev.thilanka.site_ready.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByReportVersionIdOrderByOccurredAtAsc(UUID versionId);
    List<AuditLog> findByActorIdOrderByOccurredAtDesc(UUID actorId);
}
