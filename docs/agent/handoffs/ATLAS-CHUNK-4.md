# Chunk 4 — Recovery Engine

## TASK
RESC-001–013: recovery using existing deterministic scheduling, execution and events.
Starting develop: `6e65ec8fdb908453c60bbc2b50378ac9eea1f747`.
Branch: `feature/ATLAS-CHUNK-4-recovery`.

## STATUS
Ready for independent QA; final backend, frontend, build and browser smoke passed.
RESC-011 remains partial pending product policy. One local implementation commit;
no push, PR or merge.

## ACCEPTANCE AND WHAT WAS DONE
- RESC-001: elapsed unstarted windows become unresolved once; active/executed windows
  are excluded. Periodic detection uses atomic events.
- RESC-002/003: explicit effort minus progress; suitable slots today, tomorrow, then
  current week. Existing Stage 0–8/capacity reused; fixed/protected/sticky intent kept.
- RESC-004: completed/partial/skipped retrospective reports for tasks and recurring
  instances; no invented ActualSession or timer duration.
- RESC-005/006: shared planner resolves overload; consequential changes wait for
  explicit approval. Approval revalidates selected work, duration, deferrals and tier;
  times are previews that may advance within the approved search.
- RESC-007/008: remaining effort against realistic capacity adjusted by contextual
  historical completion. Immutable snapshots, five choices and visible pending risk.
  Formal At Risk is acknowledgment-gated; silence never pauses/abandons/resolves.
- RESC-009: periodic Collaborative batch suggestion, read-only capacity preview,
  explicit reactivation. Seven-item/seven-day defaults are tunable under 05 section 8.
- RESC-010/013: recurring generation uses real scheduler, execution/history use
  existing Focus/ActualSession. Local weekly reset preserves current-week facts and
  discards prior-week debt. Linked recurring contextual evidence feeds risk.
- RESC-011 partial: explicit-policy detector/query/UI seam; no production defaults.
  Single-week evidence cannot qualify; observations never write preferences/targets.
- RESC-012: interruption reserves unavailable time and locally recovers eligible
  unstarted task work; active/recurring affected windows stay for user attention.

## FILES CHANGED
Repository-relative inventory; use `git show --name-status HEAD` on the implementation
commit to distinguish created and modified files.
- `backend/src/main/java/com/atlas/backend/commitment/Commitment.java` — guarded retrospective progress, defer/reactivate and transient recurring adapter.
- `backend/src/main/java/com/atlas/backend/commitment/CommitmentService.java` — transactional recovery domain writes and events.
- `backend/src/main/java/com/atlas/backend/execution/ExecutionService.java` — recurring execution/history and visible risk IDs.
- `backend/src/main/java/com/atlas/backend/goal/GoalService.java` — owner serialization for goal/risk mutations.
- `backend/src/main/java/com/atlas/backend/recurringintention/RecurringIntentionService.java` — clocked manual completion and reset-safe weekly target changes.
- `backend/src/main/java/com/atlas/backend/scheduling/SchedulingPipeline.java` — shared read-only preview and recovery persistence seams.
- `backend/src/main/java/com/atlas/backend/scheduling/SchedulingPlanner.java` — explicit deferred-preview mode; default ranking unchanged.
- `backend/src/main/resources/application.yml` — externalized approved launch risk threshold.
- `backend/src/test/java/com/atlas/backend/execution/ExecutionMigrationIntegrationTest.java` — historical migration target retained at V13.
- `backend/src/test/java/com/atlas/backend/goal/GoalStateMachineTest.java` — mock newly required owner-lock collaborator.
- `backend/src/test/resources/application-test.yml` — disable periodic wall-clock job in deterministic tests.
- `docs/agent/DECISION_LOG.md` — approved threshold, implementation choices and unresolved policies.
- `docs/engineering/05_RESCHEDULING_AND_RECOVERY.md` — threshold attribution and genuine policy gaps.
- `docs/engineering/12_API_SPECIFICATION.md` — recovery contracts and recurring execution extensions.
- `docs/engineering/13_DATABASE_SPECIFICATION.md` — V14–16 physical mapping.
- `frontend/src/ExecutionWorkspace.tsx` — recovery entry point, stable refresh forms, recurring execution.
- `frontend/src/WeekCalendar.tsx` — keep recurring windows outside commitment-only manual move.
- `frontend/src/dayTimeline.ts` — recurring source titles.
- `frontend/src/execution.ts` — recurring block and risk workspace fields.
- `frontend/vite.config.ts` — proxy recovery, user and recurring APIs.
- `backend/src/main/java/com/atlas/backend/recovery/ContextualCompletionEvidence.java` — bounded contextual completion evidence, preserving recovered misses.
- `backend/src/main/java/com/atlas/backend/recovery/DecisionTierClassifier.java` — pure autonomous/collaborative/critical classifier.
- `backend/src/main/java/com/atlas/backend/recovery/DeferredReviewService.java` — batched read, planner preview and explicit reactivation.
- `backend/src/main/java/com/atlas/backend/recovery/DeferredReviewTrigger.java` — tunable periodic Collaborative batch suggestion.
- `backend/src/main/java/com/atlas/backend/recovery/GoalRiskCalculator.java` — pure capacity/remaining-effort/rate calculation.
- `backend/src/main/java/com/atlas/backend/recovery/GoalRiskService.java` — immutable snapshots, real capacity, five responses and acknowledgment.
- `backend/src/main/java/com/atlas/backend/recovery/InterruptionService.java` — unavailable interval reservation and local recovery.
- `backend/src/main/java/com/atlas/backend/recovery/MissedBlockDetector.java` — idempotent elapsed-window detection without inferring outcome.
- `backend/src/main/java/com/atlas/backend/recovery/OverloadResolver.java` — shared-planner survivors and guarded deferrals.
- `backend/src/main/java/com/atlas/backend/recovery/PatternDetector.java` — explicit-policy multi-week frequency detector.
- `backend/src/main/java/com/atlas/backend/recovery/PatternObservationService.java` — configured-only read-only contextual observations.
- `backend/src/main/java/com/atlas/backend/recovery/ProgressiveSlotSearcher.java` — today, tomorrow and week suitability search.
- `backend/src/main/java/com/atlas/backend/recovery/RecoveryController.java` — authenticated recovery HTTP routes.
- `backend/src/main/java/com/atlas/backend/recovery/RecoveryExceptionHandler.java` — recovery validation error mapping.
- `backend/src/main/java/com/atlas/backend/recovery/RecoveryReconciliationJob.java` — periodic owner detection, reset and deferred review.
- `backend/src/main/java/com/atlas/backend/recovery/RecoveryService.java` — idempotent proposals, bounded approval and atomic recovery.
- `backend/src/main/java/com/atlas/backend/recovery/RecoveryWorkspaceService.java` — owned pending decisions, risks, recurring and batch suggestions.
- `backend/src/main/java/com/atlas/backend/recovery/RetrospectiveReportService.java` — truthful unstarted-window reports for tasks/recurring.
- `backend/src/main/java/com/atlas/backend/recurringintention/RecurringIntentionResetJob.java` — local-week reset without debt or lost current-week facts.
- `backend/src/main/java/com/atlas/backend/scheduling/RecurringSchedulingService.java` — recurring instance generation through existing ranking/planner.
- `backend/src/main/java/db/migration/V14__recovery_block_state.java` — compatible expansion of scheduled-block lifecycle states.
- `backend/src/main/resources/db/migration/V15__recovery_decisions.sql` — decision and unique block claim tables.
- `backend/src/main/resources/db/migration/V16__goal_risk_and_weekly_reset.sql` — immutable risk snapshots, reviews and weekly marker.
- `backend/src/test/java/com/atlas/backend/recovery/RecoveryIntegrationTest.java` — transaction, security, race, risk and multi-week integration coverage.
- `backend/src/test/java/com/atlas/backend/recovery/RecoveryMigrationTest.java` — fresh/populated/repeated V14–16 migration verification.
- `backend/src/test/java/com/atlas/backend/recovery/RecoveryRulesTest.java` — tier, risk threshold, contextual, pattern and DST rules.
- `docs/agent/handoffs/ATLAS-CHUNK-4.md` — persistent implementation/review/verification handoff.
- `frontend/e2e/recovery.browser.mjs` — real Chromium/HTTP recovery smoke journey.
- `frontend/src/RecoveryPanel.test.tsx` — recovery UI behavior and non-consent checks.
- `frontend/src/RecoveryPanel.tsx` — reports, proposals, interruption, deferred review, risk and recurring forms.
- `frontend/vite.recovery-browser.config.ts` — isolated browser-test proxy on ports 15173/18080.

## DATABASE CHANGES
V14 Java migration expands lifecycle CHECK consistently for H2/MySQL.
V15 adds decisions/unique claims; V16 adds immutable risk snapshots/reviews and weekly
markers. V1–13 unchanged. Fresh/populated/repeated migrations tested in H2 MySQL mode.
No real MySQL deployment. Rollback needs explicit forward migration or backed-up
database restoration; do not discard recovery history.

## API CHANGES
Exact routes/contracts are in 12_API_SPECIFICATION.md, “Chunk 4 recovery contract”:
workspace, detect, retrospective report, recovery/proposal response, interruption,
deferred review/preview/reactivate, goal risk/evaluate/respond, reset and recurring
generation. Existing execution routes now support recurring instances.
JWT controls ownership; mutation state/events/replay share transactions.

## TESTS RUN AND RESULTS
- `mvn -f backend/pom.xml test`: **333 passed, 0 failures/errors/skips**.
- Earlier focused `mvn -f backend/pom.xml test '-Dtest=RecoveryIntegrationTest,RecurringIntentionIntegrationTest,RecurringIntentionTransactionIntegrationTest'`: 38 passed.
- Frontend `npm test -- --run --maxWorkers=1 --minWorkers=1`: **58 passed in 8 files**.
  Earlier serial run 56 passed. Concurrent run had six timeout/cross-test-fallout
  failures under load; serial rerun passed with unchanged assertions/timeouts.
- Frontend `npm run build`: **passed**, TypeScript and Vite production bundle.
- Frontend `node e2e/recovery.browser.mjs`: **passed against latest backend**, real Chromium/HTTP.
- `git diff --check`: passed.
- Local integration testcase timings: six-item overload/approval 347 ms; inclusive
  detection/repeat 58 ms; contextual risk/conversation 136 ms. These include setup
  and assertions and are not isolated algorithm or production load measurements.
- A new test initially queried nonexistent actual_sessions.user_id; corrected to
  scheduled_block_id. A weekly-boundary regression exposed manual completion using
  real event time against an injected clock; explicit reportedAt now preserves facts.

Browser reproduction: disposable backend on 18080 using
`mvn -f backend/pom.xml spring-boot:run -Plocal '-Dspring-boot.run.arguments=--server.port=18080'`;
from frontend `npx vite --config vite.recovery-browser.config.ts` on 15173, then the
browser command. Chrome uses an isolated temporary profile/new account and real HTTP.

## REVIEW
Self-review covered scope, events, owner boundaries, immutable facts, tiers, state
distinctions and unresolved policy. Independent read-only Recovery Review agent
reviewed follow-up fixes and final recurring/deferred additions; no actionable
blocker found. Reviewer did not independently run tests. Independent QA is next.

## KNOWN FAILURES
None in final backend/frontend suites, build or browser smoke. Earlier transient
failures and their resolutions are recorded above; no tests disabled or weakened.

## KNOWN RISKS / LIMITS
- Explicit request-scoped effort follows DEC-0017. Periodic job detects misses,
  resets targets and suggests batch review; risk evaluation requires provided effort
  rather than automatically guessing estimates.
- Missing needed contextual history returns 409 without fake rate/snapshot, except
  determinate zero-work/zero-capacity cases. No statistical calibration claim.
- Browser smoke covers missed detection, truthful partial report, remaining-work
  recovery, visible risk/five choices/silence and recurring generation. Wider overload,
  interruption, race, rollback and multi-week cases have backend coverage; the entire
  requested browser matrix is not independently verified.
- H2 migration coverage does not establish production MySQL rollout.
- Local integration timings are not production load benchmarks.
- Workspace decisions are visible; delivery channels, broader notifications, AI,
  analytics dashboards, learned preferences and undo remain outside this chunk.
  Recurring manual calendar movement is unsupported.

## OPEN QUESTIONS
RESC-011 production minimum multi-week span, sample count and missed ratio require
product-owner selection. Never infer “3 weeks + 80%” from examples.
Any desired cold-start risk confidence fallback needs an explicit product choice;
current behavior reports insufficient evidence. The 0.80 launch threshold itself is
resolved by product owner DEC-0020, not supplied by the original spec.

## ARCHITECTURAL CONCERNS
No second scheduler or fake persisted recurring Commitments. Owner locks serialize
recovery/execution/goal changes; writes and events share transactions. Request-scoped
effort and approval preview semantics are recorded in DEC-0021.

## NEXT STEP
Independent QA on the feature commit; resolve pattern policy before enabling
observations. Verify wider browser matrix and real MySQL migrations before release.

## DO NOT REPEAT
Do not invent evidence, production pattern defaults, elapsed-work progress or sessions.
Do not run Maven concurrently against the same target directory. On this host use one
frontend test worker; do not loosen timeouts/assertions.
Preserve unrelated untracked editor/planning/Jira files. Do not push, PR or merge.
