# Chunk 5 — Roadmap and Resources

## TASK
ROAD-003, ROAD-004, ROAD-005, ROAD-001, ROAD-002, ROAD-006.
Base: origin/develop `5201913aaad8cee3da43bc09339b95b9fcdfd362` (fetched before branching; local develop was stale).
Branch: `feature/ATLAS-CHUNK-5-roadmap-resources`.
Implementation remains uncommitted. Product owner explicitly requested no commit or push during targeted remediation.

## STATUS
Antigravity QA reported one LOW issue: historical imports lost Open Goal. Targeted remediation verified by focused and full tests plus frontend build; ready for QA recheck.
ROAD-005 is explicitly PARTIAL under DEC-0023: single-reaction history only;
pattern detection disabled and preference persistence deferred by product owner.

## WHAT WAS DONE
- Standalone owned Resources, many-to-many attachment, metadata CRUD, atomic replacement
  and feedback history. Composite owner FKs prevent cross-tenant joins.
- Deterministic Markdown/UTF-8 structured-text parser with ordered parent-linked nodes:
  task/resource/optional/prerequisite/note/milestone/project; no AI or effort inference.
- Owned uploads and revisioned review proposals. Upload/parse/edit creates no Domain
  work or calendar rows. Explicit approval atomically creates Roadmap/Milestones/
  Commitments/Resources through existing domain services. Optional/prerequisite
  inclusion is explicit; excluded parents suppress descendants. Missing criteria
  preserve Draft semantics. Approved Ready work enters the ordinary scheduler.
- Screenshot upload validation, optional local Tesseract adapter and honest manual
  transcript/entry fallback. Ambiguous weekdays/times remain editable candidates.
  Approval requires absolute offset timestamps and creates screenshot_import Fixed
  Commitments; no recurrence invented. Conflicts surface immediately and a Recovery-
  layer adapter reuses existing recovery with explicit estimates and its approval gates.
- Basic Import navigation/workspace and task/Focus resource controls. Review counts,
  editing, exclusion, resource selection, explicit approval and distinct imported/
  scheduled status. Stable mutation keys survive uncertain response failures.
- Existing Chunk 4 migration tests pinned to their historical V16 scope; all original
  count/history assertions preserved. New migration tests cover V18 fresh/upgrade.
- DEC-0001's resolved gate replaces stale open-decision language in 03/06. Contracts
  recorded in 10/12/13; DEC-0023 records the user's explicit disabled-pattern decision.

## DATABASE CHANGES
- V17__resources.sql: resources, task_resource, resource_feedback; additive unique
  commitment owner key; owner FKs, enum checks and indexes. No data rewritten.
- V18__import_proposals.sql: source bytes, metadata, proposal/result, state/revision;
  existing replay payload columns widened to MEDIUMTEXT for supported large imports.
- MySQL DDL rollback is not transactional; use restore/forward migration for rollback.
  Runtime mutation/event/replay rollback tested. No historical migration edited.

## API CHANGES
Full shapes: docs/engineering/12_API_SPECIFICATION.md, Chunk 5 section.
- GET/POST /resources; GET/PATCH/DELETE /resources/{id}.
- GET/POST /commitments/{id}/resources; DELETE .../{resourceId}; POST .../{resourceId}/replace.
- GET/POST /resources/{id}/feedback; GET /resources/feedback-policy.
- GET/POST /roadmaps/import and /fixed-commitments/import; GET/PATCH /{id}; POST /{id}/approve.
- POST /fixed-commitments/import/{id}/recovery (Recovery owns this integration).
All require authentication/owned data; all mutations require Idempotency-Key.

## FRONTEND
ImportWorkspace, ImportConflictReview, ResourcePanel and useReplayClient.
App adds Import; task briefs/Focus expose attached resources. Vite proxies resources
and fixed-commitments. No broad navigation/Today redesign.

## TESTS RUN / TEST RESULTS
- `mvn -f backend/pom.xml clean test -q`: FINAL 357 passed, zero failures/errors/skips.
  Clean output eliminates stale test reports. Includes fresh/repeated V18 startup,
  populated V16 upgrade, ownership FKs/indexes, 100k replay payload, all 9 import
  integration cases, parser repeatability and resource rollback/API checks.
- Initial focused backend runs exposed generated-key handling and proxy-spy test setup;
  both corrected. Tests now use explicit generated ID columns and the spy target.
- Initial full backend: 354 tests, 352 passed, 2 historical migration-count failures.
  Pinned those historical tests to V16; new V18 tests preserve full upgrade coverage.
- Affected frontend tests: 17 passed (ImportWorkspace, ResourcePanel, ExecutionWorkspace).
- `npm test --prefix frontend -- --run --maxWorkers=1 --minWorkers=1`: FINAL 67 passed,
  11 files, zero failures (125.75 seconds). Prior loaded runs had existing 5-second
  interaction timeouts; assertions/timeouts were not weakened.
- `npm run build --prefix frontend`: PASS (TypeScript + Vite, 49 modules).
- After the final parsed/selected-count display change:
  `npm test --prefix frontend -- --run src/ImportWorkspace.test.tsx --maxWorkers=1 --minWorkers=1`:
  3 passed, zero failures. Excluded optional nodes remain visible in parsed counts.
- `node frontend/e2e/import.browser.mjs`: PASS, real headless Chromium for document upload, no preapproval work, review edit,
  approval, attached resource and screenshot review/approval against disposable H2.
  Vite config: vite.import-browser.config.ts;
  backend :18085, frontend :15175. No user credentials/data used.

## KNOWN FAILURES
None in final executed suites. Earlier failures and their corrections are recorded
above. MySQL, real Tesseract recognition and CI remain unverified limitations, not
reported as passing tests.

## KNOWN RISKS / LIMITATIONS
- PDF/Word/roadmap-image parsing explicitly deferred, returns 415.
- Tesseract is not installed on this host. Its adapter and unavailable fallback are
  tested; OCR recognition quality against a real engine has NOT been verified.
  Browser smoke uses a supplied transcript; candidate extraction is tested separately.
- Screenshot parser only resolves title | absolute-offset-start | absolute-offset-end;
  other extracted lines require user edits. No recurrence expansion or guessed dates.
- Nested milestones retain hierarchy in review, map to existing flat ordered domain
  milestones; tasks use nearest milestone. No dependency inference or effort estimates.
- Approval requires an owned Goal without an existing roadmap; existing one-per-Goal
  invariant is preserved. Users create a Goal through the existing UI first.
- Resource labels are stored only, never read as filesystem paths. Attached/feedback-
  referenced resources cannot be deleted; detach preserves history and other tasks.
- Conflict recovery needs explicit estimates; active/recurring conflicts remain user
  decisions. Fixed slots are never silently moved.
- No live MySQL environment verified yet; H2 MySQL-mode migration tests provide local
  evidence. Optional isolated MySQL test properties are supported in the new test.
- No CI run: user explicitly prohibited push/PR/merge. Independent Antigravity QA and
  human verification remain required, regardless of local test success.

## SELF-REVIEW
- Inspected staged diff and file inventory; no unrelated tracked files or historical
  migrations changed. Secret-pattern scan found no matches. `git diff --cached --check`
  must pass before commit. Pre-existing untracked plans/Jira/.cursor/logs preserved.
- All new code paths are owner-scoped. Import approval and resource replacement
  rollback tests force event failure, preserving Domain rows/review/replay integrity.
- No AI, automatic preferences, invented effort, guessed dates or scheduler rewrite.
- Local verification logs named chunk5-*.log remain untracked and are not committed.
  Disposable browser servers were stopped after successful verification.

## EXPLICIT DEFERRED ITEMS
ROAD-007/AI; ROAD-005 Tier 2/3 per DEC-0023; all memory/preferences, analytics,
notifications, polished UX, live calendar integration, recurrence grammar and Chunk 9
hardening. Resource/import event-query expansion is deferred; atomic events exist.

## OPEN QUESTIONS
Feedback evidence policy remains deferred, not a blocker to the authorized disabled
release. No preference confirmation endpoint claims to save absent preferences.

## ARCHITECTURAL CONCERNS
No scheduling/recovery algorithm changed. Ingestion creates Domain entities only;
Recovery-layer import adapter calls existing RecoveryService. Existing owner locks,
event repository and replay infrastructure reused.

## NEXT STEP
Verify the targeted remediation. Do not commit or push until explicitly requested.

## DO NOT REPEAT
- Do not branch from stale local develop (Chunk 3); fetched origin/develop has Chunk 4.
- Do not count only SQL migrations: V14 is a Java Flyway migration.
- Do not assume an automatically placed recovery for manual sticky windows; separate
  approval is correct existing behavior.
- Do not include pre-existing untracked files, Jira.csv, .cursor/, plans or logs in commit.
- Do not push, create a PR or merge.

## FILES CHANGED
- `backend/src/main/java/com/atlas/backend/fixedcommitment/FixedCommitmentService.java` — internal approved-import provenance seam.
- `backend/src/main/java/com/atlas/backend/ingestion/DocumentParser.java` — bounded deterministic extraction/review contract or HTTP adapter.
- `backend/src/main/java/com/atlas/backend/ingestion/FixedScheduleParser.java` — bounded deterministic extraction/review contract or HTTP adapter.
- `backend/src/main/java/com/atlas/backend/ingestion/ImportController.java` — bounded deterministic extraction/review contract or HTTP adapter.
- `backend/src/main/java/com/atlas/backend/ingestion/ImportNode.java` — bounded deterministic extraction/review contract or HTTP adapter.
- `backend/src/main/java/com/atlas/backend/ingestion/ImportService.java` — upload, review revision validation and atomic domain approval.
- `backend/src/main/java/com/atlas/backend/ingestion/ScreenshotExtractor.java` — bounded deterministic extraction/review contract or HTTP adapter.
- `backend/src/main/java/com/atlas/backend/ingestion/StructuredTextParser.java` — bounded deterministic extraction/review contract or HTTP adapter.
- `backend/src/main/java/com/atlas/backend/ingestion/TesseractScreenshotExtractor.java` — bounded deterministic extraction/review contract or HTTP adapter.
- `backend/src/main/java/com/atlas/backend/recovery/FixedImportRecoveryController.java` — reuse existing Recovery after approved fixed import.
- `backend/src/main/java/com/atlas/backend/recovery/FixedImportRecoveryService.java` — reuse existing Recovery after approved fixed import.
- `backend/src/main/java/com/atlas/backend/resource/ResourceController.java` — resource HTTP contract and sanitized validation errors.
- `backend/src/main/java/com/atlas/backend/resource/ResourceExceptionHandler.java` — resource HTTP contract and sanitized validation errors.
- `backend/src/main/java/com/atlas/backend/resource/ResourceService.java` — owned CRUD, joins, feedback and atomic replay/events.
- `backend/src/main/resources/application.yml` — bounded multipart upload configuration.
- `backend/src/main/resources/db/migration/V17__resources.sql` — additive schema and integrity constraints.
- `backend/src/main/resources/db/migration/V18__import_proposals.sql` — additive schema and integrity constraints.
- `backend/src/test/java/com/atlas/backend/ingestion/ImportIntegrationTest.java` — verification coverage for the corresponding flow.
- `backend/src/test/java/com/atlas/backend/ingestion/ImportMigrationIntegrationTest.java` — verification coverage for the corresponding flow.
- `backend/src/test/java/com/atlas/backend/ingestion/ScreenshotExtractorTest.java` — verification coverage for the corresponding flow.
- `backend/src/test/java/com/atlas/backend/ingestion/StructuredTextParserTest.java` — verification coverage for the corresponding flow.
- `backend/src/test/java/com/atlas/backend/recovery/RecoveryMigrationTest.java` — verification coverage for the corresponding flow.
- `backend/src/test/java/com/atlas/backend/resource/ResourceIntegrationTest.java` — verification coverage for the corresponding flow.
- `docs/agent/DECISION_LOG.md` — scope, contract, decision or verification record.
- `docs/agent/handoffs/ATLAS-CHUNK-5.md` — scope, contract, decision or verification record.
- `docs/engineering/03_REQUIREMENTS_TRACEABILITY.md` — scope, contract, decision or verification record.
- `docs/engineering/06_ROADMAP_AND_RESOURCE_SYSTEM.md` — scope, contract, decision or verification record.
- `docs/engineering/10_EVENT_LOG.md` — scope, contract, decision or verification record.
- `docs/engineering/12_API_SPECIFICATION.md` — scope, contract, decision or verification record.
- `docs/engineering/13_DATABASE_SPECIFICATION.md` — scope, contract, decision or verification record.
- `frontend/e2e/import.browser.mjs` — real Chromium smoke against disposable local services.
- `frontend/src/App.tsx` — Import navigation and existing work/Goal routing.
- `frontend/src/ExecutionWorkspace.test.tsx` — verification coverage for the corresponding flow.
- `frontend/src/ExecutionWorkspace.tsx` — resource controls in task brief and Focus.
- `frontend/src/ImportConflictReview.tsx` — functional import/resource review UI.
- `frontend/src/ImportWorkspace.test.tsx` — verification coverage for the corresponding flow.
- `frontend/src/ImportWorkspace.tsx` — functional import/resource review UI.
- `frontend/src/ResourcePanel.test.tsx` — verification coverage for the corresponding flow.
- `frontend/src/ResourcePanel.tsx` — functional import/resource review UI.
- `frontend/src/useReplayClient.test.tsx` — verification coverage for the corresponding flow.
- `frontend/src/useReplayClient.ts` — stable retry keys across uncertain mutation responses.
- `frontend/vite.config.ts` — API proxy or isolated browser verification configuration.
- `frontend/vite.import-browser.config.ts` — API proxy or isolated browser verification configuration.

## TARGETED QA REMEDIATION
- Persist nullable goalId in ImportService.Result using the validated approved roadmap Goal. Fixed imports persist null; older result_json without goalId loads as null without creating or inferring a Goal.
- ImportWorkspace reads result.goalId for Open Goal when reopened through Previous imports with no local Goal selection.
- Updated API contract and backend/frontend regressions. No ownership, one-roadmap constraint, scheduling, ROAD-005 scope, or other Chunk 5 product behavior changed.
- Focused backend: `mvn -f backend/pom.xml test -Dtest=ImportIntegrationTest -q`: 10 passed, zero failures/errors/skips.
- Focused frontend: `npm test --prefix frontend -- --run src/ImportWorkspace.test.tsx --maxWorkers=1 --minWorkers=1`: 6 passed.
- Full backend: `mvn -f backend/pom.xml clean test -q`: 358 passed, zero failures/errors/skips.
- Full frontend: `npm test --prefix frontend -- --run --maxWorkers=1 --minWorkers=1`: 70 passed across 11 files.
- Production build: `npm run build --prefix frontend`: passed TypeScript and Vite (49 modules).
- `git diff --check`: passed. Verification logs: chunk5-remediation-{backend-focused,frontend-focused,backend-full,frontend-full,build}.log (untracked, not for commit).
- Next step: Targeted remediation independently verified by QA; complete single commit and PR merge into develop.
