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

### DEC-0014
```
DATE: 2026-09-21
TYPE: Product
STATUS: Proposed
DECISION: Clarify SCH-010 preference evidence and conflicting Stage 8 signals.
CONTEXT: 04 section 2 defines time-of-day preference/minimizing switching only for
    otherwise equivalent survivors. It does not define comparison when time-of-day
    favors one survivor and continuity favors another, or missing evidence behavior.
    08 section 3 requires contextual confirmation for saved preferences; 11 section 3
    supplies learned fit to Problem B and historical probability for calibration only.
    SCH-009 is only an evidence seam, not a selection path. No preference/slot context
    producer or integrated survivor pipeline exists in current code.
OPEN QUESTION: Define the Stage 8 evidence source and applicable confirmation,
    time-of-day fit semantics, missing evidence, and conflicting continuity/preference
    resolution. This must not reuse historical execution probability as ranking.
IMPACT: SCH-010 blocked. No new comparator, arbitrary weights, preference persistence,
    learned behavior, or earlier-stage pipeline is introduced in this wave.
RELATED JIRA: SCH-010
RELATED DOCUMENTS: 04 sections 2/2.3/3; 08 section 3; 11 section 3; SCH-009 handoff
```

### DEC-0013
```
DATE: 2026-09-21
TYPE: Product
STATUS: Proposed
DECISION: Clarify SCH-007 remaining-work measurement and Stage 5 tradeoffs.
CONTEXT: 04 section 2 says prefer unblocking or near-completion; section 2.5 defines
    bounded traversal (already implemented by DOM-007). Neither defines relative
    preference when one candidate unblocks more work and another is nearer complete.
    02/13 and Commitment have completion percentage but no estimated effort units.
    DOM-007 handoff already flags remaining-effort units as unspecified. Percentage
    remaining is not comparable effort across differently sized tasks. Section 2.3
    also applies when an earlier tier cannot distinguish candidates: it permits
    remaining-work tie resolution, but does not define its units or how Stage 5
    first distinguishes competing dependency and near-completion advantages.
OPEN QUESTION: Define remaining-work input/units, missing-input behavior, the
    near-completion rule, and comparison against dependency-chain value (including
    which downstream work states count). Do not introduce weights or thresholds
    under implementation authority.
IMPACT: SCH-007 ranking blocked. Safe partial fix distinguishes shared/repeated
    dependencies from true cycles in existing bounded lookahead. Current production
    scheduling code has only Stage 2/3/4 helpers and Stage 7 seam, no decision pipeline;
    end-to-end Stage 0-5 scheduling assertions cannot truthfully be claimed.
RELATED JIRA: SCH-007
RELATED DOCUMENTS: 04 sections 2/2.3/2.5; 02 section 1.4; DOM-007 handoff
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
