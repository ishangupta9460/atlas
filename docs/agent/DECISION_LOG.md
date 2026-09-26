# DECISION_LOG.md — Durable Decision Record

Important decisions about Atlas's product, architecture, or
implementation must not live only in chat messages, agent memory, Jira
comments, or local session context. All of those are ephemeral relative
to a multi-agent, multi-month build. This document is where a decision
goes so that any future agent — regardless of provider or model — can
find out what was decided, when, why, and by whose authority, without
needing to have been present for the conversation.

This log does not replace the Atlas product specification (`01`–`19`)
or the prior review/reconstruction reports (`ATLAS_SPECIFICATION_REVIEW.md`,
`REVIEW_CHANGELOG.md`, `MISSING_SPEC_RECONSTRUCTION_REPORT.md`,
`19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md`). Those remain the canonical
source for product decisions already made. This log is for:

- new decisions made **during development** that aren't yet reflected
  in the numbered spec documents,
- architecture/implementation decisions this repository specifically
  needs (e.g., which test framework, where handoffs live),
- and a durable pointer to the canonical source when one already exists,
  rather than a duplicate copy of it.

---

## Decision Authority

**Product decisions** — anything that changes what Atlas does or how it
behaves from the user's perspective, including resolving any
`[OPEN PRODUCT DECISION]` flagged in the numbered spec documents —
**require human/product-owner authority.** No agent, coding or
reviewing, may resolve one unilaterally. An agent may *propose* a
resolution (with the tradeoffs laid out, per the pattern already used in
`08_MEMORY_AND_PREFERENCES.md` §5 and `06_ROADMAP_AND_RESOURCE_SYSTEM.md`
§4's own open-question write-ups), but the `STATUS` field stays
`Proposed` until a human accepts it.

**Architectural decisions** — how a requirement is structured internally
(module boundaries, data flow, which layer owns what beyond what `01`
already specifies) — may be proposed and, with reasonable confidence,
adopted by an agent, but must be recorded here so the reasoning isn't
lost, and must not contradict anything already fixed in `01`–`19`.

**Implementation decisions** — concrete technical choices within an
already-settled architecture (a specific library, a specific test
runner, a specific file layout for handoffs) — may be made directly by
an agent and recorded here for traceability, without requiring
human sign-off first, unless the choice has downstream product or
architectural weight.

Agents may propose architectural or implementation choices. **They must
not silently override an approved product decision** — if new
information suggests a product decision should change, that goes back
to the human/product owner as a proposal, not a unilateral edit to the
spec.

---

### DEC-0011 — Manual goal planning integration

- DATE: 2026-09-22
- TYPE: Implementation
- STATUS: Approved (implementation authority)
- CONTEXT/AUTHORITY: User requests coherent end-to-end batches in the main checkout,
  authorizes ordinary missing API implementation, and assigns full QA to Antigravity.
- DECISION: Expose existing Roadmap/Milestone and Commitment creation/editing in a
  progressive goal planning UI. Add owned Goal-to-Roadmap lookup and paginated
  Goal-to-Commitment read instead of relying on client-only IDs or unbounded lists.
- WHY: Saved goals need a usable path to concrete work; existing entity models and
  mutation services already own the behavior. No scheduling or AI behavior is invented.
- IMPACT: `12` documents read extensions and existing structural routes; focused
  ownership/read tests and UI interaction tests. No schema or mutation-contract change.
- RELATED JIRA: DOM-002/SCRUM-24, DOM-003/SCRUM-25, UI-006/SCRUM-52 (contributes;
  does not claim the full AI interview/onboarding flow).
- WORKFLOW: `feature/ATLAS-BUILD` in the main checkout; compact `STATE.md` checkpoint
  replaces per-story handoffs for this batch per the user's explicit instructions.

### DEC-0012 — Category and prerequisite UI integration

- DATE: 2026-09-22; TYPE: Implementation; STATUS: Approved (implementation authority).
- AUTHORITY: User's next coherent capability request after merged Goal Planning.
- DECISION: Expose existing category/default and dependency mutation contracts in the
  goal planning flow; add owner-scoped paginated literal title search for selecting
  prerequisites across goals. Existing domain services remain the mutation authority.
- WHY: Makes DOM-005/DOM-007 usable and completes MEM-004 category setup before scheduling.
- IMPACT: `12` records the search contract and existing category routes. No migrations,
  domain state changes, default recalculation, cancellation, or scheduling behavior.
- RELATED: MEM-004/SCRUM-96, DOM-005/SCRUM-27, DOM-007/SCRUM-29, DOM-003/SCRUM-25.

### DEC-0016 — Chunk 1 timezone and availability policy

- DATE: 2026-09-25; TYPE: Product; STATUS: Approved.
- CONTEXT: The Chunk 1 request requires correct DST behavior. `03` SCH-014 and
  `16` §9 explicitly identify its expected behavior as unspecified. `19` and the
  review changelog do not resolve that later reconstruction gap.
- DECISION: One saved IANA scheduling timezone per user; no available working time
  until configured; overnight weekly windows belong to their starting weekday.
  DST gaps clip the affected interval; repeated local times include both occurrences
  (only the configured local-clock minutes, without filling unconfigured minutes
  between occurrences). A timezone edit affects subsequent
  calculations and does not move existing absolute UTC reservations.
- AUTHORITY: User explicitly approved these timezone/DST rules and instructed
  continuation from the existing Chunk 1 checkpoint on 2026-09-25. Owning rules
  recorded in `04` §6; previous gap notes in `03` and `16` updated by reference.
- IMPLEMENTATION: Pure UTC interval subtraction and candidate ranges;
  per-user capacity policy using the `04` §4 baseline (70%, 10-minute buffers,
  50/10 work/break defaults); JDBC persistence matching V11; V12 additive schema;
  authenticated working-hours/capacity configuration and atomic audit using the
  existing log; read-only repeatable-read candidate/capacity query.
- IMPLEMENTATION DETAILS: Workable fraction may be configured from 0 to 1.
  Candidates describe earliest/latest valid starts, with no ranking or arbitrary
  sampling grid. Capacity is the minimum of remaining workable budget and physical
  deliverable free time; buffers are not charged a second time against the 70%.
  Existing blocks and fixed commitments receive the configured inter-block buffer.
  Same-kind weekly overlaps are rejected; cross-kind protection takes precedence.
  Minute-resolution weekly configuration is bounded to 224 entries; queries to 31
  elapsed days, with explicit UTC-offset timestamps and no implicit current time.
- RELATED: SCH-001, SCH-013, SCH-014. SCH-012 dependency applies to full pipeline
  determinism later; this batch implements foundation determinism only, per the
  user's explicit scope. No SCH-002+ implementation is authorized here.

## Entry Format

```
ID: DEC-XXXX
DATE:
TYPE: Product | Architectural | Implementation
DECISION: (one-line summary)
STATUS: Proposed | Approved | Rejected | Superseded
CONTEXT: (what situation prompted this)
OPTIONS CONSIDERED:
  - ...
  - ...
CHOSEN OPTION:
WHY:
IMPACT: (what code/docs/tests this touches or should touch)
RELATED JIRA: (story key(s), if any)
RELATED DOCUMENTS: (spec section(s), review reports, prior DEC entries)
```

IDs are sequential (`DEC-0001`, `DEC-0002`, ...) and never reused, even
if an entry is later superseded — supersession gets a new entry that
references the old one, per the append-only philosophy Atlas itself
uses for its own Event Log (`10_EVENT_LOG.md` §3).

---

## Currently Approved Decisions Relevant to Development Governance

DEC-0001 through DEC-0003 below were Open Product Decisions carried over
from the Atlas specification package (`06` §4, `08` §5,
`ATLAS_SPECIFICATION_REVIEW.md` §5 / `03` §4) and have since been
explicitly resolved by the Atlas product owner. Their final decisions
are recorded here in full, since the canonical spec documents that
originally flagged them still describe the question as open pending
this log's resolution — treat these entries, not the "OPEN PRODUCT
DECISION" language still present in those source documents, as
authoritative going forward.

### DEC-0001
```
DATE: (see source document; approved by the Atlas product owner)
TYPE: Product
DECISION: Screenshot-imported Fixed Commitments MUST go through a
          Review/Approve step before becoming active Fixed Commitments.
          They must NOT silently become active scheduling constraints
          immediately after parsing.
STATUS: Approved
CONTEXT: 06_ROADMAP_AND_RESOURCE_SYSTEM.md §4 documented the
         OCR/parse-to-Fixed-Commitment pipeline but left unresolved
         whether it commits directly or passes through a
         Review/Approve-style gate like roadmap import (06 §1.4-1.5).
         12_API_SPECIFICATION.md §5 had assumed direct commit,
         reasoned but not confirmed. This decision resolves that
         question.
OPTIONS CONSIDERED:
  - Direct commit (calendar screenshot = simple factual extraction)
  - Review/Approve gate (OCR is error-prone; Non-Negotiable Rule 4's
    concern arguably applies here too)
CHOSEN OPTION: Review/Approve gate. Required conceptual flow:
    Screenshot → OCR/deterministic parsing → detected Fixed Commitment
    candidates → user reviews/edits → user explicitly approves →
    active Fixed Commitments → Scheduling Engine may treat them as
    hard constraints (04_SCHEDULING_ENGINE.md §2 Stage 0).
    The distinction between imported/detected candidate data and
    approved active Fixed Commitments must be preserved throughout —
    candidates are proposal state, not committed `02` §1.11 entities,
    until Approve, mirroring the existing roadmap-import proposal/
    commit split (06 §1.4-1.5).
WHY: OCR-derived data is error-prone (misread times, merged cells,
     ambiguous day labels), and a Fixed Commitment is a hard scheduling
     constraint the Scheduling Engine will never autonomously move
     (04 §2.1) — an unreviewed parsing error becoming a silent hard
     constraint is a worse failure mode than the equivalent error in an
     interpretive roadmap import, not a lesser one. The product owner
     has resolved the tradeoff in 06 §4 in favor of the review gate.
IMPACT: 12_API_SPECIFICATION.md §5 (`POST /fixed-commitments/import`
        must return a review-able candidate/proposal, not committed
        entities, requiring a follow-up approve call mirroring
        `/roadmaps/import`'s two-step shape — this is a contract
        change from its prior provisional direct-commit assumption);
        14_UI_UX_SPECIFICATION.md §4 (a Review screen, full or
        lightweight, is now required for this flow — no longer
        optional); 06_ROADMAP_AND_RESOURCE_SYSTEM.md §4 (its open
        question is now resolved); Jira ROAD-006's acceptance criteria.
RELATED JIRA: ROAD-006
RELATED DOCUMENTS: 06_ROADMAP_AND_RESOURCE_SYSTEM.md §4,
                    14_UI_UX_SPECIFICATION.md §4,
                    12_API_SPECIFICATION.md §5,
                    MISSING_SPEC_RECONSTRUCTION_REPORT.md §4 item 1
```

### DEC-0002
```
DATE: (see source document; approved by the Atlas product owner)
TYPE: Product
DECISION: Atlas may detect a repeated pattern of the user manually
          moving the same task, but it MUST NOT silently convert that
          behavior into a persistent preference.
STATUS: Approved
CONTEXT: 08_MEMORY_AND_PREFERENCES.md §5 laid out three options: (a) no
         new behavior beyond the existing single-move Protected-treatment
         rule; (b) a Tier-2-style observation surfaced for explicit
         confirmation (Memory/Preferences path); (c) silently feeding
         Problem B's learned time-of-day preference (Scheduling/Analytics
         path). This decision resolves that choice as option (b), reusing
         05_RESCHEDULING_AND_RECOVERY.md §6's multi-week/frequency-ratio
         evidentiary standard as 08 §5 itself recommended.
OPTIONS CONSIDERED: (a), (b), (c) — see 08 §5 for full tradeoffs.
CHOSEN OPTION: (b) — observation surfaced for explicit confirmation.
    Required conceptual flow: repeated manual moves → Atlas detects a
    meaningful pattern (reusing 05 §6's multi-week, meaningful-
    frequency-ratio standard — one isolated move is insufficient, and a
    short run such as 3-for-3 in one bad week does not qualify) → Atlas
    surfaces an observation (e.g., "You've been moving this later
    several times. Change its default timing?" [Yes] / [Not now]) →
    Atlas asks whether the user wants to change the relevant default →
    user explicitly confirms → preference is persisted (`02` §1.10,
    `08` §1-2's existing confirmation-gated write path — no new write
    path is introduced) → future scheduling may use that confirmed
    preference. Observation alone never changes scheduling. Declining
    ("Not now") does not create a negative signal or punishment, and
    does not suppress future observations if the pattern persists.
    Temporary circumstances (e.g., a short-term conflict) must not
    automatically be read as a permanent preference — this is why the
    multi-week evidentiary bar applies rather than a short window.
    No fixed numeric threshold is introduced by this decision; the
    exact multi-week/frequency-ratio parameters remain an
    implementation/configuration detail per 05 §6's existing framing,
    not a new hardcoded product number.
WHY: This is a Collaborative-tier suggestion consistent with 05 §7's
     tiering (never silently applied) and with Non-Negotiable Rule 5 /
     08 §1's confirmation-gate principle (a pattern is not the same as
     a confirmed preference). Option (c) was rejected because it would
     quietly override a pattern the user might not want generalized
     (e.g., a temporary conflict, not a genuine standing preference) —
     exactly the single-instance-vs-pattern distinction 08 §2 exists to
     protect against.
IMPACT: New Memory/Preferences story required, depending on MEM-001
        (confirmation flow) and reusing 05 §6's pattern-detection
        evidentiary standard; 08_MEMORY_AND_PREFERENCES.md §5's open
        question is now resolved toward (b).
RELATED JIRA: None yet — new story required (see Adding a New Entry
              process; this repository does not edit Jira directly).
RELATED DOCUMENTS: 08_MEMORY_AND_PREFERENCES.md §5,
                    05_RESCHEDULING_AND_RECOVERY.md §6, §7,
                    02_DOMAIN_MODEL_AND_STATE_MACHINES.md §1.10,
                    ATLAS_SPECIFICATION_REVIEW.md §4 (Scenario D), §5.1,
                    MISSING_SPEC_RECONSTRUCTION_REPORT.md §4 item 2
```

### DEC-0003
```
DATE: (see source document; approved by the Atlas product owner)
TYPE: Product
DECISION: Cancelling a task MUST NOT silently cascade cancellation
          through all dependent tasks. The user explicitly chooses
          which dependent tasks, if any, are also cancelled.
STATUS: Approved
CONTEXT: 02 §2.3 added a Cancelled terminal Work State for Commitments
         during the September 2026 review (REVIEW_CHANGELOG.md #1), but
         whether cancelling a Commitment cascades through
         commitment_dependency (02 §1.4 relationships), or leaves
         dependents to be caught by normal traversal (04
         SCHEDULING_ENGINE.md §2.5), was deliberately left unanswered
         rather than invented unilaterally. This decision resolves that
         question as user-mediated selection.
OPTIONS CONSIDERED:
  - Automatic cascade (cancel all dependents too)
  - No cascade (leave dependents; normal Stage 5 traversal handles it)
  - User-mediated (surface affected dependents, let the user decide)
CHOSEN OPTION: User-mediated. Required conceptual flow: (1) Atlas
    identifies downstream dependent tasks via commitment_dependency
    (02 §1.4); (2) Atlas informs the user those tasks may be affected;
    (3) Atlas presents the affected tasks as selectable items; (4) the
    user explicitly chooses which dependent tasks should also be
    cancelled; (5) the user confirms; (6) only explicitly selected
    dependent tasks are cancelled; (7) unselected tasks remain; (8) any
    resulting dependency problems (e.g., a remaining task now depends on
    a cancelled one) are surfaced clearly; (9) existing history remains
    intact. Cancelled ≠ Deleted — the Cancelled Work State (02 §2.3) and
    corresponding Event Log entries (`task.cancelled`, 10 §2) are
    preserved exactly as for a single-task cancellation; dependency
    history in commitment_dependency is not silently rewritten or
    deleted. For long dependency chains, the impact must be presented
    comprehensibly (e.g., direct dependents with clear indication of
    further downstream effect) rather than forcing the user through an
    unbounded dependency graph — the specific presentation bound is an
    implementation detail, not specified further by this decision.
WHY: Automatic cascade risks silently destroying work the user did not
     intend to cancel, which conflicts with Atlas's non-negotiable
     "nothing is silently deleted or hidden" principle (Master Spec
     rule 7, also the reasoning behind adding the Cancelled state in
     the first place per REVIEW_CHANGELOG.md #1). Leaving dependents
     entirely to normal traversal risks orphaned or confusing downstream
     tasks with no clear signal to the user that their dependency was
     just cancelled. User-mediated selection preserves both: nothing is
     destroyed without the user's explicit choice, and the user is
     never left unaware of the impact.
IMPACT: New "Task cancellation flow" Jira story required (still does
        not exist — 03_REQUIREMENTS_TRACEABILITY.md §4 item 1 and
        ATLAS_SPECIFICATION_REVIEW.md §6 both already flagged this);
        its acceptance criteria must now include the selectable-
        dependents UI flow, the Event Log/history-preservation
        requirement, and the comprehensible-presentation requirement
        for long chains; 03_REQUIREMENTS_TRACEABILITY.md §4 item 3's
        cascade-policy question is now resolved.
RELATED JIRA: None yet — "Task cancellation flow" story needed
              (ATLAS_SPECIFICATION_REVIEW.md §6).
RELATED DOCUMENTS: 02_DOMAIN_MODEL_AND_STATE_MACHINES.md §2.3, §1.4,
                    04_SCHEDULING_ENGINE.md §2.5,
                    10_EVENT_LOG.md §2, §3,
                    ATLAS_SPECIFICATION_REVIEW.md §5 item 2,
                    03_REQUIREMENTS_TRACEABILITY.md §4 item 3
```

---

## Adding a New Entry

1. Check whether the decision is already covered by an existing entry
   here, or already resolved in the numbered spec documents / review
   reports — do not duplicate.
2. Determine the type (Product / Architectural / Implementation) using
   the Decision Authority rules above.
3. If Product: write it up with `STATUS: Proposed` and route it to the
   human/product owner. Do not implement against a Proposed product
   decision as if it were Approved.
4. If Architectural or Implementation: write it up, implement
   consistently with it, and set `STATUS: Approved` (or leave
   `Proposed` if you want explicit sign-off before broader adoption).
5. Reference the entry's ID from the relevant handoff
   (`docs/agent/HANDOFF_PROTOCOL.md`'s `OPEN QUESTIONS` or
   `ARCHITECTURAL CONCERNS` section) so the connection is discoverable
   from either direction.
6. Never delete an entry. If a decision is superseded, add a new entry
   with `TYPE`/`STATUS: Superseded` on the old one, referencing the new
   entry's ID — this preserves the same immutable, append-only history
   discipline Atlas's own Event Log requires of itself
   (`10_EVENT_LOG.md` §3).

---

## Technical Decisions Made During Development

### DEC-0015
```
DATE: 2026-09-21
TYPE: Implementation
STATUS: Approved
DECISION: OPS-001 uses Spring Boot's built-in Logstash JSON and SLF4J MDC.
CONTEXT: 17 section 1 requires request correlation across downstream calls but no
    specific field/header names or framework. Existing code is synchronous servlet,
    Spring Security, domain services and transactional event persistence, using SLF4J.
CHOSEN OPTION: Before-auth filter accepts one bounded safe X-Correlation-ID or
    generates a UUID, echoes it, scopes correlationId MDC with finally restoration,
    and logs request start/end. Route templates avoid raw path/query disclosure.
    Existing loggers use built-in JSON encoding. Event attempts and actual transaction
    outcomes provide trace points without event payloads, reasons or request content.
    No added logging dependency, event schema, product state or auth-log subsystem.
IMPACT: 17 section 1 documents field/propagation mechanics. Existing synchronous
    calls inherit MDC, including transaction completion before returning to servlet.
    No async executors or outbound clients exist; their future owners must explicitly
    propagate this context rather than assuming MDC crosses threads or HTTP.
RELATED JIRA: OPS-001
RELATED DOCUMENTS: 17 section 1; 01 sections 3/4/5; 15 section 2
REFERENCE: https://docs.spring.io/spring-boot/reference/features/logging.html
```

### DEC-0014
```
DATE: 2026-09-25
TYPE: Product
STATUS: Approved
AUTHORITY: Explicit product-owner approval (Choice 3 — Continuity in Stage 8, Time-of-Day in Problem B).
DECISION: Stage 8 evaluates Category Continuity only; Time-of-Day fit belongs exclusively to Problem B candidate-slot scoring.
CHOSEN OPTION:
  1. Stage 8 evaluates Category Continuity only.
  2. Continuity is determined by matching the candidate's category_id against the relevant immediately preceding scheduled block/context on the timeline.
  3. If continuity is tied (both match or neither matches) or unavailable (no preceding block), the candidates pass through to the normal tie-breaking cascade (04 §2.3).
  4. Time-of-day preference is NOT used as a Stage 8 signal.
  5. Time-of-day fit belongs exclusively to Problem B candidate-slot scoring (04 §3).
  6. Later confirmed user preferences (08 §3, 11 §3) may feed Problem B slot scoring, but must not silently create a second Stage 8 preference ranking mechanism.
WHY: Clean architectural separation between Problem A (deciding what gets time among equivalent survivors for a slot) and Problem B (scoring candidate slots across the week). Category continuity protects focus and minimizes context switching (01 §1.2 Rule 5) without entangling slot-scoring preferences into the Stage 0–8 hierarchy.
IMPACT: Unblocks SCH-010 and Chunk 2 scheduling pipeline. Stage 8 implementation compares category_id against preceding block on timeline; falls through cleanly to 04 §2.3.
RELATED JIRA: SCH-010
RELATED DOCUMENTS: 04 sections 2/2.3/3; 08 section 3; 11 section 3
```

### DEC-0013
```
DATE: 2026-09-25
TYPE: Product
STATUS: Approved
AUTHORITY: Explicit product-owner approval (Choice 1 — Unblocking First).
DECISION: Stage 5 uses downstream unblocked task count as primary ordering signal, with completion percentage breaking ties.
CHOSEN OPTION:
  1. Downstream unblocked task count is the primary Stage 5 ordering signal.
  2. Higher unblocked task count wins.
  3. If downstream unblocked counts are tied (including both being 0), higher current_completion_pct wins (closer to 100%).
  4. Completed and cancelled downstream items are excluded from the unblocked count (only non-completed, non-cancelled downstream dependents count).
  5. Preserves the bounded dependency traversal already defined in code and spec (04 §2.5, DependencyLookahead bounded to max depth 8, max count 64).
  6. In tie-breaking cascade 04 §2.3 Rule 1 ("Less remaining work wins"), remaining work is evaluated as higher current_completion_pct (i.e. fewer percentage points remaining).
WHY: Maximizes project and milestone throughput by clearing bottlenecks on the critical path, while using stored current_completion_pct (02 §1.4, V9) as a deterministic measure of near-completion when unblocking values are identical. Avoids inventing ungrounded effort units or arbitrary weighting.
IMPACT: Unblocks SCH-007 and Chunk 2 scheduling pipeline. Stage 5 comparator queries DependencyLookahead for downstream unblocked count; falls back to currentCompletionPct comparison.
RELATED JIRA: SCH-007
RELATED DOCUMENTS: 04 sections 2/2.3/2.5; 02 section 1.4; DOM-007
```

### DEC-0012
```
DATE: 2026-09-21
TYPE: Implementation
STATUS: Approved
DECISION: EVT-004 uses the documented GET /events route for raw and rendered history.
CONTEXT: 12 section 11 supplies entity filters; section 1 supplies cursor/limit.
    10 section 4 requires plain-language Atlas events with original reasons.
CHOSEN OPTION: Required entity filter, optional actor filter, descending append IDs,
    bounded page size 20/default and 100/max, events/nextCursor envelope. Descriptions
    supplement unchanged stored fields. Unknown actions get neutral wording.
    Owner checked from current domain row before any event lookup. Missing or
    foreign ownership returns the same 404; no cross-user query or payload scan.
IMPACT: 12 section 11 documents the concrete wire shape. No schema or event writes.
    Historical fixed commitments physically deleted under DEC-0008 cannot currently
    be authorized by a surviving entity row and fail closed; ownership retention for
    such queries remains a limitation, not a claim of complete deleted-entity audit.
RELATED JIRA: EVT-004
RELATED DOCUMENTS: 10 section 4; 12 sections 1/11; 14 section 5; 15 section 2
```

### DEC-0011
```
DATE: 2026-09-21
TYPE: Product
STATUS: Proposed
DECISION: Product-owner clarification required for EVT-003 reversible event types.
CONTEXT: 10 section 4 and 19 section 3 resolve history immutability, but give only
    an example block.moved -> block.move_reverted. 12 section 11 requires undo of
    last N events. No supported-type matrix, inverse state transitions, or behavior
    for intervening changes/repeated undo is defined. Current code has no scheduled
    block persistence; existing domain state machines reject undocumented reversals.
OPEN QUESTION: Which currently implemented events may be undone, what state and
    compensating type does each produce, and how are last-N selection, repeated
    undo and intervening edits handled? Block-move implementation would require
    out-of-wave scheduled-block work. No product answer is selected here.
IMPACT: EVT-003 blocked; append-only EVT-001/EVT-002 behavior remains unchanged.
RELATED JIRA: EVT-003
RELATED DOCUMENTS: 10 section 4; 12 section 11; 19 section 3; 02 section 2
```

### DEC-0004
```
DATE: 2026-09-13
TYPE: Implementation
DECISION: BCrypt selected for password hashing; minimum password length set to 8 characters; V1 Flyway migration created.
STATUS: Approved
CONTEXT: FOUND-002 (Auth/JWT) implementation. 15_SECURITY_AND_PRIVACY.md §1 specifies password hashing via a standard modern algorithm (bcrypt/argon2) as an implementation detail. The repository had no Flyway V1 migration committed (only .gitkeep).
OPTIONS CONSIDERED:
  - Password hashing: BCrypt vs Argon2 (BCrypt chosen as standard Spring Security default).
  - Minimum password length: 8 characters (NIST SP 800-63B baseline security standard).
  - Flyway version: V1 vs V2 (V1 chosen as no V1 exists in git history and versioning starts at 1).
CHOSEN OPTION: BCryptPasswordEncoder, 8-character minimum length validation, V1__create_users_table.sql migration.
WHY: BCrypt is natively supported by Spring Security. 8-char min length aligns with industry baselines. V1 migration reflects actual file version history cleanly without gaps.
IMPACT: User entity, RegisterRequest validation, AuthService, V1__create_users_table.sql.
RELATED JIRA: FOUND-002
RELATED DOCUMENTS: 15_SECURITY_AND_PRIVACY.md §1, 13_DATABASE_SPECIFICATION.md §1, §5
```

### DEC-0005
```
DATE: 2026-09-13
TYPE: Architectural
DECISION: Auth API route contracts (POST /api/auth/register, POST /api/auth/login, GET /api/auth/me) are formally documented in 12_API_SPECIFICATION.md.
STATUS: Approved
CONTEXT: Jira story FOUND-002 acceptance criteria define registration, login, and current-user endpoints. 12_API_SPECIFICATION.md headers mention JWT bearer auth but does not enumerate the specific auth endpoint paths or payloads.
OPTIONS CONSIDERED:
  - Add auth endpoint contracts to 12_API_SPECIFICATION.md.
  - Leave auth contracts implicitly defined by Jira FOUND-002 only.
CHOSEN OPTION: Add the implemented auth contracts to 12_API_SPECIFICATION.md so product specification remains complete.
WHY: Preserves single source of truth across product spec and implementation.
IMPACT: `12_API_SPECIFICATION.md` §1.1 documents the approved contracts and their implemented validation, success, and error responses.
RELATED JIRA: FOUND-002
RELATED DOCUMENTS: 12_API_SPECIFICATION.md, 15_SECURITY_AND_PRIVACY.md §1
```

### DEC-0006
```
DATE: 2026-09-13
TYPE: Implementation
DECISION: Reverted an out-of-scope edit made by a documentation-sync
          agent to 19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md during a
          task explicitly scoped to exactly three files (ATLAS_CURRENT_STATE.md,
          03_REQUIREMENTS_TRACEABILITY.md, 12_API_SPECIFICATION.md §1.2).
STATUS: Approved
CONTEXT: The doc-sync agent was given an explicit SCOPE section naming
         three files and a "WHAT YOU MUST NOT DO" instruction to touch
         nothing else. It nonetheless added a status paragraph to
         19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md §6. The added content
         was factually accurate and non-contradictory, but the file was
         not in scope.
OPTIONS CONSIDERED:
  - Keep the edit since it was harmless and correct.
  - Revert the edit and record the scope violation explicitly.
CHOSEN OPTION: Revert; record the violation here rather than let it
               pass silently.
WHY: Per docs/agent/DEVELOPMENT_RULES.md's Scope Control section, an
     agent quietly expanding scope on a documentation task is the same
     failure mode as quietly expanding scope on a code task, even when
     the specific edit is harmless. Tolerating "small and harmless"
     scope creep erodes the review discipline the whole multi-agent
     workflow depends on. Reverting and logging it, rather than
     silently accepting or silently reverting, keeps the violation
     visible for future agents and for human review.
IMPACT: No code or product-behavior impact. Establishes precedent that
        scope violations on documentation tasks are treated with the
        same rigor as on implementation tasks.
RELATED JIRA: None.
RELATED DOCUMENTS: docs/agent/DEVELOPMENT_RULES.md (Scope Control),
                   docs/agent/HANDOFF_PROTOCOL.md
```

### DEC-0007
```
DATE: 2026-09-13
TYPE: Implementation
DECISION: Per-story handoffs live in docs/agent/handoffs/<jira-key>.md.
STATUS: Approved
CONTEXT: DOM-004 is the first implementation task requiring a handoff
         after HANDOFF_PROTOCOL.md established the convention but before
         the repository contained a handoff directory.
OPTIONS CONSIDERED:
  - Store handoffs beside individual feature branches.
  - Store all handoffs under docs/agent/handoffs/ keyed by Jira story.
CHOSEN OPTION: docs/agent/handoffs/<jira-key>.md.
WHY: This follows HANDOFF_PROTOCOL.md's explicit fallback, keeps durable
     task state in a discoverable shared location, and avoids coupling
     handoff visibility to a local branch checkout.
IMPACT: Future implementing agents create or update the handoff file
        for their assigned Jira key in this directory.
RELATED JIRA: DOM-004
RELATED DOCUMENTS: docs/agent/HANDOFF_PROTOCOL.md
```

### DEC-0008
```
DATE: 2026-09-14
TYPE: Product
DECISION: Approve the bounded DOM-006 manual Fixed Commitment CRUD contract.
STATUS: Approved
AUTHORITY: Explicit product-owner approval in the DOM-006 planning conversation,
           followed by authorization to implement the revised plan.
CONTEXT: The review records claimed Fixed Commitment endpoints had been added,
         but the current API document lacked them. Recurrence, deletion,
         overlap and replay behavior needed explicit boundaries.
CHOSEN OPTION: POST /fixed-commitments and GET/PATCH/DELETE /fixed-commitments/{id};
    authenticated JWT ownership, indistinguishable missing/foreign 404s,
    no collection/range endpoint. Always Fixed. Manual creation assigns manual;
    screenshot_import is a permitted persistence value but never client-supplied
    through manual CRUD. Future import requires DEC-0001 approval.
    Nullable recurrence storage only; public writes accept omission/null and
    reject non-null values. No invented recurrence grammar or engine.
    Physical DELETE returns 204 with immutable fixed_commitment.deleted in the
    same transaction; no Cancelled state. Overlaps are allowed, with no movement
    or collision rejection. Repeated POST creates distinct entities; no persisted
    idempotency infrastructure. Scheduling, recovery, OCR/import, calendar
    integrations and frontend redesign are excluded.
WHY: Deliver the approved domain persistence/API increment without inventing
     behavior owned by later scheduling, recurrence or import stories.
IMPACT: 02 section 1.11, 12 section 5.1, 13 physical mapping, 10 event contract,
        DOM-006 traceability, V8 and implementation handoff.
RELATED JIRA: DOM-006 (Jira itself unchanged)
RELATED DOCUMENTS: 02, 03, 10, 12, 13; DEC-0001
IMPLEMENTATION DETAILS: Constant Fixed tier rather than duplicate mutable
    storage; source strings with DB check, matching existing domain patterns;
    strict local request types/unknown-field rejection; UTC DATETIME(6) mapping,
    truncation before positive-duration validation and UTC year range 1000–9999;
    owned row locks for PATCH/DELETE; snapshot event payloads; no-op PATCH adds
    no event. Historical category migration tests remain pinned to V7.
FOLLOW-UP: Define active recurrence semantics and scheduling/recovery/import
    integration in their owning stories. These do not block DOM-006.
```

### DEC-0009
```
DATE: 2026-09-15
TYPE: Product
STATUS: Approved
AUTHORITY: Explicit product-owner approval of the final DOM-003 implementation plan,
           subsequent absolute-instant deadline decision, and implementation authorization.
DECISION: DOM-003 delivers the permanent Commitment foundation while preserving FOUND-003.
CHOSEN OPTION: V9 adds commitments and nullable Category default_importance. Public API is
    POST /commitments and GET/PATCH /commitments/{id} only. Six Work State values persist;
    only draft->ready, ready->in_progress, in_progress->completed and in_progress->ready
    are implemented domain transitions. Execution transitions require their documented
    execution/user context and have no public endpoint in DOM-003. All other transitions
    are rejected. No direct Work State PATCH.
    Missing/blank title or criterion means Draft; both complete establish Ready. Neither
    defining field may be cleared after Draft. Category is optional. Explicit importance
    wins over category default, otherwise validation fails. Effective importance is stored;
    category/default edits never silently rewrite it. No inheritance-tracking infrastructure.
    Progress is DECIMAL(5,2), 0..100, initially 0; reporting/correction and idempotency belong
    to EXEC-004, not this story. Deadline is nullable Instant: explicit-offset ISO input,
    UTC DATETIME(6), microsecond truncation, UTC ISO response. No date-only/local input.
    Creation/update/readiness events are ordered and atomic using the existing Event system.
    Legacy tasks, /api/tasks, TodayScreen and task history remain intact, with no dual writes
    or conversion. Coordinated Today/execution cutover belongs to later scheduling/execution.
WHY: Establish the full domain without bypassing execution prerequisites or breaking the
     existing loop. This supersedes earlier immediate-Phase-2-cutover wording.
IMPACT: 02, 03, 10, 12, 13, 18, 19; minimal Category contract extension; V9; DOM-003 handoff.
OUT OF SCOPE: scheduling, recovery, AI, recurrence, sessions, dependency graph, cancellation
    workflow, progress endpoints, Today cutover, calendar integration. DEC-0003 remains intact.
IMPLEMENTATION DETAILS: checked lowercase strings; owned mutation locks; false server flags;
    UTC Instant-to-LocalDateTime persistence converter independent of default timezone;
    reject UTC dates outside MySQL years 1000..9999; category default omission preserves on
    PATCH and explicit null clears; no-op PATCH emits nothing. No terminal reopening.
RELATED JIRA: DOM-003 (Jira unchanged); EXEC-004; proposed DOM-008.
```

### DEC-0010
```
DATE: 2026-09-19
TYPE: Architectural
STATUS: Approved
AUTHORITY: Product-owner instruction to implement DOM-007 first (then SCH-007), freezing
           the C1 HTTP/event/schema contract that was not previously in `12`.
DECISION: DOM-007 implements directed Commitment dependencies per `13` and C1.
CHOSEN OPTION: V10 `commitment_dependency` with two FKs, unique edge, self-edge CHECK, no
    cascade. Routes: POST/GET `/commitments/{id}/dependencies` and DELETE
    `.../dependencies/{blockingCommitmentId}` where path id is the blocked item.
    201 new edge; 200 duplicate without a second event; 400 self/malformed; 404 missing or
    foreign (sanitized); 409 cycle. Unbounded cycle detection on write. Owner graph mutations
    serialize on the users row. Events `task.dependency_added` / `task.dependency_removed`
    are atomic with the edge change. Bounded lookahead is an internal read for SCH-007 and
    reports truncation; it is not a public transitive API.
WHY: Jira requires join table, cycle detection on create, and API endpoints. `12` had no
     dependency routes; C1 was the only complete proposed contract.
IMPACT: V10; dependency package; 10, 12, 13; DOM-007 handoff.
OUT OF SCOPE: SCH-007 ranking, cancellation cascade, work-state changes, estimated-effort
    fields, expanding CommitmentResponse with transitive graphs.
RELATED JIRA: DOM-007; SCH-007
RELATED DOCUMENTS: 02 §1.4; 04 §2.5; 13; 01 §4; C1 in ATLAS_PARALLEL_WORK_PLAN.md
```

### DEC-0017 — Chunk 2 temporary slot scoring and duration inputs

- DATE: 2026-09-25; TYPE: Product; STATUS: Approved.
- AUTHORITY: Explicit product-owner reply during Chunk 2 implementation.
- DECISION: Four equal-weight [0,1] Problem B dimensions, higher is better.
  Missing confirmed time-of-day/energy evidence contributes zero; no inferred
  preference. Continuity is 1 for a matching preceding category, otherwise 0.
  For leftover usable gap g and requested work seconds w, penalty is zero if
  g >= w, otherwise g/w; fragmentation score is 1 - penalty.
  Planning window and per-item work minutes are explicit request-scoped inputs;
  no estimated duration is fabricated or persisted. Later replacement requires
  a new recorded product decision. Stage 8 remains category-only.
- IMPLEMENTATION: Each maximal fitting free range supplies boundary placements;
  calendar/capacity boundary starts are also considered. Scoring leftover means
  total usable seconds left in that free range after the elapsed block duration.
  New placements never move existing blocks: their movement cost is zero;
  flexibility resistance orders which competing work is less disruptive to defer.
  Equal item creation timestamps use numeric database ID as a stable final
  technical discriminator. Equal slot scores for the same item use UTC start/end
  after the item tie cascade (which cannot distinguish an item from itself).
- RELATED: SCH-008, SCH-011, SCH-012; 04 sections 2–3; Chunk 2 request.
