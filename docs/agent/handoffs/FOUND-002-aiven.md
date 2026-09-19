TASK
Jira key: FOUND-002 follow-up
Title: Aiven MySQL Spring profile
Objective: Add an opt-in Spring profile for manually smoke-testing the existing Atlas backend against the Aiven MySQL instance without changing application behavior or committing credentials.

STATUS
[ ] Not started
[ ] In progress
[ ] Blocked
[x] Ready for review
[ ] Complete

WHAT WAS DONE
- Added `application-aiven.yml`, an opt-in `aiven` Spring profile.
- Configured the profile's Aiven JDBC URL, `avnadmin` username, and enabled Flyway migrations.
- Referenced the database password only through `${AIVEN_DB_PASSWORD}`; no password value is present in this repository change.
- Confirmed `mysql-connector-j` is already supplied as a runtime dependency in `backend/pom.xml`; no dependency change was needed.

FILES CHANGED
- `backend/src/main/resources/application-aiven.yml`: Aiven-only datasource and Flyway profile configuration.
- `docs/agent/handoffs/FOUND-002-aiven.md`: This handoff.

DATABASE CHANGES
- None. The profile enables the existing Flyway migration set when a developer manually starts the application with valid local credentials.

API CHANGES
- None.

TESTS RUN
- `mvn -DskipTests package` in `backend/`.

TEST RESULTS
- `BUILD SUCCESS`; production and test sources compiled, tests were intentionally skipped by `-DskipTests`, and `target/backend-0.0.1-SNAPSHOT.jar` was packaged successfully.

KNOWN FAILURES
- None known.

KNOWN RISKS
- The live Aiven connection was deliberately not attempted. It requires the operator to provide `AIVEN_DB_PASSWORD` locally.

OPEN QUESTIONS
- None.

ARCHITECTURAL CONCERNS
- None. The default datasource profile remains unchanged.

NEXT STEP
- For a manual smoke test, set `AIVEN_DB_PASSWORD` in the local environment and start with `SPRING_PROFILES_ACTIVE=aiven`; do not place the password in a file or command history committed to the repository.

DO NOT REPEAT
- Do not activate the `aiven` profile in automated local verification unless a user explicitly authorizes a live database connection and supplies the password locally.
