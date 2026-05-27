package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.dto.EngineerRankResponse;
import dev.thilanka.site_ready.dto.EngineerStatsResponse;
import dev.thilanka.site_ready.dto.PendingBreakdown;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.service.ManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/engineer")
@PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN') or hasRole('ENGINEER')")
@RequiredArgsConstructor
public class EngineerController {
    private final ManagerService managerService;

    @GetMapping("/stats")
    public ResponseEntity<EngineerStatsResponse> getEngineerStats(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(managerService.getEngineerStats(user));
    }


    @GetMapping("/rank")
    public ResponseEntity<EngineerRankResponse> getEngineerRank(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(managerService.getEngineerRank(user));
    }

    @GetMapping("/breakdowns")
    public ResponseEntity<PendingBreakdown> getBreakdowns(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(managerService.engineerPendingBreakdown(user));
    }
}
