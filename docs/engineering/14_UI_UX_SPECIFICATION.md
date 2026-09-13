# 14 — UI/UX Specification

**Status: RECONSTRUCTED.** This document did not exist in the reviewed package, despite being one of the most frequently cited documents in the entire package — `04`, `05`, `09`, `10`, `12`, `13`, `18`, `19` all reference it by section number, and Jira epics `UI Core` (`UI-001`–`UI-003`, `UI-006`, `UI-007`) and `UI Remaining` (`UI-004`, `UI-005`) implement it directly. Section numbers below (`§1`–`§7`) are fixed by those existing citations — do not renumber. See `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §6 for the full evidence trail.

**Authoritative for:** information architecture and interaction behavior for the Today/Now/Next/Later experience, the calendar view, onboarding, progressive AI questioning UI, the Roadmap Review/Approve screen, "what changed" explanation rendering, Critical/Collaborative conversation UI, and the notification-relevance filter. This document does not own visual styling, a component library choice, or any business rule — every rule referenced here is owned by the document cited; this document only describes how that rule surfaces to the person using Atlas.
**Source:** Master Spec §1.19, §1.20, §1.21.
**Depends on:** `05_RESCHEDULING_AND_RECOVERY.md` (Autonomous/Collaborative/Critical tiers, notification-relevance rule), `09_EXECUTION_AND_FOCUS.md` (session lifecycle this UI renders), `12_API_SPECIFICATION.md` (the only boundary this UI calls through, per `01` §3).

**Governing human principle [EXPLICIT, from the review brief and Master Spec §1.2 principle 5]:** Atlas reduces cognitive disruption. Every screen and notification described below is designed against the test: does this make a normal interruption (a missed block, a forgotten timer, a busy day) feel like a personal failure, or does it treat it as the ordinary thing it is? Where a design choice could go either way, this document takes the reading that minimizes disruption, consistent with how `05` was already written and corrected in the September 2026 review.

---

## 1. Onboarding

**[EXPLICIT]** — Jira `UI-006` acceptance criteria: "Single opening question, progressive setup, no upfront forms," depending on `FOUND-002` (Auth). Master Spec §1.9: "Questions are asked 1–2 at a time, following a What → When/Why → How → Details depth hierarchy, going deeper only when the answer actually requires it." Master Spec §1.2 principle 10: "Complex behavior lives inside Atlas, not in user configuration. Onboarding stays minimal; Atlas learns the rest progressively through normal use."

**Flow [STRONGLY INFERRED from the "single opening question" + depth-hierarchy description]:** after auth, the first screen asks one open question (a goal, an existing roadmap, or a direct task — the three entry points of Master Spec §1.3) rather than a multi-field setup form. Follow-up questions go deeper only as needed (What → When/Why → How → Details), each tagged with a reason per `02`'s question-reason categories (`task_creation`, `scheduling`, etc. — Master Spec §1.9), consistent with the AI Proposal Layer's clarifying-question mechanism (`07` §1, §4). Sleep/recovery-window setup (`04` §4's protected window, seeded "from onboarding conversation or manual edit") happens here as one of the early progressive questions, not a separate upfront form. Category flexibility defaults (`MEM-004`, `02` §1.9) are asked at category-creation time, not during onboarding itself — onboarding does not front-load every category the user might ever create.

## 2. Today / Now / Next / Later, Calendar, and Manual Overrides

**[EXPLICIT]** — Master Spec §1.19: "The home experience is task-first, not calendar-first: **NOW** (the one thing in front of the user), **UP NEXT**, **LATER**. The calendar exists as a secondary view for users who want the fuller picture." Jira `UI-001` ("Today/Now/Next/Later screen") acceptance criteria: "Task-first home view," depending on `SCH-013` (Capacity Model — the home view cannot render without a real schedule to draw from). Jira `UI-002` ("Calendar (week) view") acceptance criteria: "Secondary FullCalendar-based view" — confirming the technology commitment already recorded in `01` §5.

**Now/Next/Later structure:**
- **NOW** — the single Scheduled Block currently active or immediately due to start. Opening it shows the Task Brief (`09` §2: what to accomplish, attached Resources, the `completion_criterion` field) — "an executable piece of the roadmap, not a vague title" (Master Spec §1.19's own phrasing: "implement one GET endpoint," not "study Spring Boot").
- **UP NEXT** — the next 1–2 Scheduled Blocks in sequence.
- **LATER** — the remainder of today's schedule, collapsed/summarized rather than enumerated block-by-block, consistent with minimizing cognitive load on a day that may already contain more than is comfortable to look at in full (Scenario E from the review brief).

**Minimum Viable Day banner [EXPLICIT]** — Jira `UI-003` acceptance criteria: "Essential/Good-to-do/Optional distinction on hard days," sourced to `14` §2 and `05` §9 (Minimum Viable Day). Rendered as a banner or summary strip atop the Today view, using `05` §9's three-tier classification directly — this document does not recompute or reinterpret that classification, only displays it. Shown specifically "on hard days" (`UI-003`'s own phrasing) — **[STRONGLY INFERRED]**: a day where everything fits comfortably has no need for an Essential/Good-to-do/Optional distinction, since the distinction only carries information when something didn't fit. The trigger condition (what makes a day "hard" enough to show this banner) is not itself specified anywhere and is left as an implementation detail tied to whatever `05` §9 already determined that day (if any item landed in "Optional" or was deferred, the day qualifies).

**Manual drag/move and the sticky flag [EXPLICIT]** — Jira `UI-007` acceptance criteria: "Drag sets `user_moved_flag`; Atlas never silently reverts it," depending on `SCH-003` (Stage 1 override). Master Spec §1.21: "A manual drag/edit sets a `user-moved` flag on that instance. The autonomous scheduling engine treats user-moved items as effectively Protected going forward... If asked 'why is this still Saturday,' Atlas answers 'you moved it there,' not silence or an unexplained re-move." A drag interaction calls `POST /schedule/blocks/{id}/move` (`12` §7), and the UI's only responsibility is to reflect the resulting `user_moved_flag` state (e.g., a small persistent indicator on that block) so the person can see, without having to ask, that this block is now sticky — directly supporting §5's "what changed" principle at the point of interaction rather than only on request.

## 3. Progressive AI Questioning UI

**[EXPLICIT]** — Jira `AI-003` ("Progressive clarifying questions") sources to `` `07` §4, `14` §3 ``; `18` Phase 3 cites "Goal interview flow (`14` §3)" as a distinct build item alongside direct task capture and roadmap ingestion.

This section covers two related but distinct surfaces that share the same underlying question-asking mechanism (§1's onboarding progressive-question pattern, Master Spec §1.9):
- **Goal interview flow** (Entry Point A, Master Spec §1.3): the conversational surface that builds a Roadmap collaboratively when the user states a goal rather than uploading one. Follows the same 1–2-at-a-time, reason-tagged question pattern as onboarding.
- **In-context clarifying questions** (Entry Point C and general task creation): a question surfaced inline wherever a `07` §1 clarifying-question proposal fires (e.g., during direct task capture, or when Stage 3/4 scheduling needs a tie broken and the tie-break rules didn't resolve it — `04` §2.3 rule 5's narrow "surface to the user" exception).

**Rendering rule [STRONGLY INFERRED from Master Spec §1.9's reason-tag list + the progressive-disclosure principle]:** every question shown carries its reason implicitly in its phrasing (the person should be able to tell *why* Atlas is asking without the UI literally printing a tag name), and the UI never presents more than 1–2 questions at once, never as an upfront multi-field form — this is the same constraint as onboarding (§1), applied wherever the question-asking mechanism fires, not only at account creation.

## 4. Roadmap Review/Approve Screen

**[EXPLICIT]** — `18` Phase 3 cites "Review/Approve UI (`14` §4)" as a distinct build item from the deterministic ingestion pass (`06` §1.2) it renders. Jira `ROAD-002` ("Review/Approve UI") sources to `06` §1.4–1.5 for the *behavior*; this section owns how that behavior is presented.

Per `06` §1.4 (reconstructed), the screen must show: counts (milestones, tasks, resources, estimated effort, dependencies, optional sections found), and provide three edit actions (strike a section, mark a milestone/prerequisite as already-known, swap a resource) — all operating against the import proposal via `PATCH /roadmaps/import/{importId}` (`12` §3), never against committed entities, since nothing is committed until Approve (`06` §1.5). AI-assisted enhancement (`06` §1.3) surfaces its per-node confidence directly on this screen — a node the AI layer is uncertain about is visually flagged here, which is the concrete UI expression of `06` §2's conservative-default rule ("flag for the user's attention during Review rather than either dropping it silently or scheduling it speculatively").

**[OPEN PRODUCT DECISION, inherited from `06` §4]:** whether the screenshot-based Fixed-Schedule import (`06` §4) uses this same Review screen, a lighter-weight variant, or no review step at all is unresolved — see `06` §4 for the full open question. This document does not resolve it independently.

## 5. "What Changed" — Explanation UI

**[EXPLICIT]** — cited by `04` (via `05` §7's cross-reference), `05` §7 and §8, `09` §4, `10` §4's consumer table ("Renders recent `atlas`-actor events in plain language using their `reason` field"), and Jira `UI-004` ("Rescheduling explanation / 'what changed' UI") acceptance criteria: "Collaborative/Critical-tier changes show their real reason inline; Autonomous-tier changes are queryable on request, not surfaced proactively," depending on `EVT-004` ("what changed" query API), `RESC-006` (tier engine), `SCH-015` (placement explainability).

**Two distinct surfacing behaviors, deliberately asymmetric [EXPLICIT from the acceptance criteria's own wording]:**
- **Collaborative and Critical-tier changes** — shown **inline, proactively**, with their real Event Log `reason` string (`10` §2), at the point the person would naturally encounter the affected block. This matches `05` §7's tier definitions: these are changes Atlas either proposed and waited on, or required the user's involvement for — the person already knows something happened and the explanation should be right there, not buried behind a tap.
- **Autonomous-tier changes** — **queryable on request only**, via `GET /schedule/blocks/{id}/explanation` (`12` §6, already wired to `10` §4's `reason` field). Not surfaced proactively. This is the direct UI expression of `05` §7's design intent: an Autonomous change is "silent, logged, explainable on request" — logged and explainable are not the same as announced. Surfacing every Autonomous move proactively would itself be a cognitive-disruption regression (effectively nagging about single-Flexible-item, same-day moves that the person never needed to be interrupted about).

**Undo affordance [STRONGLY INFERRED from `10` §4's compensating-event model]:** wherever a "what changed" explanation is shown, an Undo action is available alongside it, calling `POST /events/undo` (`12` §11). Because Undo generates a new compensating event rather than rewriting history (`10` §4), the UI must render the *current* net-effect state, not the originally-placed state, after Undo — this document does not invent new undo semantics, it only requires the rendering to stay consistent with `10` §4's model.

## 6. Critical and Collaborative Conversation UI

**[STRONGLY INFERRED]** — no existing document cites a `14` section number for this specifically, but the review brief's explicit instruction to cover "critical conversation" and "collaborative/autonomous distinctions" as required UI topics, combined with fully-specified behavioral content in `05` §7 and `05` §5 that has no other UI section to live in, makes this a required section. Placed at §6 since §1–5 and §7 are all pinned by existing external citations and this is the one unclaimed slot.

**Critical-tier conversation [EXPLICIT content, from `05` §7 + §5]:** a Critical-tier event (Goal transitions to At Risk for the first time; a Pause/Abandon proposal; no valid schedule exists; a user instruction conflicts with a hard constraint) requires the user's involvement before Atlas proceeds — "silence = 'no action yet,' not consent" (`05` §7). For the At-Risk case specifically, `05` §5 already specifies the exact five options to present: increase effort, extend deadline, reduce scope, change method, defer/pause. This UI surface presents those options as an active choice, not a dismissible notification — and per `05` §5, "if the user does not respond, Atlas continues producing the best practical schedule with available data while keeping the At Risk flag visibly active (never hidden, never silently resolved)" — meaning this conversation, once opened, does not silently disappear if ignored; the At-Risk indicator persists elsewhere (the Today view, per §2 above) until addressed.

**Collaborative-tier conversation [EXPLICIT content, from `05` §7]:** "Atlas proposes and waits, without blocking the rest of the schedule." Unlike Critical tier, a Collaborative proposal (a Deferred Backlog Review per `05` §8, a Recurring Intention target recalibration per `05` §4, a pattern-detection observation per `05` §6) is presented as an optional, dismissible suggestion that does not gate anything else the person is doing — this is the concrete difference the UI must preserve: Critical blocks meaningful progress until addressed (or explicitly deferred, in the At-Risk case above); Collaborative never blocks anything.

## 7. Notifications

**[EXPLICIT]** — Master Spec §1.20: "Governing rule: notify only when the information should change what the user does next — never merely because something happened. A single pre-block reminder is appropriate; a nagging 'you still haven't started' sequence is not. A goal crossing into At Risk is notify-worthy (Critical tier); a goal losing a few points of pace is not." Jira `UI-005` acceptance criteria: "Pre-block reminder fires; At-Risk transition notifies (Critical); Collaborative-tier proposals notify; excluded patterns (repeated nagging, Autonomous-tier changes, minor pace fluctuation) never fire — tested," depending on `RESC-006`, `RESC-008`, `RESC-009`.

**Fires [EXPLICIT]:**
- A single pre-block reminder (one, not a sequence).
- A Goal's first transition to At Risk (Critical tier).
- A Collaborative-tier proposal becoming available (Deferred Backlog Review, pattern observation, target recalibration suggestion).

**Never fires [EXPLICIT, `09` §4's cross-reference to this section confirms the same philosophy governs overrun handling: "never repeated/nagging follow-ups"]:**
- Repeated nagging sequences of any kind (the overrun prompt in `09` §4 is itself a single non-blocking prompt, never repeated, consistent with this rule rather than a separate exception to it).
- Autonomous-tier changes (per §5 above, these are logged and explainable-on-request, not notification-worthy — surfacing them as notifications would contradict `05` §7's own definition of what makes a change Autonomous in the first place).
- Minor pace fluctuation that hasn't crossed into an actual At-Risk transition (per Master Spec §1.20's own example: "a goal losing a few points of pace is not" notify-worthy).

**This is the same filter `05` §8 already invokes by name** ("consistent with the notification-relevance rule, `14` §7") for the Deferred Backlog Review case: a running deferred count is not itself notify-worthy, but the periodic batched review proposal is — this document does not add a second, different notification rule; it is the single filter every other document's notification-adjacent behavior already assumes exists here.

## 8. Genuine Gaps / Requires Product Decision

1. **Screenshot-import Review screen (§4)** — inherited from `06` §4's open product decision; not resolved independently here.
2. **"Hard day" trigger condition for the Minimum Viable Day banner (§2)** — left as an implementation detail (any Optional-tier or Deferred item that day) rather than a fixed threshold, consistent with how `05` treats similar thresholds (deferred-backlog volume, At-Risk cutoff) as tunable rather than product-locked. Not blocking.
