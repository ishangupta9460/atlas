# 09 — Execution & Focus System

**Authoritative for:** the Scheduled Block → Actual Session execution flow, Focus Mode controls, overrun handling, and the forgotten-timer case.
**Source:** Master Spec §1.14.
**Entity definitions owned by:** `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` §1.6–1.7 (Scheduled Block, Actual Session) — not restated here.

---

## 1. Session Lifecycle

`Not Started → Running → Paused ⇄ Running → Finished`

| Current | Trigger | Result | Side effect |
|---|---|---|---|
| Not Started | User presses Play on a Scheduled Block | Running | Actual Session created, actual_start recorded |
| Running | User presses Pause | Paused | elapsed time frozen |
| Paused | User presses Resume | Running | — |
| Running/Paused | User presses Finish | Finished | actual_end recorded; completion report prompt shown |

## 2. Pre-Session: Task Brief

Before Start, the block displays: what to accomplish, attached Resources (`06_ROADMAP_AND_RESOURCE_SYSTEM.md` §3), and the completion_criterion field (`02` §1.4) — "what counts as done for this block," set at task creation, not invented at session time.

## 3. During Session

Elapsed time display, Pause/Resume, quick-note capture (stored on the eventual Actual Session record).

## 4. Overrun Handling

No interruption during a grace window of **5 minutes** past the block's scheduled end (resolved default — kept short deliberately, since Atlas also needs to protect the start of the next block rather than let overruns compound). After the grace window, a single non-blocking prompt: "you're N minutes over — next block starts at [time] — continue, finish now, or push the next block?" Never a bare cutoff message; never repeated/nagging follow-ups (Master Spec §1.14, and consistent with the notification philosophy in `14_UI_UX_SPECIFICATION.md` §7).

## 5. Post-Session: Completion Report

Free-form user report of what was actually accomplished — trusted per Master Spec §1.2 principle 3, not verified by a quiz. Feeds the Commitment's `current_completion_pct` belief-state field (`02` §1.7) and, if partial, triggers `task.partial` (`02` §2.3), which re-enters `05_RESCHEDULING_AND_RECOVERY.md`'s recovery flow for the remaining work.

## 6. Forgotten Timer (Block Ended, No Session Started)

Per Master Spec §1.14: Atlas does not assume failure. The user is later given the opportunity to report what actually happened (completed / partial / skipped). If nothing is reported, the block eventually transitions to `block.unresolved` (`02` §2.4) and enters the recovery flow (`05` §1).

## 7. Scheduled Block vs. Actual Session — Explicit Separation

A Scheduled Block is Atlas's intention for the calendar slot; an Actual Session is the immutable record of what really happened, created only once a session starts. This separation is defined authoritatively in `02` §1.6–1.7 — this document only describes the runtime interaction between them, it does not redefine the entities.

## 8. Genuine Gaps / Requires Product Decision

*(Resolved — see §4 above: 5-minute grace window.)*
