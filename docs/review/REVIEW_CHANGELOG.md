# REVIEW_CHANGELOG.md

Review record for the September 2026 architecture/consistency pass over the Atlas engineering package. This is a log of what changed and why — not a new architecture document.

**Scope note:** This pass reviewed `02`, `04`, `05`, `07`, `09`, `10`, `12`, `13`, `15`, `18`, `19`, and `atlas-master-product-spec-v1.md`, cross-checked against `Jira.csv` (110 stories across 14 epics). Documents `01_SYSTEM_ARCHITECTURE`, `03_REQUIREMENTS_TRACEABILITY`, `06_ROADMAP_AND_RESOURCE_SYSTEM`, `08_MEMORY_AND_PREFERENCES`, `11_ANALYTICS_AND_LEARNING`, `14_UI_UX_SPECIFICATION`, `16_TEST_STRATEGY`, and `17_OPERATIONS_AND_DEPLOYMENT` are referenced constantly by the reviewed documents and by Jira but were **not included in this upload** and were **not reviewed**. Every finding below is limited to the documents actually available; see `ATLAS_SPECIFICATION_REVIEW.md` §5 for what remains unverified as a result.

---

### 1

**Date:** 2026-09-11
**Document(s):** `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` (§2.3, §2.4), `05_RESCHEDULING_AND_RECOVERY.md` (§3, §8), `13_DATABASE_SPECIFICATION.md` (§1, §3), `10_EVENT_LOG.md` (§2)

**Problem:** `05` §3 (Overload Resolution) instructed the system to move a losing Commitment to "Planning State = Deferred," citing `02` §2.2. But `02` §2.2 defines Planning State (`Active/Deferred/Paused/At Risk`) as a **Goal**-only field. Commitments only had a Work State axis (`Draft → Ready → In Progress → Completed`) with no Deferred value, and no terminal state at all for "this task was cancelled." Separately, `02` §2.1's `goal.abandoned` side effect ("Related open Scheduled Blocks cancelled") pointed at a `Cancelled` state that did not exist in the Scheduled Block lifecycle (`02` §2.4: Scheduled/Unresolved/Superseded/Active/Completed only).

**Decision:**
- Added `Deferred` and `Cancelled` to Commitment Work State (`02` §2.3), with explicit transition rows and events (`task.deferred`, `task.reactivated`, `task.cancelled`).
- Added `Cancelled` to Scheduled Block lifecycle (`02` §2.4) with event `block.cancelled`, and pointed `goal.abandoned`'s side effect at it explicitly.
- Corrected `05` §3 to say "Work State = Deferred (`02` §2.3)" instead of "Planning State = Deferred (`02` §2.2)."
- Added a clarifying note in `02` that `Commitment.work_state = Deferred` and `Goal.planning_state = Deferred` are deliberately separate values on separate axes and must not be conflated in Event Log rendering — consistent with the existing Cross-Cutting Invariant (`02` §3), not a violation of it.
- Updated `13`'s `commitments.work_state` and `scheduled_blocks.status` enum descriptions to list the corrected value sets, and corrected `13` §3's soft-deletion table to name the right terminal state per entity (`Cancelled` for Commitments/Blocks, `Abandoned` for Goals — it previously implied "Abandoned" applied uniformly).
- Added `task.deferred` / `task.cancelled` / `block.cancelled` to `10`'s illustrative event-type list with a note distinguishing `task.deferred` from `goal.deferred`.

**Reason:** This was a real category error, not a style issue — as written, an engineer implementing `05` §3 literally would have tried to write to a field (`Commitment.planning_state`) that does not exist in the schema. It also left "cancel a task" and "cancel a scheduled block on goal abandonment" completely unspecified, which is a gap in a system whose stated non-negotiable principle is that nothing is silently deleted or hidden (Master Spec rule 7).

**Impact on other documents:** `02`, `05`, `13`, `10` (all edited above). `12_API_SPECIFICATION.md`'s `PATCH /commitments/{id}` and `POST /goals/{id}/abandon` routes are unaffected in shape (they already return affected Scheduled Blocks inline per `12` §1) but now have a concrete terminal state to return.

**Jira impact:** No existing story implements task cancellation at all (`grep`-confirmed: zero matches for "cancel" anywhere in the backlog). `DOM-003`'s acceptance criteria ("All fields incl. ... work_state") should be updated to explicitly enumerate the corrected value set. A new story is needed for the cancellation flow (see `ATLAS_SPECIFICATION_REVIEW.md` §6).

---

### 2

**Date:** 2026-09-11
**Document(s):** `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` (§1.4), `04_SCHEDULING_ENGINE.md` (§2.2)

**Problem:** `Commitment.is_hard_consequence` is populated by an asynchronous AI proposal (`classify_hard_consequence`, `07` §3). Neither `02` nor `04` stated what value the field holds, or how the Stage 2 gate behaves, for the window between task creation and that AI call returning — a real race condition, since the Scheduling Engine does not block placement on AI (per `07` §5's "AI unavailable → core flows continue" principle, which implies the engine must already tolerate an absent classification).

**Decision:** Documented the default (`false` until classified) and the resulting behavior explicitly in `02` §1.4: the task's first placement pass correctly runs at Stage 3 rather than Stage 2 if classification hasn't landed yet, and a later classification flipping the flag to `true` is handled as an ordinary Autonomous/Collaborative re-placement (`05` §7), not a special-cased retroactive correction. Cross-referenced the same resolution from `04` §2.2 rather than restating it, to avoid the two documents drifting.

**Reason:** Without this, two engineers could reasonably build incompatible behavior (one blocking placement on AI, one silently treating unset as permanently `false`) and both could point to the spec as justification. This is exactly the kind of ambiguity Non-Negotiable Rule enforcement and Stage 0–8 determinism (`04` §2.4) depend on being absent.

**Impact on other documents:** `04` §2.2 (cross-reference added, no duplicate rule stated). No Jira story currently tests this race condition explicitly.

**Jira impact:** `SCH-004` (Stage 2 gate) and `AI-007` (`classify_hard_consequence` proposal) should each get an explicit acceptance-criterion line covering the "classification arrives after first placement" case.

---

### 3

**Date:** 2026-09-11
**Document(s):** `05_RESCHEDULING_AND_RECOVERY.md` (§1)

**Problem:** The Recovery Trigger Events list covered a block's window passing, partial completion, and the user reporting lost time — but not the case of a **new Fixed Commitment appearing after a schedule already exists** and overlapping a previously-placed Flexible/Optional/Protected block (Scenario F in the human-behavior review pass: "a protected commitment appears unexpectedly"). Nothing in the document said what happens to the block it now collides with; left as written, this reads as silent double-booking, which directly contradicts the Stage 0 guarantee that Fixed items are never displaced (`04` §2) — it doesn't say what happens to the *other* side of that collision.

**Decision:** Added a fourth trigger: creation (manual or screenshot-import) of a Fixed Commitment that overlaps an existing Scheduled Block for a lower-tier item now explicitly re-enters that block into Progressive Search (`05` §2), never displacing the new Fixed item, and escalating to Critical tier only if the collision is between two Fixed/Protected items.

**Reason:** This is exactly the human-behavior failure mode the review brief asked to be tested for. Silent double-booking is the single worst outcome for user trust in an "adapts to the human" system, and the spec previously had no answer for it.

**Impact on other documents:** None required beyond `05` §1; `04`'s Stage 0 rule already supported this without modification.

**Jira impact:** `RESC-012` ("Unexpected interruption handling," currently scoped to "can't work 4–8 PM" only) should be widened, or a sibling story added, to cover the new-Fixed-Commitment-collision case.

---

### 4

**Date:** 2026-09-11
**Document(s):** `12_API_SPECIFICATION.md` (new §5), `19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md` (§1, §2)

**Problem:** The Fixed Commitment / Calendar Event entity (`02` §1.11) has a domain definition, a database table (`13` §1), and is structurally load-bearing for the Scheduling Engine (Stage 0 gate, `04` §2.1) and for the newly-added recovery trigger (change #3 above) — but `12_API_SPECIFICATION.md` had **no endpoints for it at all**. As written, there was no way to actually create one. `19`'s own consistency-audit table claimed "every API endpoint corresponds to the domain model... no orphan endpoints," which was incorrect.

**Decision:** Added `12` §5 (`POST /fixed-commitments`, `POST /fixed-commitments/import`, `GET`, `PATCH`, `DELETE`), consistent with existing conventions (`12` §1: user-scoped, inline side-effect reporting). Renumbered `12` §6–13 to §7–14 to accommodate the insertion, and fixed the two places that referenced the old numbering (`15_SECURITY_AND_PRIVACY.md` §3's export endpoint pointer, `19`'s idempotency gap-resolution row). Corrected `19`'s audit-table claim to describe the actual finding and fix instead of asserting a pass that wasn't true.

**Reason:** This is the most concrete implementability gap found in the reviewed set — screenshot-based fixed-schedule import (Jira `ROAD-006`) and manual fixed-commitment entry are both plainly intended features with no way to build them against the documented API surface.

**Impact on other documents:** `15_SECURITY_AND_PRIVACY.md` (reference fix only), `19` (audit correction). `06_ROADMAP_AND_RESOURCE_SYSTEM.md` §4 is cited by `13` and by Jira `ROAD-006` as owning the screenshot-import *parsing* behavior; this document was not available for review, so `12` §5's import endpoint description is written to be consistent with the documents available but should be checked against `06` §4 once that document is reviewed.

**Jira impact:** None required — `ROAD-006` already exists and already cites `06` §4; no new story needed, but its dependency list should probably note `12` §5 now that it exists.

---

### 5 — Claim correction (not a design change)

**Date:** 2026-09-11
**Document(s):** `19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md` (§1, §6)

**Problem:** `19` asserted "No contradictions were found requiring a fix" and "Implementation readiness: green," both stated without qualification. Both were incorrect given changes #1 and #4 above, and both omitted the fact that 8 of the 19 referenced documents were never part of this review.

**Decision:** Rewrote the relevant passages to describe what was actually found and fixed, and to name explicitly which documents remain unreviewed.

**Reason:** A specification's own "everything checks out" section is exactly the place where unearned confidence does the most damage — it is what a future reader will trust without re-verifying. Leaving it uncorrected after finding real contradictions would have made this review worse than not having done it.

**Impact on other documents:** None beyond `19` itself.

**Jira impact:** None directly, but see `ATLAS_SPECIFICATION_REVIEW.md` §5 for the follow-up review this implies.
