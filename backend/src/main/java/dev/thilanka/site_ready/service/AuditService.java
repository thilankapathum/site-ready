package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.entity.AuditLog;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.AuditAction;
import dev.thilanka.site_ready.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(ReportVersion version, User actor, AuditAction action, String ipAddress, String... kvPairs) {
        Map<String, Object> metadata = new HashMap<>();
        for (int i = 0; i < kvPairs.length - 1; i += 2) {
            metadata.put(kvPairs[i], kvPairs[i + 1]);
        }
        AuditLog entry = AuditLog.builder()
                .reportVersion(version)
                .actor(actor)
                .action(action)
                .metadata(metadata.isEmpty() ? null : metadata)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(entry);
    }

    public void log(ReportVersion version, User actor, AuditAction action, String ipAddress) {
        log(version, actor, action, ipAddress, new String[0]);
    }
}
