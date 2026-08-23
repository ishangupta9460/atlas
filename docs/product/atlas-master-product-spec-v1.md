# Atlas Master Product Specification v1

Status: **Frozen for v1.** Any future change to Atlas's product behavior must be made by deliberately amending this document, not by ad hoc decisions elsewhere.

Source precedence used to resolve any conflict during consolidation: (1) Final Decisions Lock, (2) Scheduling & Rescheduling Decision Specification, (3) Product Decisions / Groups A–K, (4) Complete Atlas Product Context, (5) Original Atlas Backlog v2.

---

# 1. Master Product Specification

## 1.1 What Atlas Is

Atlas is a personal goal, roadmap, task, scheduling, execution, and self-reflection system. Its job is to convert what a person wants to accomplish into what they actually do today, keep that plan realistic as real life interferes with it, and give the person an honest picture of what actually happened over time.

Atlas is not a todo list, not a calendar, not a habit tracker, not a general chatbot. It combines planning, scheduling, execution tracking, and reflection into one loop:

> User intention → Roadmap/Plan → Schedule → Actual work → Progress → Learning about the user → Better future schedule

## 1.2 Product Philosophy — Non-Negotiable Principles

These hold across every feature in this document and override any local design choice that conflicts with them:

1. **AI proposes; deterministic Atlas logic validates and commits.** AI never writes directly to the schedule, never silently sets progress or completion state, and Atlas's core planning system must keep working if AI is unavailable, rate-limited, or returns something malformed.
2. **Explicit current user intent overrides historical behavior.** Atlas may recommend against a choice once, citing history, but a confirmed explicit instruction is followed unless it violates a hard system constraint.
3. **Atlas trusts the user.** No forced quizzes or proof-of-learning. A user's self-report of understanding or completion is accepted.
4. **Time is not progress.** Progress is tracked across three distinct dimensions (Planned / Executed / Achieved — see §1.15), never collapsed into "hours spent."
5. **Atlas avoids unnecessary disruption.** The smallest reasonable local correction is always preferred over rebuilding the schedule.
6. **Atlas does not endlessly carry missed work forward.** Important work is protected; lower-priority flexible work is deferred, not infinitely repeated.
7. **Atlas does not silently delete or hide failure.** Goals that become unrealistic are flagged (At Risk), never quietly abandoned or quietly pretended fine.
8. **Atlas explains meaningful decisions.** Every non-trivial schedule change carries a reason, retrievable by the user.
9. **Atlas is collaborative, not authoritarian.** It suggests and recommends for meaningful decisions; it may act autonomously only for small, low-disruption, unprotected changes (see §1.26).
10. **Complex behavior lives inside Atlas, not in user configuration.** Onboarding stays minimal; Atlas learns the rest progressively through normal use.

## 1.3 User Entry Points

Work enters Atlas through exactly three paths:

- **A. Goal** — a stated outcome ("I want to learn driving in one month"). Atlas interviews the user progressively (1–2 questions at a time, following a What → When/Why → How → Details depth hierarchy) and collaboratively builds a roadmap.
- **B. Existing Roadmap** — uploaded PDF, Word doc, Markdown, or screenshot. Atlas interprets it and shows its interpretation before anything is scheduled (see §1.5).
- **C. Direct Task/Request** — a concrete ask ("assignment due Friday"). Atlas asks only what's needed to structure the task; if it cannot determine a clear task from the input, it says so rather than guessing.

In all three cases, the user approves before Atlas commits anything to the active schedule.

## 1.4 Entity Model

```
Goal
 └── Roadmap
      └── Milestone
           └── Commitment / Task
                └── Scheduled Block
                     └── Actual Session

Recurring Intention  (parallel entity — not a child of the above tree)
 └── generates → Scheduled Block(s)
```

- **Goal** — something the user wants to achieve. May carry an overall target deadline. Has a Lifecycle state (§1.18).
- **Roadmap** — the structured plan supporting a Goal, containing typed nodes (§1.5).
- **Milestone** — a grouping unit within a roadmap; not itself schedulable.
- **Commitment / Task** — a concrete, schedulable piece of work. May carry its own deadline independent of the Goal's overall deadline. Carries a Flexibility tier (§1.7) and a Work State (§1.18).
- **Scheduled Block** — Atlas's specific placement of a Commitment (or a Recurring Intention instance) onto the calendar (e.g., "Tuesday 6–7 PM").
- **Actual Session** — what really happened (actual start/end time, notes, user-reported outcome). A Scheduled Block has zero or one Actual Session; a Commitment may have several Scheduled Blocks over its life (e.g., after a miss and reschedule).
- **Recurring Intention** — a repeated target ("3 driving sessions/week," "8 hrs/week backend"), not a single task. Generates Scheduled Block instances against the target; does not carry its own deadline in the normal sense (see §1.16 for weekly reset behavior). A standalone Commitment not linked to any Goal may have no deadline at all.

A task can be `In Progress` (Work State) on a Goal that is currently `At Risk` (Planning State) while the Goal itself remains `Active` (Lifecycle) — these three axes are independent by design (§1.18).

## 1.5 Roadmap Interpretation, Import, and Review

Roadmap content (uploaded or AI-generated from a goal interview) is parsed into typed nodes, never a flat task list:

| Node type | Schedulable? | Notes |
|---|---|---|
| `task` | Yes | Normal actionable work |
| `resource` | No | Supports one or more tasks (§1.6) |
| `optional` | Only if user opts in | Default unscheduled |
| `prerequisite` | Only if user lacks it | May become a task only if the user says they don't already know it |
| `note` | No | Context only |
| `milestone` | No | Grouping |
| `project` | Usually yes | Typically a culminating task near the end |

Pipeline: **Import → Interpret → Review → Approve → Schedule.** The Review step must show counts (milestones, tasks, resources, estimated effort, dependencies, optional sections found) and let the user strike sections, mark milestones as already-known (removes them, not just deprioritizes), and swap resources — all before anything touches the active schedule. Nothing from an import is scheduled without user approval.

A deterministic, rule-based ingestion pass (structural parsing — headers, bullets, keyword heuristics for task/resource/optional detection) is a foundational capability, not deferred, since roadmap import is one of Atlas's three primary entry points. AI-assisted ingestion (semantic understanding of messy documents, smarter effort estimation) is a later enhancement layered on the same feature, following the AI-proposes/engine-decides rule (§1.10).

## 1.6 Resources and Resource Feedback

A **Resource** (video, PDF, doc, link, book, course) is a standalone entity, attachable to one or many tasks — never duplicated per task. Replacing a disliked resource swaps the attachment without affecting the task's identity, progress, or history.

Resource feedback is captured at three tiers, matching the general memory-write model:

1. **Single-resource reaction** ("disliked this video") — always logged.
2. **Cross-resource pattern** ("3 of last 4 long videos abandoned early") — surfaced as an observation, never silently applied.
3. **Saved preference** ("prefer short videos") — created only on explicit user confirmation, same as any other persistent preference (§1.8).

## 1.7 Categories/Tags and the Flexibility Model

Categories/tags (`college`, `skill_learning`, `health`, `project`, etc.) are user-defined; Atlas does not hardcode universal importance ordering between them (college is not automatically more important than a life-skill goal).

Every Commitment (directly or via its category default) carries one of four **Flexibility** tiers:

- **Fixed** — cannot move autonomously, ever (college classes, appointments, explicit protected events). Only a direct, explicit user instruction targeting that specific commitment can move it — this is not an Atlas-initiated decision, it is the user consciously overriding their own default.
- **Protected** — can move, but only through Collaborative- or Critical-tier user involvement (§1.26), never silently. Example: gym.
- **Flexible** — Atlas may move it autonomously within the bounded-autonomy rules.
- **Optional** — easiest to defer or drop first under overload.

When a user creates a new category, Atlas may ask relevant setup questions (usually fixed or flexible? can it be split? should it be protected?) rather than requiring manual rule configuration up front.

## 1.8 User Preferences and Explicit Memory

Atlas does not permanently store every stated sentence. A preference (learning style, scheduling rule, resource preference, rescheduling rule) becomes persistent memory only on explicit user confirmation ("remember this as my preference?" → yes). Preferences live in a viewable/editable/deletable Preferences area and are applied only with contextual confirmation when relevant ("I remember you prefer project-based learning — use that here?") — never silently.

A single disliked instance ("I disliked this lecture") is not the same as a generalized preference ("I dislike long lectures") — only the latter, explicitly confirmed, is saved as memory.

## 1.9 Progressive AI Questioning

Questions are asked 1–2 at a time, following a What → When/Why → How → Details depth hierarchy, going deeper only when the answer actually requires it.

Every AI-asked question must carry one of these internal reason tags; if none apply, the question should not be asked:

- `task_creation` — needed to build the task at all
- `scheduling` — needed to place it
- `conflict_resolution` — something competes and tie-break rules didn't resolve it
- `uncertainty` — multiple reasonable options exist
- `goal_risk` — current pace won't meet the deadline

## 1.10 AI Behavior Boundaries

AI output is always a structured proposal (e.g., `{action: estimate_duration, task_id, value, confidence}`, `{action: suggest_split, task_id, sessions:[...]}`) — never free-form control of state. The deterministic scheduling engine (§1.11–1.12) validates and decides whether/how to apply it.

If AI is unavailable, rate-limited, times out, or returns malformed output, Atlas's core scheduling/execution functionality continues to work — the user loses smart estimates and suggestions, not the ability to plan and execute their day.

Atlas's chatbot is task/goal-oriented, not a general-purpose or emotional-companion assistant. It engages with emotion only insofar as it affects the work ("you seem to be struggling with today's block — shorten it, move it, or postpone it?"), not as open-ended emotional conversation.

## 1.11 Scheduling & Rescheduling — Problem A: What Deserves Time

Solved by a staged gate/tier hierarchy, not a single composite score. A candidate only reaches a later stage if it survives every earlier one.

| Stage | Name | Role |
|---|---|---|
| 0 | Hard Constraint Gate | Elimination — removes Fixed commitments from the movable set entirely |
| 1 | Explicit Current User Instruction | Override — a live user instruction wins over everything below Stage 0 |
| 2 | Irreversible/Hard Consequence Urgency | Gate/tier — narrowly defined: real, near-term, hard-to-reverse consequence (graded deadline, exam, time-locked event) — not merely "has a due date" |
| 3 | User-Defined Importance | Tier — explicit task/goal importance or category default |
| 4 | Goal Importance & At-Risk Status | Tier — at-risk goals get a protective boost for their tasks |
| 5 | Remaining Work & Dependencies | Tier — unblocking value, near-completion protection |
| 6 | Flexibility & Disruption Cost | Tier — prefer the option that moves least and respects Flexibility tiers (§1.7) |
| 7 | Historical Execution Probability | Calibration only, not a ranking input — adjusts realistic time allocation and confidence, never task selection |
| 8 | Preference & Continuity | Pure tie-break between otherwise-equivalent options |

**Fixed vs. Stage 1 clarification:** Fixed commitments are protected from *autonomous* decisions (Stage 0 is the limit of Atlas's own authority). An explicit user instruction may override that protection for the one specifically targeted commitment (Stage 1 is the limit of the user's own authority over their own defaults) — these are not contradictory, they describe two different actors.

**Tie-breaking rule** (when candidates are equal through Stage 8), applied in order until one distinguishes them: (1) less remaining work wins, (2) closer to its own deadline/failure point wins, (3) whichever choice causes less disruption if the other task is the one moved instead, (4) stronger dependency chain wins, (5) if still tied, don't ask the user for a trivial tie — pick deterministically (earliest-created); only surface a tie to the user if both options are independently Stage 3/4-level important and rules 1–4 didn't resolve it.

## 1.12 Scheduling & Rescheduling — Problem B: Where Should It Go

A genuinely separate problem from Problem A, solved by scoring among candidate slots that already survived the Stage 0–8 hierarchy. This is where the original scoring-engine concepts remain valid and useful:

- Time-of-day fit
- Energy match
- Context continuity (grouping similar work, minimizing context-switching)
- Fragmentation penalty
- Learned per-user preferences

Problem A decides *what* gets a slot; Problem B decides *which* slot among the valid options. A single global composite score deciding overall importance is explicitly rejected — that role belongs entirely to Problem A.

## 1.13 Capacity, Breaks, Buffers, and Realistic Availability

**Capacity ≠ raw free calendar space.** Atlas maintains a *realistic daily capacity* figure: the fraction of free time actually treated as workable. This starts as a **cold-start default (~70%)** and personalizes per user as real completion data accumulates — it is never a fixed global rule, since a user with predictable days may sustain ~85%, a user with chaotic days may realistically sustain ~55%, and the same user may temporarily push higher on purpose.

Buffer time (roughly 10–15 minutes between blocks) is drawn from this same reserved capacity, not bolted on as a separate mechanism. Any planned block over ~50–60 minutes carries an implicit break structure (e.g., 50 work / 10 break) reflected in how much deliverable work Atlas counts it as providing.

**Sleep** (and optionally meals/commute) is modeled as an explicit protected recovery window, seeded from early conversation and editable later — not inferred as a leftover gap, not a manually-created blackout event.

## 1.14 Focus Mode and Execution

A scheduled block opens as a real work session, not a bare timer: task brief (what to accomplish, resources, expected outcome / "what counts as done") shown before Start; elapsed time, pause, and quick-note controls during; a completion report at the end ("what did you actually complete" — free-form, trusted per §1.2).

**Overrun handling:** no interruption on a short grace window; then a single non-blocking prompt ("you're 18 min over, next block starts at 7 — continue, finish now, or push the next one?"), never a bare cutoff message and never repeated nagging.

**Forgotten timer (block ended, no session started):** Atlas does not assume failure. The user can report what actually happened (completed / partial / skipped); if nothing is reported, it is eventually treated as unresolved/missed and enters the recovery flow (§1.16).

## 1.15 Progress Model: Planned vs. Executed vs. Achieved

Three distinct, simultaneously-tracked figures, never collapsed into one number:

- **Planned** — sum of Scheduled Block durations.
- **Executed** — sum of Actual Session durations.
- **Achieved** — user-reported roadmap/topic progress.

These can legitimately disagree (e.g., Planned 10h, Executed 7h, Achieved 35%) — that gap is itself a useful signal (time spent without proportional progress), not noise to be averaged away.

Progress correction is supported without corrupting history: Actual Session records are an **immutable event log** (what really happened, logged at the time); current completion percentage is a **mutable belief state** the user can correct anytime without rewriting the log underneath it.

## 1.16 Missed, Partial, and Forgotten Blocks — Recovery Flow

A missed or partial Commitment re-enters the candidate pool and is run back through Stages 0–8. Search for a new slot proceeds progressively outward — remaining time today → tomorrow → rest of week → flag if nothing fits — never an immediate full-week re-optimization, and only realistically suitable slots are considered, not merely empty ones.

When several missed items compete for limited recovered time and not everything fits: protect the highest survivors of the hierarchy, move the rest to **Deferred** (§1.18), and escalate to the user only when a Stage 3/4-important item genuinely cannot be placed anywhere this week. Missed work is never carried forward indefinitely.

**Recurring Intentions reset weekly** rather than accumulating debt: a missed session does not create a permanent backlog item. Each week starts fresh at the full target. History still matters through two separate channels: (a) if goal-linked, a poor completion rate feeds that goal's At-Risk calculation (§1.17); (b) independently, Atlas may propose recalibrating the target itself ("you've averaged 1.5/week for a month — lower this to 2?") as a Collaborative-tier suggestion, never an automatic change.

**Pattern detection** (e.g., "user only completes 30 of 120 planned minutes") requires repeated evidence across multiple weeks, not a few consecutive incidents, and frequency/ratio matters (8 of 10 instances is evidence; 3 of 3 in one bad week is not). Findings are phrased as observations ("I've noticed...") never judgments.

## 1.17 Goal Risk and Long-Term Feasibility

Feasibility = remaining work vs. available capacity, adjusted by the user's **context-specific** historical completion rate (per category/time-of-day, not one blended global percentage — a user's 92% morning-study rate and 58% evening-study rate for the same category tell a materially different story than an averaged 75%).

When feasibility drops below a workable threshold, the Goal transitions to **At Risk** (Planning State, §1.18) — this is a Critical-tier event (§1.26): Atlas surfaces the situation and options (increase effort, extend deadline, reduce scope, change method, defer/pause) rather than silently continuing or silently failing. If the user doesn't respond, Atlas keeps making the best practical schedule with what it has, while keeping the At Risk flag visibly active rather than hiding it.

An At-Risk goal's tasks receive a protective boost at Stage 4 of the hierarchy — but this never outranks a genuine Stage 0–2 item. A goal can remain At Risk indefinitely if real hard-consequence deadlines keep legitimately winning contested time; this is honest behavior, not a bug (see §4).

## 1.18 State Model — Three Independent Axes

Status is deliberately **not** a single enum. Three orthogonal axes:

- **Work State** (per Commitment/Task): `Draft → Ready → In Progress → Completed`
- **Planning State** (per Goal, and derivable for its tasks): `Active → Deferred → Paused → At Risk`
- **Lifecycle** (per Goal): `Active → Completed / Abandoned`

These combine freely and meaningfully — e.g., a task `In Progress` (Work State) belonging to a Goal that is `At Risk` (Planning State) while the Goal itself remains `Active` (Lifecycle).

Transitions:
- **Deferred** — Atlas-initiated, under scheduling pressure, reversible; automatically returns to Active once capacity frees and the item resurfaces in the candidate pool.
- **At Risk** — system-calculated (§1.17), always a Critical-tier event requiring user acknowledgment before formally changing state (the one exception: Active ↔ Deferred is allowed to happen automatically, since it's reversible and low-stakes by design).
- **Paused** — explicit user action only ("not now, might resume"). Never inferred.
- **Abandoned** — explicit user action only ("not ever, moved on"). Never inferred, never auto-transitioned from At Risk or Paused. Kept distinct from Paused so analytics don't conflate a strategic pause with a genuine give-up.
- **Completed** — terminal, either Work State (task level) or Lifecycle (goal level).

## 1.19 Today / Now / Next / Later Experience

The home experience is task-first, not calendar-first: **NOW** (the one thing in front of the user), **UP NEXT**, **LATER**. The calendar exists as a secondary view for users who want the fuller picture. Each block, when opened, shows what exactly to do (description, resources, roadmap context, prior discussion) — an executable piece of the roadmap, not a vague title ("implement one GET endpoint," not "study Spring Boot").

**Minimum Viable Day:** on a difficult day, Atlas can distinguish what it determined must be protected ("Essential") from what would have been good progress ("Good to do") from what was always lowest-stakes ("Optional"). This determination is *informed by* the Stage 0–8 hierarchy (a Stage 2 item is almost always Essential; a Stage 4 at-risk item usually is) but is not a mechanical stage-number mapping — a Stage 3 item the user marked very important can also be Essential, and Stage 7 (calibration only) never determines this tier directly. This lets Atlas honestly say "you protected everything essential" even on a day where good-to-do/optional work slipped.

## 1.20 Notifications

Governing rule: **notify only when the information should change what the user does next** — never merely because something happened. A single pre-block reminder is appropriate; a nagging "you still haven't started" sequence is not. A goal crossing into At Risk is notify-worthy (Critical tier); a goal losing a few points of pace is not.

## 1.21 Manual Overrides

A manual drag/edit sets a `user-moved` flag on that instance. The autonomous scheduling engine treats user-moved items as effectively Protected going forward — it will not move them again without a new trigger significant enough to reach Collaborative tier. If asked "why is this still Saturday," Atlas answers "you moved it there," not silence or an unexplained re-move.

## 1.22 Undo, Event History, and Explainability

A single **append-only event log** (task created, block generated, user moved X, Atlas moved X + reason, session started/ended, progress changed, goal → At Risk, etc.) backs three features from one source: **undo** (reverse the last N events), **"what changed"** (render events in plain language with reasons), and **analytics** (aggregate the same events differently). Every Atlas-initiated change carries a stored `reason` string — this is what makes §1.2's explainability principle actually implementable.

## 1.23 Analytics and Personal Learning

Reported metrics are specific rather than one blended score: Consistency %, Planned-vs-Completed %, Focused Hours, Goal Progress %, plus the Planned/Executed/Achieved breakdown (§1.15). Atlas can answer "why am I falling behind" by identifying the dominant contributing factor (e.g., a specific time-of-day or category miss pattern) rather than restating a raw completion percentage.

Historical learning (session-length reliability, best working times, category-specific completion rates) feeds Stage 7 calibration and capacity personalization (§1.13) — it informs recommendations and realistic planning, and per §1.2 never overrides a live explicit user instruction.

Atlas functions as a mirror, not a judge: a bad week is reported factually and specifically ("42% of planned work completed, mostly missed evening blocks"), not moralized.

## 1.24 Privacy and Portability

Baseline commitments: full data export on request, full account deletion that actually deletes (not a soft flag), and only the minimum context needed for a given AI call is sent to the AI provider — not the user's entire history by default. Export baseline is JSON (complete, re-importable) with Markdown as a secondary human-readable option for roadmaps/goals specifically.

## 1.25 Existing Timetable/Schedule Import

For v1, the user provides a screenshot of an existing timetable or calendar rather than requiring live calendar integration. Atlas extracts fixed/reserved blocks from it (e.g., "Monday 9–10 — Data Structures") and treats them as existing reserved time under the Fixed flexibility tier. Live calendar integration (e.g., Google Calendar) is an explicit non-goal for v1 and may be considered later.

## 1.26 Autonomous vs. Collaborative vs. Critical Decisions

The boundary is measurable — based on what a change touches, not judgment calls made ad hoc:

- **Autonomous** (Atlas acts silently, logged, explainable on request): moves exactly one Flexible-tier task, stays within the same day or the very next day, doesn't touch anything Protected/Fixed or any At-Risk goal's tasks.
- **Collaborative** (Atlas proposes and waits, without blocking the rest of the schedule): touches more than one task or spans more than a day or two; affects a task linked to an important or At-Risk goal; a genuine Stage 3/4-level tie exists; a recurring pattern is detected and Atlas wants to change a future default.
- **Critical** (Atlas must involve the user before proceeding; silence means "no action yet," not consent): anything touching a Fixed or Protected commitment; a Goal transitioning to At Risk for the first time, or a proposal to Pause/Abandon a goal; no valid schedule exists at all; the user's explicit instruction conflicts directly with a hard constraint (Atlas explains why it can't comply rather than silently overriding the constraint).

---

# 2. Backlog Reconciliation

Mapping the original Sprint 0–15 backlog against this specification. "Valid" = proceed as originally scoped. "Extend" = original scope still needed, plus the noted addition. "Supersede" = original approach is replaced.

| Sprint | Original scope | Status |
|---|---|---|
| 0 | Repo, Spring Boot, Java 17, MySQL/Aiven, Flyway, React/TS, FullCalendar, CI | **Valid** — infrastructure unaffected |
| 1 | Registration, login, JWT, current-user endpoint | **Valid** |
| 2 | Task domain design, task creation | **Extend** — task model must include Flexibility tier (§1.7), typed roadmap-node origin (§1.5), and Work State (§1.18) rather than a single generic status field |
| 3 | Task list/update/archive, status lifecycle, tags, categories | **Extend** — replace the single status lifecycle with the three-axis model (§1.18); category creation should prompt for flexibility/protection defaults (§1.7) |
| 4 | Dependencies, cycle detection | **Valid** — feeds Stage 5 of the hierarchy; exact lookahead depth is an open calibration item (§4) |
| 5 | Working availability, blackout periods, timezone, manual calendar events | **Extend** — add screenshot-based fixed-schedule import (§1.25), sleep/recovery as a protected window (§1.13), and buffer configuration |
| 6 | Deterministic candidate slots, hard constraint enforcement, first schedule generation | **Extend** — implement as Stage 0 gate plus the Problem A / Problem B split (§1.11–1.12) |
| 7 | Scoring formula: deadline urgency, time-of-day fit, energy match, context continuity, fragmentation penalty, priority weighting, composite score, greedy placement, splitting, local search, score explanation | **Superseded in part** — priority weighting/composite score for overall importance is replaced by the Stage 0–8 hierarchy (§1.11). Time-of-day fit, energy match, context continuity, and fragmentation penalty are **retained**, repurposed as Problem B slot-scoring (§1.12). Task splitting and score explanation remain valid concepts, re-expressed via §1.16 recovery search and §1.22 event log respectively |
| 8 | Calendar UI, drag/drop with backend validation, rescheduling, manual event creation | **Extend** — manual moves must set the `user-moved`/Protected flag (§1.21) |
| 9 | Focus mode, start/pause/resume/end, actual vs. estimated, in-session notes, overrun handling | **Extend** — add task brief before start and the grace-window + single-prompt overrun rule (§1.14) |
| 10 | Duration learning, time-of-day performance, confidence, completion pattern recognition | **Extend** — feeds Stage 7 calibration and capacity personalization (§1.13); pattern detection must use the multi-week/frequency threshold (§1.16) |
| 11 | Risk engine, impossible schedules, deadline conflicts, overload/burnout detection, low-confidence schedules | **Extend** — becomes the At-Risk detection and transition logic (§1.17–1.18), plus Minimum Viable Day (§1.19) and Critical-tier escalation (§1.26) |
| 12 | Analytics, weekly/daily reports, learning summaries | **Extend** — adopt the Planned/Executed/Achieved split (§1.15) and specific-metric reporting (§1.23) instead of a single discipline score |
| 13 | AI integration: task capture, clarification, document ingestion, duration estimation, decomposition, coaching/reflection, weekly review, AI rate/cost controls | **Extend + reorder** — basic deterministic roadmap ingestion (§1.5) moves earlier as a foundational capability rather than waiting for this sprint; AI-assisted ingestion remains here as an enhancement. All AI output in this sprint must conform to the structured-proposal architecture (§1.10) from the start, not be retrofitted later |
| 14 | Notifications and reviews | **Extend** — apply the decision-relevance notification philosophy (§1.20) |
| 15 | Production hardening: security, DB, backend, scheduling, AI hardening, observability | **Valid** — add explicit test coverage for AI-down graceful degradation (§1.10) |

---

# 3. Non-Negotiable Rules

1. AI proposes; deterministic Atlas logic validates and commits. AI never directly writes schedule state, progress, or completion.
2. Explicit current user instruction overrides historical behavior, always, except where it conflicts with a hard system constraint (which Atlas must explain, not silently override).
3. Fixed commitments cannot be moved by Atlas autonomously; only a direct user instruction targeting that specific commitment can move it.
4. No task is scheduled from an imported roadmap without user review and approval.
5. A single disliked instance of a resource is never auto-generalized into a saved preference; only explicit confirmation creates persistent memory.
6. Missed work is never carried forward indefinitely; overload is resolved by protecting the most important survivors and deferring the rest.
7. Recurring Intentions reset to their full target each week; missed sessions do not accumulate as permanent backlog.
8. Pattern-based behavioral conclusions require multi-week, multi-instance evidence — never a few consecutive incidents — and are phrased as observations, not judgments.
9. A Goal transitioning to At Risk, or a proposal to Pause/Abandon it, is always a Critical-tier event requiring user involvement; silence is not consent.
10. Paused and Abandoned states are set only by explicit user action, never inferred by Atlas.
11. Progress correction (belief state) never rewrites the underlying Actual Session event log (historical fact).
12. Work State, Planning State, and Lifecycle are independent axes and must never be collapsed into a single status field.
13. Every Atlas-initiated schedule change carries a stored reason and is reversible via the event log.
14. Notifications fire only when they would change what the user does next — never merely because something happened.
15. Only the minimum context needed for a given AI call is sent to the AI provider; full user data export and full account deletion must be genuinely honored, not soft-flagged.

---

# 4. Remaining Genuine Ambiguities

These are calibration/implementation decisions explicitly flagged during design but never assigned a concrete value. They are not architectural contradictions — the framework fully accommodates any reasonable answer — but each needs a decision before the relevant feature is built:

1. **Mechanism for flagging Stage 2 "hard consequence."** Is this a user-set toggle at task creation ("this is graded/time-locked"), or should Atlas attempt to infer it from text ("exam," "due," "assignment")? Affects both UI design and the reliability of Stage 2 gating.
2. **UI representation of Stage 3 "user importance."** A numeric scale, a simple high/medium/low tag, or purely inherited from category defaults with per-task override? Needs a concrete choice for implementation, independent of the underlying algorithm.
3. **Exact numeric threshold for triggering At Risk (§1.17).** The feasibility math is defined, but "how far below realistic pace" as a fixed percentage or something that adapts per goal has not been assigned a starting number.
4. **Dependency-chain lookahead depth at Stage 5.** One level (directly blocks one other task) versus full chain analysis — full chains are more correct but materially more complex to compute and explain.
5. **Granularity of capacity personalization (§1.13).** Confirmed as per-user and not a global constant; whether it should additionally vary per-category (a chaotic college week vs. a calm summer week for the same person) was raised but not formally decided — currently a single per-user figure is the specified default.

---

# Addendum 1 — Calibration Item Resolutions (Post-Documentation-Pass)

Resolved during engineering documentation review, prior to implementation. These are product-level decisions and are recorded here to keep this frozen document in sync with what will actually be built. Full technical detail lives in the engineering documentation package (`19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md` §2), not repeated here.

1. **Stage 2 hard-consequence detection (§4 item 1):** resolved to AI inference from task context, with user confirmation surfaced only when the classification is about to materially change a scheduling decision — not a mandatory field the user must fill in at task creation.
2. **Stage 3 importance representation (§4 item 2):** resolved to a `Low / Medium / High / Critical` scale, category-defaulted with per-task override. No numeric slider — deliberately avoids false precision.
3. **At-Risk threshold (§4 item 3):** resolved to a formula-based definition (projected completion probability from remaining work, capacity, and historical completion rate) rather than a fixed percentage. The formula's *shape* is fixed by this addendum; the specific cutoff value remains an externalized, tunable configuration parameter, expected to be calibrated further once real usage data exists.
4. **Data retention before account deletion** (not one of the original five, but product-policy-level and therefore recorded here alongside them): resolved to immediate logical deactivation, a 30-day user-recoverable window, then unconditional permanent deletion.

Item 4 of the original five (dependency lookahead depth) and the five newly-discovered implementation gaps from the engineering pass are resolved at the implementation/architecture level and do not require amending this product document — see the engineering package for those.
