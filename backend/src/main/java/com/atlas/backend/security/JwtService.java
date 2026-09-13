package com.atlas.backend.security;

import com.atlas.backend.user.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creates and validates JWTs for the Atlas auth layer.
 *
 * <p>Token contents:
 * <ul>
 *   <li>Subject: {@code user.getId().toString()}</li>
 *   <li>Claim {@code email}: the user's email address</li>
 *   <li>Algorithm: HS256 with the configured secret</li>
 * </ul>
 *
 * <p>Security invariants enforced here:
 * <ul>
 *   <li>The raw token value is <strong>never logged</strong> at any level.</li>
 *   <li>Validation failures are logged at WARN with a structural reason only
 *       (no token content in the log message).</li>
 *   <li>User identity ({@code user_id}) is always extracted from the JWT
 *       subject — never from a request parameter.</li>
 * </ul>
 *
 * <p>Per 15_SECURITY_AND_PRIVACY.md §1 and DEC-0004.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_EMAIL = "email";

    private final SecretKey signingKey;
    private final long      expirationMs;

    public JwtService(JwtProperties props) {
        // Derive a key from the configured secret. Keys.hmacShaKeyFor enforces
        // minimum key length for HS256 (256-bit / 32 bytes).
        this.signingKey   = Keys.hmacShaKeyFor(
                props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.getExpirationMs();
    }

    /**
     * Generate a signed JWT for the given user.
     *
     * @param user the authenticated user
     * @return signed JWT string — treat as opaque credential, never log
     */
    public String generateToken(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate a token string.
     *
     * @param token the raw bearer token value
     * @return {@code true} if the token is structurally valid, signed with the
     *         correct key, and not expired; {@code false} for any other case
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT validation failed: token expired");
        } catch (UnsupportedJwtException e) {
            log.warn("JWT validation failed: unsupported token format");
        } catch (MalformedJwtException e) {
            log.warn("JWT validation failed: malformed token");
        } catch (SignatureException e) {
            log.warn("JWT validation failed: invalid signature");
        } catch (IllegalArgumentException e) {
            log.warn("JWT validation failed: empty or null token");
        }
        return false;
    }

    /**
     * Extract the user id from a <em>previously validated</em> token.
     *
     * <p>Call {@link #validateToken} first. This method throws if called on
     * an invalid token.
     *
     * @param token the raw bearer token value
     * @return the user id stored in the token's subject
     */
    public Long getUserIdFromToken(String token) {
        String subject = parseClaims(token).getSubject();
        return Long.parseLong(subject);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
