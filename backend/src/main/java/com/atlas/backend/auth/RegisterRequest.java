package com.atlas.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request body.
 *
 * <p>This record is an input-only DTO. It is <strong>never serialized into
 * any response</strong>. The password field exists only to receive the raw
 * password value during registration; it is hashed immediately in
 * {@link AuthService} and never stored or returned in plaintext.
 *
 * <p>Minimum password length: 8 characters.
 * Rationale: NIST SP 800-63B baseline; no Atlas specification constraint
 * defines this value — recorded as DEC-0004 (Implementation) in
 * docs/agent/DECISION_LOG.md.
 */
public record RegisterRequest(

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {}
