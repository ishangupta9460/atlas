# Jira Change Proposals — Final Product Decisions Propagation

Per instruction, Jira is not modified directly. This document lists every proposed change; a human applies them in Jira.

## Decision #1 — Screenshot Fixed-Commitment Import (Review/Approve)

### `ROAD-006` — Screenshot fixed-schedule import

- **Existing behavior (acceptance criteria):** "OCR/parse → Fixed Commitment entities" (implies direct commit from parsed output).
- **Required behavior:** OCR/parse → candidates (proposal state) → user review/edit → explicit approve → Fixed Commitment entities. Reject/discard produces no entities.
- **Exact proposed acceptance-criteria change:**
  > OCR/parse produces Fixed Commitment **candidates**, not committed entities. User reviews and may edit each candidate (title/day/time) or remove it. Candidates only become active Fixed Commitments on explicit Approve. Reject discards the import with no created entities and no scheduling constraint of any kind.
- **Dependency changes:** none — still depends only on `DOM-006`.
- **New story required?** No — this is an acceptance-criteria correction to an existing story, not new scope.
- **Reason:** Final Product Decision #1 resolves what `06` §4 had recorded as an open product decision; `12` §5, `13` §1, `14` §4, and `02` §1.11 have been updated to match.

## Decision #2 — Repeated Manual Task Movement

### `MEM-003` — Contextual preference application

- **Existing behavior:** unchanged — this story's own scope (contextual application of an already-confirmed preference) is unaffected.
- **Required behavior:** unchanged.
- **Exact proposed acceptance-criteria change:** none.
- **Dependency changes:** none.
- **New story required?** No change to this story. See `MEM-005` below for the new, adjacent behavior.
- **Reason:** documented here only to confirm `MEM-003` does not absorb Decision #2's scope — the repeated-move observation is a distinct write-trigger, not a new consumer of `MEM-003`'s existing contextual-application mechanism.

### `MEM-005` (NEW) — Repeated-manual-move preference observation

- **Existing behavior:** does not exist.
- **Required behavior:** detect a repeated manual-relocation pattern for the same recurring/repeating item (reusing `RESC-011`'s multi-week, meaningful-frequency-ratio evidentiary standard); surface a Collaborative-tier, dismissible observation ("You've been moving this later. Change its default timing?"); on "Yes," persist via the existing `POST /preferences` write path (`MEM-001`); on "Not now"/dismiss, persist nothing and set no negative flag.
- **Exact proposed acceptance-criteria:**
  > Given a repeated manual-move pattern meeting the reused multi-week/frequency-ratio evidentiary bar (`08` §5, `05` §6), a Collaborative-tier observation prompt is surfaced with Yes/Not now actions. Yes calls `POST /preferences` and persists a `scheduling`-domain preference. Not now/dismiss persists nothing and creates no queryable negative marker. A single manual move never triggers the prompt. A short-lived (single-week) disruption never triggers the prompt.
- **Dependencies:** `MEM-001` (write path), `RESC-011` (pattern-detection evidentiary machinery, reused not duplicated).
- **New story required?** Yes.
- **Reason:** Final Product Decision #2 resolves `08` §5's prior open question to option (b); no existing story covers this behavior (`03` §4 confirmed zero prior coverage).

## Decision #3 — Task Cancellation + Dependency Impact Review

### `DOM-008` (NEW) — Task cancellation & dependency-impact review

- **Existing behavior:** does not exist — `03` §4 confirmed zero Jira stories implement cancellation at all (grep for "cancel" across the 110-row backlog returns no matches).
- **Required behavior:** cancelling a Commitment with dependents (`commitment_dependency`) never auto-cascades. The user is shown direct dependents as selectable checkboxes (pre-unchecked), plus a bounded summary of further downstream impact for longer chains. Only explicitly-selected dependents are cancelled alongside the primary task; unselected dependents are left unmodified. Remaining dependency problems (an unselected dependent still blocked by another item) are surfaced at confirmation time. Cancelled ≠ deleted — history is preserved.
- **Exact proposed acceptance criteria:**
  > Cancelling a Commitment with no dependents cancels immediately with a simple confirm. Cancelling one with dependents shows direct dependents as pre-unchecked selectable items; user selects which (if any) to also cancel; only selected items are cancelled; unselected items are untouched; remaining dependency problems on unselected items are surfaced; long chains show a bounded downstream-impact summary, never an unbounded graph; cancelled Commitments/Scheduled Blocks remain in history (`10`, `13` §3) and are never hard-deleted.
- **Dependencies:** `DOM-003` (Commitment), `DOM-007` (Dependency modeling).
- **New story required?** Yes.
- **Reason:** Final Product Decision #3 resolves the cancellation-cascade question `ATLAS_SPECIFICATION_REVIEW.md` §5.2 and `03` §4 item 3 had left open; `02` §2.3, `05` §10, `12` §4, `13` (index only), `14` §6.1 have been updated to specify the full behavior this story implements.

## Stories Requiring No Change

Confirmed by re-reading each against the three resolved decisions:
- `SCH-001`–`SCH-015` (Scheduling Engine) — Decision #2's confirmed preference feeds Problem B (`SCH-012`) as an additional input source, not a new scoring dimension or story; no acceptance-criteria change needed since `SCH-012`'s criteria are already dimension-agnostic ("candidate-slot scoring," not enumerating every input source).
- `RESC-001`–`RESC-013` (Recovery) — `05` §10's cancellation-impact behavior deliberately reuses `04` §2.5's existing dependency traversal rather than introducing new Recovery-owned logic; no `RESC-*` story's scope changes.
- `AI-001`–`AI-012` (AI Architecture) — none of the three decisions introduces a new AI proposal type or changes an existing one's shape.
- `EVT-001`–`EVT-004` (Event Log) — the new events introduced (`fixed_commitment_import.*`, `preference.observation_surfaced`, `preference.declined`) reuse the existing schema and write path `EVT-001`/`EVT-002` already define; no schema change needed.
- `UI-001`–`UI-007` — none of the three decisions changes the Today/Now/Next/Later, Calendar, or drag/move stories' scope; the new UI surfaces (screenshot-import review variant, repeated-move prompt, cancellation-impact screen) are covered by `ROAD-002` (reused pattern), `MEM-005` (new), and `DOM-008` (new) respectively.
