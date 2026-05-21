package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.dto.CompanyRequest;
import dev.thilanka.site_ready.dto.CompanyResponse;
import dev.thilanka.site_ready.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    // All authenticated users can see the company list (needed for dropdowns)
    @GetMapping
    public ResponseEntity<List<CompanyResponse>> listAll() {
        return ResponseEntity.ok(companyService.listAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<CompanyResponse>> listActive() {
        return ResponseEntity.ok(companyService.listActive());
    }

    @GetMapping("/vendors")
    public ResponseEntity<List<CompanyResponse>> listVendors() {
        return ResponseEntity.ok(companyService.listVendors());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.ok(companyService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompanyResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CompanyRequest request
    ) {
        return ResponseEntity.ok(companyService.update(id, request));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompanyResponse> setActive(
            @PathVariable UUID id,
            @RequestParam boolean active
    ) {
        return ResponseEntity.ok(companyService.setActive(id, active));
    }
}
