package dev.thilanka.site_ready.security;

import dev.thilanka.site_ready.entity.User;
import dev.thilanka.site_ready.entity.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {

    private final JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private boolean authenticates(boolean active) throws Exception {
        User user = User.builder().email("v@test").role(UserRole.VENDOR).active(true).build();
        String token = jwtUtil.generateToken(user);   // issued while active
        user.setActive(active);                        // then admin (de)activates
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, email -> user);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    @Test
    void activeUserIsAuthenticated() throws Exception {
        assertTrue(authenticates(true));
    }

    @Test
    void deactivatedUserTokenIsRejected() throws Exception {
        assertFalse(authenticates(false));
    }
}
