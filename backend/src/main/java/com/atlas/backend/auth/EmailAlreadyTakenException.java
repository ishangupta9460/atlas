package com.atlas.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a registration request supplies an email that is already
 * associated with an existing Atlas account.
 *
 * <p>Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class EmailAlreadyTakenException extends RuntimeException {

    public EmailAlreadyTakenException(String email) {
        super("Email address is already registered: " + email);
    }
}
