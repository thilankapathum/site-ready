package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.dto.AuthResponse;
import dev.thilanka.site_ready.entity.Company;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.UserRole;
import dev.thilanka.site_ready.repository.CompanyRepository;
import dev.thilanka.site_ready.repository.UserRepository;
import dev.thilanka.site_ready.service.ManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompanyRepository companyRepository;
    private final ManagerService managerService;

    @GetMapping("/users")
    public ResponseEntity<List<AuthResponse>> listUsers() {
        return ResponseEntity.ok(
                userRepository.findAllByOrderByCreatedAtDesc()
                        .stream().map(this::toResponse).toList()
        );
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<AuthResponse> setRole(
            @PathVariable UUID id,
            @RequestParam UserRole role
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setRole(role);
        userRepository.save(user);
        return ResponseEntity.ok(new AuthResponse(
                null,
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.isActive(),
                null,
                user.getCompany().getId().toString(),
                user.getCompany().getName(),
                user.getCompany().getType().toString()
                ));
    }

    @PatchMapping("/users/{id}/activate")
    public ResponseEntity<AuthResponse> setActive(
            @PathVariable UUID id,
            @RequestParam boolean active
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setActive(active);
        userRepository.save(user);
        return ResponseEntity.ok(new AuthResponse(null, user.getId().toString(),
                user.getEmail(), user.getFullName(), user.getRole().name(), user.isActive(), null,user.getCompany().getId().toString(),
                user.getCompany().getName(),
                user.getCompany().getType().toString()));
    }

    @GetMapping("/users/engineers")
    public ResponseEntity<List<AuthResponse>> listEngineers() {
        List<AuthResponse> engineers = userRepository.findByRoleAndActiveTrue(UserRole.ENGINEER)
                .stream()
                .map(u -> new AuthResponse(null, u.getId().toString(), u.getEmail(),
                        u.getFullName(), u.getRole().name(), u.isActive(),null,u.getCompany().getId().toString(),
                        u.getCompany().getName(),u.getCompany().getType().toString()))
                .toList();
        return ResponseEntity.ok(engineers);
    }

    @PatchMapping("/users/{id}/company")
    public ResponseEntity<AuthResponse> setCompany(
            @PathVariable UUID id,
            @RequestParam UUID companyId
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        user.setCompany(company);
        userRepository.save(user);
        return ResponseEntity.ok(toResponse(user));
    }

    // Helper to build AuthResponse from User
    private AuthResponse toResponse(User u) {
        return new AuthResponse(
                null, u.getId().toString(), u.getEmail(), u.getFullName(),
                u.getRole().name(), u.isActive(), u.getCompany().getName(),
                u.getCompany().getId().toString(),
                u.getCompany().getName(),
                u.getCompany().getType().name()
        );
    }

    @GetMapping("/managers")
    public ResponseEntity<List<AuthResponse>> listManagers() {
        return ResponseEntity.ok(
                userRepository.findAll().stream()
                        .filter(u -> u.getRole() == UserRole.MANAGER || u.getRole() == UserRole.ADMIN)
                        .map(this::toResponse).toList()
        );
    }

    @PutMapping("/managers/{managerId}/engineers")
    public ResponseEntity<Void> setEngineersForManager(
            @PathVariable UUID managerId,
            @RequestBody List<UUID> engineerIds
    ) {
        managerService.setEngineersForManager(managerId, engineerIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/managers/{managerId}/engineers")
    public ResponseEntity<List<AuthResponse>> getEngineersForManager(
            @PathVariable UUID managerId
    ) {
        return ResponseEntity.ok(
                managerService.getEngineersForManager(managerId).stream()
                        .map(this::toResponse).toList()
        );
    }
}
