package com.atlas.backend.auth;

import com.atlas.backend.user.User;

/**
 * Public representation of an Atlas user.
 *
 * <p>Returned by {@code POST /api/auth/register} (201) and
 * {@code GET /api/auth/me} (200).
 *
 * <p>Contains <strong>no password, hash, or credential field</strong>.
 * The {@code id} field is the internal database id used in future API
 * references; {@code email} is the human-readable identity.
 *
 * <p>Per 15_SECURITY_AND_PRIVACY.md §2: every response is scoped to the
 * authenticated user — never cross-user data.
 */
public record UserResponse(Long id, String email) {

    /** Convenience factory from the User entity. */
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail());
    }
}
