# Atlas wave: Event + Scheduling + Ops

TASK: Sequential EVT-003, EVT-004, SCH-007, SCH-010, OPS-001 wave.
STATUS: Partial implementation; product decisions block three stories. Not merged.
BASE: Freshly fetched origin/develop, d699bf1 (contains EVT-002).
BRANCH: feature/ATLAS-WAVE-EVT-SCH-OPS.

WHAT WAS DONE:
- EVT-003: blocked; DEC-0011 requests supported reversal types and semantics.
  Independent reviewer confirmed block is justified, not implementation completion.
- EVT-004: implemented current-entity raw/Atlas event query with JWT scoping,
  pagination and plain-language reasons; independent re-review PASS.
- SCH-007: fixed false cycle reports for shared/repeated dependencies; focused-tested
  and independently reviewed PASS for this partial change. Ranking remains blocked
  by DEC-0013 (remaining-work units and competing Stage 5 advantages).
- SCH-010: blocked; DEC-0014 requests preference evidence and conflicting-signal
  semantics. Independent review PASS for blocked record only.
- OPS-001: JSON structured logging, safe correlation IDs, request/event transaction
  traces; independent re-review PASS. Synchronous propagation boundary documented.
- Five separate story commits, in requested order; no merge or Jira modifications.

FILES CHANGED (exact paths relative to repository root):
- backend/src/main/java/com/atlas/backend/dependency/DependencyLookahead.java
- backend/src/main/java/com/atlas/backend/event/EventController.java
- backend/src/main/java/com/atlas/backend/event/EventJpaRepository.java
- backend/src/main/java/com/atlas/backend/event/EventQueryExceptionHandler.java
- backend/src/main/java/com/atlas/backend/event/EventQueryService.java
- backend/src/main/java/com/atlas/backend/event/JpaEventRepository.java
- backend/src/main/java/com/atlas/backend/logging/CorrelationIdFilter.java
- backend/src/main/resources/application.yml
- backend/src/test/java/com/atlas/backend/dependency/DependencyIntegrationTest.java
- backend/src/test/java/com/atlas/backend/dependency/DependencyLookaheadTest.java
- backend/src/test/java/com/atlas/backend/event/EventQueryIntegrationTest.java
- backend/src/test/java/com/atlas/backend/logging/CorrelationIdFilterTest.java
- backend/src/test/java/com/atlas/backend/logging/RequestLoggingIntegrationTest.java
- docs/agent/DECISION_LOG.md
- docs/agent/handoffs/EVT-003.md
- docs/agent/handoffs/EVT-004.md
- docs/agent/handoffs/SCH-007.md
- docs/agent/handoffs/SCH-010.md
- docs/agent/handoffs/OPS-001.md
- docs/agent/handoffs/ATLAS-WAVE-EVT-SCH-OPS.md
- docs/engineering/12_API_SPECIFICATION.md
- docs/engineering/17_OPERATIONS_AND_DEPLOYMENT.md

DATABASE CHANGES: None. Existing migrations and event schema unchanged.
API CHANGES: GET /events; X-Correlation-ID response header. See story handoffs.
TESTS RUN / RESULTS:
- EVT-003: no tests; documentation-only block.
- EVT-004: mvn test '-Dtest=EventQueryIntegrationTest,EventRepositoryAppendOnlyTest,EventRepositoryAppendOnlyIntegrationTest,EventTransactionIntegrationTest' — 42 tests, 0 failures/errors/skips after fixing new 403-vs-401 assertion.
- SCH-007: mvn test '-Dtest=DependencyLookaheadTest,DependencyIntegrationTest,DependencyTransactionIntegrationTest,Stage2HardConsequenceGateTest,Stage3UserImportanceTierTest,Stage4GoalAtRiskTierTest' — 37 tests, 0 failures/errors/skips. Partial traversal/regression coverage; not completed Stage 5 decision-path tests.
- SCH-010: no tests; documentation-only block.
- OPS-001: mvn test '-Dtest=CorrelationIdFilterTest,RequestLoggingIntegrationTest,EventTransactionIntegrationTest,EventRepositoryAppendOnlyTest' — 43 tests, 0 failures/errors/skips after correcting duplicate JSON correlationId field.
- Full backend: mvn test — 188 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS
  on 2026-09-21. Confirmed from Maven summary and Surefire XML totals.
- Frontend: not run locally; no frontend changes.
- git diff --check origin/develop...HEAD — passed.

KNOWN FAILURES: None in focused or full backend suites.
KNOWN RISKS: EVT-004 denies queries for physically deleted entities without an
ownership row; future outbound/async boundaries must propagate correlation explicitly.
H2 tests do not claim new real-MySQL/Aiven verification. Actual Stage 0-8 pipeline is
not implemented by this wave, and prior stage helpers remain unchanged.
OPEN QUESTIONS: DEC-0011, DEC-0013, DEC-0014 require product-owner decisions.
ARCHITECTURAL CONCERNS: No duplicate event store, invented undo transitions,
ranking weights, AI ranking, preference learning, or out-of-wave pipeline introduced.
USER-OWNED FILES: .cursor/, docs/agent/handoffs/ATLAS-PARALLEL-PLAN.md,
docs/agent/plans/, docs/jira/Jira.csv remain untouched and unstaged.
FINAL REVIEW: Independent integration review PASS on the final implementation;
the final commit was then amended only to record validation in handoffs. All 188 backend tests
passed; reviewer confirms protected files and earlier scheduling helpers unchanged.
Fresh final fetch confirms origin/develop remains d699bf1.
NEXT STEP: Review the single batch PR to develop; CI and human verification before
any merge. Resolve product blockers in subsequent authorized work. Do not merge
under this wave's authorization.
DO NOT REPEAT: Do not label this full five-story completion or satisfy missing
product semantics with mechanical defaults.
