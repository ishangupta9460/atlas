package com.atlas.backend.security;

import com.atlas.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-only-atlas-jwt-secret-not-used-in-production-64-chars-padding");
        props.setExpirationMs(3600000); // 1 hour

        jwtService = new JwtService(props);

        testUser = User.of("test@example.com", "hashed_password");
        ReflectionTestUtils.setField(testUser, "id", 42L);
    }

    @Test
    @DisplayName("Generate token successfully and extract user id")
    void generateAndValidateToken() {
        String token = jwtService.generateToken(testUser);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(jwtService.validateToken(token));
        assertEquals(42L, jwtService.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("Reject expired token")
    void rejectExpiredToken() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-only-atlas-jwt-secret-not-used-in-production-64-chars-padding");
        props.setExpirationMs(-1000); // Already expired

        JwtService shortLivedJwtService = new JwtService(props);
        String token = shortLivedJwtService.generateToken(testUser);

        assertFalse(shortLivedJwtService.validateToken(token));
    }

    @Test
    @DisplayName("Reject tampered token signature")
    void rejectTamperedToken() {
        String token = jwtService.generateToken(testUser);
        String tamperedToken = token.substring(0, token.length() - 4) + "XXXX";

        assertFalse(jwtService.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Reject malformed or empty token")
    void rejectMalformedToken() {
        assertFalse(jwtService.validateToken("invalid.token.string"));
        assertFalse(jwtService.validateToken(""));
        assertFalse(jwtService.validateToken(null));
    }
}
