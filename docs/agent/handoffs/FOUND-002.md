TASK
Jira key: FOUND-002
Title: Auth (JWT)
Objective: Implement JWT-based authentication for Atlas (register, login, me endpoints, BCrypt password hashing, JWT filter, protected routes).

STATUS
[ ] Not started
[ ] In progress
[ ] Blocked
[x] Ready for review
[ ] Complete

WHAT WAS DONE
- Created feature branch `feature/FOUND-002-jwt-auth`.
- Added Spring Security, JJWT 0.12.6 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`), `spring-boot-starter-flyway`, and `spring-security-test` dependencies to `pom.xml`.
- Created Flyway migration `V1__create_users_table.sql` for the `users` table (`id`, `email`, `password_hash`, `created_at`).
- Implemented `User` JPA Entity (`com.atlas.backend.user.User`) implementing `UserDetails`. Only `password_hash` is persisted/held. Plaintext password is never stored or exposed.
- Implemented `UserRepository` (`com.atlas.backend.user.UserRepository`) with `findByEmail` and `existsByEmail`.
- Implemented `JwtProperties` (`com.atlas.backend.security.JwtProperties`) bound to `atlas.jwt.secret` and `atlas.jwt.expiration-ms`.
- Implemented `JwtService` (`com.atlas.backend.security.JwtService`) to sign (HS256) and validate JWT tokens. Token raw string is never logged.
- Implemented `JwtAuthenticationFilter` (`com.atlas.backend.security.JwtAuthenticationFilter`) inspecting `Authorization: Bearer <token>` and setting `SecurityContextHolder`.
- Implemented `SecurityConfig` (`com.atlas.backend.security.SecurityConfig`) setting stateless session policy, disabling CSRF, permitting `POST /api/auth/register`, `POST /api/auth/login`, and `GET /api/health`, and returning JSON 401 on unauthenticated access.
- Implemented DTOs (`RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserResponse`), custom exceptions (`EmailAlreadyTakenException`, `InvalidCredentialsException`), `AuthService`, and `AuthController`.
- Implemented `GlobalExceptionHandler` formatting errors to `{error_code, message, details}` per `12_API_SPECIFICATION.md §1`.
- Configured `application.yml` and `application-test.yml` with JWT properties.
- Created `JwtServiceTest` (4 unit tests) and `AuthIntegrationTest` (12 integration tests).
- Fixed unknown-email login timing enumeration by running BCrypt against a fixed dummy hash before returning the existing generic 401 response.
- Made the database unique constraint the final authority for registration by flushing the insert and translating only violations identified as the named `uq_users_email` constraint to `EMAIL_TAKEN` (409); unrelated integrity failures propagate normally.
- Added `@Size(max = 255)` validation for registration emails so values exceeding the `users.email` database column are rejected as validation errors before persistence.
- Added `AuthServiceTest` (2 unit tests) for the unknown-user BCrypt path and for preserving unrelated integrity errors, plus integration tests for concurrent registration, oversized email validation, and two-user `/api/auth/me` JWT-context isolation.
- Recorded `DEC-0004` (BCrypt selection, 8-char min password length, Flyway V1 numbering) and `DEC-0005` (approved auth API contracts) in `docs/agent/DECISION_LOG.md`.
- After final independent review, the product owner approved DEC-0005 and the implemented auth API contracts were added to `12_API_SPECIFICATION.md` §1.1.

FILES CHANGED
- `backend/pom.xml`: Added dependencies for Security, JJWT, Flyway starter, spring-security-test.
- `backend/src/main/resources/db/migration/V1__create_users_table.sql`: Initial Flyway migration for users table.
- `backend/src/main/java/com/atlas/backend/user/User.java`: User entity implementing UserDetails.
- `backend/src/main/java/com/atlas/backend/user/UserRepository.java`: User JPA repository interface.
- `backend/src/main/java/com/atlas/backend/security/JwtProperties.java`: JWT configuration property binding bean.
- `backend/src/main/java/com/atlas/backend/security/JwtService.java`: JJWT 0.12.6 signing & validation service.
- `backend/src/main/java/com/atlas/backend/security/JwtAuthenticationFilter.java`: OncePerRequestFilter processing Bearer tokens.
- `backend/src/main/java/com/atlas/backend/security/SecurityConfig.java`: Spring Security filter chain and BCrypt bean configuration.
- `backend/src/main/java/com/atlas/backend/auth/RegisterRequest.java`: DTO receiving registration payload, including database-aligned email length validation.
- `backend/src/main/java/com/atlas/backend/auth/LoginRequest.java`: DTO receiving login payload.
- `backend/src/main/java/com/atlas/backend/auth/AuthResponse.java`: DTO returning JWT token.
- `backend/src/main/java/com/atlas/backend/auth/UserResponse.java`: DTO returning public user representation (id, email).
- `backend/src/main/java/com/atlas/backend/auth/EmailAlreadyTakenException.java`: Exception for duplicate email (409 Conflict).
- `backend/src/main/java/com/atlas/backend/auth/InvalidCredentialsException.java`: Exception for failed login (401 Unauthorized).
- `backend/src/main/java/com/atlas/backend/auth/AuthService.java`: Service orchestrating registration, login, and current-user lookup; identifies the actual `uq_users_email` database constraint before mapping it to a duplicate-email response.
- `backend/src/main/java/com/atlas/backend/auth/AuthController.java`: RestController for /api/auth endpoints.
- `backend/src/main/java/com/atlas/backend/GlobalExceptionHandler.java`: Global exception handler for 400, 401, 409, 500 error shapes.
- `backend/src/main/resources/application.yml`: Added atlas.jwt configuration binding.
- `backend/src/test/resources/application-test.yml`: Added test JWT configuration binding.
- `backend/src/test/java/com/atlas/backend/security/JwtServiceTest.java`: Unit tests for JwtService.
- `backend/src/test/java/com/atlas/backend/auth/AuthIntegrationTest.java`: Integration tests for auth endpoints/security filter, including concurrent duplicate registration, oversized email validation, and JWT-context isolation between two users.
- `backend/src/test/java/com/atlas/backend/auth/AuthServiceTest.java`: Unit tests proving unknown-email login performs a password comparison before the generic failure and unrelated integrity failures are not translated to `EMAIL_TAKEN`.
- `docs/engineering/12_API_SPECIFICATION.md`: Added §1.1 documenting the approved, implemented auth routes, validation, success bodies, and error bodies.
- `docs/agent/DECISION_LOG.md`: Added DEC-0004 and updated approved DEC-0005.
- `docs/agent/ATLAS_CURRENT_STATE.md`: Recorded final-review completion and the remaining human-verification gate.

DATABASE CHANGES
- Added `backend/src/main/resources/db/migration/V1__create_users_table.sql`.
- Creates `users` table with `id` (BIGINT AUTO_INCREMENT PRIMARY KEY), `email` (VARCHAR(255) NOT NULL UNIQUE), `password_hash` (VARCHAR(255) NOT NULL), `created_at` (DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)).
- Rollback: `DROP TABLE users;` (or Flyway clean in dev/test).

API CHANGES
- Endpoints added:
  - `POST /api/auth/register` : Request `{email, password}` -> 201 Created `{id, email}`
  - `POST /api/auth/login` : Request `{email, password}` -> 200 OK `{token}`
  - `GET /api/auth/me` : Header `Authorization: Bearer <token>` -> 200 OK `{id, email}`
- Contract impact: Standard error responses for validation failure (400), duplicate email (409), invalid credentials (401), unauthorized request (401).
- `12_API_SPECIFICATION.md` §1.1 formally documents these approved contracts exactly as implemented; DEC-0005 is Approved.

TESTS RUN
- `mvn test` in `backend/` directory (attempted as required; this Windows/JDK environment's Surefire fork reports a cross-drive classpath issue).
- `mvn '-DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true' test` in `backend/` directory (the compatibility property must be passed to Surefire's forked JVM in this workspace).

TEST RESULTS
- Total tests run: 19. Failures: 0. Errors: 0. Skipped: 0.
- `com.atlas.backend.BackendApplicationTests`: 1/1 passed.
- `com.atlas.backend.security.JwtServiceTest`: 4/4 passed.
- `com.atlas.backend.auth.AuthIntegrationTest`: 12/12 passed.
- `com.atlas.backend.auth.AuthServiceTest`: 2/2 passed.

KNOWN FAILURES
- `mvn test` without the Surefire `argLine` compatibility property can abort the forked JVM in this Windows workspace because generated classpath entries span drive roots. The complete suite passed with `-DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true`.

KNOWN RISKS
- `ATLAS_JWT_SECRET` must be set in production environments (via env var). In application.yml, no default secret is supplied to enforce fail-fast behavior if omitted in production.

OPEN QUESTIONS
- None for FOUND-002. DEC-0005 is Approved and reflected in `12_API_SPECIFICATION.md` §1.1.

ARCHITECTURAL CONCERNS
- None. Implementation conforms strictly to `15_SECURITY_AND_PRIVACY.md §1` and `01_SYSTEM_ARCHITECTURE.md`.

NEXT STEP
- Obtain human verification before merge. Do not mark complete until that verification passes.

DO NOT REPEAT
- Do not autowire `ObjectMapper` in `SecurityConfig` without checking bean availability in Spring Boot web security setup — write JSON strings directly or use standard HttpMessageConverters.
- Ensure `spring-boot-starter-flyway` dependency is present alongside `flyway-core` so `FlywayAutoConfiguration` triggers properly during test execution.
