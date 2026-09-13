# 03 — Requirements Traceability

**Status: RECONSTRUCTED.** This document did not exist in the reviewed package. It is cited by `04` §2.5 (cycle-detection provenance, "Sprint 4"), `18` §1/§3 (backlog disposition, Definition of Done), and `19` §1 (Sprint 7 supersession, "no backlog items silently lost") as owning the full Sprint 0–15-to-Jira disposition. Section `03` §2 is the section those documents already cite by number — do not renumber it.

**Authoritative for:** the canonical map from original backlog Sprint → Jira epic/story → owning specification section → dependency chain → specification-completeness status. This document does not define behavior itself; it only traces where each requirement's behavior is defined and whether that definition is complete.

**Method [EXPLICIT]:** built directly from `Jira.csv`'s `Source` and `Dependencies` fields (present on every one of the 91 real Atlas stories, embedded in the `Description` column), cross-checked against the Master Spec §2 Backlog Reconciliation table and against every section actually present in `02`–`19` plus the eight documents reconstructed alongside this one. No requirement below was invented — every row's "Owning Spec" column is a citation already present in Jira, not a guess.

---

## 1. Purpose and Reading Guide

For every Jira story, this document answers: what does it implement, which document defines that behavior, what does it depend on, and is the specification actually complete enough to build against. Status values used throughout:

- **Fully specified** — the cited section exists, is internally consistent, and a competent engineer could build directly against it without asking a clarifying question the spec should have already answered.
- **Partially specified** — the cited section exists but has a known incompleteness: an acceptance-criteria text that hasn't caught up to a spec correction, or a sub-behavior the owning document flags as open.
- **Blocked by open product decision** — the cited section explicitly defers a decision (marked OPEN PRODUCT DECISION in its owning document) that must be resolved before this story can be built as more than a stub.
- **Missing specification** — no section anywhere in the package defines this behavior, despite Jira or another document implying it should exist. (None of the 91 stories fall in this category as of this reconstruction pass — see §4 for genuine gaps that exist despite full story coverage, and the reconstruction report for the one behavior — task cancellation — that has full domain-model support but *no story at all*.)
- **Complete / merged** — implementation, required review, and merge into `develop` are complete; the remainder of the entry retains its specification-completeness evidence.

## 2. Sprint 0–15 → Jira Epic Disposition

**[EXPLICIT]**, extending the Master Spec §2 table with the Jira epic(s) each sprint now maps to. This is the "full item-by-item disposition" that `18` and `19` cite by section number.

| Sprint | Original scope | Master Spec §2 status | Jira epic(s) carrying it forward |
|---|---|---|---|
| 0 | Repo, Spring Boot, Java 17, MySQL/Aiven, Flyway, React/TS, FullCalendar, CI | Valid | Foundation (`FOUND-001`) |
| 1 | Registration, login, JWT, current-user endpoint | Valid | Foundation (`FOUND-002`) |
| 2 | Task domain design, task creation | Extend (Flexibility tier, typed roadmap-node origin, Work State) | Domain Model (`DOM-003`) |
| 3 | Task list/update/archive, status lifecycle, tags, categories | Extend (three-axis state model; category flexibility defaults) | Domain Model (`DOM-001`, `DOM-005`), Memory/Preferences (`MEM-004`) |
| 4 | Dependencies, cycle detection | Valid (feeds Stage 5; lookahead depth resolved) | Domain Model (`DOM-007`), Scheduling Engine (`SCH-007`) |
| 5 | Working availability, blackout periods, timezone, manual calendar events | Extend (screenshot fixed-schedule import, sleep/recovery window, buffers) | Domain Model (`DOM-006`), Roadmap/Resources (`ROAD-006`), Scheduling Engine (`SCH-013`) |
| 6 | Deterministic candidate slots, hard constraint enforcement, first schedule generation | Extend (Stage 0 gate, Problem A/B split) | Scheduling Engine (`SCH-001`–`SCH-002`) |
| 7 | Scoring formula (composite score, greedy placement, splitting, local search, explanation) | Superseded in part (composite score replaced by Stage 0–8; slot-scoring dimensions retained) | Scheduling Engine (`SCH-011`–`SCH-012`), Rescheduling/Recovery (`RESC-002`), Event Log (`EVT-004`) |
| 8 | Calendar UI, drag/drop, rescheduling, manual event creation | Extend (`user-moved`/Protected flag) | UI Core (`UI-002`, `UI-007`) |
| 9 | Focus mode, start/pause/resume/end, actual vs. estimated, overrun | Extend (task brief, grace-window + single-prompt overrun) | Execution/Focus (`EXEC-001`–`EXEC-003`) |
| 10 | Duration learning, time-of-day performance, confidence, completion pattern recognition | Extend (feeds Stage 7/capacity; multi-week pattern threshold) | Analytics (`ANLY-004`), Rescheduling/Recovery (`RESC-011`) |
| 11 | Risk engine, impossible schedules, deadline conflicts, overload/burnout detection | Extend (At-Risk transition, Minimum Viable Day, Critical-tier escalation) | Rescheduling/Recovery (`RESC-007`–`RESC-009`), UI Core (`UI-003`) |
| 12 | Analytics, weekly/daily reports, learning summaries | Extend (Planned/Executed/Achieved split, specific-metric reporting) | Analytics (`ANLY-001`–`ANLY-003`), Execution/Focus (`EXEC-005`) |
| 13 | AI integration (capture, clarification, document ingestion, estimation, decomposition, coaching, weekly review, rate/cost control) | Extend + reorder (deterministic ingestion moves earlier; structured-proposal architecture from the start) | AI Architecture (`AI-001`–`AI-012`), Roadmap/Resources (`ROAD-001`, `ROAD-007`) |
| 14 | Notifications and reviews | Extend (decision-relevance notification philosophy) | UI Remaining (`UI-005`) |
| 15 | Production hardening (security, DB, backend, scheduling, AI hardening, observability) | Valid (add AI-down test coverage) | Security (`SEC-001`–`SEC-005`), Operations (`OPS-001`–`OPS-005`) |

**Cross-check result:** every original Sprint 0–15 item has at least one Jira story carrying it forward. No backlog item is silently dropped — this confirms (rather than merely repeats) `19`'s claim on this specific point, which this reconstruction pass had no reason to dispute.

## 3. Full Story-Level Traceability

**[EXPLICIT]**, generated directly from `Jira.csv`. "Owning Spec" and "Dependencies" columns are the literal `Source`/`Dependencies` fields already present in the backlog — this reconstruction did not alter them, only added the "Status" column based on cross-checking whether the cited section actually exists and is complete after this and the prior review pass.

### Foundation

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `FOUND-001` | Repo & CI setup | `01` §5, `17` §7 | none | Complete / merged — `01` §5 and `17` §7 remain fully specified and match the sub-tasks exactly. |
| `FOUND-002` | Auth (JWT) | `15` §1 | FOUND-001 | Complete / merged — JWT auth, the V1 user migration, and the approved §1.1 auth contracts are implemented in `develop`; `15` §1 remains fully specified. |
| `FOUND-003` | Walking skeleton loop | `18` §1 Phase 0 | FOUND-002 | Complete / merged — the V2/V3 placeholder task/event migrations and bare Today loop are implemented in `develop`; `18` §1 Phase 0 remains fully specified. |

### Domain Model

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `DOM-001` | Goal entity + Lifecycle/Planning state | `02` §1.1, §2.1–2.2 | FOUND-003 | Implemented; independently reviewed across two rounds — PASS; merged pending human verification. |
| `DOM-002` | Roadmap/Milestone entity | `02` §1.2–1.3 | DOM-001 | Fully specified — `02` §1.2–1.3. |
| `DOM-003` | Commitment/Task full model | `02` §1.4, §5 | DOM-002 | Partially specified — `02` §1.4/§2.3/§5 now correctly enumerate `deferred`/`cancelled` work_state values (fixed in the September 2026 review pass), but this story's own acceptance criteria text still says only "incl. ... work_state" and should be updated to name the full value set explicitly (Jira change recommended, see `REVIEW_CHANGELOG.md` #1). |
| `DOM-004` | Recurring Intention entity | `02` §1.5 | DOM-001 | Fully specified — `02` §1.5. |
| `DOM-005` | Category/Tag entity + defaults | `02` §1.9 | DOM-001 | Fully specified — `02` §1.9. |
| `DOM-006` | Fixed Commitment / Calendar Event entity | `02` §1.11 | DOM-001 | Fully specified — `02` §1.11; API surface gap (no endpoints existed) fixed in `12` §5 during the September 2026 review. |
| `DOM-007` | Dependency modeling | `02` §1.4, `04` §2.5 | DOM-003 | Fully specified — `02` §1.4, `04` §2.5. |

### Event Log

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `EVT-001` | Event log schema & append-only enforcement | `10` §2–3 | DOM (all) | Fully specified — `10` §2–3. |
| `EVT-002` | Transaction-boundary enforcement | `10` §5, `13` §4 | EVT-001 | Fully specified — `10` §5, `13` §4, and now `01` §4 (this document), which is the rule both were quoting without a source to point to. |
| `EVT-003` | Compensating-event undo | `10` §4 (resolved) | EVT-002 | Fully specified — `10` §4 (resolved). |
| `EVT-004` | "What changed" query API | `10` §4 | EVT-001 | Fully specified — `10` §4; UI consumption now specified in `14` §5. |

### Scheduling Engine

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `SCH-001` | Candidate slot generation | `04` §1–2 | DOM-006 | Fully specified — `04` §1–2. |
| `SCH-002` | Stage 0 hard constraint gate | `04` §2, §2.1 | SCH-001 | Fully specified — `04` §2, §2.1. |
| `SCH-003` | Stage 1 explicit instruction override | `04` §2, §2.1 | SCH-002 | Fully specified — `04` §2, §2.1. |
| `SCH-004` | Stage 2 hard-consequence gate | `04` §2, §2.2 | DOM-003 | Partially specified — `04` §2, §2.2 define the gate; the AI-classification timing race (what happens if `classify_hard_consequence` hasn't returned yet) was undocumented until the September 2026 review fixed it in `02` §1.4 / `04` §2.2. This story's acceptance criteria should gain an explicit line for that case (Jira change recommended). |
| `SCH-005` | Stage 3 user importance tier | `04` §2, `02` §5 | DOM-003, DOM-005 | Fully specified — `04` §2, `02` §5. |
| `SCH-006` | Stage 4 goal/at-risk boost | `04` §2 | DOM-001, RESC-007 (soft dep — stub risk flag until RESC lands) | Fully specified — `04` §2. |
| `SCH-007` | Stage 5 dependency & remaining-work tier | `04` §2, §2.5 | DOM-007 | Fully specified — `04` §2, §2.5. |
| `SCH-008` | Stage 6 flexibility/disruption tier | `04` §2 | DOM-003 | Fully specified — `04` §2. |
| `SCH-009` | Stage 7 historical calibration stub | `04` §2 | ANLY-004 (soft dep) | Fully specified — `04` §2; the soft dependency on `ANLY-004` is satisfied now that `11` §5 exists. |
| `SCH-010` | Stage 8 preference/continuity tie-break | `04` §2 | SCH-009 | Fully specified — `04` §2. |
| `SCH-011` | Tie-breaking cascade | `04` §2.3 | SCH-002–010 | Fully specified — `04` §2.3. |
| `SCH-012` | Problem B candidate-slot scoring | `04` §3 | SCH-011 | Fully specified — `04` §3. |
| `SCH-013` | Capacity model | `04` §4 | SCH-001 | Fully specified — `04` §4. |
| `SCH-014` | Determinism & timezone handling | `04` §2.4 | SCH-012 | Fully specified — `04` §2.4. Timezone-aware slot math itself (DST transitions, cross-timezone travel) is not detailed anywhere in `04` beyond "correct timezone-aware slot math" — flagged as a genuine gap in `04`, not fabricated here (see §8 below). |
| `SCH-015` | Explainability (placement reason) | `04`, `10` §4 | SCH-011, EVT-002 | Fully specified — `04`, `10` §4. |

### Rescheduling/Recovery

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `RESC-001` | Missed block detection | `02` §2.4, `05` §1 | EXEC-001 | Fully specified — `02` §2.4, `05` §1. |
| `RESC-002` | Progressive recovery search | `05` §2 | RESC-001, SCH-011 | Fully specified — `05` §2. |
| `RESC-003` | Partial completion handling | `02` §2.3, `05` §1 | EXEC-004 | Fully specified — `02` §2.3, `05` §1. |
| `RESC-004` | Forgotten timer handling | `09` §6 | RESC-001 | Fully specified — `09` §6. |
| `RESC-005` | Overload resolution | `05` §3 | RESC-002 | Fully specified — `05` §3 (corrected in the September 2026 review to reference the right Work State axis). |
| `RESC-006` | Autonomous/Collaborative/Critical engine | `05` §7 | RESC-002, RESC-005 | Fully specified — `05` §7. |
| `RESC-007` | Goal Risk feasibility calculation | `05` §5 (resolved) | SCH-013, ANLY-004 (soft dep) | Fully specified — `05` §5 (resolved); soft dependency on `ANLY-004` satisfied now that `11` §5 exists. |
| `RESC-008` | At-Risk transition + Critical conversation | `05` §5, `02` §2.2 | RESC-007 | Fully specified — `05` §5, `02` §2.2. |
| `RESC-009` | Deferred Backlog Review batching | `05` §8 (resolved) | RESC-005 | Fully specified — `05` §8 (resolved). |
| `RESC-010` | Recurring Intention weekly reset + recovery | `05` §4 | DOM-004, RESC-007 | Fully specified — `05` §4. |
| `RESC-011` | Pattern detection (multi-week evidence) | `05` §6 | RESC-002 | Fully specified — `05` §6. |
| `RESC-012` | Unexpected interruption handling | `05` §1 | RESC-002 | Partially specified — originally scoped only to user-reported lost time ("can't work 4–8 PM"); `05` §1 now also defines the new-Fixed-Commitment-collision trigger (September 2026 review), which this story's acceptance criteria does not yet mention. Jira change recommended. |
| `RESC-013` | Recurring Intention end-to-end integration | `05` §4, `02` §2.5 | DOM-004, SCH-001, RESC-010, RESC-007 | Fully specified — `05` §4, `02` §2.5. |

### Execution/Focus

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `EXEC-001` | Focus session lifecycle | `09` §1, `02` §2.6 | UI-001, SCH-015 | Fully specified — `09` §1, `02` §2.6. |
| `EXEC-002` | Task brief display | `09` §2 | EXEC-001 | Fully specified — `09` §2. |
| `EXEC-003` | Overrun handling | `09` §4 (resolved) | EXEC-001 | Fully specified — `09` §4 (resolved). |
| `EXEC-004` | Completion report + belief-state update | `09` §5, `02` §1.7 | EXEC-001 | Fully specified — `09` §5, `02` §1.7. |
| `EXEC-005` | Planned/Executed/Achieved tracking | `11` §1 | EXEC-004, EVT-002 | Fully specified — `11` §1. |

### AI Architecture

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `AI-001` | Structured proposal schema & validation | `07` §3–4, `01` §2 | DOM (all) | Fully specified — `07` §3–4, `01` §2 (this document now provides the section `07` always pointed at). |
| `AI-002` | AI failure/timeout/malformed handling | `07` §5 | AI-001 | Fully specified — `07` §5. |
| `AI-003` | Progressive clarifying questions | `07` §4, `14` §3 | AI-001 | Fully specified — `07` §4, `14` §3. |
| `AI-004` | estimate_duration proposal | `07` §3 | AI-001 | Fully specified — `07` §3. |
| `AI-005` | suggest_split proposal | `07` §3 | AI-001 | Fully specified — `07` §3. |
| `AI-006` | suggest_resource proposal | `07` §3 | AI-001, ROAD-003 | Fully specified — `07` §3. |
| `AI-007` | classify_hard_consequence proposal | `07` §3 | AI-001 | Partially specified — `07` §3 defines the proposal shape; the timing race with `SCH-004`'s consumption of the flag is now documented (`02` §1.4, `04` §2.2) but not yet reflected in this story's acceptance criteria. Jira change recommended. |
| `AI-008` | extract_roadmap proposal | `07` §3 | AI-001, ROAD-001 | Fully specified — `07` §3; integrates with `06` §1.3, §2 (reconstructed). |
| `AI-009` | suggest_goal_revision proposal | `07` §3 | AI-001, RESC-008 | Fully specified — `07` §3, `05` §5 options list. |
| `AI-010` | summarize_progress proposal | `07` §3 | AI-001, ANLY-003 | Fully specified — `07` §3, `11` §4 (reconstructed). |
| `AI-011` | Confidence banding | `07` §4, §8 (resolved) | AI-001 | Fully specified — `07` §4, §8 (resolved). |
| `AI-012` | Rate/cost protection | `07` §7, `17` §3 | AI-002 | Fully specified — `07` §7, `17` §3 (reconstructed). |

### Roadmap/Resources

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `ROAD-001` | Document upload & deterministic parsing | `06` §1.1–1.2 | DOM-002, DOM-003 | Fully specified — `06` §1.1–1.2 (reconstructed). |
| `ROAD-002` | Review/Approve UI | `06` §1.4–1.5 | ROAD-001 | Fully specified — `06` §1.4–1.5, `14` §4 (both reconstructed). |
| `ROAD-003` | Resource entity + attachment | `02` §1.8, `06` §3 | DOM-003 | Fully specified — `02` §1.8, `06` §3 (reconstructed). |
| `ROAD-004` | Resource replacement | `06` §3.1 | ROAD-003 | Fully specified — `06` §3.1 (reconstructed). |
| `ROAD-005` | Resource feedback (3-tier) | `06` §3.2 | ROAD-003, MEM-001 (soft dep) | Fully specified — `06` §3.2 (reconstructed). |
| `ROAD-006` | Screenshot fixed-schedule import | `06` §4 | DOM-006 | Partially specified — `06` §4 (reconstructed) documents the deterministic OCR/parse-to-Fixed-Commitment pipeline as EXPLICIT/STRONGLY INFERRED, but whether screenshot-imported Fixed Commitments pass through a Review/Approve-style gate before committing, or commit directly, is recorded there as an **OPEN PRODUCT DECISION** — `12` §5 currently assumes direct commit, reasoned but not confirmed. Resolve before implementing. |
| `ROAD-007` | AI-assisted ingestion enhancement | `06` §1.3, §2 | ROAD-001, AI-008 | Fully specified — `06` §1.3, §2 (reconstructed). |

### Memory/Preferences

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `MEM-001` | Preference entity + confirmation flow | `08` §1–2, `02` §5 | AI-003 | Fully specified — `08` §1–2 (reconstructed), `02` §5. |
| `MEM-002` | Preference management UI | `08` §4 | MEM-001 | Fully specified — `08` §4 (reconstructed). |
| `MEM-003` | Contextual preference application | `08` §3 | MEM-001 | Fully specified for its stated scope (contextual application of an already-confirmed preference, `08` §3). The adjacent question of whether *repeated manual rescheduling* should ever become a suggested preference is a separate, currently-unimplemented **OPEN PRODUCT DECISION** (`08` §5) with no Jira story at all — see gap list below. |
| `MEM-004` | Category default flexibility config | `02` §1.9 | DOM-005 | Fully specified — `02` §1.9. |

### Analytics

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `ANLY-004` | Historical completion rate calc | `11` §5 | EXEC-005 | Fully specified — `11` §5 (reconstructed). |
| `ANLY-001` | Headline metrics | `11` §2 | EXEC-005 | Fully specified — `11` §2 (reconstructed). |
| `ANLY-002` | Planned/Executed/Achieved reporting | `11` §1 | EXEC-005 | Fully specified — `11` §1 (reconstructed). |
| `ANLY-003` | Why-falling-behind analysis | `11` §4 | ANLY-001 | Fully specified — `11` §4 (reconstructed). |

### Security

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `SEC-001` | Authorization/user-scoping audit | `15` §2 | All API stories | Fully specified — `15` §2. |
| `SEC-002` | Data export | `15` §3 | SEC-001 | Fully specified — `15` §3. |
| `SEC-003` | Account deletion (30-day window) | `15` §3 (resolved) | SEC-001 | Fully specified — `15` §3 (resolved). |
| `SEC-004` | AI context minimization enforcement | `15` §4, `07` §6 | AI-001 | Fully specified — `15` §4, `07` §6. |
| `SEC-005` | Idempotency key enforcement | `12` §1 (resolved), `13` §1 | SCH-003, EXEC-001 | Fully specified — `12` §1 (resolved), `13` §1. |

### Operations

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `OPS-001` | Structured logging + correlation IDs | `17` §1 | FOUND-001 | Fully specified — `17` §1 (reconstructed). |
| `OPS-002` | Health checks | `17` §8 | FOUND-001 | Fully specified — `17` §8 (reconstructed). |
| `OPS-003` | AI retry/backoff/cost config finalization | `17` §3 | AI-012 | Fully specified — `17` §3 (reconstructed). |
| `OPS-004` | Full test suite completion & CI gating | `16` (all) | All stories | Fully specified — `16` (reconstructed, all categories). |
| `OPS-005` | Production deployment config | `17` §5–7 | OPS-001, OPS-002 | Fully specified — `17` §5–7 (reconstructed). |

### UI Core

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `UI-001` | Today/Now/Next/Later screen | `14` §2 | SCH-013 | Fully specified — `14` §2 (reconstructed). |
| `UI-002` | Calendar (week) view | `14` §2 | UI-001 | Fully specified — `14` §2 (reconstructed); FullCalendar commitment confirmed independently in `01` §5. |
| `UI-003` | Minimum Viable Day banner | `14` §2, `05` §9 | RESC-008 (soft dep, stub until Sprint 6) | Fully specified — `14` §2, `05` §9. |
| `UI-006` | Onboarding flow | `14` §1 | FOUND-002 | Fully specified — `14` §1 (reconstructed). |
| `UI-007` | Manual drag/move + sticky flag | `14` §2, `05` §7 | SCH-003 | Fully specified — `14` §2, `05` §7. |

### UI Remaining

| Story | Requirement | Owning Spec | Dependencies | Status |
|---|---|---|---|---|
| `UI-004` | Rescheduling explanation / "what changed" UI | `14` §5, `10` §4 | EVT-004, RESC-006, SCH-015 | Fully specified — `14` §5, `10` §4. |
| `UI-005` | Notification implementation | `14` §7, Master Spec §1.20 | RESC-006, RESC-008, RESC-009 | Fully specified — `14` §7 (reconstructed), Master Spec §1.20. |
## 4. Requirements With No Owning Story (Genuine Backlog Gaps)

**[EXPLICIT]** — confirmed by grepping the full 110-row backlog for "cancel": zero matches.

1. **Task cancellation.** `02` §2.3 now defines a `Cancelled` Work State for Commitments (added in the September 2026 review pass, since `05` §3's Overload Resolution and `02` §2.1's Goal-abandonment side effect both required a terminal non-completion state that didn't exist). No Jira story implements the API, UI, or dependency-cascade behavior for it. This is a missing story, not a missing spec section — the domain model is ready; nothing builds against it yet.
2. **Repeated-manual-move handling.** No story exists because no product decision exists yet (`08` §5, OPEN PRODUCT DECISION). Once decided, this would most likely land as a new Memory/Preferences story depending on `MEM-001`.
3. **Cancellation dependency-cascade policy.** A sub-question of (1) above — whether cancelling a Commitment cascades to its `commitment_dependency` graph. Recorded as an OPEN PRODUCT DECISION in `ATLAS_SPECIFICATION_REVIEW.md` §5.2; blocks a complete acceptance criterion for the story in (1), not merely a nice-to-have.

## 5. Dependency-Order Risk Check

**[STRONGLY INFERRED]** from the Dependencies column across all 91 stories, cross-checked against `18`'s phased plan.

- No circular dependencies were found in the full 91-story graph.
- Every "soft dep" (a dependency marked non-blocking, e.g. `SCH-006`'s soft dependency on `RESC-007`, `SCH-009`'s soft dependency on `ANLY-004`, `ROAD-005`'s soft dependency on `MEM-001`) correctly matches a case where `18`'s phased plan builds the dependent story before the dependency lands, using an explicit stub — this is intentional, not an oversight, and matches Phase 0's "walking skeleton first, breadth later" philosophy.
- One ordering risk worth flagging: `SEC-001` ("Authorization/user-scoping audit") depends on "All API stories," and `OPS-004` ("Full test suite completion") depends on "All stories." Both are correctly late-phase (`18` Phase 7) but this means user-scoping is only *audited* at the end, not verified incrementally per endpoint as it's built. `15` §2 states the principle ("cross-user data access is not a code path that should exist, not merely one that's permission-checked") but no story enforces it per-endpoint during Phases 1–6. **[STRONGLY INFERRED] recommendation:** treat `15` §2's user-scoping rule as part of every API story's own Definition of Done (`18` §3 already requires "Security requirements met... user-scoping verified" per feature), with `SEC-001` as a final sweep rather than the only check.

## 6. Genuine Gaps / Requires Product Decision

None beyond those already listed in §4 above and in the owning documents' own Genuine Gaps sections. This document is a map, not a source of new requirements — it does not introduce product decisions that `02`–`19` and the reconstructed set don't already carry.
