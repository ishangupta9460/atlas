# TESTING_RULES.md — Testing Governance for Atlas

This document defines what must be tested, why, and what must pass
before merge. It implements the testing categories envisioned in
`16_TEST_STRATEGY.md` (the Atlas product's own test-strategy document)
as concrete, mergeable-or-not gates for this repository's development
process. It does not replace `16_TEST_STRATEGY.md` — it operationalizes
it for day-to-day agent work. No specific test framework is mandated
here; `16_TEST_STRATEGY.md` §9 already notes none is committed to
anywhere in the Atlas package, and this document does not invent one
either — use whatever framework the repository has already adopted, and
if none exists yet, that choice is an implementation decision consistent
with the stack `01_SYSTEM_ARCHITECTURE.md` §5 already commits to (Spring
Boot/Java 17 backend, React/TypeScript frontend), recorded in
`docs/agent/DECISION_LOG.md`.

---

## Testing Philosophy

**A story is not complete because code compiles.**

**A story is complete when its specified behavior is demonstrated by
appropriate automated tests, plus any required integration or manual
verification the story calls for.**

This mirrors Atlas's own principle that an explanation is only
trustworthy if re-running the same inputs reproduces the same result
(`04_SCHEDULING_ENGINE.md` §2.4) — a test is only evidence if it actually
exercises the claimed behavior and would fail if that behavior broke.

---

## Test Categories

### Unit Tests
Pure business logic in isolation, independent of database or API layers:
state-transition logic, Stage 0–8 scoring functions, capacity-model
arithmetic (`04_SCHEDULING_ENGINE.md` §4), tie-breaking cascade logic
(`04` §2.3), validation rules, utility behavior. Every deterministic
calculation in `04` and `05_RESCHEDULING_AND_RECOVERY.md` should have
unit coverage that does not require spinning up a database.

### Integration Tests
Cross-layer interactions that stay within the backend: a Commitment
creation flowing through to a Scheduled Block placement (Domain →
Scheduling), a `task.partial` report flowing into Recovery
(`05_RESCHEDULING_AND_RECOVERY.md`), and — load-bearing, not optional —
verifying that a state-changing write and its Event Log entry actually
land in the same database transaction
(`01_SYSTEM_ARCHITECTURE.md` §4, `13_DATABASE_SPECIFICATION.md` §4).
This last case requires a test that actually inspects transactional
behavior (e.g., forcing a failure after the entity write but before the
event write, and asserting both roll back together) — a test that only
checks both rows exist after a successful call does not verify the
invariant.

### API / Contract Tests
Every endpoint in `12_API_SPECIFICATION.md` against its documented
request/response shape and error shape (`{error_code, message,
details?}`, `12` §1); user-scoping (every response scoped to the JWT's
`user_id`, never the request body, `15_SECURITY_AND_PRIVACY.md` §2); and
the mandatory `Idempotency-Key` header on every scheduling-mutation
endpoint, including the specific replay case — a retried request with
the same key must return the original result, not re-apply the mutation
(`12` §1, `13_DATABASE_SPECIFICATION.md` §1's `idempotency_keys` table).

### State-Machine Tests
Every transition table in `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` §2:
Goal Lifecycle, Goal Planning State, Commitment Work State (including
the `Deferred`/`Cancelled` values), Scheduled Block Lifecycle (including
`Cancelled`), Recurring Intention weekly cycle, Focus Session. For each:
every documented transition fires correctly, and every **undocumented**
transition is rejected (e.g., a Commitment must not jump `Draft` →
`In Progress` without passing through `Ready`). The three-axis
independence invariant (`02` §3) must be tested explicitly: changing a
Commitment's Work State must never mutate its parent Goal's Planning
State as a side effect, and vice versa.

### Scheduling Tests
Hard constraints (Stage 0 gate never moves a Fixed item autonomously);
deterministic results (identical snapshot in → identical result out, run
N times); timezone behavior (flagged as under-specified in
`04_SCHEDULING_ENGINE.md`/`16_TEST_STRATEGY.md` §9 — test what is
specified, and record what isn't rather than inventing expected
behavior); tie-breaking cascade (`04` §2.3, in order, including the
deterministic "earliest-created wins" final rule); user overrides (Stage
1 always wins over Stages 2–8); capacity model arithmetic (`04` §4);
disruption/flexibility ordering (`04` §2 Stage 6); recovery re-entry into
Stage 0–8; and cycle-detection termination on the dependency-lookahead
traversal (`04` §2.5) so a cyclic dependency graph cannot hang
evaluation.

### Recovery Tests
Missed block (`block.unresolved` → `05_RESCHEDULING_AND_RECOVERY.md`
§1–2); partial completion (`task.partial` → belief-state update, remaining
work re-enters candidate pool); forgotten timer; interruption
(user-reported lost time); overload resolution (`05` §3, correctly
targeting Commitment Work State, not Goal Planning State — this exact
confusion was a real defect, see `REVIEW_CHANGELOG.md` #1, and is a
standing regression risk); new Fixed Commitment colliding with an
existing lower-tier Scheduled Block (`05` §1's fourth trigger, added to
close the "silent double-booking" gap in `REVIEW_CHANGELOG.md` #3); and
Goal Risk / At-Risk transition (`05` §5), including verifying the
At-Risk flag stays visibly active if the user doesn't respond, and is
never silently resolved.

### AI Tests
Every scenario in `07_AI_ARCHITECTURE.md` §5's failure-handling table,
verified end to end: AI unavailable/provider down (core flows continue
unblocked); rate limit hit (queued/deferred per
`17_OPERATIONS_AND_DEPLOYMENT.md` §3, no core flow blocks); timeout
(treated as unavailable, deterministic fallback used where one exists —
e.g., roadmap ingestion's deterministic pass,
`06_ROADMAP_AND_RESOURCE_SYSTEM.md` §1.2, must stand alone); malformed or
unparseable output (rejected at validation, logged, treated as
unavailable — include adversarial malformed payloads: wrong types,
missing required fields, an attempted field no proposal type may
legitimately carry). Additionally: a task scheduled before
`classify_hard_consequence` returns, followed by that classification
later returning `true` — verify the resulting re-placement is an
ordinary Autonomous/Collaborative re-placement
(`05_RESCHEDULING_AND_RECOVERY.md` §7) that does not corrupt or
duplicate the Scheduled Block history (`02` §2.4's Superseded-not-deleted
model). Low-confidence banding and schema validation failures at the
proposal boundary (`07` §4) must also be covered — a malformed or
low-confidence proposal must never reach state-mutating code.

### Security Tests
Cross-user data access rejected on every endpoint
(`15_SECURITY_AND_PRIVACY.md` §2); authentication (JWT issuance,
expiry, rejection of invalid/expired tokens); authorization (every
route's user-scoping); idempotency-key enforcement including the replay
case (`12` §1); sensitive-data handling (no secrets or sensitive values
in logs, per `docs/agent/DEVELOPMENT_RULES.md`'s Security section).

### Frontend Tests
Important interaction states relevant to the UI/UX behavior specified in
`14_UI_UX_SPECIFICATION.md` — e.g., the Now/Next/Later home view actually
reflecting the underlying schedule, the manual-drag sticky-flag indicator
appearing after a move (`14` §2), the asymmetric "what changed" surfacing
(Collaborative/Critical inline, Autonomous on-request-only, `14` §5), and
user-visible error states (AI temporarily unavailable, a failed
mutation). Focus on critical workflow behavior, not exhaustive UI
snapshot coverage.

## Regression Rules

- When a bug is found (in this repository, not merely a spec
  ambiguity), add a regression test whenever practical, targeted at the
  specific defect, before or alongside the fix.
- Never delete a failing test merely because the implementation is
  inconvenient to fix. If a test is failing because it encodes a
  requirement that has genuinely been superseded, cite the spec change
  that superseded it (and the `docs/agent/DECISION_LOG.md` entry, if any)
  before removing or changing the test — do not remove it silently.

## What Must Pass Before Merge

For every merge into `develop` or `main`:

1. All unit, integration, and contract tests for the changed area pass.
2. The specific test category the story's spec section maps to —
   scheduling, rescheduling/recovery, state-machine, or AI-contract, per
   `16_TEST_STRATEGY.md`'s own category framing — passes in full, not
   just the tests the coding agent happened to write.
3. No existing test in the suite has been weakened, skipped, or deleted
   without a recorded, cited justification (see Regression Rules above).
4. Security tests relevant to the changed surface pass (any new/changed
   endpoint gets cross-user-access and idempotency coverage where
   applicable).
5. The transaction-boundary invariant test(s) pass for any change
   touching `commitments`, `goals`, `scheduled_blocks`, or
   `recurring_intentions`.
6. CI is green, including the migration-check step
   (`17_OPERATIONS_AND_DEPLOYMENT.md` §7) for any schema change.
7. The handoff (`docs/agent/HANDOFF_PROTOCOL.md`) records the actual
   commands run and actual results — not an unverified summary.

A story does not merge on "should be fine" — it merges on tests that
were actually run and actually passed, per the Definition of Done in
`docs/agent/DEVELOPMENT_RULES.md`.
