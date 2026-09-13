package com.atlas.backend.auth;

/**
 * Response body for a successful login.
 *
 * <p>Contains only the JWT bearer token. No user details are included here;
 * use {@code GET /api/auth/me} to retrieve the authenticated user's profile.
 * The token value is treated as an opaque credential by the client.
 */
public record AuthResponse(String token) {}
