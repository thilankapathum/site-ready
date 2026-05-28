package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.dto.Breakdown;
import dev.thilanka.site_ready.dto.EngineerRankResponse;
import dev.thilanka.site_ready.dto.EngineerStatsResponse;
import dev.thilanka.site_ready.dto.PendingBreakdown;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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

    public List<EngineerStatsResponse> getEngineersStats(UUID managerId) {
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

        long total = reports.size();
        long pending = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.PENDING_REVIEW).count();
        long resubmission = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.RESUBMISSION_REQUIRED).count();
        long approved = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.APPROVED).count();
        long condApproved = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.CONDITIONALLY_APPROVED).count();
        long rejected = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.REJECTED).count();
        long totalApproved = approved + condApproved;

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
                if (v.getReviewedAt() == null && v.getUploadedAt() != null) {
                    double hours = Duration
                            .between(
                                    v.getUploadedAt().truncatedTo(ChronoUnit.HOURS),
                                    OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS))
                            .toHours();
                    if (hours >= 0) reviewHours.add(hours);
                }
            }
        }
        Double avgReviewHours = reviewHours.isEmpty() ? null
                : reviewHours.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Longest currently-pending report
        Long longestPendingDays = null;
        String longestPendingReport = null;
        UUID longestPendingReportId = null;
        Optional<Report> longestPending = reports.stream()
                .filter(r -> r.getCurrentStatus() == ReportStatus.PENDING_REVIEW)
                .min(Comparator.comparing(Report::getUpdatedAt));

        if (longestPending.isPresent()) {
            Report lp = longestPending.get();
            longestPendingDays = Duration.between(
                    lp.getUpdatedAt(), OffsetDateTime.now()).toDays();
            longestPendingReport = lp.getNamingKey();
            longestPendingReportId = lp.getId();
        }

        return new EngineerStatsResponse(
                eid,
                engineer.getFullName(),
                engineer.getEmail(),
                engineer.getCompany().getName(),
                total, pending, resubmission,
                approved, condApproved, totalApproved,
                rejected, avgReviewHours,
                longestPendingDays, longestPendingReport, longestPendingReportId
        );
    }

    public EngineerStatsResponse getEngineerStats(User engineer) {
        return buildEngineerStats(engineer);
    }

    // ── Manager's combined review queue ──

    public Page<Report>
    getManagerQueue(UUID managerId, Pageable pageable) {
        List<UUID> engineerIds = getEngineerIdsForManager(managerId);
        if (engineerIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return reportRepository.findByAssignedEngineerIdIn(engineerIds, pageable);
    }

    public List<EngineerStatsResponse> getAllEngineerRankings() {
        // Get all active engineers across the system
        List<User> allEngineers = userRepository.findByRoleAndActiveTrue(UserRole.ENGINEER);
        return allEngineers.stream()
                .map(this::buildEngineerStats)
                // Rank by avg review time ascending (null = no reviews yet, goes to bottom)
                .sorted(Comparator.comparing(
                        EngineerStatsResponse::avgReviewHours,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }

    public EngineerRankResponse getEngineerRank(User engineer) {
        List<EngineerStatsResponse> ranked = getAllEngineerRankings();
        int totalRanked = (int) ranked.stream()
                .filter(e -> e.avgReviewHours() != null).count();
        int rank = -1;
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).engineerId().equals(engineer.getId())) {
                rank = i + 1;
                break;
            }
        }
        EngineerStatsResponse myStats = buildEngineerStats(engineer);
        // Top and bottom neighbouring engineers for context
        EngineerStatsResponse rankAbove = (rank > 1 && rank <= ranked.size())
                ? ranked.get(rank - 2) : null;
        EngineerStatsResponse rankBelow = (rank > 0 && rank < ranked.size())
                ? ranked.get(rank) : null;

        return new EngineerRankResponse(
                rank < 0 ? null : rank,
                totalRanked,
                ranked.size(),
                myStats.avgReviewHours(),
                rankAbove != null ? rankAbove.engineerName() : null,
                rankAbove != null ? rankAbove.avgReviewHours() : null,
                rankBelow != null ? rankBelow.engineerName() : null,
                rankBelow != null ? rankBelow.avgReviewHours() : null,
                computePercentile(rank, totalRanked)
        );
    }

    public PendingBreakdown engineerPendingBreakdown(User engineer) {
        // All reports assigned to this engineer
        List<Report> reports =
                reportRepository.findByAssignedEngineerId(engineer.getId());

        List<Report> pending = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.PENDING_REVIEW).toList();

        List<Breakdown> versions = pending.stream()
                .collect(Collectors.groupingBy(Report::getCurrentVersion, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new Breakdown("V" + entry.getKey(), entry.getValue()))
                .toList();

        List<Breakdown> rats = pending.stream()
                .collect(Collectors.groupingBy(Report::getRat, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new Breakdown(entry.getKey(), entry.getValue()))
                .toList();

        List<Breakdown> vendors = pending.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getVendorCompany() != null ? r.getVendorCompany().getShortName() : "Unknown Vendor", Collectors.counting()
                ))
                .entrySet().stream()
                .map(entry -> new Breakdown(entry.getKey(), entry.getValue()))
                .toList();
        return new PendingBreakdown(versions, rats, vendors,null);
    }

    public PendingBreakdown vendorPendingBreakdown(User vendor){
        List<Report> reports = reportRepository.findByCreatedByVendorId(vendor.getId());

        List<Report> pending = reports.stream().filter(r -> r.getCurrentStatus() == ReportStatus.RESUBMISSION_REQUIRED).toList();

        List<Breakdown> versions = pending.stream()
                .collect(Collectors.groupingBy(Report::getCurrentVersion, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new Breakdown("V" + entry.getKey(), entry.getValue()))
                .toList();

        List<Breakdown> rats = pending.stream()
                .collect(Collectors.groupingBy(Report::getRat, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new Breakdown(entry.getKey(), entry.getValue()))
                .toList();

        List<Breakdown> engineers = pending.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getAssignedEngineer() != null ? r.getAssignedEngineer().getFullName() : "Engineer Unassigned", Collectors.counting()
                ))
                .entrySet().stream()
                .map(entry -> new Breakdown(entry.getKey(), entry.getValue()))
                .toList();
        return new PendingBreakdown(versions, rats, null,engineers);
    }

    private Integer computePercentile(int rank, int total) {
        if (rank <= 0 || total <= 0) return null;
        // Percentile = % of engineers this engineer is faster than
        return (int) Math.round(((double) (total - rank) / total) * 100);
    }
}
