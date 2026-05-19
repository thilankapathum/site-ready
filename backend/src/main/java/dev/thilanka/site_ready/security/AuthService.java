package dev.thilanka.site_ready.security;

import dev.thilanka.site_ready.dto.AuthResponse;
import dev.thilanka.site_ready.dto.ChangePasswordRequest;
import dev.thilanka.site_ready.dto.LoginRequest;
import dev.thilanka.site_ready.dto.RegisterRequest;
import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.UserRole;
import dev.thilanka.site_ready.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationProvider authenticationProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .company(request.company())
                .role(UserRole.VENDOR)
                .active(false)
                .build();
        userRepository.save(user);
        return new AuthResponse(null, user.getId().toString(),
                user.getEmail(), user.getFullName(), user.getRole().name(),
                false, "Account created. Awaiting admin activation.");
    }

    public AuthResponse login(LoginRequest request) {
        authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = userRepository.findByEmail(request.email()).orElseThrow();
        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getId().toString(),
                user.getEmail(), user.getFullName(), user.getRole().name(), true, null);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}