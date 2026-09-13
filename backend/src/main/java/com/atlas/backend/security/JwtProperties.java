package com.atlas.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized JWT configuration.
 *
 * <p>Values are bound from the {@code atlas.jwt.*} namespace in
 * {@code application.yml} / environment variables. The secret is
 * <strong>never hardcoded</strong> — the application will fail to start
 * (via Spring's binding validation) if {@code ATLAS_JWT_SECRET} is absent
 * in a non-test environment.
 *
 * <p>Per 15_SECURITY_AND_PRIVACY.md §1 and DEC-0004 in
 * docs/agent/DECISION_LOG.md.
 */
@ConfigurationProperties(prefix = "atlas.jwt")
public class JwtProperties {

    /**
     * HMAC-SHA256 signing secret.
     * Supplied via {@code ATLAS_JWT_SECRET} environment variable.
     * Must be at least 32 characters for HS256 key strength.
     * Never logged, never returned in any response.
     */
    private String secret;

    /**
     * Token lifetime in milliseconds.
     * Default: 86400000 (24 hours).
     * Overridable via {@code ATLAS_JWT_EXPIRATION_MS} environment variable.
     */
    private long expirationMs = 86_400_000L;

    public String getSecret()          { return secret; }
    public void   setSecret(String s)  { this.secret = s; }

    public long   getExpirationMs()         { return expirationMs; }
    public void   setExpirationMs(long ms)  { this.expirationMs = ms; }
}
