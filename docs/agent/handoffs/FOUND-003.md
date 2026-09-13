TASK
Jira key: FOUND-003
Title: Walking skeleton loop
Objective: Prove the minimal end-to-end loop: create task → naive Today listing → start → finish → event recorded.

STATUS
[ ] Not started
[ ] In progress
[ ] Blocked
[x] Ready for review
[ ] Complete

WHAT WAS DONE
- Added placeholder `tasks` and `events` Flyway migrations, intentionally narrower than the later Commitment and Event Log models.
- Added `Task`, `TaskRepository`, `TaskService`, `TaskController`, request/response DTOs, and transition exceptions in `com.atlas.backend.task`.
- Added minimal `Event` and `EventRepository` classes in `com.atlas.backend.event`.
- Added protected endpoints: `POST /api/tasks`, `GET /api/tasks/today`, `POST /api/tasks/{id}/start`, and `POST /api/tasks/{id}/finish`.
- Implemented per-user queries using the JWT-derived authenticated principal; foreign-owned and absent task mutations both return the same 404 response.
- Kept create/start/finish task mutations and their corresponding event writes inside service-layer `@Transactional` methods.
- Added integration tests for creation, validation/authentication, Today filtering, transitions, invalid transitions, cross-user isolation, event persistence, and rollback when an event write fails.

FILES CHANGED
- `backend/src/main/resources/db/migration/V2__create_tasks_table.sql`: minimal task table with user FK, limited statuses, and lifecycle timestamps.
- `backend/src/main/resources/db/migration/V3__create_events_table.sql`: minimal event table with task FK and limited event types.
- `backend/src/main/java/com/atlas/backend/task/Task.java`: placeholder task entity.
- `backend/src/main/java/com/atlas/backend/task/TaskRepository.java`: user-scoped task queries.
- `backend/src/main/java/com/atlas/backend/task/CreateTaskRequest.java`: validated create request.
- `backend/src/main/java/com/atlas/backend/task/TaskResponse.java`: task response DTO.
- `backend/src/main/java/com/atlas/backend/task/TaskService.java`: state transitions and atomic paired event writes.
- `backend/src/main/java/com/atlas/backend/task/TaskController.java`: protected task endpoints.
- `backend/src/main/java/com/atlas/backend/task/TaskNotFoundException.java`: indistinguishable missing/foreign task exception.
- `backend/src/main/java/com/atlas/backend/task/InvalidTaskStateException.java`: invalid-transition exception.
- `backend/src/main/java/com/atlas/backend/event/Event.java`: placeholder event entity.
- `backend/src/main/java/com/atlas/backend/event/EventRepository.java`: event lookup by task ID.
- `backend/src/main/java/com/atlas/backend/GlobalExceptionHandler.java`: 404 task and 409 invalid-state error mappings.
- `backend/src/test/java/com/atlas/backend/task/TaskIntegrationTest.java`: task API integration coverage.
- `backend/src/test/java/com/atlas/backend/task/TaskTransactionIntegrationTest.java`: forced event-write failure/transaction rollback coverage.
- `docs/agent/handoffs/FOUND-003.md`: this handoff.

DATABASE CHANGES
- Added `V2__create_tasks_table.sql`: `id`, `user_id` (FK to `users`), `title`, status constrained to `ready`/`in_progress`/`completed`, `created_at`, nullable `started_at`, nullable `finished_at`.
- Added `V3__create_events_table.sql`: `id`, `task_id` (FK to `tasks`), type constrained to `task.created`/`task.started`/`task.finished`, and `timestamp`.
- Migrations were applied successfully by Flyway against the fresh H2 test schema. Rollback for local development is dependent-table first: `DROP TABLE events; DROP TABLE tasks;`.

API CHANGES
- `POST /api/tasks` with `{ "title": "..." }`: JWT required; returns 201 ready task and records `task.created`.
- `GET /api/tasks/today`: JWT required; returns only the caller's non-completed tasks, with no scheduling/ranking logic.
- `POST /api/tasks/{id}/start`: JWT required; transitions only ready tasks and records `task.started`.
- `POST /api/tasks/{id}/finish`: JWT required; transitions only in-progress tasks and records `task.finished`.
- Manual test: register/login through `/api/auth/*`; supply `Authorization: Bearer <token>` on the task endpoints above; create a task, list Today, start it, finish it, then confirm it disappears from Today. Invalid state transitions return `409 INVALID_TASK_STATE`; another user's ID returns `404 TASK_NOT_FOUND`.
- `12_API_SPECIFICATION.md` was intentionally not changed. It owns the full future Commitment API and does not define this walking-skeleton placeholder route; documenting a new permanent contract requires the independent review/documentation-consistency sweep.

TESTS RUN
- `mvn -q -DskipTests package` in `backend/`.
- `mvn test` in `backend/`.

TEST RESULTS
- Build package: passed (`PACKAGE_SUCCEEDED`).
- Total tests run: 28. Failures: 0. Errors: 0. Skipped: 0.
- `BackendApplicationTests`: 1/1 passed.
- `JwtServiceTest`: 4/4 passed.
- `AuthIntegrationTest`: 12/12 passed.
- `AuthServiceTest`: 2/2 passed.
- `TaskIntegrationTest`: 8/8 passed.
- `TaskTransactionIntegrationTest`: 1/1 passed.

KNOWN FAILURES
- None.

KNOWN RISKS
- This is deliberately a placeholder data model and must be replaced by the full Commitment/Event Log models in the later DOM/EVT stories; it is not a migration target for those richer behaviors.

OPEN QUESTIONS
- The full Work State specification uses `task.completed`, while FOUND-003 explicitly requires the placeholder event type `task.finished`. This implementation follows the story's explicit walking-skeleton requirement; review should confirm the later Event Log migration maps/replaces this placeholder deliberately.

ARCHITECTURAL CONCERNS
- Explicitly excluded from this story: task dependencies; categories/tags/flexibility/importance; Stage 0–8 scheduling or capacity logic; pause/resume, overrun handling, or Task Briefs; full Event Log fields (`actor`, `reason`, `payload`); goals/roadmaps/milestones; AI calls; and a real Today/Now/Next/Later UI. The Today endpoint is only a non-completed-task filter.

NEXT STEP
- Independent reviewer: inspect this branch against FOUND-003 scope, transaction boundaries, user isolation, migration correctness, test adequacy, and the API-documentation follow-up question before human verification.

DO NOT REPEAT
- Do not broaden this placeholder into the full Commitment, Focus Session, Scheduler, or Event Log models before their own stories.
