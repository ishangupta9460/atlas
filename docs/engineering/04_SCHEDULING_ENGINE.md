# 04 — Scheduling Engine

**Authoritative for:** the Stage 0–8 decision hierarchy, candidate-slot scoring, and the capacity model. This is the single source of truth for "what deserves time" and "where it goes" — no other document may restate or redefine these rules.
**Source:** Master Spec §1.11–1.13.
**Referenced by:** `05_RESCHEDULING_AND_RECOVERY.md` (re-runs this engine on missed work and reads capacity for Goal Risk math), `14_UI_UX_SPECIFICATION.md` (renders results and explanations).

---

## 1. Overview: Two Separate Problems

- **Problem A — what deserves time.** Solved by §2 below (Stage 0–8 gate/tier hierarchy). Never a single composite score.
- **Problem B — where it goes.** Solved by §3 below (candidate-slot scoring), operating only on the set of slots that survived Problem A.

## 2. Problem A — Stage 0–8 Hierarchy

**Input:** a candidate work item (Commitment or Recurring Intention instance) requiring placement, plus current calendar/task state.
**Output:** either (a) a ranked position relative to other competing candidates for the same time, or (b) elimination from consideration for autonomous placement (escalated per `05` §7).

| Stage | Name | Type | Rule |
|---|---|---|---|
| 0 | Hard Constraint Gate | Elimination | Remove all Fixed-tier commitments from the movable set. Nothing below this stage may touch them, except §2.1. |
| 1 | Explicit Current User Instruction | Override | A live, explicit user instruction targeting this item wins over Stages 2–8. |
| 2 | Hard-Consequence Urgency | Gate/tier | Qualifies only if `is_hard_consequence = true` (AI-inferred with confirm-when-consequential, resolved — see `02` §5). Narrow definition: real, near-term, irreversible-if-missed consequence. A due date alone does not qualify. |
| 3 | User-Defined Importance | Tier | Explicit `low`/`medium`/`high`/`critical` value or category default (resolved — see `02` §5). |
| 4 | Goal Importance & At-Risk Status | Tier | Tasks linked to a Goal in `at_risk` Planning State (`02` §2.2) receive a protective boost. Never outranks Stage 0–2. |
| 5 | Remaining Work & Dependencies | Tier | Unblocking-first: downstream unblocked task count is primary ordering signal (higher count wins; excludes completed/cancelled items). If unblocked counts are tied, higher current_completion_pct wins. Bounded traversal — resolved per DEC-0013, see §2.5. |
| 6 | Flexibility & Disruption Cost | Tier | Prefer the option requiring the least movement, respecting flexibility_tier (`02` §1.4): Fixed > Protected > Flexible > Optional in resistance-to-moving. |
| 7 | Historical Execution Probability | Calibration only | Adjusts time allocation/confidence. **Never used as a ranking input for what gets scheduled.** |
| 8 | Category Continuity | Tie-break | Category continuity with the relevant preceding scheduled block/context on the timeline. If tied or unavailable, passes to §2.3 cascade. Time-of-day fit belongs exclusively to Problem B — resolved per DEC-0014, see §2.6. |

### 2.1 Fixed vs. Stage 1
Stage 0 is the limit of the Scheduling Engine's own authority — it will never autonomously move a Fixed item. Stage 1 is the limit of the *user's* authority over their own defaults — an explicit instruction targeting a specific Fixed commitment may move it. This is implemented as: Stage 0 filters candidates for the *autonomous* placement pass; a direct user command bypasses Stage 0 entirely and is handled as a manual override (`02` §1.4 `user_moved_flag`), not as engine output.

### 2.2 Stage 2 Flag — Implementation Note
The engine consumes `Commitment.is_hard_consequence` (boolean) as given. Population mechanism resolved: AI infers it from task context via the `classify_hard_consequence` proposal (`07_AI_ARCHITECTURE.md` §3); the user is asked to confirm only when the classification is about to materially change a scheduling decision (e.g., "I'm treating this as deadline-critical because late submissions aren't accepted — correct?"), not at creation time by default.

### 2.3 Tie-Breaking (applies after Stage 8, or whenever an earlier tier fails to distinguish two items competing for the same slot)
Applied in order, stop at first distinguishing rule:
1. Less remaining work wins (evaluated as higher current_completion_pct, i.e. fewer percentage points remaining to reach 100% per DEC-0013).
2. Closer to its own deadline/failure point wins.
3. Less disruption if the *other* item is the one moved instead.
4. Stronger dependency chain wins.
5. Still tied → do not ask the user for a trivial tie; pick deterministically (earliest-created item wins). Only surface to the user if both candidates are independently Stage 3/4-level important AND rules 1–4 didn't resolve it.

### 2.4 Determinism Requirement
Given an identical snapshot of task state, calendar state, capacity, and preferences, Stage 0–8 evaluation must produce an identical result on repeated runs. No randomness. No embedded AI calls. This is required for testability (`16` §4) and for explainability (`10` §3) — an explanation is only trustworthy if re-running the same inputs reproduces the same decision.

### 2.5 Stage 5 Ordering & Dependency Lookahead — Resolved (DEC-0013)
Stage 5 ordering follows an **Unblocking First** hierarchy:
1. **Primary signal:** Downstream unblocked task count from full dependency-chain traversal. Higher unblocked count wins.
2. **Exclusions:** Completed (`work_state = 'completed'`) and cancelled (`work_state = 'cancelled'`) downstream items are excluded from the unblocked count. Only actionable downstream work is counted.
3. **Secondary signal (tie-breaker):** If unblocked counts are tied (including both being 0), higher `current_completion_pct` wins (closer to 100% completion).
4. **Traversal bounds:** Full dependency-chain traversal is bounded by maximum depth (default 8) and maximum node count (default 64) via `DependencyLookahead` to preserve evaluation performance and cycle safety. Traversal cleanly terminates on cycles.
5. In §2.3 Rule 1 ("Less remaining work wins"), remaining work is identically evaluated as higher `current_completion_pct`.

### 2.6 Stage 8 Category Continuity & Problem B Boundary — Resolved (DEC-0014)
Stage 8 evaluates **Category Continuity only**:
1. When two equivalent survivors compete for the same slot, Stage 8 checks whether either candidate's `category_id` matches the `category_id` of the immediately preceding scheduled block/context on the timeline.
2. A matching candidate wins over a non-matching candidate to minimize context-switching and protect focus (`01_SYSTEM_ARCHITECTURE.md` §1.2 Rule 5).
3. If continuity is tied (both match, or neither matches, or no preceding block exists), Stage 8 makes no determination and passes the candidates cleanly to the §2.3 Tie-Breaking cascade.
4. **Time-of-day fit is explicitly excluded from Stage 8.** Time-of-day preference belongs exclusively to Problem B Candidate-Slot Scoring (§3 below). Later confirmed user preferences (`08` §3, `11` §3) feed Problem B slot scoring, but must not silently create a second Stage 8 ranking mechanism.

## 3. Problem B — Candidate-Slot Scoring

Operates only on slots that survived §2. Scoring dimensions (Master Spec §1.12), all retained from the original backlog Sprint 7 design:

- **Time-of-day fit** — learned per-user best working windows per category.
- **Energy match** — heavier/lighter work matched to typical energy at that time.
- **Context continuity** — grouping similar work, minimizing context-switching between unrelated categories.
- **Fragmentation penalty** — penalize slot choices that leave awkward small gaps elsewhere in the day.
- **Learned preferences** — any other per-user pattern (`11_ANALYTICS_AND_LEARNING.md` supplies the learned values; this engine only consumes them).

**Output:** a single best slot (or ranked list, for UI preview during roadmap-import scheduling) among Problem-A-approved candidates. This is the only place a composite score is used — it never influences *what* gets a slot, only *which* slot among options already deserving one.

### 3.1 Temporary Chunk 2 scoring baseline (DEC-0017)

Product-owner approved 2026-09-25: all four dimensions are normalized [0,1],
with equal nominal weights (arithmetic mean). Unknown confirmed time-of-day or
energy evidence contributes zero; the engine does not infer preferences.
Continuity is 1 if the preceding relevant block category matches, otherwise 0.
For leftover usable gap seconds g and requested work seconds w, fragmentation
penalty is 0 when g >= w, otherwise g/w (bounded [0,1]); score is 1 - penalty.
Explicit planning-window and per-item work-minute inputs are temporary,
request-scoped values, never persisted as duration estimates. Replacement by
confirmed preferences or persistent estimates requires a new product decision.


## 4. Capacity Model

**Realistic daily capacity ≠ raw free calendar space.**

- Cold-start default: **~70%** of raw free time is treated as workable, before any user-specific data exists.
- Personalization: as completion data accumulates, this figure adjusts per user (a user with predictable days may trend toward ~85%; a chaotic-schedule user may trend toward ~55%). Per-category granularity is a Genuine Gap (§5, item 3) — v1 default is a single per-user figure.
- Buffer time (~10–15 minutes between blocks) is drawn from the same reserved (non-workable) fraction, not computed separately.
- Blocks over ~50–60 minutes carry an implicit break structure (e.g., 50 work/10 break) — the engine counts such a block as providing less deliverable work than its raw duration when computing what fits in a day.
- Sleep (and optionally meals/commute) is modeled as an explicit protected window (source: onboarding conversation or manual edit), not inferred from leftover gaps.
- This capacity figure is the same value consumed by Goal Risk feasibility math in `05_RESCHEDULING_AND_RECOVERY.md` §5 — defined once, here, and referenced there.

## 5. Genuine Gaps / Requires Product Decision

*(All three items previously listed here — Stage 2 flag mechanism, dependency lookahead depth, capacity granularity — are resolved. See §2.2, §2.5, and §4 above respectively. Per-category capacity remains explicitly deferred past v1 by decision, not by default-without-review: single per-user figure confirmed as the v1 model.)*

## 6. Scheduling Foundation — Approved Timezone Policy (DEC-0016)

The product owner approved the following on 2026-09-25 for SCH-001/013/014:
- One saved IANA scheduling timezone per user. No available hours until explicitly configured.
- Weekly overnight windows belong to their starting ISO weekday (Monday = 1).
- DST gaps clip affected intervals: nonexistent local minutes contribute no available or
  protected time. Repeated local minutes include both occurrences. For example, a fall-back
  01:15–01:30 window produces two 15-minute intervals, not the unconfigured minutes between them.
- A timezone change affects future calculations only; persisted UTC blocks never move as a
  side effect. No automatic travel detection or historical timezone inference is introduced.
- Sleep and other protected windows are explicit and override intersecting working windows.

Implementation contract: intervals are half-open, ordered by UTC start/end. Same-kind weekly
overlaps are rejected (including Sunday overnight into Monday); adjacent intervals are allowed.
Cross-kind protected overlaps are unioned before subtraction. An empty working list means no
availability. Fixed commitments and existing scheduled/active/completed blocks exclude time;
superseded block history does not. No schedule mutation, task ranking or Problem B scoring occurs.

Candidate queries return maximal fitting start ranges with requested deliverable work and the
elapsed duration including internal breaks. They represent physical options, not authorization
to consume more than the separately returned daily capacity. Later placement must enforce that
budget. No arbitrary slot grid or current-clock input is used.

For each affected local day, raw free time is working time minus protected/fixed time. The
workable budget is that amount times the per-user fraction, rounded down to whole seconds.
Existing blocks consume deliverable work from that budget; 50/10 defaults preserve the block's
break origin when crossing midnight. Inter-block buffers (default 10 minutes around reservations)
consume the reserved fraction, not an additional percentage deduction. Remaining deliverable work
is capped by both the remaining daily budget and physically available time after buffers/breaks.
For a partial-day query, the budget still accounts for the full day while the physical cap uses
the queried portion. v1 uses one configurable per-user fraction; learning remains a later chunk.
