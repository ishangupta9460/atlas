package com.atlas.backend.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Persistent user entity.
 *
 * <p>Maps the {@code users} table defined in V1__create_users_table.sql.
 * Implements {@link UserDetails} so that Spring Security's filter chain can
 * work directly with this entity without a separate adapter.
 *
 * <p>The plaintext password is <em>never stored here</em>. Only
 * {@code passwordHash} (BCrypt output) is persisted. No getter named
 * {@code getPassword} is present on this class; Spring Security's
 * {@link UserDetails#getPassword()} is satisfied by returning the hash,
 * which is only ever compared by {@code BCryptPasswordEncoder#matches()}.
 *
 * <p>Per 13_DATABASE_SPECIFICATION.md §1 and 15_SECURITY_AND_PRIVACY.md §1.
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt hash of the user's password.
     * Column name is explicitly {@code password_hash} — never {@code password}.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // -------------------------------------------------------------------------
    // UserDetails contract
    // -------------------------------------------------------------------------

    /**
     * Returns the BCrypt hash. Spring Security uses this only for
     * {@code BCryptPasswordEncoder#matches(rawPassword, encodedPassword)}.
     * The hash itself is never exposed to callers via the API layer.
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Username for Spring Security is the email address. */
    @Override
    public String getUsername() {
        return email;
    }

    /** Atlas does not use role-based authorities in FOUND-002 scope. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override public boolean isAccountNonExpired()  { return true; }
    @Override public boolean isAccountNonLocked()   { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()            { return true; }

    // -------------------------------------------------------------------------
    // Getters (no setters exposed; package-private setters used by service)
    // -------------------------------------------------------------------------

    public Long    getId()          { return id; }
    public String  getEmail()       { return email; }
    public Instant getCreatedAt()   { return createdAt; }

    // Package-private setters — only AuthService (same package boundary) sets these.
    void setEmail(String email)             { this.email = email; }
    void setPasswordHash(String hash)       { this.passwordHash = hash; }

    /** Factory used by AuthService to create a new user. */
    public static User of(String email, String passwordHash) {
        User u = new User();
        u.email        = email;
        u.passwordHash = passwordHash;
        return u;
    }
}
