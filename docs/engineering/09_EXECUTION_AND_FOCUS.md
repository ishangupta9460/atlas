# 09 — Execution & Focus

**Status: RECONSTRUCTED.** This document was not present in the uploaded package, despite being cited by `01` §1 (Execution layer entry: "Scheduled Block → Actual Session runtime flow, Focus Mode, overrun handling"), `02` §2.6, `03` (`EXEC-001`–`EXEC-005`, `RESC-004`), `05` §1/§3 (recovery triggers originating here), `12` (session endpoints), `14` §1 (Task Brief rendering), and Jira epic `Execution` (`EXEC-001`–`EXEC-005`) plus `RESC-004`. Section numbers below (`§1`, `§2`, `§4`, `§5`, `§6`, `§8`) are fixed by those existing citations — do not renumber. `§3`, `§7`, and `§9` have no prior citation and are placed here to complete the natural flow without displacing any cited number.

**Depends on:** `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` §2.6 (Focus Session state values, referenced not redefined there), `04_SCHEDULING_ENGINE.md` (a session executes against a Scheduled Block this document does not itself create), `06_ROADMAP_AND_RESOURCE_SYSTEM.md` §3 (attached Resources shown in the Task Brief).

**Boundary this document owns, and does not cross (per the correction brief):** `09` = what happens *during* execution and its immediate reporting. `05_RESCHEDULING_AND_RECOVERY.md` = what Atlas does *after*, when the resulting missed/partial/interrupted state requires recovery. This document ends at the moment a session's outcome is recorded; `05` picks up from there. Nothing below duplicates or contradicts a `05` mechanism — where a `05` section already owns a behavior (e.g., the recovery-trigger event itself), this document only produces the input `05` consumes.

## 1. Focus Session Lifecycle

**[EXPLICIT]** — `EXEC-001`'s acceptance criteria: "Start/Pause/Resume/Finish state machine," sourced to `` `09` §1, `02` §2.6 ``, depending on `UI-001` (Today screen) and `SCH-015` (placement explainability, so a session always starts against a Scheduled Block whose placement is already explainable).

A Focus Session tracks the *actual* runtime execution of one Scheduled Block (`02` §1.6). Its states, matching `02` §2.6's own reference ("Not Started → Running → Paused ⇄ Running → Finished"):

| State | Entered by | Meaning |
|---|---|---|
| Not Started | Session created (implicitly, when a Scheduled Block's start time arrives, or explicitly via a user "Start" action) | Block is Now/Next but no execution has begun |
| Running | User taps Start, or Resume from Paused | Active work is underway; this is the only state in which elapsed time accrues toward the session's actual duration |
| Paused | User taps Pause while Running | Elapsed time stops accruing; the session is not abandoned — this state exists precisely to cover realistic interruptions (a phone call, a knock at the door) without treating the user as having failed the block |
| Finished | User taps Finish, or the overrun grace window (§4) elapses without a Finish/extend action | Terminal; triggers the completion report (§5) |

**Multiple Pause⇄Running cycles are expected, not an edge case** — the state machine explicitly supports "9:00–10:00 research block → interruption at 9:10 → user returns later" without any special-casing: the session simply sits Paused for however long the interruption lasts, then Resumes. No maximum pause count or pause duration is enforced by this state machine itself (an extremely long pause is instead covered by the Forgotten Timer flow, §6, once it's clear the user isn't coming back to that session in any meaningful sense).

**Undocumented transitions are rejected**, consistent with `16` §5's state-machine testing principle applied here: a session cannot go directly from Not Started to Finished (skipping Running entirely) except via the Forgotten Timer's retroactive report path (§6), which is a distinct, explicitly-logged path, not a silent shortcut through this table.

## 2. Task Brief

**[EXPLICIT]** — `EXEC-002`'s acceptance criteria: "Shows `completion_criterion`, resources before Start," sourced to `09` §2, depending on `EXEC-001`. Cross-cited by `14` §1: "Opening it shows the Task Brief (`09` §2: what to accomplish, attached Resources, the `completion_criterion` field) — 'an executable piece of the roadmap, not a vague title.'"

Before a session enters Running for the first time, the Task Brief is shown:
- **Title** and the Commitment's `completion_criterion` (`02` §1.4, free text per Master Spec §1.19 — e.g., "implement one GET endpoint," never a vague restatement of the title).
- **Attached Resources** (`06` §3) relevant to this Commitment, so the user has what they need before starting rather than discovering a missing link mid-session.
- **Any `is_hard_consequence` context** (`02` §1.4) the user has previously confirmed, shown as a plain-language note (not a warning banner) if it exists, since it's already-known context rather than a new decision point at this moment.

The Task Brief is shown again (or remains visible) through Running/Paused — it is not a one-time interstitial the user must dismiss and can't return to; a session interrupted at 9:10 and resumed later should not force the user to somehow reconstruct what they were doing.

## 3. In-Session State & Interruption Handling

**[STRONGLY INFERRED]** — no document cites `09` §3 by number, so this section introduces no citation conflict; its content is a direct, necessary elaboration of §1's Pause/Resume states and is required to make `ATLAS_SPECIFICATION_REVIEW.md`'s Scenario A verdict ("Handled adequately: Pause/Resume (`09` §1) plus free-form trusted completion report (`09` §5) cover this without requiring the user to justify the gap. No fix needed.") concretely implementable.

While Running or Paused, the session tracks: elapsed active time (accrues only while Running, per §1), a running log of Pause/Resume timestamps (feeding the Actual Session record, §5), and nothing else that requires user input mid-session — no progress checkbox, no interim status prompt. **The user is never asked to justify a pause or explain an interruption while it's happening** — this is the concrete mechanism behind the review's "without requiring the user to justify the gap" verdict: justification, if any, happens once, at Finish, via the free-form completion report (§5), never as a running interrogation.

## 4. Overrun Handling — RESOLVED

**[EXPLICIT, resolved]** — `EXEC-003`'s acceptance criteria: "5-min grace, single non-blocking prompt," sourced to `` `09` §4 (resolved) ``. This is also the section `19` gap item 8 cites for the grace-window duration, resolved jointly with §8 below.

When a Scheduled Block's planned end time passes while the session is still Running: a **5-minute grace window** (§8) begins during which nothing changes — the session continues exactly as if still within its planned window, since a 5-minute overrun is common and not worth interrupting anyone over. If the session is still Running when the grace window elapses, Atlas surfaces **one single, non-blocking prompt** ("Still working on this? Wrap up or keep going") — never a repeated or escalating nagging sequence (`14` §7's notification-relevance rule explicitly cites this section as sharing its "never repeated/nagging follow-ups" philosophy). The prompt does not pause the session, does not force a decision, and does not fire again for the same session if ignored — the user can simply keep working, and the next relevant signal is whatever happens at Finish (§5) or, if the session is abandoned without Finish, the Forgotten Timer flow (§6).

**Why 5 minutes, and why deliberately short (§8, resolved):** the grace window is intentionally brief because an overrunning session's cost isn't only to itself — it's to whatever is scheduled immediately after it. A short grace window protects the next block's start time from silently eroding session after session, while still being long enough to absorb a genuinely brief wrap-up.

## 5. Completion Report & Belief-State Update

**[EXPLICIT]** — `EXEC-004`'s acceptance criteria: "Free-form report; updates `current_completion_pct` without touching session history," sourced to `` `09` §5, `02` §1.7 ``. Cross-cited by `ATLAS_SPECIFICATION_REVIEW.md` Scenario C: "free-form completion report feeds `current_completion_pct` (belief state, not rewriting the Actual Session record) and triggers `task.partial` → recovery for the remainder (`09` §5, `02` §2.3)."

On Finish, the user gives a **free-form completion report** — not a rigid form, not a required percentage slider, not a justification demand. This report updates `Commitment.current_completion_pct` (`02` §1.7 — the Domain layer's belief about how done the work is) as a distinct write from the Actual Session record itself:

- **The Actual Session record (start/pause/resume/finish timestamps, elapsed active time) is immutable once Finish occurs** — this is `EXEC-004`'s own "without touching session history" requirement, and it is the same append-only discipline the Event Log (`10` §3–4) applies elsewhere: what actually happened, second by second, is a historical fact that a later belief update must never rewrite.
- **`current_completion_pct` is a belief, not a fact-of-record** — it can be revised by a later session's report on the same Commitment without that revision implying the earlier session's history was wrong. A user who reports "50% done" after one session and later realizes it was closer to 30% is not correcting a data-entry error; they're updating Atlas's current belief, and both sessions' immutable records remain exactly as they were.
- **If the report indicates the work is not fully complete,** this is not treated as failure — it generates the appropriate Work State transition (`02` §2.3: `Completed` if 100%, or a partial-completion signal that hands off to Recovery, §7 below) and the Event Log records `task.progress_changed` (`10` §2) rather than any judgmental framing. This matches `11` §5's "mirror, not a judge" tone constraint applied here at the point of report, not just in later analytics.

## 6. Forgotten Timer Handling

**[EXPLICIT]** — `RESC-004`'s acceptance criteria: "User can report what happened after the fact," sourced to `09` §6, depending on `RESC-001` (missed block detection, `05` §1).

If a session is left Running or Not Started well past its Scheduled Block's window with no Pause/Finish action at all — the timer was, in effect, forgotten — Atlas does not assume failure and does not silently mark the block missed without recourse. Once `05` §1's missed-block detection identifies this state, the user is offered a **retroactive report**: the same free-form completion-report mechanism as §5, but filed after the fact rather than at a live Finish action. This is the concrete answer to Scenario B in `ATLAS_SPECIFICATION_REVIEW.md`: "`09` §6 forgotten-timer flow → `block.unresolved` → `05` §1 recovery. Atlas does not assume failure; the user can still report what happened." The retroactive report updates `current_completion_pct` exactly as a live report would (§5); the only difference is timing, not mechanism.

## 7. Handoff From Execution Into Recovery

**[STRONGLY INFERRED]** — no document cites `09` §7 by number, so this section introduces no conflict. Its content is required to make the "important boundary" the correction brief itself states (`09` = execution/reporting; `05` = recovery) concretely testable rather than merely asserted.

This document's responsibility ends, for any given session, at one of exactly three points, each of which becomes a `05`-owned input and nothing more:
- **Finish with 100% completion (§5):** Work State → `Completed` (`02` §2.3). No recovery handoff — there is nothing for `05` to recover.
- **Finish with partial completion (§5):** the remaining, uncompleted portion re-enters the normal scheduling candidate pool through `05` §... (Partial Completion Handling, `RESC-003`) exactly as any other not-yet-done work would — this document does not re-schedule it itself.
- **No Finish at all within a reasonable window (§6):** `05` §1's missed-block detection fires (`block.unresolved` or the equivalent Event Log signal, `10` §2), and everything from that point — Progressive Search, tier classification, notification — is `05`'s responsibility exclusively. This document's only remaining role is offering the retroactive report (§6) if and when the user returns.

This document never itself decides *where* a partial or missed item gets rescheduled to, never itself classifies an interruption as Autonomous/Collaborative/Critical, and never itself sends a recovery-related notification — all three are `05`'s and `14`'s responsibilities, cited there.

## 8. Resolved Configuration Values

**[RESOLVED]** — matching the same pattern as `07` §8 and `06` §2 (fix the shape in the owning section, keep the exact number here as a labeled resolved value rather than embedding it as an unlabeled magic number in §4's prose).

- **Overrun grace window: 5 minutes** (§4). Deliberately short, per §4's own rationale, to protect whatever is scheduled next rather than to be generous to the overrunning session.

## 9. Genuine Gaps / Requires Product Decision

None identified. Every `EXEC-*` story and `RESC-004` has a corresponding section above with an EXPLICIT or STRONGLY INFERRED classification, and `ATLAS_SPECIFICATION_REVIEW.md` independently scored this domain "90%... small, well-scoped, no defects found" prior to this reconstruction, which the reconstructed content above does not contradict.
