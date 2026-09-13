package com.atlas.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request body.
 *
 * <p>Input-only DTO. Never serialized into any response.
 * The password value is used solely to verify against the stored BCrypt hash;
 * it is never logged or returned.
 */
public record LoginRequest(

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password must not be blank")
        String password
) {}
