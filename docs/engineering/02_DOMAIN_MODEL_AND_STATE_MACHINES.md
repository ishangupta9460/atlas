# 02 — Domain Model & State Machines

**Authoritative for:** every entity definition, field ownership, relationship, invariant, and state transition in Atlas.
**Source:** Master Spec §1.4, §1.7, §1.15, §1.18.
**Referenced by:** all other documents. No other document may redefine an entity's fields, relationships, or transitions — reference this document by section instead.

---

## 1. Entities

### 1.1 Goal
- **Purpose:** something the user wants to achieve.
- **Fields (owned here):** id, user_id, title, description, target_deadline (nullable), created_at.
- **Relationships:** has one Roadmap (optional — a Goal can exist before a roadmap is built); has many Commitments (directly or via Roadmap → Milestone).
- **State:** carries Lifecycle and (derived) Planning State — see §2.1, §2.2.
- **Invariant:** a Goal's target_deadline, if present, is independent of any Commitment's own deadline (Master Spec §1.4).

### 1.2 Roadmap
- **Purpose:** the structured plan supporting a Goal.
- **Fields:** id, goal_id, source (`user_interview` | `imported`), created_at.
- **Relationships:** belongs to one Goal; has many Milestones and typed nodes (§1.6 below).
- **Invariant:** a Roadmap is never directly schedulable — only the Commitments/Tasks it contains are (Master Spec §1.5).

### 1.3 Milestone
- **Purpose:** grouping unit within a Roadmap.
- **Fields:** id, roadmap_id, title, order.
- **Not schedulable.**

### 1.4 Commitment / Task

**Approved DOM-003 contract (DEC-0009):** owner `user_id` is required and immutable.
Title and free-text completion criterion may be null/blank only while defining Draft;
both nonblank establish Ready on create/update. After leaving Draft neither may be cleared.
Description is nullable. `category_id` is nullable. Importance is copied from an explicit
value or the Category default, in that order; absence of both is a validation error. Store
that effective value; later category/default edits do not recompute it. Flexibility uses an
explicit value or category default on creation, with no universal fallback. PATCH preserves
stored importance/flexibility unless explicitly supplied.
`own_deadline` is a nullable absolute Java Instant. Input requires an explicit offset or Z;
normalize to UTC and truncate to microseconds before persistence/audit. Read as UTC ISO-8601.
No date-only/local-timezone inference. Timezone preferences, DST/recurrence, local calendar
rendering and deadline scheduling are later concerns. `is_hard_consequence` defaults false
until a validated classification exists; DOM-003 exposes no classifier or public flag setter.
`user_moved_flag` starts false; no movement behavior is introduced. Completion belief is
DECIMAL(5,2), 0..100, initially 0, and not publicly writable here; EXEC-004 owns reporting.
No stored typed-roadmap-node-origin field is required in this increment.
- **Purpose:** a concrete, schedulable piece of work.
- **Fields:** id, milestone_id (nullable — a Commitment can exist outside any roadmap, per Master Spec §1.4), goal_id (nullable, derivable via milestone if present), title, description, completion_criterion (free text, Master Spec §1.19), own_deadline (nullable), is_hard_consequence (boolean — set via AI inference from task context, with user confirmation surfaced only when the classification is consequential to a scheduling decision, never a mandatory checkbox; see `07_AI_ARCHITECTURE.md` §3 `classify_hard_consequence` proposal type), importance (enum: `low` | `medium` | `high` | `critical`, defaulted from category, per-task override — resolved, no numeric scale), flexibility_tier (enum: Fixed | Protected | Flexible | Optional — see `06`/Master Spec §1.7), category_id, work_state, user_moved_flag (boolean, set by manual drag/edit — Master Spec §1.21), created_at.
- **Relationships:** optionally belongs to a Milestone; has many Scheduled Blocks over its life; has many Resources (many-to-many via a join entity); may have Dependencies (self-referential many-to-many, "blocks"/"blocked_by").
- **State:** Work State (§2.3).
- **Invariant:** own_deadline, when present, only qualifies for Stage 2 of the Scheduling Engine if it also carries a hard-consequence flag (see `04_SCHEDULING_ENGINE.md` §2.2, and Genuine Gaps §4 of this doc).

### 1.5 Recurring Intention
- **Purpose:** a repeated weekly target, not a single task (Master Spec §1.4, §1.16).
- **Fields:** id, user_id, goal_id (nullable), title, target_count_per_week, category_id, flexibility_tier, created_at.
- **Relationships:** generates many Scheduled Block instances per week; not a child of Goal/Roadmap/Milestone — a parallel entity.
- **Invariant:** does not carry its own deadline in the normal sense; resets to full target_count_per_week every week (Master Spec §1.16) — no permanent backlog accumulates from missed instances.

### 1.6 Scheduled Block
- **Purpose:** Atlas's specific placement of a Commitment or Recurring Intention instance onto the calendar.
- **Fields:** id, commitment_id (nullable if generated from a Recurring Intention instead), recurring_intention_id (nullable, mutually exclusive with commitment_id), start_time, end_time, placement_reason (references Event Log entry — `10_EVENT_LOG.md`), user_moved_flag.
- **Relationships:** belongs to one Commitment OR one Recurring Intention (exactly one); has zero or one Actual Session.
- **Invariant:** a Commitment may have several Scheduled Blocks over its life (e.g., after a miss and reschedule) — old blocks are not deleted, they are superseded (see §2.4).

### 1.7 Actual Session
- **Purpose:** what really happened.
- **Fields:** id, scheduled_block_id, actual_start, actual_end, notes, user_reported_outcome (free text), created_at.
- **Immutability:** once created, an Actual Session's actual_start/actual_end/notes/user_reported_outcome are never rewritten by a progress correction (Master Spec §1.15). A correction changes the Commitment's *belief state* (§1.4 completion percentage — see below), not this record.
- **Note on completion belief:** Commitment carries a derived/mutable `current_completion_pct` field, editable directly by the user, decoupled from Actual Session history.

### 1.8 Resource
- **Purpose:** supporting material (video, PDF, doc, link, book, course).
- **Fields:** id, type, title, url_or_file_ref, added_by (`user` | `ai_suggested`).
- **Relationships:** many-to-many with Commitment/Task via a join entity (`task_resource`), never duplicated per task (Master Spec §1.6).
- **Resource Feedback (separate, not a Resource field):** see `06_ROADMAP_AND_RESOURCE_SYSTEM.md` §3 for the three-tier feedback model — owned there, not here.

### 1.9 Category / Tag
- **DOM-003 dependency extension (DEC-0009):** nullable `default_importance`, constrained to
  `low`/`medium`/`high`/`critical`. Existing categories get no fabricated default. Create/update
  may set it; PATCH omission preserves and explicit null clears. Existing Commitment importance
  remains unchanged. Category deletion is refused while Commitments or Recurring Intentions reference it.
- **Fields:** id, user_id, name, default_flexibility_tier, color.
- **Invariant:** user-defined; Atlas does not hardcode cross-category importance ordering (Master Spec §1.7).

### 1.10 User Preference (Memory)
- **Fields:** id, user_id, domain (`learning` | `scheduling` | `resource` | `rescheduling`), statement (free text), created_from (reference to originating conversation turn, optional), active (boolean — supports "temporarily disabled" per Master Spec §1.8).
- **Invariant:** created only on explicit user confirmation (Master Spec §1.8) — never written directly from an AI observation without that confirmation step.

### 1.11 Fixed Commitment / Calendar Event
- **Fields:** id, user_id, title, start_time, end_time, source (`manual` | `screenshot_import`), recurrence_rule (nullable).
- **Invariant:** always Flexibility Tier = Fixed; the only entity type that Stage 0 removes from the movable candidate set entirely (Master Spec §1.11).
- **Approved DOM-006 boundary (DEC-0008):** manual CRUD persists reservations independently of scheduling. Overlaps and repeated creates are allowed. Source is server-controlled; manual creation uses `manual`, while future approved import may use `screenshot_import`. Public recurrence input is null-only; the nullable storage field has no recurrence semantics in this story. Physical user deletion writes an immutable deletion event in the same transaction, with no cancellation lifecycle state. API contract: `12` §5.1.

### 1.12 Event Log Entry
- Owned entirely by `10_EVENT_LOG.md`. Referenced here only to note that most entities above have a "reason"/history relationship to it, not a duplicated field.

### 1.13 Goal Risk Snapshot
- **Purpose:** stores the feasibility calculation result at a point in time (Master Spec §1.17).
- **Fields:** id, goal_id, calculated_at, remaining_work_estimate, available_capacity_estimate, context_adjusted_completion_rate, result (`feasible` | `at_risk`), triggered_transition (boolean).
- **Relationship:** belongs to Goal; historical (append, not update) so risk trend is queryable for analytics (`11_ANALYTICS_AND_LEARNING.md`).

---

### 1.14 Scheduling Configuration (Chunk 1, DEC-0016)

User-owned configuration consists of one optional IANA scheduling timezone and a weekly
list of windows (starting ISO weekday, local start/end time, kind: working/sleep/protected),
plus one per-user workable fraction, buffer minutes, continuous-work minutes and break minutes.
It does not create another task/calendar/block model. Defaults, interval interpretation and
capacity semantics are owned by `04` §4/§6. Physical mapping is in `13`, API in `12` §14.
Direct user replacement is atomic and audited; identical replacements are no-ops.
Configuration does not transition or relocate existing Commitments or Scheduled Blocks.

## 2. State Machines

### 2.1 Goal — Lifecycle
`Active → Completed | Abandoned`

| Current | Trigger | Conditions | Result | Side effects | Event |
|---|---|---|---|---|---|
| Active | User marks all roadmap work done, or explicitly marks complete | — | Completed | Related Recurring Intentions stop generating instances | `goal.completed` |
| Active | User explicitly abandons | Explicit user action only — never inferred (Master Spec §3 rule 10) | Abandoned | Related open Scheduled Blocks cancelled | `goal.abandoned` |
| Active | (never auto-transitions to Abandoned from At Risk or Paused) | — | — | — | — |

### 2.2 Goal — Planning State
`Active → Deferred → Paused → At Risk` (not strictly linear — see transition table)

| Current | Trigger | Conditions | Result | Side effects | Event |
|---|---|---|---|---|---|
| Active | Scheduling overload pushes goal's tasks out of current week | Automatic, reversible (Master Spec §1.18 exception) | Deferred | Tasks' Scheduled Blocks removed, Commitments remain Ready | `goal.deferred` |
| Deferred | Capacity frees, item resurfaces in candidate pool | Automatic | Active | Re-enters Stage 0–8 | `goal.reactivated` |
| Active or Deferred | Feasibility calculation (Goal Risk Snapshot) crosses threshold | System-calculated; Critical-tier — requires user acknowledgment before formally changing (Master Spec §1.18) | At Risk | User presented options (increase effort / extend deadline / reduce scope / change method / defer / pause) | `goal.at_risk` |
| At Risk | User takes action restoring feasibility | User-confirmed | Active | New Goal Risk Snapshot recorded | `goal.risk_resolved` |
| At Risk | User explicitly pauses | Explicit user action | Paused | — | `goal.paused` |
| Paused | User explicitly resumes | Explicit user action | Active | Re-enters candidate pool | `goal.resumed` |

### 2.3 Commitment/Task — Work State

**DEC-0009 implementation boundary:** persisted values are `draft`, `ready`, `deferred`,
`in_progress`, `completed`, `cancelled`. Only the four transition rows below are implemented
in DOM-003. All other transitions are rejected, including deferral/reactivation/cancellation
and reopening. The earlier review recorded additional values/events but did not retain their
complete transition rows in this document; this is not permission to invent their guards.
Only readiness is exposed through domain CRUD. The other three guards are internal execution
integration seams, not session/progress endpoints. Completed/cancelled are not reopened.
Work State is never directly PATCHable. DEC-0003 cancellation workflow remains proposed DOM-008.
`Draft → Ready → In Progress → Completed`

| Current | Trigger | Result | Event |
|---|---|---|---|
| Draft | User/AI finishes defining task (title, completion_criterion set) | Ready | `task.ready` |
| Ready | Scheduling Engine places a Scheduled Block AND user starts it (Focus Mode Start) | In Progress | `task.started` |
| In Progress | User reports completion | Completed | `task.completed` |
| In Progress | User reports partial completion | Ready (remaining work recalculated, re-enters candidate pool per `05_RESCHEDULING_AND_RECOVERY.md`) | `task.partial` |

### 2.4 Scheduled Block — Lifecycle
| Current | Trigger | Result | Event |
|---|---|---|---|
| Scheduled | Block's time window arrives, user starts it | Active session begins (Actual Session created) | `block.started` |
| Scheduled | Block's time window passes, no session started | Unresolved | `block.unresolved` (feeds `05` recovery flow) |
| Scheduled/Unresolved | Recovery flow places a new block for the same Commitment | Old block → Superseded (not deleted — historical), new block → Scheduled | `block.superseded` |
| Active | User finishes (on time or after overrun prompt) | Completed | `block.completed` |

### 2.5 Recurring Intention — Weekly Cycle
| Current | Trigger | Result | Event |
|---|---|---|---|
| Week start | New week begins | target_count resets to full value regardless of prior week's completion (Master Spec §1.16) | `recurring_intention.reset` |
| Mid-week | Instance completed | remaining count for the week decrements | `recurring_intention.instance_completed` |
| Mid-week | Instance missed | remaining count unchanged (not carried as debt); may trigger Goal Risk recalculation if goal-linked | `recurring_intention.instance_missed` |

### 2.6 Focus Session (maps to Actual Session creation, execution detail owned by `09_EXECUTION_AND_FOCUS.md`)
Referenced here only for completeness: Not Started → Running → Paused ⇄ Running → Finished. Full detail, including overrun handling, in `09_EXECUTION_AND_FOCUS.md` §2.

---

## 3. Cross-Cutting Invariant

Work State, Planning State, and Lifecycle are independent fields on different (or the same, for Goal) entities and are never combined into a single status enum, anywhere in the schema (Master Spec §3 rule 12). A query needing "is this task blocked by a struggling goal" joins across Commitment.work_state and Goal.planning_state — it does not read a single composite field.

---

## 4. Genuine Gaps / Requires Product Decision

*(Resolved — see Resolution Log below. Section retained for history per the package's amendment-tracking convention.)*

## 5. Resolution Log

- **Importance representation (`importance` field):** resolved to `low`/`medium`/`high`/`critical` enum, category-defaulted with per-task override. No numeric scale — deliberately avoids fake precision.
- **Hard-consequence flag (`is_hard_consequence`):** resolved to AI inference from task context (e.g., "late submissions aren't accepted"), with user confirmation surfaced only when the classification materially affects a scheduling decision — not a mandatory field at task creation. See `07_AI_ARCHITECTURE.md` §3.
