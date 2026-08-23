# 05 — Rescheduling & Recovery Engine

**Authoritative for:** missed/partial/forgotten work handling, overload resolution, Goal Risk/feasibility calculation, Minimum Viable Day derivation, and the Autonomous/Collaborative/Critical decision-tier boundary.
**Source:** Master Spec §1.16–1.19, §1.26.
**Depends on:** `04_SCHEDULING_ENGINE.md` (re-runs Stage 0–8 and consumes the capacity model — does not redefine either).

---

## 1. Recovery Trigger Events

A Commitment or Recurring Intention instance enters the recovery flow when any of: its Scheduled Block's time window passes with no Actual Session (`block.unresolved`, `02` §2.4), the user reports partial completion (`task.partial`, `02` §2.3), or an external event removes previously-available time (user reports "can't work 4–8 PM today").

## 2. Progressive Search

For a single recovery item, search outward in this fixed order, stopping at the first slot that is both open AND realistically suitable (not merely empty — respects working-hours/energy/category-fit signals from `04` §3):

1. Remaining suitable time **today**.
2. **Tomorrow**.
3. **Rest of the current week**.
4. If nothing fits: flag as unresolved and escalate per §7.

This is never skipped in favor of an immediate full-week re-optimization (Master Spec §1.16). Each candidate slot found is still evaluated through the full Stage 0–8 hierarchy (`04` §2) before being accepted — progressive search determines *where to look*, the hierarchy determines *whether the result is valid and how it ranks against competitors*.

## 3. Overload Resolution (Multiple Missed Items)

When several recovery items compete for the same limited recovered time and not all fit:
1. Run each item back through Stage 0–8 as if newly entering the candidate queue.
2. Protect the highest-ranked survivors.
3. Move the remainder to Planning State = **Deferred** (`02` §2.2) — not deleted, not silently repeated forever.
4. Escalate to the user (Critical or Collaborative tier per §7) only if a Stage 3/4-important item cannot be placed anywhere in the current week at all.

## 4. Recurring Intention Recovery

Recurring Intentions do not follow §2–3. Per Master Spec §1.16:
- Each week resets to the full `target_count_per_week` regardless of the prior week's completion — no missed-instance backlog.
- A goal-linked Recurring Intention's completion rate feeds that goal's Goal Risk calculation (§5).
- Independently, a sustained low completion rate (multi-week evidence — see §6) may generate a Collaborative-tier suggestion to recalibrate the target itself. This is a proposal, never an automatic change to `target_count_per_week`.

## 5. Goal Risk / Feasibility Calculation

**Formula inputs:** remaining work estimate (sum of incomplete Commitments' estimated effort under the Goal) vs. available capacity (from `04_SCHEDULING_ENGINE.md` §4, the same realistic-capacity figure, over the time remaining until target_deadline), adjusted by **context-specific** historical completion rate (per category and/or time-of-day — never one blended global percentage; Master Spec §1.17).

**Trigger (resolved):** At Risk is defined as a *formula*, not a fixed percentage. Atlas computes a projected completion probability from remaining work, capacity-adjusted available time, and context-specific historical completion rate; when that projected probability drops below a configured confidence threshold, the Goal Risk Snapshot (`02` §1.13) is recorded with `result = at_risk`. The threshold itself is an externalized, tunable configuration value — not hardcoded in business logic — defaulted conservatively at launch and revisited once real completion data exists to calibrate it properly. This keeps the *shape* of the rule fixed (formula-based, not a magic "20% behind") while leaving the *number* adjustable without a code change. The Goal's Planning State transitions per `02` §2.2 — always a **Critical-tier** event (§7).

**Presented options on At Risk:** increase effort, extend deadline, reduce scope, change method, defer/pause. If the user does not respond, Atlas continues producing the best practical schedule with available data while keeping the At Risk flag visibly active (never hidden, never silently resolved).

**Boundary condition:** an At-Risk goal's Stage 4 protective boost never outranks a genuine Stage 0–2 item (`04` §2). A goal may remain At Risk indefinitely if real hard-consequence deadlines keep legitimately winning contested time — this is correct, honest behavior, not a defect (Master Spec §1.17).

## 6. Pattern Detection

A behavioral pattern claim (e.g., "user completes ~30 of 120 planned minutes for evening sessions") requires:
- Evidence spanning **multiple separate weeks**, not consecutive days in one bad week.
- A meaningful **frequency ratio** (e.g., 8 of 10 relevant instances), not 3-for-3 in a short window.

Findings are surfaced as observations ("I've noticed...") and are Collaborative-tier (never silently applied to future scheduling defaults without confirmation).

## 7. Autonomous / Collaborative / Critical Decision Tiers

The boundary is measured by what a change touches, not judged ad hoc.

| Tier | Definition | Examples |
|---|---|---|
| **Autonomous** | Silent, logged, explainable on request | Moves exactly one Flexible-tier item, same day or next day only, touches nothing Protected/Fixed and no At-Risk goal's tasks |
| **Collaborative** | Atlas proposes and waits, without blocking the rest of the schedule | Touches >1 task or spans >1–2 days; affects an important or At-Risk goal's task; a genuine Stage 3/4-level tie (`04` §2.3 rule 5); a detected pattern (§6) suggesting a future default change |
| **Critical** | Must involve the user before proceeding; silence = "no action yet," not consent | Anything touching Fixed/Protected; a Goal's first transition to At Risk, or a Pause/Abandon proposal; no valid schedule exists at all; explicit user instruction directly conflicts with a hard constraint |

Every Autonomous and Collaborative/Critical decision writes an Event Log entry with a `reason` string (`10_EVENT_LOG.md` §2) — this is what powers "why did Atlas move this" (`14_UI_UX_SPECIFICATION.md` §5).

## 8. Deferred Backlog Review (Resolved)

When many low-priority items accumulate in Deferred state without any single Stage 3/4-important item being blocked, Atlas does not surface each deferral individually and does not silently delete any of them. Instead, once accumulated deferred work crosses a meaningful volume (implementation-tunable, e.g. count or age-based), Atlas periodically proposes a batched **Deferred Backlog Review** as a Collaborative-tier suggestion ("you've deferred 7 optional items over the last 3 weeks — some may no longer be worth keeping, review?"). This is consistent with the notification-relevance rule (`14_UI_UX_SPECIFICATION.md` §7) — a running deferred count is not itself notify-worthy, but a periodic batched check-in is.

## 9. Minimum Viable Day

Not a mechanical stage-number mapping. Atlas's own daily determination, informed by but not equal to the hierarchy:

- **Essential** — items Atlas determined must be protected today. Typically Stage 2 or Stage 4 survivors, but a Stage 3 item explicitly marked very important by the user can also qualify.
- **Good to do** — ordinary Stage 3/5/6 survivors that received a slot.
- **Optional** — items that only reached Stage 7/8 relevance or did not get placed. (Stage 7 alone is never sufficient to classify something as Optional — it is calibration-only, per `04` §2, and must not be read as a ranking signal here either.)

## 10. Genuine Gaps / Requires Product Decision

*(Both items previously listed here — the At-Risk threshold and the deferred-item overload behavior — are resolved. See §5 and §8 above respectively.)*
