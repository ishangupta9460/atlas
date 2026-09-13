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
