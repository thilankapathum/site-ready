package dev.thilanka.site_ready.controller;

import dev.thilanka.site_ready.dto.AuthResponse;
import dev.thilanka.site_ready.entity.enums.UserRole;
import dev.thilanka.site_ready.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * Returns list of active engineers — used in vendor upload form's engineer dropdown.
     * Accessible to any authenticated user.
     */
    @GetMapping("/engineers")
    public ResponseEntity<List<AuthResponse>> activeEngineers() {
        List<AuthResponse> engineers = userRepository.findByRoleAndActiveTrue(UserRole.ENGINEER)
                .stream()
                .map(u -> new AuthResponse(null, u.getId().toString(), u.getEmail(),
                        u.getFullName(), u.getRole().name(), u.isActive(), u.getCompany()))
                .toList();
        return ResponseEntity.ok(engineers);
    }
}
