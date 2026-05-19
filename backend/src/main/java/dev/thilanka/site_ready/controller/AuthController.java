package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.dto.AuthResponse;
import dev.thilanka.site_ready.dto.ChangePasswordRequest;
import dev.thilanka.site_ready.dto.LoginRequest;
import dev.thilanka.site_ready.dto.RegisterRequest;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.security.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(user.getEmail(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new AuthResponse(
                null, user.getId().toString(), user.getEmail(),
                user.getFullName(), user.getRole().name(), user.isActive(), null));
    }
}
