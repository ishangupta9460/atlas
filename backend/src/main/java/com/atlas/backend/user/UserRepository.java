package com.atlas.backend.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link User}.
 *
 * <p>Only the lookup needed for authentication is defined here. Additional
 * query methods belong in later stories (DOM-001 and beyond) — do not
 * add them here prematurely (DEVELOPMENT_RULES.md §Scope Control).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by email address.
     * Used during login to load the user for credential verification.
     */
    Optional<User> findByEmail(String email);

    /**
     * Check whether an email is already registered, without fetching the
     * full entity. Used during registration to detect duplicates.
     */
    boolean existsByEmail(String email);
}
