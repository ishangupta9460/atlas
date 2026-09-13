# 08 — Memory and Preferences

**Status: RECONSTRUCTED.** This document did not exist in the reviewed package, despite being cited by `07` §1/§2, `12` §9, and Jira epic `Memory/Preferences` (`MEM-001`–`MEM-004`). Section numbers below (`§1`, `§2`, `§3`, `§4`) are fixed by those existing citations — do not renumber. See `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §4 for the evidence trail.

**Authoritative for:** the User Preference entity's write path (confirmation-gated persistence), the distinction between a raw observation and a saved preference, contextual application of already-saved preferences, and preference management. This document does not own the Preference entity's *fields* (`02` §1.10 does) — only its lifecycle and application rules.
**Source:** Master Spec §1.8.
**Depends on:** `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` §1.10 (User Preference fields, not redefined here), `07_AI_ARCHITECTURE.md` (AI may surface an observation; may never itself write a preference).

---

## 1. The Explicit-Confirmation Write Path

**[EXPLICIT]** — Master Spec §1.8: "A preference... becomes persistent memory only on explicit user confirmation ('remember this as my preference?' → yes)." Non-Negotiable Rule 5: "A single disliked instance of a resource is never auto-generalized into a saved preference; only explicit confirmation creates persistent memory." `02` §1.10's invariant states the same rule at the field level: "created only on explicit user confirmation... never written directly from an AI observation without that confirmation step." Jira `MEM-001` ("Preference entity + confirmation flow") acceptance criteria: "Only explicit confirmation persists a preference."

**The write path has exactly one entry point:** `POST /preferences` (`12` §9, already annotated "Create (only via explicit confirmation flow, `08` §2)" — the citation this section number resolves). There is no other code path, AI-triggered or otherwise, that inserts a `user_preferences` row. This mirrors `07` §2's AI Non-Responsibility list item: AI "silently converts an observation into a saved preference" is something AI *never* does — the confirmation step is not a formality layered on top of an AI write, it is structurally the only way a write happens at all.

## 2. Observation → Confirmed Answer → Memory

**[EXPLICIT]** — cited by `07` §1: "ask clarifying questions (tagged per `02` question-reason categories — see `08_MEMORY_AND_PREFERENCES.md` §2 for how confirmed answers become memory)." This section is that reconstruction.

Three distinct things must not be conflated, per Master Spec §1.8's own example ("I disliked this lecture" is not "I dislike long lectures"):

1. **A raw observation** — a single data point (one disliked resource, one pattern Atlas notices across a few instances). Never persisted as a preference by itself. May be computed on demand (§3.2 of `06`, the Tier-2 resource-feedback pattern) or surfaced conversationally by the AI Proposal Layer as a clarifying question (`07` §1) tagged with one of `02`'s reason categories (`task_creation`, `scheduling`, `conflict_resolution`, `uncertainty`, `goal_risk` — Master Spec §1.9).
2. **A confirmed answer** — the user's direct response to a specific clarifying question in the moment ("should this task be Protected?" → "yes"). This resolves *that* question but does not automatically become a standing rule for all future similar tasks unless step 3 happens.
3. **Memory** — a `user_preferences` row (`02` §1.10), created only when the user explicitly confirms the *generalization*, not just the one-off answer — e.g., Atlas asking "should I treat all your gym sessions as Protected going forward?" and the user confirming, as distinct from confirming it for today's session only.

**The distinction that matters:** answering a clarifying question resolves the current task-creation or scheduling decision (§1.9 reason tags); it does not, by itself, write a `user_preferences` row. Only an explicit confirmation of the *generalized* statement does. This is why `MEM-001` depends on `AI-003` (progressive clarifying questions) rather than the reverse — the question-asking mechanism has to exist first, and this document's confirmation gate sits between it and persistence.

## 3. Contextual Application

**[EXPLICIT]** — Master Spec §1.8: "Preferences... are applied only with contextual confirmation when relevant ('I remember you prefer project-based learning — use that here?') — never silently." Jira `MEM-003` ("Contextual preference application") acceptance criteria: "Applied only with per-use confirmation, never silent," depending on `MEM-001`.

When a scheduling or task-creation decision touches a domain (`02` §1.10's `domain` field: `learning`/`scheduling`/`resource`/`rescheduling`) where an `active` preference exists, Atlas surfaces it as a contextual prompt rather than silently applying it — even though the preference was already confirmed once at creation time. This is a second, lighter confirmation ("use that here?"), not a re-litigation of whether the preference is real; the distinction from §1 above is that §1 gates *creating* the preference, this gates *applying* an already-created one to a specific new situation. Both gates exist because a preference that was true and confirmed in one context (a specific goal, a specific category) is not automatically the right call in every future context — e.g., "prefer project-based learning" confirmed for a coding goal shouldn't silently apply to an unrelated language-learning goal without the user noticing it's being invoked there.

A preference can be temporarily disabled (`02` §1.10's `active` boolean) without being deleted — a "not right now, but don't forget it either" state, distinct from Pause/Abandon at the Goal level (`02` §2.1–2.2) which this document does not redefine.

## 4. Preference Management

**[EXPLICIT]** — Jira `MEM-002` ("Preference management UI") acceptance criteria: "View/edit/delete/disable." API surface already fully defined in `12` §9: `GET /preferences` (list), `PATCH /preferences/{id}` (edit/disable), `DELETE /preferences/{id}` (delete). This section adds no new endpoint — it confirms the existing `12` §9 surface is sufficient for `MEM-002`'s stated scope and notes the UI behavior it implies: a viewable, editable, deletable list, matching Master Spec §1.8's "Preferences live in a viewable/editable/deletable Preferences area."

## 5. Repeated-Behavior Observations — Open Product Decision

**[OPEN PRODUCT DECISION]** — surfaced during the September 2026 specification review (`ATLAS_SPECIFICATION_REVIEW.md` §4, Scenario D: "user repeatedly manually moves the same flexible task"). This is recorded here, not resolved, per that review's explicit instruction not to guess.

**What is known:** a manual drag/move sets `user_moved_flag` on the affected Scheduled Block (`02` §1.6, `05` §7), and per Master Spec §1.21, the Scheduling Engine subsequently treats that item as effectively Protected — it will not move it again without a new Collaborative-tier trigger. This is well-specified for a *single* move. Separately, `05` §6's Pattern Detection mechanism exists for a structurally different signal: repeated *non-completion* of planned work, requiring multi-week evidence, feeding Goal Risk and Recurring Intention recalibration suggestions.

**What is unknown:** whether repeated manual *relocation* of the same recurring item (not a missed-work signal — the user is completing the work, just consistently at a different time than Atlas placed it) should:
- (a) be silently ignored beyond the existing single-move Protected-treatment rule (§1.21) — i.e., no new behavior at all;
- (b) feed a Tier-2-style observation ("you've moved your Tuesday gym block to Thursday three weeks running — want to change its default time?"), surfaced but never auto-applied, analogous to `06` §3.2's Tier 2 resource-feedback pattern and gated by the same confirmation rule as §1 above; or
- (c) be silently learned into Problem B's per-user time-of-day preference (`04` §3, `11` §3) without ever surfacing an explicit observation, on the theory that this is scheduling personalization (which the Analytics/Scheduling feedback loop already does per `04` §3: "Learned preferences — any other per-user pattern") rather than a Memory-tier preference requiring confirmation.

**Why this matters and why it isn't decided here:** options (b) and (c) have materially different privacy/control implications — (b) requires this document's confirmation-gate machinery and a new Jira story; (c) would live entirely inside `04`/`11`'s existing learned-preference loop and require no new story, but risks quietly overriding a pattern the user might not want generalized (e.g., they moved it those three weeks because of a temporary conflict, not a genuine standing preference — exactly the single-instance-vs-pattern distinction §2 above exists to protect against). Choosing (c) without the same evidentiary bar `05` §6 applies to missed-work patterns (multi-week, meaningful frequency ratio, never 3-for-3 in a short window) would be inconsistent with Non-Negotiable Rule 8 applied elsewhere in the system.

**Recommendation, not a decision:** whichever option is chosen, it should reuse `05` §6's multi-week/frequency-ratio evidentiary standard rather than defining a new one, for consistency. No Jira story currently exists for this behavior in any of the three forms.

## 6. Genuine Gaps / Requires Product Decision

*(One item — see §5 above. No other open questions identified; the explicit-confirmation write path (§1) and contextual-application gate (§3) are fully specified by existing citations and require no further product decision.)*
