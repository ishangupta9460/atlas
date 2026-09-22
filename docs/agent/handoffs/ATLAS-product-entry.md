TASK
Jira key: ATLAS-product-entry (coherent UI entry batch; contributes to UI-006 and the existing DOM-001/FOUND-003 foundations)
Title: Product entry: account, goals, and task loop
Objective: Replace the developer-only JWT-paste frontend with a usable entry point for account access, goal capture, and the existing task loop.

STATUS
[ ] Not started
[ ] In progress
[ ] Blocked
[x] Ready for review
[ ] Complete

WHAT WAS DONE
- Added registration/sign-in UI, session-scoped token persistence, session restoration, sign-out, expired-session handling, and connection-error retry.
- Replaced the token-paste screen with a task-first Goals home screen. It starts with one goal question and offers an optional target date after capture.
- Added user-scoped, newest-first cursor pagination to `GET /goals`; it is read-only and returns goals in every lifecycle/planning state.
- Retained the Phase 0 task create → start → finish loop behind its own Tasks view until the scheduled-block/execution cutover exists.
- Added frontend interaction coverage for the main account, goal, session, pagination, error, and task flows.
- Fixed a real restore-session race: an effect cleanup previously aborted the request before the loading state was cleared.

FILES CHANGED
- `frontend/src/App.tsx`, `AuthScreen.tsx`, `GoalsScreen.tsx`, `TodayScreen.tsx`, `api.ts`, `index.css` — product UI, session handling, API client, and responsive styling.
- `frontend/src/App.test.tsx`, `test-setup.ts`, `vitest.config.ts`, `package.json`, `package-lock.json` — Vitest interaction tests and test dependencies.
- `frontend/vite.config.ts` — proxies `/goals` to the backend for local development.
- `backend/src/main/java/com/atlas/backend/goal/{GoalController,GoalRepository,GoalService}.java` — paginated read route and implementation.
- `backend/src/test/java/com/atlas/backend/goal/GoalIntegrationTest.java` — pagination, validation, owner isolation, and no-event assertions.
- `docs/engineering/12_API_SPECIFICATION.md` — documents `GET /goals` contract.
- `docs/agent/handoffs/ATLAS-product-entry.md` — this handoff.

DATABASE CHANGES
- None.

API CHANGES
- `GET /goals?cursor=&limit=` now returns `{ goals, nextCursor }`; JWT scoped, newest-first, `limit` 1–100 (default 20), exclusive positive cursor, no Event Log write.

TESTS RUN
- `mvn test` in `backend`.
- `npm run build` in `frontend`.
- `npm test` in `frontend`.
- `git diff --check` from the worktree root.

TEST RESULTS
- Backend: 190 tests run, 0 failures, 0 errors, 0 skipped.
- Frontend build: passed; TypeScript and Vite production bundle completed.
- Frontend interactions: 8 tests passed, 0 failed.
- `git diff --check`: no whitespace errors after the final cleanup.

KNOWN FAILURES
- None.

KNOWN RISKS
- The Tasks screen deliberately uses the temporary Phase 0 `/api/tasks` API. It is not a real Today/Now/Next/Later schedule and must be replaced only by the coordinated scheduling/execution work.
- There is no browser-driven test against a live backend in this batch; frontend mocked interaction tests and the backend integration suite cover the client and server contracts separately.
- Publishing is pending: `gh auth status` reports the configured GitHub token is invalid, so this branch and commit are local only.

OPEN QUESTIONS
- None for this batch. The next implementation slice should follow the actual scheduling prerequisites rather than expanding the temporary task UI.

ARCHITECTURAL CONCERNS
- Full goal planning and execution state changes remain backend-owned. The UI does not expose undocumented state transitions.

NEXT STEP
- Independent review of the product-entry diff, especially session expiration behavior, `GET /goals` cursor semantics, and scope against the permanent scheduling UI requirements.
- Re-authenticate the GitHub CLI with `gh auth refresh -h github.com`, then push `feature/ATLAS-product-entry` and open a draft PR targeting `develop`.

DO NOT REPEAT
- Do not clear session loading only in a request `finally` guarded by an abort signal when the successful response sets a dependency of that effect; React cleanup can abort the request before `finally` runs.
