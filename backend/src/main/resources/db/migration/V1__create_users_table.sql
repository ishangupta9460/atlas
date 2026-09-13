-- FOUND-002: Auth (JWT)
-- Creates the users table required by 15_SECURITY_AND_PRIVACY.md §1
-- and 13_DATABASE_SPECIFICATION.md §1.
--
-- Notes:
--   - password_hash stores the BCrypt hash only. The plaintext password
--     is never persisted anywhere.
--   - email is the user identity key; unique constraint enforced here.
--   - This is V1 (first real migration). FOUND-001 left the Flyway
--     scaffold in place but no V1 file was committed. See DEC-0004
--     in docs/agent/DECISION_LOG.md.

CREATE TABLE users (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_users_email UNIQUE (email)
);
