package com.atlas.backend.auth;

import com.atlas.backend.security.JwtService;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling core authentication business logic for Atlas.
 *
 * Invariants:
 * - Passwords are never stored or logged in plaintext; hashed using BCrypt.
 * - Credential verification uses constant-time BCrypt matching to mitigate timing attacks.
 * - Login failures for non-existent users vs wrong passwords throw identical InvalidCredentialsException.
 */
@Service
public class AuthService {

    /*
     * A valid BCrypt hash used only when an account is not found. Running the
     * same expensive comparison as the known-account path prevents the
     * observable response time from revealing whether an email is registered.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyTakenException(request.email());
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.of(request.email(), passwordHash);
        try {
            // saveAndFlush makes the database UNIQUE constraint authoritative
            // within this call. The repository transaction is rolled back if
            // a simultaneous registration wins the race.
            User savedUser = userRepository.saveAndFlush(user);
            return UserResponse.from(savedUser);
        } catch (DataIntegrityViolationException ex) {
            if (isUsersEmailUniqueViolation(ex)) {
                throw new EmailAlreadyTakenException(request.email());
            }
            throw ex;
        }
    }

    /**
     * A {@link DataIntegrityViolationException} can represent many database
     * failures. Only the named users.email unique constraint is a duplicate
     * registration; other integrity failures must retain their original
     * failure semantics.
     */
    private boolean isUsersEmailUniqueViolation(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && isUsersEmailConstraint(constraintViolation.getConstraintName())) {
                return true;
            }

            if (cause instanceof SQLException sqlException
                    && "23000".equals(sqlException.getSQLState())
                    && isUsersEmailConstraint(sqlException.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isUsersEmailConstraint(String constraintNameOrMessage) {
        return constraintNameOrMessage != null
                && constraintNameOrMessage.toLowerCase(java.util.Locale.ROOT)
                        .contains("uq_users_email");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(request.email());
        String passwordHash = user.map(User::getPassword).orElse(DUMMY_PASSWORD_HASH);

        if (!passwordEncoder.matches(request.password(), passwordHash) || user.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.get());
        return new AuthResponse(token);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return UserResponse.from(user);
    }
}
