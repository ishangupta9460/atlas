# MISSING_SPEC_RECONSTRUCTION_REPORT.md

Reconstruction record for the eight missing Atlas specification documents (`01`, `03`, `06`, `08`, `11`, `14`, `16`, `17`), performed as a follow-up to the September 2026 specification review (`ATLAS_SPECIFICATION_REVIEW.md`, `REVIEW_CHANGELOG.md`).

**Method statement:** every requirement in the eight reconstructed documents is tagged **[EXPLICIT]** (stated in Jira, the Master Spec, or an existing document, even if not in the missing document itself), **[STRONGLY INFERRED]** (not stated verbatim, but architecturally required to make an existing citation or documented behavior coherent), or **[OPEN PRODUCT DECISION]** (evidence does not determine the answer; recorded, not guessed). No requirement was silently upgraded from B or C to A. Section numbers in every reconstructed document were fixed to match citations already present in `02`–`19`, the Master Spec, and `Jira.csv` — a full grep-based evidence pass was performed before any document was written (raw output retained in this working session; the mapping is summarized per-document below).

---

## 1. Evidence Used, Per Document

### `01_SYSTEM_ARCHITECTURE.md`
- `07`'s header: "Governing invariant (defined authoritatively in `01_SYSTEM_ARCHITECTURE.md` §2, not restated here in full)."
- `10` §5 and `13` §4: both quote "Per `01_SYSTEM_ARCHITECTURE.md` §4" for the transaction-boundary rule, word-for-word similar enough that the two quotes constrain what §4 must say.
- `18`'s Phase 4 rationale: "the proposal/validation contract (`01` §2)."
- Jira `AI-001` Source field: `` `07` §3–4, `01` §2 ``.
- Jira `FOUND-001` Source field: `` `01` §5, `17` §7 ``, sub-tasks "repo init; GitHub Actions config; Flyway scaffold."
- Master Spec §2 Backlog Reconciliation, Sprint 0 row: the only place in the package committing to a concrete tech stack (Spring Boot, Java 17, MySQL/Aiven, Flyway, React/TS, FullCalendar, CI).
- Jira `UI-002` acceptance criteria independently confirms FullCalendar.
- The "Authoritative for / Referenced by" headers on all 18 other documents, used to build the layer map and dependency-direction diagram.

### `03_REQUIREMENTS_TRACEABILITY.md`
- `04` §2.5: "Sprint 4, `03_REQUIREMENTS_TRACEABILITY.md` §2."
- `18` §1 and §3: "full item-by-item disposition in `03_REQUIREMENTS_TRACEABILITY.md` §2"; "Requirement traced to its Master Spec section and technical home (`03_REQUIREMENTS_TRACEABILITY.md`)."
- `19` §1: "Sprint 7 composite-score approach is explicitly marked superseded in `03` §2 and `04` §1"; "every original Sprint 0–15 item has a disposition row in `03` §2."
- All 91 real Jira stories' `Source` and `Dependencies` fields (the `Description` column), used verbatim, not re-derived.
- Master Spec §2's own Sprint 0–15 disposition table, extended rather than duplicated.

### `06_ROADMAP_AND_RESOURCE_SYSTEM.md`
- Master Spec §1.3 (Entry Point B), §1.5 (full pipeline description and typed-node table), §1.6 (Resources and three-tier feedback), §1.25 (screenshot fixed-schedule import).
- `02` §1.4, §1.8: cites `06` §3 for resource feedback ownership.
- `07` §2, §4: cites `06` §1.4–1.5 (Review/Approve gate) and `06` §2 (conservative-default rule).
- `09` §2: cites `06` §3 for attached Resources in the Task Brief.
- `12` §3, §8: `/roadmaps/import` pipeline endpoints; `06` §3.2 tier 1 for resource feedback.
- `13` §1: `resource_feedback` table note citing `06` §3.2.
- `15` §5: citing `06` §1, §4 for stored files.
- `18` Phase 2, 3, 6: citing `06` §4, §1.2, §3.
- Jira `ROAD-001`–`ROAD-007` full descriptions (acceptance criteria and dependencies).

### `08_MEMORY_AND_PREFERENCES.md`
- Master Spec §1.8 (full section: confirmation-gated persistence, contextual application, single-instance-vs-pattern distinction) and Non-Negotiable Rule 5.
- `02` §1.10: User Preference entity fields and its own invariant, cited but not redefined.
- `07` §1, §2: citing `08` §2 (confirmed answers → memory) and `08` §1 (AI never silently writes a preference).
- `12` §9: full Preferences API surface, citing `08` §2.
- `ATLAS_SPECIFICATION_REVIEW.md` §4/§5 (Scenario D) and `18` Phase 6: citing `08` generally for the repeated-manual-move open question.
- Jira `MEM-001`–`MEM-004` full descriptions.

### `11_ANALYTICS_AND_LEARNING.md`
- Master Spec §1.15 (Planned/Executed/Achieved) and §1.23 (headline metrics, why-falling-behind, historical learning feed).
- `02` §1.13: Goal Risk Snapshot's "queryable for analytics" note.
- `04` §3, §4: "Learned preferences — `11_ANALYTICS_AND_LEARNING.md` supplies the learned values; this engine only consumes them"; capacity personalization feed.
- `05` §5: context-specific historical completion rate as a direct Goal Risk formula input.
- `10` §4: consumer table entry for Analytics.
- `12` §10: three analytics endpoints citing `11` §1, §2, §4.
- `18` Phase 5, 6: citing `11` §1, §2–4.
- Jira `ANLY-001`–`ANLY-004` full descriptions, including `ANLY-004`'s explicit consumer list (`SCH-009`/`SCH-013`/`RESC-007`).

### `14_UI_UX_SPECIFICATION.md`
- Master Spec §1.19 (Now/Next/Later, Minimum Viable Day), §1.20 (notifications), §1.21 (manual overrides/sticky flag).
- `04` header: "Referenced by... `14_UI_UX_SPECIFICATION.md` (renders results and explanations)."
- `05` §7, §8: citing `14` §5 ("what changed") and `14` §7 (notification-relevance rule).
- `09` §4: citing `14` §7 for the overrun-prompt notification philosophy.
- `10` §4: consumer table entry citing `14` §5.
- `12` §6, `13` §2: citing `14` §2 (Today/Week view).
- `18` Phase 3, 7: citing `14` §3 (goal interview), `14` §4 (Review/Approve UI), `14` §7 (notification filter), and the four-screen-state Definition-of-Done requirement.
- Jira `UI-001`–`UI-007` full descriptions (the single richest source for this document, since every UI story already names its own `14` section).
- The review brief's own explicit scenario list (Now/Next/Later, MVD, drag/move, "what changed," notifications, onboarding, recovery, critical/collaborative conversation).

### `16_TEST_STRATEGY.md`
- `04` §2.4: "This is required for testability (`16` §4)."
- `18` Phase 4, 7, §3: citing `16` §6 (AI-down scenarios), `16` §8 (security test suite), and "relevant test categories... unit, integration, and the specific category — scheduling/rescheduling/state-machine/AI-contract."
- Jira `OPS-004`: "Every category in `16` green," depending on "All stories."
- `07` §5's failure-handling table, used directly as the §6 test matrix.
- `15`'s own content (§2, §3, §4), used directly as the §8 security-test scope.

### `17_OPERATIONS_AND_DEPLOYMENT.md`
- `07` §5, §7: citing `17` §3 (retry policy, rate/cost mechanics).
- `10` §4: consumer table citing `17` §4 (audit/debugging).
- `15` §5, §6, §7: citing `17` §5 (file storage), `17` §4 (auth/audit log, explicitly "not the domain Event Log"), `17` §3 (rate limiting, "not duplicated here").
- `18` Phase 7: "observability (`17`)."
- Jira `FOUND-001`: `` `01` §5, `17` §7 ``, sub-task "GitHub Actions config."
- Jira `AI-012`, `OPS-001`–`OPS-005` full descriptions.

## 2. Explicit Requirements Reconstructed (Representative, Not Exhaustive)

The full set is in each document; the following are the load-bearing ones a future engineer would look for first:
- The AI-boundary invariant now has an authoritative home (`01` §2) instead of being elaborated in three places (`07`, Master Spec, `19`) with none of them the actual source.
- The transaction-boundary rule (`01` §4) now exists as the document `10` and `13` were both quoting without a target.
- The full Sprint 0 technology stack (`01` §5) is now written down in the architecture document, not only buried in the Master Spec's backlog-reconciliation table.
- The three-tier resource feedback model (`06` §3.2) — heavily cited (`02`, `07`, `12`, `13`) but never previously defined — now has one authoritative definition.
- The Preference confirmation-gate mechanism (`08` §1–2) — the single most-cited undefined behavior in the AI/Memory boundary — is now concretely specified.
- The Planned/Executed/Achieved reporting behavior and the four headline metrics (`11` §1–2) now have defined computations, not just names.
- The asymmetric "what changed" surfacing rule (`14` §5: Collaborative/Critical inline, Autonomous on-request-only) — cited by four other documents but never itself written down — now exists.
- The notification-relevance filter (`14` §7) that `05` §8 and `09` §4 both explicitly invoke by name now has a concrete fires/never-fires list.
- The AI-down test matrix (`16` §6) is now a direct, checkable mapping to `07` §5's failure table rather than an unwritten expectation.

## 3. Strong Inferences (Flagged, Not Silently Promoted to Explicit)

Every one of these is marked **[STRONGLY INFERRED]** in its owning document, not upgraded to EXPLICIT:
- The layer dependency-direction diagram (`01` §3) — no document states a dependency graph; it is derived from 19 documents' headers being mutually consistent only one way.
- The deterministic roadmap-parsing pipeline's step-by-step mechanics (`06` §1.2) — Master Spec §1.5 names the technique ("structural parsing... keyword heuristics") but not the algorithm.
- The `08` §5 recommendation to reuse `05` §6's multi-week/frequency-ratio evidentiary standard for any future repeated-manual-move feature — a recommendation, explicitly not a decision.
- The why-falling-behind dominant-factor computation approach (`11` §4) — the *name* of the output is explicit; the ranking method is inferred from Master Spec's own worked example.
- The three-environment (dev/test-CI/production) framing and the exclusion of AI-provider health from the readiness check (`17` §6, §8) — both inferred from `07` §5's graceful-degradation requirement applied to infrastructure.
- Several `14` sections' exact boundaries (§2's "hard day" trigger for the MVD banner, §6's placement as the one uncited section number) — placed by elimination against citations that pin every other section, not because evidence demands exactly this shape.

## 4. Open Product Decisions (Consolidated)

1. **Screenshot-import approval-gate policy** (`06` §4, inherited by `12` §5 and `14` §4) — does a screenshot-imported Fixed Commitment commit directly or pass through a Review/Approve-style gate? This is the most consequential open decision from this pass, because `12` §5 (written during the prior review, before `06` existed) already assumed an answer that this reconstruction found to be only provisionally justified.
2. **Repeated-manual-rescheduling handling** (`08` §5) — silently ignore beyond the existing single-move Protected-treatment rule, surface as a confirmable observation, or silently feed Problem B's learned time-of-day preference. No Jira story exists in any form.
3. **Task-cancellation dependency-cascade policy** (carried over from the September 2026 review, `ATLAS_SPECIFICATION_REVIEW.md` §5.2, restated in `03` §4) — does cancelling a Commitment cascade through `commitment_dependency`, or leave dependents to be caught by normal traversal (`04` §2.5)?
4. **Hosting/runtime target and AI-Proposal-Layer deployment topology** (`01` §7) — in-process module vs. separate service; no cloud provider named anywhere in the package.
5. **File-storage backend** (`17` §5) — dependent on (4) above.

None of these were resolved by this reconstruction pass. Each is recorded in its owning document with what is known, what is unknown, and what depends on the answer, per the review brief's explicit instruction not to guess.

## 5. Existing Documents Modified As a Result

- `19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md` — updated to reflect that the 8 previously-missing documents now exist and were cross-checked (no contradictions found against any existing citation to them), and that 3 new open product decisions surfaced. §6 "Current Status" rewritten accordingly.
- No other existing document required a correction. Every citation from `02`–`19` to a section number in `01`, `06`, `08`, `11`, `14`, `16`, or `17` was checked against the reconstructed document, and in every case the reconstructed section number matches what was already cited — this was verified by direct grep cross-check, not assumed. This is a meaningfully different (better) outcome than the September 2026 review found for the 11 originally-reviewed documents, where four real contradictions existed; it means the citing documents were, in fact, written with a consistent mental model of the missing documents even though those documents were never committed to text.

## 6. Jira Stories That Should Be Updated

- `EVT-002` — should cite `01` §4 as the origin of the transaction-boundary rule it implements, now that `01` exists (currently cites only `10` §5, `13` §4, which were themselves quoting an uncited source).
- `AI-001` — Source field already correctly cites `01` §2; no change needed, but this is confirmation the reconstruction matched an existing correct forward-reference rather than a coincidence.
- `ROAD-006` — acceptance criteria should note the screenshot-import approval-gate question is unresolved (open product decision #1 above) rather than silently assuming direct commit.
- `MEM-003` — acceptance criteria is accurate for its current stated scope but should explicitly note that repeated-manual-move handling (open product decision #2) is out of its current scope, to prevent a future implementer from assuming this story already covers it.
- The three stories already flagged in `REVIEW_CHANGELOG.md` from the prior pass (`DOM-003`, `SCH-004`, `AI-007`) remain outstanding — this reconstruction pass did not re-resolve them, only confirmed they're still accurate asks.

## 7. New Jira Stories That Appear Necessary

1. **Task cancellation flow** (already flagged in the September 2026 review, restated here since it remains unimplemented) — API, UI, and dependency-cascade behavior for the `Cancelled` Work State added to `02` §2.3.
2. **Repeated-manual-move preference suggestion** — cannot be scoped as a story until open product decision #2 is resolved; once resolved, likely depends on `MEM-001`.
3. **Screenshot-import Review screen** — only necessary if open product decision #1 resolves toward "yes, a gate is required"; if it resolves toward direct commit, no new story is needed and `ROAD-006`/`12` §5 stand as already specified.

## 8. Remaining Unresolved Architecture Risks

1. **The five open product decisions in §4 are now the single largest source of remaining ambiguity in the package** — larger than any purely textual gap, since the prior review's textual contradictions are fixed and this pass found no new ones. Recommend resolving these before Phase 3 (`ROAD-006`/screenshot import) and before any Memory/Preferences work beyond `MEM-001`/`MEM-002` begins.
2. **Confidence-tagging discipline must be preserved as the package evolves.** This reconstruction's STRONGLY INFERRED tags are load-bearing — if a future editor treats them as equivalent to EXPLICIT requirements without checking back against the evidence cited, inferred architecture could silently calcify into assumed-authoritative product decisions the same way the original 11-document review found real defects hiding behind an overconfident self-audit (`19`, before the September 2026 correction). The lesson from that review — that a document claiming "no contradictions" can itself be wrong — applies equally to a reconstruction claiming "no gaps found."
3. **This reconstruction is evidence-complete relative to the package as given, not omniscient.** If source documents referenced in the original consolidation precedence list (Master Spec's own header: "Final Decisions Lock," "Scheduling & Rescheduling Decision Specification," "Product Decisions / Groups A–K," "Complete Atlas Product Context," "Original Atlas Backlog v2") exist outside this package, they were not available and could not be checked — it is possible one of them resolves an item this report lists as an open product decision. Worth asking before treating any of §4's five items as requiring a fresh decision rather than a lookup.
