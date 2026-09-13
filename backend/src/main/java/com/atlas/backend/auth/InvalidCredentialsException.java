package com.atlas.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when login credentials are invalid.
 *
 * <p>Used for <em>both</em> "email not found" and "wrong password" cases.
 * The identical exception ensures the response gives no information about
 * whether the email exists (prevents credential enumeration attacks).
 *
 * <p>Mapped to HTTP 401 Unauthorized by {@link GlobalExceptionHandler}.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        // Generic message only — no detail about which credential was wrong.
        super("Invalid email or password");
    }
}
