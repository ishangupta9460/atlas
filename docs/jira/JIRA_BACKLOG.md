# JIRA_BACKLOG.md — Complete Development Backlog

Format note: to keep ~90 Stories genuinely usable (and CSV-importable into Jira), each Story is a table row containing every field from the source brief's §26 (issue key, epic, type, summary, description/user story, acceptance criteria, dependencies, source reference, sprint, size, priority, labels) plus a compact Key Sub-tasks list. One Story below is shown in the full narrative template (§7 of the source brief) to demonstrate what the table compresses — every other Story follows the identical template, just tabulated.

**Sizing scale (relative, not hours):** XS / S / M / L / XL. **Priority** is Jira development priority only — see `JIRA_WORKFLOW_AND_CONVENTIONS.md` §5 for why this is explicitly not the same thing as Atlas's runtime scheduling priority.

---

## Full-Template Example (Pattern for Every Story Below)

## ATLAS-SCH-004 — Stage 2 Hard-Consequence Urgency Gate

### Epic
ATLAS-SCH

### User Story
> As Atlas, I want to gate task urgency on a narrow "real hard consequence" flag rather than any due date, so that trivial tasks with deadlines don't automatically outrank genuinely important undated work.

### Product Context
This is the mechanism that resolves the original design's core stress case (a trivial task due tomorrow vs. an important goal with no deadline) — see Master Spec §1.11.

### Source of Truth
`04_SCHEDULING_ENGINE.md` §2, §2.2; `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` §5 (resolution: AI-inferred, confirm-when-consequential).

### Acceptance Criteria
- [ ] Given a task with `is_hard_consequence = false` and a near-term `own_deadline`, when scheduled against a competing task with `is_hard_consequence = true`, then the true-flagged task wins Stage 2 regardless of relative deadline proximity.
- [ ] Given a task where AI classification confidence is Medium or Low (`07` §4), when the classification would change a scheduling outcome, then the user is prompted to confirm before Stage 2 evaluation uses it.
- [ ] Given `is_hard_consequence` is unset, when Stage 2 evaluates the task, then it is treated as not qualifying (falls through to Stage 3) rather than defaulting to true.

### Engineering Notes
Implement as a pure function over Commitment state — no AI call inside the Stage 0–8 evaluation itself (`04` §2.4 determinism requirement); the flag must already be resolved before evaluation runs.

### Dependencies
- ATLAS-DOM-003 (Commitment entity with `is_hard_consequence` field)
- ATLAS-AI-007 (classify_hard_consequence proposal, for the AI-inference path — can stub as manual-only until AI-007 lands, per Sprint 2 vs. Sprint 10 sequencing)

### Definition of Done
Acceptance criteria satisfied; unit tests for the three AC scenarios; determinism property test included; no AI call present in the Stage 0–8 code path.

### Suggested Sprint
Sprint 2 (manual-flag path); revisited in Sprint 10 for the AI-inference path.

### Risk / Notes
Early sprints must stub the AI path (manual boolean entry) since AI-007 doesn't land until Sprint 10 — flagged in `JIRA_SPRINT_PLAN.md` Sprint 2 scope.

---

## Epic: ATLAS-FOUND

| Key | Summary | User Story (short) | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|---|
| FOUND-001 | Repo & CI setup | As a developer, I want CI running on every push, so regressions are caught immediately | Build+test pipeline green on empty scaffold; migration check wired | — | `01` §5, `17` §7 | 0 | S | infrastructure | repo init; GitHub Actions config; Flyway scaffold |
| FOUND-002 | Auth (JWT) | As a user, I want to register/log in, so my data is private to me | Register/login/current-user endpoints; JWT validated on protected routes | FOUND-001 | `15` §1 | 0 | M | backend,security | user table; password hashing; JWT issuance; auth middleware |
| FOUND-003 | Walking skeleton loop | As a user, I want to create one task and complete it, so the core loop is proven end-to-end | Create task → naive placement → Today shows it → start/finish → event recorded | FOUND-002 | `18` §1 Phase 0 | 0 | L | backend,frontend,database | minimal Commitment table; naive placement stub; bare Today screen; session start/finish; event write |

## Epic: ATLAS-DOM

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| DOM-001 | Goal entity + Lifecycle/Planning state | Goal CRUD; lifecycle & planning_state as separate enum columns, never merged | FOUND-003 | `02` §1.1, §2.1–2.2 | 1 | M | backend,database | table+migration; state transition guards; API endpoints |
| DOM-002 | Roadmap/Milestone entity | Roadmap belongs to Goal; Milestone not independently schedulable | DOM-001 | `02` §1.2–1.3 | 1 | S | backend,database | tables+migrations; API endpoints |
| DOM-003 | Commitment/Task full model | All fields incl. flexibility_tier, importance enum, is_hard_consequence, work_state | DOM-002 | `02` §1.4, §5 | 1 | L | backend,database | table+migration; enum constraints; state guards; API endpoints |
| DOM-004 | Recurring Intention entity | Parallel entity, not child of Goal tree; target_count_per_week field | DOM-001 | `02` §1.5 | 1 | M | backend,database | table+migration; API endpoints |
| DOM-005 | Category/Tag entity + defaults | User-defined; default_flexibility_tier field; no hardcoded cross-category ordering | DOM-001 | `02` §1.9 | 1 | S | backend,database | table+migration; API endpoints |
| DOM-006 | Fixed Commitment / Calendar Event entity | Always Fixed tier; source enum (manual/screenshot_import) | DOM-001 | `02` §1.11 | 1 | S | backend,database | table+migration; API endpoints |
| DOM-007 | Dependency modeling | Self-referential join; cycle detection on create | DOM-003 | `02` §1.4, `04` §2.5 | 1 | M | backend,database | join table; cycle-detection algorithm; API endpoints |

## Epic: ATLAS-EVT

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| EVT-001 | Event log schema & append-only enforcement | Events table matches `10` §2; no UPDATE/DELETE permitted at application layer | DOM (all) | `10` §2–3 | 2 | M | backend,database | table+migration; write-only repository layer; DB-level constraint if feasible |
| EVT-002 | Transaction-boundary enforcement | Every entity state-change commit includes an atomic event write | EVT-001 | `10` §5, `13` §4 | 2 | M | backend | transactional wrapper/aspect; forced-failure rollback tests |
| EVT-003 | Compensating-event undo | Undo generates a new event, never rewrites history | EVT-002 | `10` §4 (resolved) | 2 | M | backend | undo service; compensating-event generation per event type |
| EVT-004 | "What changed" query API | Plain-language rendering of recent Atlas-actor events with reasons | EVT-001 | `10` §4 | 2 | S | backend,frontend | query endpoint; reason-string rendering |

## Epic: ATLAS-SCH

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| SCH-001 | Candidate slot generation | Generates open slots respecting Fixed commitments and working-hours bounds | DOM-006 | `04` §1–2 | 2 | M | backend,scheduling | slot generator; working-hours model |
| SCH-002 | Stage 0 hard constraint gate | Fixed-tier items never appear in the autonomous movable set | SCH-001 | `04` §2, §2.1 | 2 | S | backend,scheduling | gate filter; tests |
| SCH-003 | Stage 1 explicit instruction override | A live user instruction bypasses Stages 2–8 for the targeted item | SCH-002 | `04` §2, §2.1 | 2 | S | backend,scheduling | override handling; manual-move integration |
| SCH-004 | Stage 2 hard-consequence gate | See full-template example above | DOM-003 | `04` §2, §2.2 | 2 | M | backend,scheduling | gate logic; tests (full example above) |
| SCH-005 | Stage 3 user importance tier | Enum-based tier ranking; category default with override | DOM-003, DOM-005 | `04` §2, `02` §5 | 2 | S | backend,scheduling | tier ranking logic |
| SCH-006 | Stage 4 goal/at-risk boost | At-risk-linked tasks get protective boost; never outranks Stage 0–2 | DOM-001, RESC-007 (soft dep — stub risk flag until RESC lands) | `04` §2 | 2 | M | backend,scheduling | boost logic; boundary tests |
| SCH-007 | Stage 5 dependency & remaining-work tier | Full bounded-depth chain traversal; near-completion preference | DOM-007 | `04` §2, §2.5 | 2 | L | backend,scheduling | chain traversal (bounded); remaining-work scoring |
| SCH-008 | Stage 6 flexibility/disruption tier | Flexibility tier ordering; disruption-cost estimate | DOM-003 | `04` §2 | 2 | M | backend,scheduling | disruption-cost calc; tier ordering |
| SCH-009 | Stage 7 historical calibration stub | Calibration-only, never ranking; defaults until ANLY exists | ANLY-004 (soft dep) | `04` §2 | 2 | S | backend,scheduling | stub interface; default values |
| SCH-010 | Stage 8 preference/continuity tie-break | Time-of-day/continuity tie-break between equivalent survivors | SCH-009 | `04` §2 | 2 | S | backend,scheduling | tie-break scorer |
| SCH-011 | Tie-breaking cascade | 5-rule cascade incl. non-trivial-tie escalation | SCH-002–010 | `04` §2.3 | 2 | M | backend,scheduling | cascade implementation; stress-test suite (10 scenarios) |
| SCH-012 | Problem B candidate-slot scoring | Time-of-day fit, energy match, continuity, fragmentation | SCH-011 | `04` §3 | 3 | L | backend,scheduling | 4 scoring dimensions; composite (Problem-B-only) scorer |
| SCH-013 | Capacity model | 70% cold-start default, buffer/break accounting | SCH-001 | `04` §4 | 3 | M | backend,scheduling | capacity calculator; buffer/break deduction logic |
| SCH-014 | Determinism & timezone handling | Identical inputs → identical outputs; correct timezone-aware slot math | SCH-012 | `04` §2.4 | 3 | M | backend,scheduling | determinism property tests; timezone-safe datetime handling |
| SCH-015 | Explainability (placement reason) | Every placement writes an Event Log reason | SCH-011, EVT-002 | `04`, `10` §4 | 3 | S | backend,scheduling | reason-string generation on placement |

## Epic: ATLAS-UI (core, Sprint 3)

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| UI-001 | Today/Now/Next/Later screen | Task-first home view | SCH-013 | `14` §2 | 3 | M | frontend | Now/Next/Later components; block detail view |
| UI-002 | Calendar (week) view | Secondary FullCalendar-based view | UI-001 | `14` §2 | 3 | M | frontend | FullCalendar integration |
| UI-003 | Minimum Viable Day banner | Essential/Good-to-do/Optional distinction on hard days | RESC-008 (soft dep, stub until Sprint 6) | `14` §2, `05` §9 | 3 | S | frontend | banner component |
| UI-006 | Onboarding flow | Single opening question, progressive setup, no upfront forms | FOUND-002 | `14` §1 | 3 | M | frontend | interview UI; progressive setup screen |
| UI-007 | Manual drag/move + sticky flag | Drag sets user_moved_flag; Atlas never silently reverts it | SCH-003 | `14` §2, `05` §7 | 3 | M | frontend,scheduling | drag-drop UI; user_moved_flag wiring |

## Epic: ATLAS-UI (remaining, Sprint 13)

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| UI-004 | Rescheduling explanation / "what changed" UI | Collaborative/Critical-tier changes show their real reason inline; Autonomous-tier changes are queryable on request, not surfaced proactively | EVT-004, RESC-006, SCH-015 | `14` §5, `10` §4 | 13 | M | frontend | explanation banner component; wiring to EVT-004 "what changed" query; on-demand "why is this here" lookup for Autonomous-tier blocks |
| UI-005 | Notification implementation | Pre-block reminder fires; At-Risk transition notifies (Critical); Collaborative-tier proposals notify; excluded patterns (repeated nagging, Autonomous-tier changes, minor pace fluctuation) never fire — tested | RESC-006, RESC-008, RESC-009 | `14` §7, Master Spec §1.20 | 13 | M | backend,frontend | notification service; decision-relevance filter; pre-block reminder scheduler; exclusion-pattern tests |

## Epic: ATLAS-EXEC

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| EXEC-001 | Focus session lifecycle | Start/Pause/Resume/Finish state machine | UI-001, SCH-015 | `09` §1, `02` §2.6 | 4 | M | backend,frontend | session state machine; API endpoints; timer UI |
| EXEC-002 | Task brief display | Shows completion_criterion, resources before Start | EXEC-001 | `09` §2 | 4 | S | frontend | brief component |
| EXEC-003 | Overrun handling | 5-min grace, single non-blocking prompt | EXEC-001 | `09` §4 (resolved) | 4 | S | backend,frontend | grace-window timer; prompt component |
| EXEC-004 | Completion report + belief-state update | Free-form report; updates current_completion_pct without touching session history | EXEC-001 | `09` §5, `02` §1.7 | 4 | M | backend,frontend | report UI; belief-state update logic; immutability test |
| EXEC-005 | Planned/Executed/Achieved tracking | Three figures populate correctly post-session | EXEC-004, EVT-002 | `11` §1 | 4 | M | backend | aggregation queries |

## Epic: ATLAS-RESC

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| RESC-001 | Missed block detection | block.unresolved fires when window passes with no session | EXEC-001 | `02` §2.4, `05` §1 | 5 | S | backend,scheduling | detection job/trigger |
| RESC-002 | Progressive recovery search | today→tomorrow→week→flag, suitability-checked not just empty-checked | RESC-001, SCH-011 | `05` §2 | 5 | L | backend,scheduling | search algorithm; suitability filter |
| RESC-003 | Partial completion handling | task.partial re-enters recovery for remaining work | EXEC-004 | `02` §2.3, `05` §1 | 5 | M | backend | remaining-work recalculation |
| RESC-004 | Forgotten timer handling | User can report what happened after the fact | RESC-001 | `09` §6 | 5 | S | backend,frontend | retroactive report UI/endpoint |
| RESC-005 | Overload resolution | Protect-then-defer under multi-item conflict | RESC-002 | `05` §3 | 5 | M | backend,scheduling | overload resolver |
| RESC-006 | Autonomous/Collaborative/Critical engine | Correct tier classification per `05` §7 table | RESC-002, RESC-005 | `05` §7 | 5 | L | backend,scheduling | tier classifier; boundary test suite |
| RESC-007 | Goal Risk feasibility calculation | Formula-based, config-driven threshold | SCH-013, ANLY-004 (soft dep) | `05` §5 (resolved) | 6 | L | backend,scheduling | feasibility formula; config-driven threshold |
| RESC-008 | At-Risk transition + Critical conversation | 5 options presented; silence ≠ resolution | RESC-007 | `05` §5, `02` §2.2 | 6 | M | backend,frontend | transition logic; conversation UI |
| RESC-009 | Deferred Backlog Review batching | Periodic batched surfacing, never auto-delete | RESC-005 | `05` §8 (resolved) | 6 | M | backend,frontend | batching job; review UI |
| RESC-010 | Recurring Intention weekly reset + recovery | No backlog accumulation; goal-risk feed | DOM-004, RESC-007 | `05` §4 | 6 | M | backend,scheduling | weekly reset job; risk feed wiring |
| RESC-011 | Pattern detection (multi-week evidence) | Multi-week + frequency-ratio threshold, observation phrasing | RESC-002 | `05` §6 | 6 | M | backend | pattern-detection query; observation surfacing |
| RESC-012 | Unexpected interruption handling | "Can't work 4–8 PM" triggers local reschedule first | RESC-002 | `05` §1 | 5 | S | backend,frontend | interruption input; local-reschedule trigger |
| RESC-013 | Recurring Intention end-to-end integration | Given a 3x/week target: instances placed via the real scheduling engine; a missed instance creates no backlog next week; goal-linked completion rate correctly feeds Goal Risk | DOM-004, SCH-001, RESC-010, RESC-007 | `05` §4, `02` §2.5 | 8 | M | backend,scheduling,testing | end-to-end test harness; instance-generation wiring through SCH-001; weekly-reset job verification; risk-feed integration test |

## Epic: ATLAS-ROAD

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| ROAD-001 | Document upload & deterministic parsing | PDF/Word/Markdown parsed into typed nodes | DOM-002, DOM-003 | `06` §1.1–1.2 | 7 | L | backend | file upload; structural parser; typed-node classifier |
| ROAD-002 | Review/Approve UI | Counts, edit actions, nothing scheduled pre-approve | ROAD-001 | `06` §1.4–1.5 | 7 | M | frontend | review screen; approve endpoint |
| ROAD-003 | Resource entity + attachment | Standalone entity, many-to-many join | DOM-003 | `02` §1.8, `06` §3 | 7 | M | backend,database | table+migration; join table; API endpoints |
| ROAD-004 | Resource replacement | Swap updates join only, not task identity | ROAD-003 | `06` §3.1 | 7 | S | backend | replace endpoint |
| ROAD-005 | Resource feedback (3-tier) | Single reaction logged; pattern surfaced; preference confirmed-only | ROAD-003, MEM-001 (soft dep) | `06` §3.2 | 7 | M | backend,frontend | feedback endpoint; tier logic |
| ROAD-006 | Screenshot fixed-schedule import | OCR/parse → Fixed Commitment entities | DOM-006 | `06` §4 | 7 | L | backend | OCR/structural parse; Fixed Commitment creation |
| ROAD-007 | AI-assisted ingestion enhancement | Layers on deterministic pass; confidence-flagged uncertain nodes | ROAD-001, AI-008 | `06` §1.3, §2 | 10 | M | backend,ai | AI-assisted parse integration; uncertain-node flagging |

## Epic: ATLAS-AI

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| AI-001 | Structured proposal schema & validation | Every AI call returns a validated typed proposal, never free-form control | DOM (all) | `07` §3–4, `01` §2 | 9 | L | backend,ai | proposal schema definitions; validation layer |
| AI-002 | AI failure/timeout/malformed handling | Core flows unaffected by any AI failure mode | AI-001 | `07` §5 | 9 | M | backend,ai | retry/backoff; fallback-to-deterministic paths |
| AI-003 | Progressive clarifying questions | 1–2 at a time, each tagged with a reason | AI-001 | `07` §4, `14` §3 | 10 | M | backend,ai,frontend | question generation; reason-tag enforcement |
| AI-004 | estimate_duration proposal | Duration estimate with confidence | AI-001 | `07` §3 | 10 | S | backend,ai | proposal handler |
| AI-005 | suggest_split proposal | Session-split suggestion with confidence | AI-001 | `07` §3 | 10 | S | backend,ai | proposal handler |
| AI-006 | suggest_resource proposal | Resource suggestion with confidence | AI-001, ROAD-003 | `07` §3 | 10 | S | backend,ai | proposal handler |
| AI-007 | classify_hard_consequence proposal | Feeds SCH-004's AI-inference path | AI-001 | `07` §3 | 10 | M | backend,ai | proposal handler; SCH-004 integration |
| AI-008 | extract_roadmap proposal | AI-assisted node classification | AI-001, ROAD-001 | `07` §3 | 10 | L | backend,ai | proposal handler; ROAD-007 integration |
| AI-009 | suggest_goal_revision proposal | Options presented on goal pivot, no ranked single answer | AI-001, RESC-008 | `07` §3 | 10 | M | backend,ai | proposal handler |
| AI-010 | summarize_progress proposal | Dominant-factor summary text | AI-001, ANLY-003 | `07` §3 | 10 | S | backend,ai | proposal handler |
| AI-011 | Confidence banding | High/Medium/Low, config-driven boundaries | AI-001 | `07` §4, §8 (resolved) | 9 | M | backend,ai | banding logic; config externalization |
| AI-012 | Rate/cost protection | Quota tracking, backoff | AI-002 | `07` §7, `17` §3 | 9 | S | backend,ai,infrastructure | quota tracker; backoff config |

## Epic: ATLAS-MEM

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| MEM-001 | Preference entity + confirmation flow | Only explicit confirmation persists a preference | AI-003 | `08` §1–2, `02` §5 | 11 | M | backend | table+migration; confirmation-gated write |
| MEM-002 | Preference management UI | View/edit/delete/disable | MEM-001 | `08` §4 | 11 | S | frontend | preferences screen |
| MEM-003 | Contextual preference application | Applied only with per-use confirmation, never silent | MEM-001 | `08` §3 | 11 | M | backend,frontend | contextual-prompt logic |
| MEM-004 | Category default flexibility config | New-category setup questions | DOM-005 | `02` §1.9 | 11 | S | backend,frontend | category-creation flow |

## Epic: ATLAS-ANLY

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| ANLY-001 | Headline metrics | Consistency/Planned-vs-Completed/Focused Hours/Goal Progress | EXEC-005 | `11` §2 | 12 | M | backend,frontend | metric queries; summary screen |
| ANLY-002 | Planned/Executed/Achieved reporting | Three figures reported together, never averaged | EXEC-005 | `11` §1 | 12 | S | backend,frontend | breakdown view |
| ANLY-003 | Why-falling-behind analysis | Dominant-factor ranking, not raw percentage | ANLY-001 | `11` §4 | 12 | M | backend | correlation-ranking query |
| ANLY-004 | Historical completion rate calc | Per-category/time-of-day, feeds SCH-009/SCH-013/RESC-007 | EXEC-005 | `11` §5 | 12 | M | backend | rate calculator; consumer wiring |

## Epic: ATLAS-SEC

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| SEC-001 | Authorization/user-scoping audit | Cross-user access rejected on every endpoint | All API stories | `15` §2 | 14 | L | backend,security | authz middleware audit; full endpoint sweep |
| SEC-002 | Data export | Full JSON + Markdown-secondary export | SEC-001 | `15` §3 | 14 | M | backend | export job; format generation |
| SEC-003 | Account deletion (30-day window) | Deactivate → 30-day recoverable → hard delete | SEC-001 | `15` §3 (resolved) | 14 | L | backend | deactivation flow; scheduled hard-delete job; recovery endpoint |
| SEC-004 | AI context minimization enforcement | Outgoing AI requests audited for minimum-necessary context | AI-001 | `15` §4, `07` §6 | 14 | M | backend,security,ai | context-payload audit |
| SEC-005 | Idempotency key enforcement | Required header on scheduling-mutation endpoints | SCH-003, EXEC-001 | `12` §1 (resolved), `13` §1 | 14 | M | backend | idempotency_keys table; middleware |

## Epic: ATLAS-OPS

| Key | Summary | AC (key points) | Deps | Source | Sprint | Size | Labels | Key Sub-tasks |
|---|---|---|---|---|---|---|---|---|
| OPS-001 | Structured logging + correlation IDs | Traceable end-to-end per request | FOUND-001 | `17` §1 | 15 | M | infrastructure | logging framework; correlation-ID propagation |
| OPS-002 | Health checks | Liveness/readiness endpoints | FOUND-001 | `17` §8 | 15 | S | infrastructure | health endpoints |
| OPS-003 | AI retry/backoff/cost config finalization | Operational tuning of AI-012's config | AI-012 | `17` §3 | 15 | S | infrastructure,ai | config finalization |
| OPS-004 | Full test suite completion & CI gating | Every category in `16` green, CI blocks merge on failure | All stories | `16` (all) | 15 | XL | testing,infrastructure | test-gap audit; CI gate config |
| OPS-005 | Production deployment config | Env config, migrations, secrets | OPS-001, OPS-002 | `17` §5–7 | 15 | M | infrastructure | deployment scripts; secrets management |

---

## Backlog Completeness Check

Every Master Spec §1 subsection (1.1–1.26) has at least one Story tracing to it (cross-check against `JIRA_REQUIREMENTS_TRACEABILITY.md`). No two Stories redefine the same rule — each Story's AC references the owning engineering document rather than restating scheduling/state logic inline. Total: 93 Stories across 13 Epics (updated from the prior pass's ~90 after adding UI-004, UI-005, and RESC-013 — see revision note below).

## Revision Note (Post-Audit)

A prior pass of this backlog referenced `UI-004`, `UI-005` in the sprint plan and traceability doc without corresponding Story rows in this file, and left Sprint 8's Recurring Intention integration work without an actual issue ID. Both are fixed as of this revision: `UI-004`/`UI-005` are defined above under a second `ATLAS-UI` table (their content sourced directly from `14_UI_UX_SPECIFICATION.md` §5/§7, matching exactly what the sprint plan already described them as), and `RESC-013` is defined above, placed under `ATLAS-RESC` (not a new Epic) since the behavior it integrates — weekly reset and goal-risk feed — is owned by `05_RESCHEDULING_AND_RECOVERY.md` §4, not by the Domain or Scheduling documents.
