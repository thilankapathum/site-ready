package dev.thilanka.site_ready.dto;

public record AuthResponse(
        String token,
        String userId,
        String email,
        String fullName,
        String role,
        boolean active,
        String message
) {}
