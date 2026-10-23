package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.dto.EngineerPendingResponse;
import dev.thilanka.site_ready.dto.PendingBreakdown;
import dev.thilanka.site_ready.dto.VendorStatsResponse;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.service.ManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/vendor")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'VENDOR', 'VENDOR_MANAGER')")
@RequiredArgsConstructor
public class VendorController {
    private final ManagerService managerService;

    @GetMapping("/breakdowns")
    public ResponseEntity<PendingBreakdown> getBreakdowns(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(managerService.vendorPendingBreakdown(user));
    }

    @GetMapping("/team-stats")
    @PreAuthorize("hasRole('VENDOR_MANAGER')")
    public ResponseEntity<List<VendorStatsResponse>> getTeamStats(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(managerService.getVendorTeamStats(user));
    }

    @GetMapping("/review-pending")
    @PreAuthorize("hasRole('VENDOR_MANAGER')")
    public ResponseEntity<List<EngineerPendingResponse>> getReviewPending(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(managerService.getVendorReviewPending(user));
    }
}
