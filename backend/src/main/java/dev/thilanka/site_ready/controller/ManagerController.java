package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.dto.AuthResponse;
import dev.thilanka.site_ready.dto.EngineerStatsResponse;
import dev.thilanka.site_ready.dto.ReportResponse;
import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.repository.ReportVersionRepository;
import dev.thilanka.site_ready.service.ManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/manager")
@PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
@RequiredArgsConstructor
public class ManagerController {
    private final ManagerService managerService;
    private final ReportVersionRepository versionRepository;

    @GetMapping("/engineers")
    public ResponseEntity<List<AuthResponse>> getMyEngineers(
            @AuthenticationPrincipal User user
    ) {
        List<User> engineers = managerService.getEngineersForManager(user.getId());
        return ResponseEntity.ok(engineers.stream().map(e -> new AuthResponse(
                null, e.getId().toString(), e.getEmail(), e.getFullName(),
                e.getRole().name(), e.isActive(), null,
                e.getCompany().getId().toString(),
                e.getCompany().getName(),
                e.getCompany().getType().name()
        )).toList());
    }

    @GetMapping("/stats")
    public ResponseEntity<List<EngineerStatsResponse>> getEngineerStats(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(managerService.getEngineersStats(user.getId()));
    }

    @GetMapping("/queue")
    public ResponseEntity<Page<ReportResponse>> getQueue(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Report> reports = managerService.getManagerQueue(user.getId(),
                PageRequest.of(page, size, Sort.by("updatedAt").descending()));
        return ResponseEntity.ok(reports.map(r -> {
            var latest = versionRepository
                    .findByReportIdOrderByVersionNumberDesc(r.getId())
                    .stream().findFirst().orElse(null);
            return ReportResponse.from(r, latest);
        }));
    }
}
