package com.atlas.backend.auth;

import com.atlas.backend.security.JwtService;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void unknownUserPerformsPasswordComparisonBeforeReturningGenericFailure() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService);

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("submitted-password"), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("unknown@example.com", "submitted-password")));

        verify(passwordEncoder).matches(eq("submitted-password"), anyString());
        verifyNoInteractions(jwtService);
    }

    @Test
    void nonEmailIntegrityViolationIsNotReportedAsEmailTaken() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService);
        RegisterRequest request = new RegisterRequest("user@example.com", "securePassword123");
        DataIntegrityViolationException databaseFailure = new DataIntegrityViolationException(
                "other constraint failed", new SQLException("other constraint failed", "23000"));

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("password-hash");
        when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenThrow(databaseFailure);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> authService.register(request));

        org.junit.jupiter.api.Assertions.assertSame(databaseFailure, thrown);
    }
}
