# Reconstruction Report — 07, 09, 18

Scope: recover `07_AI_ARCHITECTURE.md`, `09_EXECUTION_AND_FOCUS.md`, and `18_IMPLEMENTATION_PLAN_AND_DEFINITION_OF_DONE.md`, none of which were present in the uploaded `Project_Documentations` package, from cross-references in the 16 documents that *were* present plus `Jira.csv`.

## 1. Evidence Used

- All 16 present numbered documents (`01`–`06`, `08`, `10`–`17`, `19`) — grepped exhaustively for every citation to `07`, `09`, and `18` by section/phase number.
- `ATLAS_SPECIFICATION_REVIEW.md` and `REVIEW_CHANGELOG.md` — both explicitly state they *did* review `07`, `09`, and `18` (unlike the 8 documents reconstructed in the prior pass, which those two files explicitly flag as unreviewed). This means `07`/`09`/`18` are attested to have existed and been internally sound at that point in the project's history, even though no copy of them survived into this upload — a materially stronger evidence position than a document that was never reviewed at all.
- `Jira.csv` — all 91 stories with an `Original key`, parsed programmatically for `Source` and `Dependencies` fields, to reconstruct both content (§3 of each document) and sequencing (`18` §1–2).
- `MISSING_SPEC_RECONSTRUCTION_REPORT.md` — itself cites `07`, `09`, and `18` repeatedly while reconstructing the other 8 documents; those citations were cross-checked for consistency with what this pass reconstructs.
- `DECISION_LOG.md` — not present in the uploaded package or prior context; no content from it was available to use. Flagged as a gap below (§6).
- `atlas-master-product-spec-v1.md` — not present as a separate file in this session's context; Master Spec content was instead drawn from the specific Master Spec section numbers already quoted verbatim inside the 16 present documents (e.g., §1.1, §1.5, §1.9, §1.19, §1.23, §3 rule numbers). No Master Spec section was invented; every Master Spec citation in the three new documents traces to a quotation already present in an existing document.

## 2. Cross-References Discovered

A full grep of every present document for `` `07` ``, `` `09` ``, `` `18` `` (by exact backtick-quoted citation, to avoid false positives from prose mentions) returned:
- **~45 distinct citations to `07`**, spanning §1 through §8 — every section number in the reconstructed document has at least one prior citation anchoring it, except §9 and §10, which were added as synthesis/gap sections with no citation to conflict with.
- **~15 distinct citations to `09`**, spanning §1, §2, §4, §5, §6 — §3, §7, §8, §9 had no prior citation and were added to complete the natural flow (§8 was additionally required by `19`'s existing "`09` §4, §8" gap-table citation for the overrun grace-window value).
- **~20 distinct citations to `18`**, overwhelmingly to specific phase numbers (0, 2, 3, 4, 5, 6, 7) and to §1 and §3 specifically — §2 and §4 had no prior citation.

No citation was found anywhere in the package to a section number in any of the three documents that this reconstruction does not now define — i.e., no stale reference remains unresolved.

## 3. Jira Stories Used

`AI-001`–`AI-012` (07), `EXEC-001`–`EXEC-005` and `RESC-004` (09), and the full 91-story dependency graph including `FOUND-001`–`FOUND-003`, `OPS-001`–`OPS-005` (18, phase sequencing). Every story's `Source` field pointing at `07`, `09`, or `18` was checked against the reconstructed section it names; none required a section number this reconstruction doesn't provide.

## 4. Explicitly Reconstructed Requirements

Tagged `[EXPLICIT]` in the documents themselves. Highlights: `07`'s ten-row proposal-type table (§3) and its failure-handling table (§5, directly reused by `16` §6's existing test matrix, confirming the reconstruction is consistent with content that already existed downstream of it); `09`'s Focus Session lifecycle (§1), Task Brief (§2), and the 5-minute overrun grace window (§4/§8, matching `19`'s pre-existing gap-table entry exactly); `18`'s Phase 0 walking skeleton and Definition of Done (§3), both quoted almost verbatim from language already present in `16` and `17`'s citations of them.

## 5. Strongly Inferred Requirements

Tagged `[STRONGLY INFERRED]`. The single most consequential inference is `18` §1's phase-boundary assignment: no document states which story lands in which phase, only which *phase* certain named stories or concepts belong to. This reconstruction computed the actual dependency graph from `Jira.csv` rather than trusting the correction brief's informal one-line summary, and found that summary needed one correction (AI, Phase 4, precedes Execution/Recovery, Phase 5 — see `18` §2). All three new documents' "no direct citation" sections (`07` §9–10, `09` §3/§7/§9, `18` §2/§4) are inferences necessary to make the cited sections coherent, not independently evidenced content.

## 6. Open / Unresolved Items

1. **`DECISION_LOG.md`** was named as available evidence in the correction brief but was not present in this session's context in any form. No content was drawn from it; if it contains information contradicting anything in this reconstruction, that would only be discoverable by providing it directly.
2. **`01` §5, §7's hosting/runtime target and AI in-process-vs-external-provider topology** remain open (pre-existing, not introduced by this pass) and are referenced but not resolved by `07` §6 or `18`.
3. **Filename discrepancy** — the correction brief requested `18_IMPLEMENTATION_PLAN_AND_DEFINITIONS.md`; every existing citation in the package points to `18_IMPLEMENTATION_PLAN_AND_DEFINITION_OF_DONE.md`. This reconstruction used the already-cited name to avoid breaking ~20 existing cross-references. Flagging explicitly in case the shorter filename was an intentional rename request rather than a shorthand.

## 7. Contradictions Found

None. Every specific claim already made about `07`/`09`/`18`'s content by a citing document (e.g., `06` §2's "Low confidence band per `07` §4's three-band model," `ATLAS_SPECIFICATION_REVIEW.md`'s Scenario A/B/C verdicts citing `09` §1/§5/§6, `03` §5's phase-ordering risk note citing `18`) was checked against the corresponding reconstructed section and found consistent without requiring any adjustment to the citing document.

## 8. Contradictions Resolved

One apparent tension was found and resolved during reconstruction, not a true contradiction on inspection: `ATLAS_SPECIFICATION_REVIEW.md`'s narrative description of the phase order ("Phase 0 walking skeleton → Phase 1 breadth → Phase 2 event log/scheduling") appears to disagree with a hard citation elsewhere to "Phase 2's exit criterion" being about domain breadth completing ("the same loop... now with the real hierarchy instead of a trivial placeholder"), not event-log/scheduling. Resolved in `18` §1 by splitting domain breadth across Phase 1 (begins) and Phase 2 (completes) — both citations describe true statements about the same two-phase span, and the dependency graph independently supports the split (Goal/Roadmap/Category/Recurring-Intention entities are a genuine prerequisite wave ahead of Commitment/Dependency).

## 9. Other Documents That May Need Follow-Up

- **`01_SYSTEM_ARCHITECTURE.md`** — its Genuine Gaps list (§7, per its own numbering) already correctly anticipated needing `07`'s existence to fully resolve the AI-topology question; no change needed now that `07` exists, since that question remains genuinely open per `07` §10.
- **`MISSING_SPEC_RECONSTRUCTION_REPORT.md`** — a historical record of the *prior* reconstruction pass (the 8 other documents). This pass did not edit it, consistent with the instruction not to recreate or rewrite documents outside this reconstruction's specific scope; a future editorial pass could append a pointer noting `07`/`09`/`18` have since also been reconstructed, but that is an optional consistency nicety, not a defect.
- **No Jira changes are required by this reconstruction itself** — `AI-*`, `EXEC-*`, and `RESC-004`'s existing acceptance criteria are all satisfied by the sections now defined; `FOUND-*` and `OPS-*` likewise. The two *new* stories introduced by the earlier Final Product Decisions propagation pass (`DOM-008`, `MEM-005` — see `JIRA_CHANGE_PROPOSALS.md`) are correctly sequenced into this document's Phase 3 and Phase 6 respectively.

## 10. Confidence / Readiness of Each Reconstructed Document

| Document | Confidence | Readiness |
|---|---|---|
| `07_AI_ARCHITECTURE.md` | High — every section anchored to at least one prior hard citation; the proposal-type table's field shapes are the only material the evidence trail underspecifies field-by-field (payload field *names* are `[STRONGLY INFERRED]`, not `[EXPLICIT]`, though the *existence and purpose* of each type is `[EXPLICIT]`). | Implementation-ready; the AI-topology question (§10) is the only open item, and it's explicitly low-stakes. |
| `09_EXECUTION_AND_FOCUS.md` | High — this was the smallest, most self-contained of the three (`ATLAS_SPECIFICATION_REVIEW.md` independently scored it 90% with no defects before this reconstruction, which reconstruction did not need to contradict). | Implementation-ready; no open items. |
| `18_IMPLEMENTATION_PLAN_AND_DEFINITION_OF_DONE.md` | Medium-High — the Definition of Done (§3) is `[EXPLICIT]` almost verbatim; the phase-boundary assignment (§1) is the one substantial `[STRONGLY INFERRED]` construction in this entire reconstruction effort, since no document states story-to-phase membership directly. Verified rather than merely asserted, via §2's topological method. | Implementation-ready for sequencing purposes; sprint-level calendar mapping is explicitly out of scope (§5 gap item 1). |
