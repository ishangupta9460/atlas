# ATLAS CHUNK 3 — Execution and Focus

## TASK
Jira: EXEC-001–005, UI-001, UI-002, UI-007.
Objective: execute real scheduled work, preserve historical facts, and honor manual placement.
Branch: `feature/ATLAS-CHUNK-3-execution`, based on develop `de9e3c5` (PR #26).

## STATUS
Implementation and verification complete for the eight scoped stories; ready for Antigravity/human acceptance.
Independent scoped review, including approved duration semantics and new regression, PASS.
Antigravity/human acceptance and merge remain separate downstream gates.

## WHAT WAS DONE
- Reused execution runtime, controller, V11 history and idempotency; no parallel models.
- Persisted atomic overrun claim at end + five minutes, with no forced stop or repeated nagging.
- Enforced working hours, protected/fixed time, buffers and capacity on manual placement/move.
- Added saved-zone week calendar with drag and keyboard moves, persistent sticky indication,
  deterministic ordering, fixed/overlap rendering, navigation and DST-aware exact slots.
- Today consumes actual generated blocks; active focus dominates; Next limited to two blocks.
- Server-anchored monotonic display timer restores persisted active/paused state on refresh.
- Task brief surfaces criterion, goal/context, existing hard-consequence flag and honest resource absence.
- Completion reporting preserves immutable actual/runtime/event facts across later belief revisions.
- Backend P/E/A gives planned durations, finished active durations and separate per-task beliefs.
- DEC-0018 is APPROVED: Executed excludes pauses. 60 wall-clock minutes minus 10 paused = 50 Executed.
  Exact regression also checks a subsequent report leaves original actual/runtime/pause-resume facts intact.
- Legacy schema/API/surface preserved; no destructive or silent cutover.

## FILES CHANGED
Implementation and test inventory:
- backend/src/main/java/com/atlas/backend/execution/ExecutionService.java — workspace, overrun, validation.
- backend/src/main/java/com/atlas/backend/execution/ExecutionController.java — overrun claim route.
- backend/src/main/java/com/atlas/backend/execution/ExecutionClock.java — injectable server time.
- backend/src/main/java/com/atlas/backend/execution/ManualWindowValidator.java — existing calendar constraints.
- backend/src/main/resources/db/migration/V13__focus_overrun_prompt.sql — nullable runtime delivery marker.
- backend/src/test/java/com/atlas/backend/execution/ExecutionIntegrationTest.java — lifecycle, ownership, replay, moves, overrun, history.
- backend/src/test/java/com/atlas/backend/execution/ExecutionMigrationIntegrationTest.java — fresh and populated upgrade.
- frontend/src/ExecutionWorkspace.tsx — focus, overrun, current work, timezone and progress surfaces.
- frontend/src/ExecutionWorkspace.test.tsx — interaction and reload regressions.
- frontend/src/ScheduleTimeline.tsx — saved-zone week/day navigation and move callbacks.
- frontend/src/ScheduleTimeline.test.tsx — calendar, drag, keyboard, fixed, overlap and DST tests.
- frontend/src/WeekCalendar.tsx — secondary week grid with manual moves.
- frontend/src/dayTimeline.ts — saved-zone day boundaries.
- frontend/src/execution.ts — execution response types and zoned formatting.
- frontend/src/executionTime.ts — local input, date boundaries and cached formatters.
- frontend/src/executionTime.test.ts — saved zone and DST regression cases.
- frontend/src/index.css — scoped week calendar styling.
- frontend/vitest.live.config.ts — opt-in live integration test configuration.
- frontend/e2e/execution.live.tsx — real React-to-HTTP-backend execution journey.
- docs/agent/handoffs/ATLAS-CHUNK-3.md — this checkpoint.

- frontend/e2e/calendar.browser.mjs — isolated Chromium pointer/drag/reload verification.
- docs/agent/DECISION_LOG.md — approved DEC-0018/0019.
- docs/engineering/09_EXECUTION_AND_FOCUS.md — active-time and non-forced overrun semantics.
- docs/engineering/11_ANALYTICS_AND_LEARNING.md — approved Executed aggregation formula.
- docs/engineering/12_API_SPECIFICATION.md — workspace, overrun, manual-placement contract.
- docs/engineering/13_DATABASE_SPECIFICATION.md — V13 mapping.

## DATABASE CHANGES
V13 adds nullable UTC `focus_sessions.overrun_prompted_at`. V1–V12 and legacy data unchanged.
Fresh and populated H2 upgrades validated. MySQL DDL rollback is not claimed; forward migration only.

## API CHANGES
- POST /blocks/{id}/session/overrun: authenticated owner, existing Idempotency-Key, response
  {showPrompt,promptedAt}; atomic runtime marker/event/replay; inclusive five-minute boundary.
- GET /execution: repeatable-read workspace gains timezone, sticky/overrun fields, hard consequence,
  and progress {plannedMillis,executedMillis,achieved}. Executed sums immutable active_millis.
- Existing manual place/move now enforce configured calendar/capacity constraints; failed moves roll back.
- Finish remains atomic report+finish. No separate correction/retroactive route added.
- Contracts recorded in 12 section 7.2; P/E/A definition in 11 section 1 and DEC-0018.

## TESTS RUN / TEST RESULTS
- Prior full backend `mvn -f backend/pom.xml test`: 273 passed, zero failures/errors/skips.
- Final backend rerun including new 60/10/50 regression: **274 passed, zero failures/errors/skips**, BUILD SUCCESS, 1:39.
- Frontend `npm test -- --maxWorkers=1 --minWorkers=1`: 48 passed, 7 files.
- `npm run build`: passed, 44 transformed modules.
- Migration `mvn -f backend/pom.xml test '-Dtest=ExecutionMigrationIntegrationTest'`: 2 passed.
- Live `npm test -- --config vitest.live.config.ts`: 1 passed, test 368.275 seconds,
  run 373.37 seconds. Real React tree, production HTTP client, Spring/Flyway/H2; no mocked
  execution responses or clock. Generated block → calendar move → sticky/regenerate → exact
  move to now → Start/Pause → remount/Resume → real five-minute grace → dismiss/refresh →
  Finish/report → history/P/E/A → Today/Calendar.
- `node e2e/calendar.browser.mjs`: PASS real Chromium pointer hit-testing, keyboard move,
  coordinate drag/drop, sticky state and reload in an isolated temporary profile.
- `git diff --check`: passed.
- Added backend tests: 10 execution and 2 migration; frontend: 4 execution, 5 calendar,
  2 timezone; plus 1 live HTTP scenario and standalone Chromium verification.

## KNOWN FAILURES
None in final verification.
Initial failures were corrected fixture/harness issues: nanosecond input, nested Mockito stubbing,
H2 connection lifetime, jsdom/Node AbortSignal realm, locale selectors and asynchronous navigation.
Initial concurrent frontend timeouts resolved by serial execution/cached Intl formatters.
No tests disabled, deleted or weakened.

## KNOWN RISKS
- Single server prompt claim and retry do not guarantee visual delivery through a browser crash
  between commit and render. Existing ambiguous attempts replay; new mounts suppress claimed prompts.
- Real MySQL and independent Antigravity QA not run; human acceptance/merge still required.
- Browser connector disconnected; isolated Chromium supplied browser verification successfully.

## OPEN QUESTIONS
Only legacy data migration versus archival remains unresolved; explicitly deferred by user.
Executed semantics are resolved, approved and implemented; do not reopen DEC-0018.

## ARCHITECTURAL CONCERNS
No scheduler algorithm, recovery, AI, analytics infrastructure, notifications or hardening scope added.
Independent reviewer found two P2 UI defects (overrun replay after refresh; card hit-test occlusion).
Both fixed, regression-tested and re-reviewed. No outstanding concrete finding.

## NEXT STEP
Antigravity/human acceptance, then authorized merge. No push or merge performed.
Single implementation commit message: `feat(execution): complete focus and execution loop`.
Use `git log -1 --format=%H` on this branch for the commit containing this final handoff.

## DO NOT REPEAT
- Keep pre-existing .cursor/, master plan, parallel-plan handoff, plans/ and Jira.csv untracked.
- Do not run concurrent Maven tests in the same target directory.
- Use microsecond scheduling fixtures and SingleConnectionDataSource migration fixtures.
- Never call service/clock inside a Mockito thenReturn argument while stubbing clock.now().
- Real HTTP tests must await navigation/enabled controls, not merely the prior screen's button.
- Local verification servers were stopped after use. Scratch logs/configs removed after results recorded.
