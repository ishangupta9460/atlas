# ATLAS_SPECIFICATION_REVIEW.md

Final review report — September 2026 pass.

**What this review actually covers, and what it doesn't:** the uploaded package contained 11 numbered documents (`02`, `04`, `05`, `07`, `09`, `10`, `12`, `13`, `15`, `18`, `19`), the Master Spec, and `Jira.csv` (110 stories, 14 epics). Eight documents that the reviewed set and the Jira backlog both reference constantly — `01_SYSTEM_ARCHITECTURE`, `03_REQUIREMENTS_TRACEABILITY`, `06_ROADMAP_AND_RESOURCE_SYSTEM`, `08_MEMORY_AND_PREFERENCES`, `11_ANALYTICS_AND_LEARNING`, `14_UI_UX_SPECIFICATION`, `16_TEST_STRATEGY`, `17_OPERATIONS_AND_DEPLOYMENT` — were **not provided** and are **not reviewed here**. Every verdict below is scoped to the documents actually available. Treat this as a review of the scheduling/execution/recovery/data core of Atlas, not the whole system.

---

## 1. Executive Verdict

The **reviewed core** (domain model, scheduling, recovery, AI boundary, execution, event log, API, database, security) is now internally coherent — it was not fully coherent before this pass. Two genuine defects were found and fixed (a Commitment state that didn't exist but was referenced as if it did, and an entire missing API surface for a load-bearing entity), plus two real specification gaps closed (an unhandled AI-timing race, an unhandled "new fixed commitment collides with existing schedule" scenario). Full detail in `REVIEW_CHANGELOG.md`.

The package's own prior self-audit (`19`, before this pass) claimed zero contradictions and full endpoint coverage. Both claims were incorrect. This isn't a minor nitpick — it means the "consistency audit" had not actually been adversarial, and its "green" readiness verdict should not have been trusted at face value. It has been corrected in place.

**Everything outside the reviewed core is unverified, not confirmed-fine.** Roadmap ingestion, AI-orchestration-layer detail beyond the proposal schema, Memory/Preferences, Analytics, UI/UX, testing strategy, and Operations all live in documents not provided. Given that this pass found real defects in the documents that *were* provided, there is no basis to assume the unreviewed documents are clean.

## 2. Critical Issues Fixed

- **Commitment "Deferred" state didn't exist, but was written into `05` as if it did** (referenced the wrong entity's field, `Goal.planning_state`, which is a different axis on a different entity). Would have blocked implementation the moment an engineer tried to build Overload Resolution literally as written. Fixed in `02`, `05`, `13`, `10`. See changelog #1.
- **No terminal "Cancelled" state existed for Commitments or Scheduled Blocks**, despite Goal abandonment explicitly requiring one ("Related open Scheduled Blocks cancelled"). Fixed in `02`, `13`. See changelog #1.
- **Zero API endpoints existed for Fixed Commitments**, an entity the Scheduling Engine's Stage 0 gate and the recovery-trigger rule both depend on structurally. Fixed in `12` (new §5), with cascading section renumbering fixed in `12`, `15`, `19`. See changelog #4.

## 3. Important Issues Fixed

- **AI-classification timing race for the Stage 2 hard-consequence flag** was unaddressed — two engineers could have built incompatible behavior for the window between task creation and the async `classify_hard_consequence` proposal returning. Documented explicitly in `02` §1.4, cross-referenced from `04` §2.2. See changelog #2.
- **New Fixed Commitment colliding with an already-scheduled lower-tier block** had no defined recovery trigger — as written this was silent double-booking, the worst possible outcome for a system whose core philosophy is "minimize cognitive disruption." Added as a fourth Recovery Trigger Event in `05` §1. See changelog #3.
- **`19`'s self-audit overstated its own thoroughness** ("no contradictions," "green" readiness, "no orphan endpoints") in ways that were demonstrably false once tested adversarially. Corrected in place rather than left as a false signal for future readers. See changelog #5.

## 4. Product / Human-Behavior Issues

Walking through the review brief's seven scenarios against the reviewed documents:

| Scenario | Verdict |
|---|---|
| A — brief interruption mid-session, user returns in 15 min | Handled adequately: Pause/Resume (`09` §1) plus free-form trusted completion report (`09` §5) cover this without requiring the user to justify the gap. No fix needed. |
| B — user forgets the block entirely, returns 75 min late | Handled adequately: `09` §6 forgotten-timer flow → `block.unresolved` → `05` §1 recovery. Atlas does not assume failure; the user can still report what happened. No fix needed. |
| C — 40 minutes worked, 50% complete | Handled adequately: free-form completion report feeds `current_completion_pct` (belief state, not rewriting the Actual Session record) and triggers `task.partial` → recovery for the remainder (`09` §5, `02` §2.3). No fix needed. |
| D — user repeatedly manually moves the same flexible task | **Not explicitly addressed anywhere in the reviewed documents.** `user_moved_flag` is recorded per move, but there's no stated behavior for a *pattern* of repeated manual moves on the same item (as distinct from `05` §6's multi-week missed-work pattern detection, which is about non-completion, not repeated relocation). This is a genuine open question, not fixed here — see §5 below. It plausibly belongs in `08_MEMORY_AND_PREFERENCES.md` (not reviewed) rather than `05`. |
| E — day has more tasks than realistically fit | Handled adequately: Capacity Model (`04` §4) plus Overload Resolution (`05` §3, now corrected) plus Minimum Viable Day (`05` §9) together define what survives, what defers, and what's escalated. No fix needed beyond change #1. |
| F — a protected/fixed commitment appears unexpectedly | **Was a genuine gap; now fixed** (changelog #3). Previously undefined; now an explicit recovery trigger with a stated non-displacement guarantee for the new Fixed item. |
| G — deadline approaches while user keeps missing work | Handled adequately: Goal Risk formula (`05` §5) is explicitly context-adjusted (not one blended global rate) and triggers a Critical-tier, non-dismissible-but-non-blocking At Risk state with presented options. No fix needed. |

Net: the recovery/scheduling philosophy of minimizing disruption and never silently punishing normal human behavior is well-executed in the documents reviewed, with two real exceptions (both now fixed) and one open question (D, not fixed — genuinely needs a product decision about whether/how repeated manual moves should be surfaced, and likely needs `08` to answer it properly).

## 5. Remaining Open Questions (Require a Product Decision From You)

1. **Repeated manual rescheduling of the same item (Scenario D).** Should Atlas ever surface an observation like "you've moved this task 4 times this week — want to change its default time or flexibility tier?" This is a Memory/Preferences-shaped feature (`08`, not reviewed) more than a Recovery one — recommend deciding this once `08` is available for review rather than bolting it onto `05`.
2. **Task cancellation UX**, now that the state exists (`02` §2.3): does cancelling a Commitment cascade to its dependents (`commitment_dependency`, `02` §1.4 relationships), or leave them orphaned to be caught by normal dependency-chain traversal (`04` §2.5)? The state machine fix in this pass deliberately left this unanswered rather than inventing a cascade policy unilaterally.
3. **Screenshot-import Fixed Commitments and the Review/Approve gate.** `12` §5 (added this pass) assumes screenshot-imported Fixed Commitments commit directly, unlike Roadmap import's Review/Approve gate (`06` §1.4–1.5, not reviewed) — reasoned from the fact that a calendar screenshot is a simple factual parse, not an interpretive structure. This should be explicitly confirmed once `06` §4 is available, since it may already state a different policy.
4. **The eight unreviewed documents** are the largest open item overall: `01` (core AI/system invariant, cited as authoritative by `07`), `03` (requirements traceability — the source of truth for "every backlog item's disposition," cited by `18` and `19`), `06` (Roadmap/Resource ingestion), `08` (Memory/Preferences), `11` (Analytics), `14` (UI/UX — cited by nearly every reviewed document for how it renders results), `16` (Test Strategy — cited by `18`'s Definition of Done), `17` (Operations — cited by `07`, `13`, `15` for rate limiting, migrations, and audit logging). A second review pass covering these, and re-checking their cross-references against the fixes made here, is recommended before treating the full 19-document package as implementation-ready.

## 6. Jira Issues to Change

- **`DOM-003`** (Commitment/Task full model): acceptance criteria should explicitly enumerate the corrected `work_state` value set (`draft/ready/deferred/in_progress/completed/cancelled`) rather than the current generic "incl. ... work_state."
- **New story needed** under Domain Model or Execution/Focus: "Task cancellation flow" — no existing story implements it (confirmed zero matches for "cancel" anywhere in the 110-row backlog), and it's now a defined state transition (`02` §2.3) with an open cascade-policy question (§5.2 above).
- **`SCH-004`** (Stage 2 hard-consequence gate) and **`AI-007`** (`classify_hard_consequence` proposal): both should get an explicit acceptance-criterion line covering the AI-classification-timing race resolved in `02` §1.4 / `04` §2.2.
- **`RESC-012`** (Unexpected interruption handling): currently scoped only to "can't work 4–8 PM"-style user-reported time loss. Should be widened, or given a sibling story, to cover the new-Fixed-Commitment-collision trigger added to `05` §1.
- **`ROAD-006`** (Screenshot fixed-schedule import): dependency list should note `12` §5 now that Fixed Commitment endpoints exist; previously this story had no API surface to build against at all.
- No changes needed to implementation **ordering** — `18`'s phased plan (Phase 0 walking skeleton → Phase 1 breadth → Phase 2 event log/scheduling → …) already sequences prerequisites sensibly (domain before scheduling, event log before explainability, deterministic core before AI), and nothing found in this pass contradicts that order.

## 7. Documents That Are Now Complete (Within Reviewed Scope)

`02`, `04`, `05`, `07`, `09`, `10`, `12`, `13` are internally consistent with each other and with the Master Spec as of this pass, subject to the open questions in §5 and to re-verification once the 8 unreviewed documents are checked against them.

## 8. Documents That Still Need Work

- `19` — corrected in place, but its consolidated-gap list should be re-run once `01`, `03`, `06`, `08`, `11`, `14`, `16`, `17` are added to a future review pass; it currently only reflects the 11-document subset.
- `18` — no defect found, but its Definition of Done cites `16_TEST_STRATEGY.md` categories that could not be verified to exist or align, since `16` was not provided.
- `15` — one stale cross-reference fixed (§3 pointer to `12`'s export endpoint); otherwise sound within scope.

## 9. Architectural Risks

- **Single-point trust in `19`'s self-audit.** This pass demonstrated that a document asserting "no contradictions" can itself be wrong. Recommend treating any future "audit" document as a hypothesis to re-test, not a substitute for re-testing.
- **Cross-document section-number references are fragile.** Inserting one section into `12` required fixing three separate external references in two other documents. As the package grows, consider referencing entities/concepts by name plus document (e.g., "Fixed Commitments, `12`") rather than by section number where the reference will outlive likely reorganizations, or add a lint step that greps for `` `NN` §X `` patterns against actual headers before merging changes.
- **Async-AI timing races are a class of bug, not a one-off.** The Stage 2 flag race (fixed here) likely has siblings anywhere else a deterministic engine consumes an AI-populated field synchronously with placement — worth an explicit sweep once `01` (system architecture) and `07`'s full detail are cross-checked together.

## 10. Implementation Readiness Score (Reviewed Subsystems Only)

| Subsystem | Score | Basis |
|---|---|---|
| Domain Model & State Machines (`02`) | 90% | Now internally consistent; cancellation-cascade policy (§5.2) is the only remaining open question. |
| Scheduling Engine (`04`) | 90% | Deterministic hierarchy is well-specified and testable; AI-timing race now documented. |
| Rescheduling & Recovery (`05`) | 85% | Core flow is strong; Scenario D (repeated manual moves) genuinely unaddressed, likely blocked on `08`. |
| AI Architecture (`07`, proposal/validation layer only — `01`'s full invariant not reviewed) | 75% | Internally sound but partially unverifiable without `01`. |
| Execution & Focus (`09`) | 90% | Small, well-scoped, no defects found. |
| Event Log (`10`) | 90% | Sound; event-type list now corrected. |
| API Specification (`12`) | 80% | Fixed Commitments gap closed; cannot verify completeness against `06`, `08`, `11`, `14` endpoints since those documents weren't available. |
| Database Specification (`13`) | 85% | Now matches `02`'s corrected state model. |
| Security & Privacy (`15`) | 80% | Sound within scope; depends on `17` (not reviewed) for rate limiting and audit-log detail. |
| Implementation Plan (`18`) | 75% | Sequencing is sound; Definition of Done depends on `16` (not reviewed). |

**Overall (reviewed subsystems, weighted toward the core loop): ~85%.** The package cannot be scored as a whole — Roadmap/Resources, Memory/Preferences, Analytics, UI/UX, Test Strategy, Operations, and the top-level System Architecture document are unscored because they were not part of this review.
