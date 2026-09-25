# 16 — Test Strategy

**Status: RECONSTRUCTED.** This document did not exist in the reviewed package, despite being cited by `04` §2.4 (determinism testability), `18` §1 (Phase 4/7 exit criteria, per-feature Definition of Done), and Jira `OPS-004` ("Full test suite completion & CI gating," depending on "All stories," source `` `16` (all) ``). Section numbers below (`§4`, `§6`, `§8`) are fixed by existing citations — do not renumber. See `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §7 for the evidence trail.

**Authoritative for:** what must be tested and why, organized by category. This document defines **what** every category must verify, not a specific testing framework or library — per the review brief's explicit instruction, and consistent with `01` §5's principle that technology commitments are only made where evidence (Master Spec §2 Sprint 0) already commits to them. No test framework is named anywhere in the reviewed package, so none is invented here.
**Source:** `18_IMPLEMENTATION_PLAN_AND_DEFINITION_OF_DONE.md` §3 (Definition of Done, which requires "relevant test categories from `16_TEST_STRATEGY.md` pass... the specific category — scheduling/rescheduling/state-machine/AI-contract — that applies," naming four of this document's categories directly).

---

## 1. Unit Tests

**[STRONGLY INFERRED]** — required by `18` §3's Definition of Done ("unit, integration... pass") applying to every feature, every phase, but no further detail is given anywhere in the package beyond the category name. Scope: individual functions/methods in isolation — Stage 0–8 scoring functions, capacity-model arithmetic (`04` §4), tie-breaking cascade logic (`04` §2.3), Event Log payload shape validation (`10` §2), proposal-schema field validation (`07` §3). Every deterministic calculation in `04` and `05` (Goal Risk formula inputs, capacity percentage, tie-break ordering) should have unit coverage independent of any database or API layer.

## 2. Integration Tests

**[STRONGLY INFERRED]** — same basis as §1. Scope: cross-layer interactions that stay within the backend — a Commitment creation flowing through to a Scheduled Block placement (Domain → Scheduling), a `task.partial` report flowing into `05`'s recovery flow (Execution → Recovery → Scheduling), a Goal Risk Snapshot write happening atomically with its Event Log entry (`01` §4's transaction-boundary rule, `13` §4) — this last case is where an integration test is not optional but load-bearing: `01` §4 is a hard invariant, and only an integration test that actually inspects the database transaction can verify a commit path was rejected for lacking its atomic event write.

## 3. Contract / API Tests

**[STRONGLY INFERRED]** — required implicitly by `12` §1's own framing ("a contract requirement, not an implementation footnote" for the Idempotency-Key header) and `18` §3's requirement that "input validation matches the API contract (`12_API_SPECIFICATION.md`)." Scope: every endpoint in `12` against its documented request/response shape, error shape (`{error_code, message, details?}`, `12` §1), user-scoping (every response is scoped to the JWT's `user_id`, never the request body — `15` §2), and the mandatory `Idempotency-Key` header on every scheduling-mutation endpoint (`12` §1) — including the specific case of a retried request with the same key returning the original result rather than re-applying the mutation (`12` §1, `13` §1's `idempotency_keys` table).

## 4. Determinism & Property-Based Tests (Scheduling)

**[EXPLICIT]** — this is the section `04` §2.4 cites by number: "This is required for testability (`16` §4) and for explainability (`10` §3) — an explanation is only trustworthy if re-running the same inputs reproduces the same decision." `04` §2.4's own requirement is the test specification: **given an identical snapshot of task state, calendar state, capacity, and preferences, Stage 0–8 evaluation must produce an identical result on repeated runs.** This is a property to verify (run the same snapshot N times, assert identical output), not a single example-based test — hence "property-based" rather than "unit," even though no specific property-testing library is named (none is committed to anywhere in the package).

**What this category must specifically verify [STRONGLY INFERRED, extending `04` §2.4's stated requirement to its logical test cases]:**
- No randomness anywhere in Stage 0–8 evaluation, including the tie-breaking cascade's final "earliest-created item wins" rule (`04` §2.3 rule 5) — a genuinely deterministic tiebreak, not an arbitrary one that happens to look stable.
- No embedded AI call inside Stage 0–8 evaluation (`04` §2.4's own wording) — a test that mocks the AI provider to fail/hang and asserts Stage 0–8 still completes and produces the same result as when AI succeeds, since the AI-classification timing race (`02` §1.4, `04` §2.2) means `is_hard_consequence` may legitimately be `false` at evaluation time regardless of AI availability.
- Timezone-aware slot math correctness — verify the approved `04` §6 / DEC-0016 rules: DST clipping, both repeated occurrences, overnight weekday ownership, no availability before configuration, and timezone edits preserving existing UTC blocks. Automatic travel detection is not part of this contract.
- Cycle-detection termination on the dependency-chain lookahead (`04` §2.5) — a cyclic dependency graph must not cause Stage 5 evaluation to loop; this reuses the cycle-detection machinery `04` §2.5 already cites from the original Sprint 4 backlog item (`03` §2).

## 5. State-Machine Tests

**[STRONGLY INFERRED]** — required by `18` §3 naming "state-machine" as one of the four category examples. Scope: every transition table in `02` §2 (Goal Lifecycle, Goal Planning State, Commitment Work State — including the `Deferred`/`Cancelled` values added in the September 2026 review, Scheduled Block Lifecycle — including the new `Cancelled` state, Recurring Intention weekly cycle, Focus Session). For each: every documented transition fires correctly, and — at least as important — every **undocumented** transition is rejected (e.g., a Goal cannot move directly from `Active` Lifecycle to anything except `Completed`/`Abandoned`; a Commitment cannot move from `Draft` straight to `In Progress` without passing through `Ready`). The three-axis independence invariant (`02` §3) should be tested explicitly: changing a Commitment's Work State must never mutate its parent Goal's Planning State as a side effect, and vice versa — a regression here would silently reintroduce the composite-status error the original Sprint 7 backlog item was superseded specifically to avoid (`03` §2, `19` §1).

## 6. AI Failure-Mode & Malformed-Output Tests

**[EXPLICIT]** — this is the section `18` Phase 4 cites by number: "every AI-down scenario in `16_TEST_STRATEGY.md` §6 passes." The test matrix is `07` §5's failure-handling table, verified end-to-end rather than restated:

| Scenario (from `07` §5) | What must be verified |
|---|---|
| AI unavailable / provider down | Domain and Scheduling Engine continue normally; UI indicates AI-suggestions are temporarily unavailable (`14`, reconstructed); no core flow blocks |
| Rate limit hit | Request queued/deferred per `17` §3 (reconstructed) retry policy; no core flow blocks |
| Timeout | Treated as unavailable for that call; deterministic fallback used where one exists (e.g., roadmap ingestion's deterministic pass, `06` §1.2, stands alone) |
| Malformed/unparseable output | Rejected at `07` §4's validation step; logged; treated as unavailable for that call — test must include adversarial malformed payloads (wrong types, missing required fields, an attempted `commit=true`-style field `07` §3 explicitly says no proposal type may carry) |

**Additional required case, direct consequence of the September 2026 review's timing-race fix (`02` §1.4, `04` §2.2):** a task scheduled before `classify_hard_consequence` returns, followed by that classification later returning `true` — must verify the resulting re-placement is handled as an ordinary Autonomous/Collaborative re-placement (`05` §7) and does not corrupt or duplicate the task's Scheduled Block history (`02` §2.4's Superseded-not-deleted model).

## 7. End-to-End & Regression Tests

**[STRONGLY INFERRED]** — required by `18` §1 Phase 0's own exit criterion ("a user can create one task, see it scheduled, start it, finish it, and see the resulting event — the whole loop, narrow but real") and by the general principle that every phase in `18` builds toward "the same loop... now with the real hierarchy instead of a trivial placeholder" (Phase 2's exit criterion). Scope: the full Create → Schedule → Execute → Recover → Risk-flag loop (`18` Phase 5's own description) run as a single end-to-end scenario per phase, plus regression coverage ensuring each new phase's breadth doesn't break the walking-skeleton loop Phase 0 proved. The seven human-behavior scenarios from the September 2026 review (`ATLAS_SPECIFICATION_REVIEW.md` §4 — brief interruption, forgotten block, partial completion, repeated manual moves, overloaded day, unexpected Fixed Commitment, approaching deadline with continued misses) are the natural end-to-end regression suite for the Recovery/Scheduling core, since they are the exact scenarios that specification review was tested against.

## 8. Security Test Suite

**[EXPLICIT]** — this is the section `18` Phase 7 cites by number: "security test suite (`16` §8)." Scope, drawn directly from `15`'s own content:
- Cross-user data access rejected on every endpoint (`15` §2, Jira `SEC-001`'s own acceptance criteria: "Cross-user access rejected on every endpoint").
- Full data export completeness and re-importability (`15` §3, `SEC-002`).
- The two-stage deletion process end-to-end: deactivation → 30-day recoverable window → unconditional hard deletion, verified as genuinely complete across every user-scoped table (`15` §3, `13` §3, `SEC-003`).
- AI context-minimization enforcement — an outgoing AI request audited for containing only the minimum necessary context, not full user history by default (`15` §4, `07` §6, `SEC-004`).
- Idempotency-key enforcement on scheduling-mutation endpoints, including the specific replay case (`12` §1, `SEC-005`).

## 9. Genuine Gaps / Requires Product Decision

1. **Timezone/DST edge-case behavior — resolved:** product-owner approval DEC-0016 on 2026-09-25 establishes `04` §6 as the owning behavior. Test that contract; do not infer automatic travel detection or relocation of historical blocks.
2. **No specific test framework is committed to anywhere in the package** (unlike the backend/frontend/CI stack in `01` §5, which Master Spec §2 Sprint 0 explicitly commits to). This is deliberate per the review brief's instruction not to invent one — choosing a framework (e.g., JUnit for the Spring Boot backend, a property-based testing library for §4's determinism suite) is an implementation decision consistent with the already-committed Spring Boot/Java 17 stack, not a product decision requiring this document to specify further.
