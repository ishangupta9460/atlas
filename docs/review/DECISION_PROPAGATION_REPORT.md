# Decision Propagation Report — Final Product Decisions #1–3

## 1. Decision 1 — Screenshot Fixed-Commitment Import (Review/Approve)

- **Documents changed:** `06` §4 (resolved, pipeline rewritten), `12` §5 (propose→review→approve/reject endpoint shape), `13` §1 (new `fixed_commitment_import_candidates` staging table + index), `14` §4 and §8 (lightweight review-screen variant resolved, gap closed), `02` §1.11 (creation-path invariant), `04` §2.1 (candidate invisibility to Stage 0 — no engine change required, clarified only), `05` §1 (trigger now requires *approved* Fixed Commitments), `03` (`ROAD-006` status), `19` (gap table item 11), `16` (contract + end-to-end test coverage).
- **Jira stories affected:** `ROAD-006` (acceptance-criteria correction — see `JIRA_CHANGE_PROPOSALS.md`). No new story required.
- **API impact:** `POST /fixed-commitments/import` now returns a proposal, not committed entities. Four new/changed routes: `GET/PATCH .../import/{importId}`, `POST .../approve`, `POST .../reject`.
- **Database impact:** one new table (`fixed_commitment_import_candidates`), one new supporting index.
- **Events:** three new lifecycle events (`fixed_commitment_import.reviewed/approved/rejected`) reusing the existing entity's downstream event pattern once committed — no proliferation into `task.*`/`block.*` types.
- **Tests:** contract tests for edit-after-approve/reject rejection; end-to-end coverage for all four minimum test cases from the decision brief (candidates inactive before approval, edits reflected, rejection creates nothing, scheduling ignores pending candidates).

## 2. Decision 2 — Repeated Manual Task Movement

- **Documents changed:** `08` §5 (fully resolved to option (b), rationale for rejecting option (c)), `08` §6 (gap closed), `04` §3 (Problem B now also consumes confirmed Memory-tier preferences, kept distinct from Analytics-sourced values), `05` §1/§6/§7 (cross-references distinguishing this from non-completion pattern detection; tier table updated), `11` §6 (confirms resolution, Analytics role unaffected), `14` §6 (Collaborative-tier prompt UI), `10` (two new reused-pattern events), `03` (`MEM-003` clarified, gap list item 2), `19` (gap table item 12), `16` (end-to-end test coverage).
- **Jira stories affected:** `MEM-003` (no scope change, clarified). New story `MEM-005` proposed.
- **API impact:** none new — reuses the existing single `POST /preferences` write path (`12` §10) unchanged.
- **Database impact:** none — reuses the existing `user_preferences` table (`02` §1.10, `13`); no new table or field.
- **Events:** two new reused-pattern events (`preference.observation_surfaced`, `preference.declined`); confirmed "Yes" reuses whatever generic preference-creation event already exists.
- **Tests:** end-to-end coverage for all six minimum test cases from the decision brief (single move insufficient, repeated pattern surfaces observation, observation itself doesn't change scheduling, decline saves nothing/no flag, confirmation saves via the one write path, temporary disruption doesn't falsely trigger).

## 3. Decision 3 — Task Cancellation + Dependency Impact Review

- **Documents changed:** `02` §2.3 (no-auto-cascade invariant), `05` (new §10, renumbered old §10→§11 with no external citation broken), `04` §2.5 (traversal reuse noted), `12` §4 (new cancellation endpoints — described; full route table deferred to the new story's own implementation), `13` (new indexes on `commitment_dependency`), `14` (new §6.1, full UI flow matching the brief's worked example), `10` (event reuse via `payload.trigger` rather than a new type), `03` §4 (gap list items 1 and 3 resolved), `19` (gap table item 13), `16` (state-machine + determinism + end-to-end test coverage).
- **Jira stories affected:** none existing (confirmed zero "cancel" stories pre-existed). New story `DOM-008` proposed.
- **API impact:** new cancellation-preview and cancel-with-selection endpoints on the Commitments resource (`12` §4).
- **Database impact:** no new tables — `commitment_dependency` and the `Cancelled` Work State/Scheduled-Block-state already existed; two new indexes added to support the impact-preview query path.
- **Events:** no new event type — `task.cancelled` reused for every cancelled item (primary and cascade-selected dependents alike), distinguished by a `payload.trigger` field; "remaining dependency problems" is a read-only computation, not an event.
- **Tests:** state-machine coverage (unselected dependents never mutated), determinism/property coverage (traversal never writes), end-to-end coverage for all six minimum test cases from the decision brief.

## 4. Cross-Document Conflicts Found

1. `12` §5's prior direct-commit assumption for screenshot import directly contradicted `06` §4's now-resolved Review/Approve requirement — `12` §5 had been written before `06` existed to consult and reasoned only from the "simple factual extraction" half of the argument `06` §4 originally posed both sides of.
2. `08` §5's prior framing left options (b) and (c) genuinely open, and `11` §6 had provisionally described what its own role would be *if* (c) were chosen — a latent inconsistency that would have surfaced only once (c) was rejected, which this pass did before it could cause drift.
3. `14` §4's Review-screen section and `14` §8's gap list both inherited `06` §4's open question without independently resolving it — consistent with each other, but both needed updating together once §4 resolved, or `14` §8 would have kept listing a gap that no longer existed.
4. No conflict was found between `04`'s Scheduling Engine and any of the three decisions' new behavior — in every case (candidate invisibility, confirmed-preference scoring input, dependency-traversal reuse), the existing engine design already had the correct hook point, requiring clarification rather than a design change.

## 5. Conflicts Resolved

All four items in §4 above were resolved in the edits described in §1–3: `12` §5 rewritten to match `06` §4; `11` §6 rewritten to confirm the (b)/not-(c) resolution rather than hedge on it; `14` §4/§8 both updated together; no `04` change was needed beyond clarifying cross-references, so no conflict existed there to resolve.

## 6. Remaining Implementation-Level Questions

These are deliberately left as configuration/implementation choices, not silently invented product rules, per each decision's own instruction:
- The exact multi-week count and frequency ratio for Decision #2's evidentiary threshold (`08` §5) — shape fixed, number configurable, matching the existing pattern for At-Risk and confidence-band thresholds elsewhere in the package.
- The exact bounded-traversal depth/count for both the pre-existing Stage 5 dependency lookahead and its reuse in Decision #3's cancellation-impact preview (`04` §2.5) — already configurable before this pass; unchanged by it.
- Whether a declined repeated-move observation (Decision #2) may resurface later if the pattern continues, and after how long — the decision brief requires that resurfacing never constitute punishment, but does not specify a cooldown; left as an implementation detail consistent with that constraint.

## 7. Final Specification Readiness Assessment

All three Final Product Decisions are fully propagated: every document each decision's own review list named has been checked and updated where the decision required a change, no contradictory statement was found remaining anywhere in a final consistency grep pass (`screenshot import`, `Review`/`Approve`, `manual move`, `preference`, `cancel`/`cancelled`, `dependency`/`dependent`, `Deferred`, `Scheduled Block`, `task.cancelled`), and `19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md` §2/§6 have been updated to reflect all three as resolved rather than open. Two new Jira stories (`MEM-005`, `DOM-008`) are proposed but not created, per instruction. No product-level specification gap remains blocking; the next step, per `19` §6's own updated current-status note, is tooling/platform selection for implementation — outside this pass's scope.
