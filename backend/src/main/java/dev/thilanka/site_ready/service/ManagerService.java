package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.dto.EngineerStatsResponse;
import dev.thilanka.site_ready.entity.ManagerEngineerAssignment;
import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.ReportVersion;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import dev.thilanka.site_ready.entity.enums.UserRole;
import dev.thilanka.site_ready.repository.ManagerEngineerAssignmentRepository;
import dev.thilanka.site_ready.repository.ReportRepository;
import dev.thilanka.site_ready.repository.ReportVersionRepository;
import dev.thilanka.site_ready.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ManagerService {
    private final ManagerEngineerAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final ReportVersionRepository versionRepository;

    // ── Assignment management ──

    @Transactional
    public void setEngineersForManager(UUID managerId, List<UUID> engineerIds) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new IllegalArgumentException("Manager not found"));
        if (manager.getRole() != UserRole.MANAGER && manager.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("User is not a MANAGER or ADMIN");
        }
        // Replace all assignments for this manager
        assignmentRepository.deleteByManagerId(managerId);
        for (UUID engineerId : engineerIds) {
            User engineer = userRepository.findById(engineerId)
                    .orElseThrow(() -> new IllegalArgumentException("Engineer not found: " + engineerId));
            if (engineer.getRole() != UserRole.ENGINEER) {
                throw new IllegalArgumentException(
                        engineer.getFullName() + " is not an ENGINEER");
            }
            ManagerEngineerAssignment assignment = new ManagerEngineerAssignment();
            assignment.setManagerId(managerId);
            assignment.setEngineerId(engineerId);
            assignmentRepository.save(assignment);
        }
    }

    public List<User> getEngineersForManager(UUID managerId) {
        return assignmentRepository.findEngineersByManagerId(managerId);
    }

    public List<UUID> getEngineerIdsForManager(UUID managerId) {
        return assignmentRepository.findByManagerId(managerId)
                .stream().map(ManagerEngineerAssignment::getEngineerId).toList();
    }

    // ── Dashboard stats ──

    public List<EngineerStatsResponse> getEngineerStats(UUID managerId) {
        List<User> engineers = getEngineersForManager(managerId);
        return engineers.stream()
                .map(this::buildEngineerStats)
                .sorted(Comparator.comparing(EngineerStatsResponse::engineerName))
                .toList();
    }

    private EngineerStatsResponse buildEngineerStats(User engineer) {
        UUID eid = engineer.getId();

        // All reports assigned to this engineer
        List<Report> reports =
                reportRepository.findByAssignedEngineerId(eid);

        long total           = reports.size();
        long pending         = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.PENDING_REVIEW).count();
        long resubmission    = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.RESUBMISSION_REQUIRED).count();
        long approved        = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.APPROVED).count();
        long condApproved    = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.CONDITIONALLY_APPROVED).count();
        long rejected        = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.REJECTED).count();
        long totalApproved   = approved + condApproved;

        // Average review time: time between vendor upload and engineer's review upload
        // Computed across all reviewed versions assigned to this engineer
        List<Report> assignedReports =
                reportRepository.findByAssignedEngineerId(eid);

        List<Double> reviewHours = new ArrayList<>();
        for (Report report : assignedReports) {
            List<ReportVersion> versions =
                    versionRepository.findByReportIdOrderByVersionNumberDesc(report.getId());
            for (ReportVersion v : versions) {
                if (v.getReviewedAt() != null && v.getUploadedAt() != null) {
                    double hours = Duration.between(v.getUploadedAt(), v.getReviewedAt())
                            .toMinutes() / 60.0;
                    if (hours >= 0) reviewHours.add(hours);
                }
            }
        }
        Double avgReviewHours = reviewHours.isEmpty() ? null
                : reviewHours.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Longest currently-pending report
        Long longestPendingDays = null;
        String longestPendingReport = null;
        Optional<Report> longestPending = reports.stream()
                .filter(r -> r.getCurrentStatus() == ReportStatus.PENDING_REVIEW)
                .min(Comparator.comparing(Report::getUpdatedAt));

        if (longestPending.isPresent()) {
            Report lp = longestPending.get();
            longestPendingDays = Duration.between(
                    lp.getUpdatedAt(), OffsetDateTime.now()).toDays();
            longestPendingReport = lp.getNamingKey();
        }

        return new EngineerStatsResponse(
                eid,
                engineer.getFullName(),
                engineer.getEmail(),
                engineer.getCompany().getName(),
                total, pending, resubmission,
                approved, condApproved, totalApproved,
                rejected, avgReviewHours,
                longestPendingDays, longestPendingReport
        );
    }

    // ── Manager's combined review queue ──

    public org.springframework.data.domain.Page<Report>
    getManagerQueue(UUID managerId, org.springframework.data.domain.Pageable pageable) {
        List<UUID> engineerIds = getEngineerIdsForManager(managerId);
        if (engineerIds.isEmpty()) {
            return org.springframework.data.domain.Page.empty(pageable);
        }
        return reportRepository.findByAssignedEngineerIdIn(engineerIds, pageable);
    }
}
