# 11 — Analytics and Learning

**Status: RECONSTRUCTED.** This document did not exist in the reviewed package, despite being cited by `02` §1.13, `04` §3, `10` §4, `12` §10, `18`, `19`, and Jira epic `Analytics` (`ANLY-001`–`ANLY-004`). Section numbers below (`§1`, `§2`, `§3`, `§4`, `§5`) are fixed by those existing citations — do not renumber. See `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §5 for the evidence trail.

**Authoritative for:** the Planned/Executed/Achieved progress model's reporting behavior, headline metrics, the learned-preference feed into Scheduling, why-falling-behind analysis, and historical completion-rate calculation. This document is a **read-only aggregation layer** — per `01` §3's layer-dependency rule, it consumes the Event Log (`10`) and Actual Session history (`02` §1.7); it never writes to any Domain entity.
**Source:** Master Spec §1.15, §1.23.
**Depends on:** `10_EVENT_LOG.md` (the single source this document aggregates, never a second copy of the same data), `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` §1.7 (Actual Session, Commitment completion belief-state).

---

## 1. Planned / Executed / Achieved — Reporting

**[EXPLICIT]** — Master Spec §1.15: "Three distinct, simultaneously-tracked figures, never collapsed into one number... Planned — sum of Scheduled Block durations. Executed — sum of Actual Session durations. Achieved — user-reported roadmap/topic progress. These can legitimately disagree... that gap is itself a useful signal... not noise to be averaged away." Jira `ANLY-002` acceptance criteria: "Three figures reported together, never averaged," depending on `EXEC-005` (Planned/Executed/Achieved tracking). API surface: `GET /analytics/progress-breakdown` (`12` §11, already annotated "Planned/Executed/Achieved, `11` §1").

| Figure | Source | Computed from |
|---|---|---|
| Planned | Scheduled Blocks | `SUM(scheduled_blocks.end_time - start_time)` over the reporting window, per `13` §1 |
| Executed | Actual Sessions | `SUM(actual_sessions.actual_end - actual_start)` over the same window — the immutable record, `02` §1.7 |
| Achieved | Commitment belief-state | `current_completion_pct` (`02` §1.7) at window end, aggregated per Goal/category as relevant to the requested breakdown |

**Reporting rule [EXPLICIT]:** these three figures are always presented together, never averaged into a single "productivity score." A Planned-10h/Executed-7h/Achieved-35% result is reported as exactly that triple, with the gap itself treated as the signal (per §4 below), not smoothed away.

## 2. Headline Metrics

**[EXPLICIT]** — Master Spec §1.23: "Reported metrics are specific rather than one blended score: Consistency %, Planned-vs-Completed %, Focused Hours, Goal Progress %." Jira `ANLY-001` acceptance criteria names these four exactly: "Consistency/Planned-vs-Completed/Focused Hours/Goal Progress." API surface: `GET /analytics/summary` (`12` §11, "Headline metrics, `11` §2").

| Metric | Definition | Source |
|---|---|---|
| Consistency % | Share of scheduled sessions where a session was actually started (any Actual Session created), independent of whether it was completed on time or in full | Event Log `session.started` events over Scheduled Blocks in the window |
| Planned-vs-Completed % | Executed duration as a share of Planned duration (§1 above) | §1's Planned/Executed figures |
| Focused Hours | Raw sum of Executed duration (§1) | §1's Executed figure |
| Goal Progress % | Per-goal `current_completion_pct` aggregate, or roadmap-derived milestone completion where available | `02` §1.7 belief-state, `02` §1.13 Goal Risk Snapshot history for trend |

**[STRONGLY INFERRED]** — none of these four is defined field-by-field anywhere else in the package beyond the names Master Spec §1.23 and `ANLY-001` give; the computation column above is the most literal reading consistent with §1's already-EXPLICIT Planned/Executed/Achieved definitions, not a new metric invented independently of them.

## 3. Learned Preferences — Feed Into Scheduling

**[EXPLICIT]** — `04` §3 (Problem B candidate-slot scoring) states: "Learned preferences — any other per-user pattern (`11_ANALYTICS_AND_LEARNING.md` supplies the learned values; this engine only consumes them)." Master Spec §1.23: "Historical learning (session-length reliability, best working times, category-specific completion rates) feeds Stage 7 calibration and capacity personalization (§1.13)... and per §1.2 never overrides a live explicit user instruction."

This section is the supply side of that consumer relationship — `04` and the Capacity Model (`04` §4) read from here, this document never reads scheduling decisions back in a way that would create a cycle (`01` §3's dependency-direction rule: Scheduling depends on Analytics-supplied values, not the reverse).

**Learned values supplied [EXPLICIT from `04` §2 Stage 7's description + §3's scoring dimensions, cross-referenced to this document as their source]:**
- Time-of-day fit per category (feeds `04` §3's slot scoring).
- Energy-match patterns per time-of-day (feeds `04` §3).
- Session-length reliability / historical execution probability (feeds `04` §2 Stage 7 — **calibration only, never a ranking input**, per `04`'s own explicit restriction; this document must not supply this value in a form that Stage 7 could be tempted to use for ranking instead of calibration).
- Capacity personalization input (feeds `04` §4's per-user realistic-capacity percentage, which starts at the ~70% cold-start default and adjusts as completion data accumulates).

**Boundary rule [EXPLICIT, Master Spec §1.2 principle 2]:** none of these learned values ever override a live explicit user instruction (Stage 1, `04` §2) — they inform Problem B slot scoring and Stage 7 calibration only, both of which sit below Stage 1–2 in the hierarchy `04` already defines. This document does not redefine the hierarchy; it only supplies values into stages `04` already reserved for exactly this input.

## 4. Why-Falling-Behind Analysis

**[EXPLICIT]** — Master Spec §1.23: "Atlas can answer 'why am I falling behind' by identifying the dominant contributing factor (e.g., a specific time-of-day or category miss pattern) rather than restating a raw completion percentage." Jira `ANLY-003` acceptance criteria: "Dominant-factor ranking, not raw percentage," depending on `ANLY-001`. API surface: `GET /analytics/why-falling-behind` (`12` §11, "Dominant-factor analysis, `11` §4"). The `summarize_progress` AI proposal type (`07` §3: `{summary_text, dominant_factor}`) consumes this analysis's output to produce natural-language framing — this document computes the factor; `07`'s proposal type only phrases it.

**[STRONGLY INFERRED] computation approach**, consistent with the "dominant factor, not raw percentage" framing and with `05` §5's Goal Risk formula already using **context-specific** (per-category/time-of-day) completion rates rather than one blended figure: rank candidate contributing factors (specific category miss rate, specific time-of-day miss rate, a specific goal's disproportionate share of misses, Executed-vs-Planned gap concentration) by how much each would need to improve to close the current shortfall, and report the single largest contributor as the "dominant factor" — e.g., "mostly missed evening blocks" rather than "42% completion." Master Spec §1.23's own example phrasing ("42% of planned work completed, mostly missed evening blocks") supports reporting *both* the raw figure and the dominant factor together, not the dominant factor alone — this document does not read Master Spec's example as replacing the percentage, only as never letting the percentage stand *without* the factor.

**Tone constraint [EXPLICIT, Master Spec §1.23]:** "Atlas functions as a mirror, not a judge... reported factually and specifically... not moralized." This governs the `summarize_progress` output text (`07` §3), not just this document's raw computation.

## 5. Historical Completion Rate Calculation

**[EXPLICIT]** — Jira `ANLY-004` acceptance criteria: "Per-category/time-of-day, feeds SCH-009/SCH-013/RESC-007," depending on `EXEC-005`. This is the single most load-bearing number this document produces — it is a direct input to `05` §5's Goal Risk formula (which explicitly requires "context-specific historical completion rate... never one blended global percentage") and to `04` §2 Stage 7's calibration.

**Computation [EXPLICIT from the "per-category/time-of-day" requirement + `05` §5's formula description]:** a completion rate is calculated per (category, time-of-day-bucket) pair, not as a single global percentage — Master Spec §1.17's own example makes the reasoning explicit: "a user's 92% morning-study rate and 58% evening-study rate for the same category tell a materially different story than an averaged 75%." Computed from the ratio of `session.finished` Actual Sessions reporting completion to total Scheduled Block instances for that (category, time-bucket) pair, over a rolling historical window.

**Consumers, matching `ANLY-004`'s stated feed list exactly:**
- `SCH-009` (Stage 7 historical-calibration stub, `04` §2) — calibration only, never ranking, per `04`'s own restriction.
- `SCH-013` (Capacity Model, `04` §4) — personalizes the per-user realistic-capacity percentage as data accumulates.
- `RESC-007` (Goal Risk feasibility calculation, `05` §5) — the context-specific completion-rate input to the At-Risk formula.

## 6. Genuine Gaps / Requires Product Decision

None identified specific to this document. The one open question adjacent to Analytics — whether repeated manual task-moving should ever feed a learned scheduling default — is recorded in `08` §5 (Memory/Preferences), since it is fundamentally a confirmation-gating question, not an aggregation-computation question; this document's role in that scenario, if resolved toward option (c) in `08` §5, would be purely mechanical (supplying the value through the existing §3 pipeline) and requires no new decision here.
