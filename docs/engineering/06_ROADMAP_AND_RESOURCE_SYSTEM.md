# 06 — Roadmap and Resource System

**Status: RECONSTRUCTED.** This document did not exist in the reviewed package, despite being cited by name and section number throughout `02`, `07`, `09`, `12`, `13`, `15`, `18` and by Jira epic `Roadmap/Resources` (`ROAD-001`–`ROAD-007`). Section numbers below (`§1.1`–`§1.5`, `§2`, `§3`, `§3.1`, `§3.2`, `§4`) are fixed by those existing citations — do not renumber. See `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §3 for the evidence trail.

**Authoritative for:** document/screenshot upload, deterministic and AI-assisted roadmap ingestion, typed-node interpretation, the Review/Approve gate, the Resource entity, resource attachment/replacement, the three-tier resource feedback model, and screenshot-based Fixed Commitment import.
**Source:** Master Spec §1.3 (Entry Point B), §1.5, §1.6, §1.25.
**Depends on:** `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` (Roadmap/Milestone/Commitment/Resource/Fixed Commitment entity definitions — not redefined here), `07_AI_ARCHITECTURE.md` (the `extract_roadmap` and `suggest_resource` proposal types this document's AI-assisted layer consumes).

---

## 1. Roadmap Ingestion

### 1.1 Document Upload

**[EXPLICIT]** — Master Spec §1.3 Entry Point B: "uploaded PDF, Word doc, Markdown, or screenshot." Jira `ROAD-001` acceptance criteria: "PDF/Word/Markdown parsed into typed nodes," depending on `DOM-002` (Roadmap/Milestone) and `DOM-003` (Commitment). API surface: `POST /roadmaps/import` (`12` §3), which accepts the upload and returns an interpretation proposal — not committed entities (`12` §3, Master Spec §1.5 pipeline).

Accepted formats: PDF, Word (.docx), Markdown, and screenshot (image). A screenshot uploaded here is interpreted as roadmap *content* (e.g., a photographed syllabus or study plan) — this is distinct from the screenshot-based **fixed-schedule** import in §4 below, which is calendar-slot extraction, not roadmap-structure extraction. Both accept an image upload through different endpoints (`/roadmaps/import` here vs. `/fixed-commitments/import`, `12` §5) because they produce structurally different output (typed roadmap nodes vs. calendar-slot facts).

### 1.2 Deterministic Ingestion Pass

**[EXPLICIT]** — Master Spec §1.5: "A deterministic, rule-based ingestion pass (structural parsing — headers, bullets, keyword heuristics for task/resource/optional detection) is a foundational capability, not deferred, since roadmap import is one of Atlas's three primary entry points." This is why `18` Phase 3 (not Phase 4, where the AI Proposal Layer lives) schedules "deterministic roadmap ingestion pass (`06` §1.2)" — the deterministic pass must work standalone, per the Master Spec's "Atlas works without AI first" principle (`18` Phase 3 note).

**Deterministic parsing pipeline [STRONGLY INFERRED from Master Spec §1.5's node table + §1.3's "structural parsing" description]:**
1. Extract document structure: headings (as candidate Milestones), bullet/numbered lists (as candidate Tasks), and inline links/attachments (as candidate Resources).
2. Classify each structural unit into one of the seven node types (§1.5 table below) using keyword heuristics — e.g., a bullet containing "watch," "read," or a bare URL is a `resource` candidate; a bullet phrased as an imperative verb ("implement," "write," "submit") is a `task` candidate; a bullet under a heading like "optional" or "if time permits" is an `optional` candidate.
3. Output a typed-node tree, not a flat list — mirroring the Roadmap → Milestone → Commitment structure `02` §1.2–1.4 already defines, so Approve (§1.4 below) can create real entities directly from it without a second transformation step.

**Typed Node Table [EXPLICIT, Master Spec §1.5, reproduced here since `06` is this table's owning document]:**

| Node type | Schedulable? | Notes |
|---|---|---|
| `task` | Yes | Normal actionable work — becomes a Commitment (`02` §1.4) on Approve |
| `resource` | No | Supports one or more tasks (§3 below) — becomes a Resource (`02` §1.8) on Approve |
| `optional` | Only if user opts in | Default unscheduled — becomes a Commitment with `flexibility_tier = Optional` only if the user opts in during Review |
| `prerequisite` | Only if user lacks it | May become a task only if the user says they don't already know it |
| `note` | No | Context only — not persisted as a schedulable or attachable entity, retained as free text on its parent node for Review display |
| `milestone` | No | Grouping — becomes a Milestone (`02` §1.3) |
| `project` | Usually yes | Typically a culminating task near the end — becomes a Commitment, often with dependencies (`02` §1.4 relationships) on the tasks beneath it |

### 1.3 AI-Assisted Ingestion Enhancement

**[EXPLICIT]** — Master Spec §1.5: "AI-assisted ingestion (semantic understanding of messy documents, smarter effort estimation) is a later enhancement layered on the same feature, following the AI-proposes/engine-decides rule (§1.10)." Jira `ROAD-007` ("AI-assisted ingestion enhancement," depends on `ROAD-001` + `AI-008`) acceptance criteria: "Layers on deterministic pass; confidence-flagged uncertain nodes."

The AI-assisted layer consumes the `extract_roadmap` proposal type (`07` §3: `{nodes: [{type, title, parent_ref, confidence}]}`, per-node confidence). It does not replace the deterministic pass's output — it **re-classifies or re-titles nodes the deterministic pass produced, and flags nodes it is uncertain about**, using the per-node `confidence` field. It never adds a node type the deterministic pass wouldn't also recognize (task/resource/optional/prerequisite/note/milestone/project) — the AI layer improves classification accuracy and effort estimates, it does not invent a new interpretation model.

### 1.4 Review Step

**[EXPLICIT]** — Master Spec §1.5: "The Review step must show counts (milestones, tasks, resources, estimated effort, dependencies, optional sections found) and let the user strike sections, mark milestones as already-known (removes them, not just deprioritizes), and swap resources — all before anything touches the active schedule." Jira `ROAD-002` acceptance criteria: "Counts, edit actions, nothing scheduled pre-approve." API surface: `GET /roadmaps/import/{importId}` (fetch current interpretation state) and `PATCH /roadmaps/import/{importId}` (user edits — strike, mark-known, swap resource) — both already defined in `12` §3, operating on the *proposal*, not committed entities.

**Review actions [EXPLICIT, from Master Spec §1.5]:**
- **Strike** a section — removes it from the set that will be approved; does not delete it from the original uploaded document, only from the interpretation that will become real entities.
- **Mark as already-known** — for `prerequisite` nodes specifically; per the node table (§1.2), a prerequisite only becomes a schedulable task if the user indicates they *don't* already know it. Marking it known removes it from the approved set entirely (not merely deprioritized — Master Spec §1.5 is explicit that this is a removal, not a demotion).
- **Swap a resource** — replaces an AI-suggested or deterministically-detected resource with a different one before it's ever attached to a real task (§3.1 below covers replacement *after* attachment; this is the pre-commit equivalent).

### 1.5 Approve

**[EXPLICIT]** — Master Spec §3 Non-Negotiable Rule 4: "No task is scheduled from an imported roadmap without user review and approval." API surface: `POST /roadmaps/import/{importId}/approve` (`12` §3) — "Commits approved subset → creates Roadmap/Milestone/Commitment/Resource entities, hands off to Scheduling Engine."

**Approve is the only point at which real Domain entities (`02`) are created from an import.** Before Approve, everything is proposal state (owned by the import record itself, not by `02`'s entities) — this is what makes Strike/mark-known/swap safe: there is nothing real to undo yet. After Approve, the created Commitments enter the normal candidate pool and are handled entirely by `04`'s Stage 0–8 hierarchy like any other Commitment — this document's responsibility ends at entity creation; it does not talk to the Scheduling Engine directly, matching `01` §3's layer-dependency rule that Roadmap/Resources creates Domain entities on Approve but never schedules directly.

## 2. AI-Assisted Layer — Conservative-Default Rule

**[EXPLICIT]** — cited by `07` §4 ("consistent with `06` §2's conservative-default rule for uncertain roadmap interpretation") as the source of a rule `07` only asserts compliance with. Reconstructed rule, consistent with that citation and with the AI Proposal Layer's confidence-banding model (`07` §4):

> When the AI-assisted ingestion layer (§1.3) is uncertain about a node's classification (Low confidence band per `07` §4's three-band model), it does not silently guess a schedulable interpretation. The conservative default is: **classify as `note` or leave the node under its nearest confident ancestor without promoting it to `task`/`project`**, and flag it for the user's attention during Review (§1.4) rather than either dropping it silently or scheduling it speculatively. A wrongly-omitted or flagged task costs the user one Review-screen glance; a wrongly-scheduled one costs a slot on their actual calendar — the asymmetry is why the conservative default leans toward under-classification, not over-classification.

## 3. Resources

**[EXPLICIT]** — entity fields and relationships owned by `02` §1.8 (not restated here); this section owns the feedback/replacement *behavior* Jira and other documents cite as `06` §3, §3.1, §3.2.

A Resource (video, PDF, doc, link, book, course) is created either during roadmap ingestion (§1 above, as a `resource`-type node) or directly via `POST /commitments/{id}/resources` (`12` §8, entry point C-style direct attachment). It is a standalone entity attachable to one or many Commitments through the `task_resource` join table (`02` §1.8, `13` §1) — never duplicated per task, per Master Spec §1.6.

### 3.1 Resource Replacement

**[EXPLICIT]** — Master Spec §1.6: "Replacing a disliked resource swaps the attachment without affecting the task's identity, progress, or history." Jira `ROAD-004` acceptance criteria: "Swap updates join only, not task identity." API surface: `DELETE /commitments/{id}/resources/{resourceId}` followed by `POST /commitments/{id}/resources` with the replacement (`12` §8 already frames this endpoint pair as "Detach/replace"). Replacing a resource is a `task_resource` join-row operation — it never touches the Commitment's own row, its Work State, its completion percentage, or its Event Log history. A replaced Resource entity itself is not deleted if other Commitments still reference it (many-to-many, `02` §1.8) — only the specific join row for this Commitment is removed.

### 3.2 Resource Feedback — Three Tiers

**[EXPLICIT]** — Master Spec §1.6 defines exactly three tiers, matching the general memory-write model (§1.8 of the Master Spec, elaborated in `08` §1–3 of this reconstruction). Jira `ROAD-005` acceptance criteria: "Single reaction logged; pattern surfaced; preference confirmed-only." `13`'s `resource_feedback` table stores only Tier 1 rows (`tier` enum = `single_reaction`), explicitly noting that "cross-instance patterns and saved preferences are computed/stored elsewhere per `06` §3.2, not as rows in this table" — this section is that "elsewhere."

| Tier | Trigger | Persistence | Governing rule |
|---|---|---|---|
| 1 — Single-resource reaction | User reacts to one specific resource instance ("disliked this video") | **Always logged** as a `resource_feedback` row (`13` §1) via `POST /resources/{id}/feedback` (`12` §8, "Single-reaction feedback, `06` §3.2 tier 1") | No confirmation needed — a single reaction about a single instance carries no generalization risk |
| 2 — Cross-resource pattern | Atlas detects a repeated signal across several Tier-1 reactions (e.g., "3 of last 4 long videos abandoned early") | **Not persisted as a standalone entity** — computed on demand from Tier-1 rows, surfaced as an observation | Surfaced only, per Master Spec §1.6: "surfaced as an observation, never silently applied." Uses the same multi-instance evidence standard as `05` §6's pattern detection (not a coincidence — both trace to Non-Negotiable Rule 8) |
| 3 — Saved preference | User explicitly confirms a generalized preference ("prefer short videos") in response to a Tier-2 observation | Persisted as a `user_preferences` row (`02` §1.10, `08` §1–2 of this reconstruction) — **only** on explicit confirmation | Master Spec §1.6: "A single disliked instance... is not the same as a generalized preference... only the latter, explicitly confirmed, is saved as memory" — this is Non-Negotiable Rule 5 verbatim |

AI's role here (`07` §2 AI Non-Responsibilities): AI never converts a Tier-1 reaction directly into a Tier-3 preference — it may only surface the Tier-2 observation, exactly as `07` §2 states ("silently converts an observation into a saved preference (`06` §3.2, `08` §1)" is listed as something AI never does).

## 4. Screenshot-Based Fixed-Schedule Import

**[EXPLICIT]** — Master Spec §1.25 ("Existing Timetable/Schedule Import"): "For v1, the user provides a screenshot of an existing timetable or calendar rather than requiring live calendar integration. Atlas extracts fixed/reserved blocks from it... and treats them as existing reserved time under the Fixed flexibility tier. Live calendar integration... is an explicit non-goal for v1." Jira `ROAD-006` acceptance criteria: "OCR/parse → Fixed Commitment entities," depending on `DOM-006` (Fixed Commitment entity, `02` §1.11).

**Pipeline [STRONGLY INFERRED from the OCR/parse phrasing + `02` §1.11's `source = screenshot_import` field]:**
1. User uploads a screenshot via `POST /fixed-commitments/import` (`12` §5, added during the September 2026 review specifically to close the API-surface gap this story exposed).
2. OCR/structural parse extracts time-labeled entries (e.g., "Monday 9–10 — Data Structures") — this is a simpler extraction target than roadmap ingestion (§1 above): the output is a flat list of (title, day/time, optional recurrence) tuples, not a typed node tree, because a timetable has no Milestone/dependency structure to preserve.
3. Each extracted entry becomes a `fixed_commitments` row (`02` §1.11, `13` §1) with `source = screenshot_import`, always `flexibility_tier = Fixed` (this is invariant, not inferred — a timetable entry is definitionally a Fixed commitment per Master Spec §1.25).
4. Any newly-created Fixed Commitment that overlaps an already-Scheduled Block for a lower-tier item triggers the recovery-trigger rule added to `05` §1 during the September 2026 review — that rule and this pipeline were designed for exactly this interaction, even though they were fixed in separate documents at different times.

**[OPEN PRODUCT DECISION]** — **does step 3 above commit directly, or does it pass through a Review/Approve-style gate first (matching §1.4–1.5 above)?** Evidence points in two directions and does not resolve cleanly:
- *For direct commit:* a calendar screenshot is a simple factual extraction (times and titles), not an interpretive structure requiring judgment calls the way roadmap ingestion's `optional`/`prerequisite` classification does. Master Spec §1.25 describes extraction and placement in one breath, without mentioning a review step, unlike §1.5's explicit multi-step pipeline language for roadmaps.
- *For a review gate:* OCR is meaningfully more error-prone than parsing a well-formed PDF/Markdown document (misread times, merged cells, ambiguous day labels), and Non-Negotiable Rule 4 ("no task is scheduled from an imported roadmap without user review and approval") doesn't textually exclude Fixed Commitments — it says "imported roadmap," and this is arguably a different import type, but the underlying concern (don't let a parsing error silently become a calendar fact) applies at least as strongly here as it does to roadmap tasks.

`12` §5 (added in the September 2026 review, before this document existed to consult) assumed direct commit, reasoning only from the first bullet above. That assumption should be treated as provisional pending this product decision, not as settled. **This decision affects:** `12` §5's endpoint behavior (does `POST /fixed-commitments/import` return committed entities or a proposal needing a follow-up approve call, mirroring `/roadmaps/import`'s two-step shape), `14` §4 (reconstructed — whether a Review screen is needed for this flow), and `ROAD-006`'s acceptance criteria (currently silent on this question).

## 5. Genuine Gaps / Requires Product Decision

1. **Screenshot-import approval gate (§4 above)** — the primary open question this reconstruction surfaced.
2. **Deterministic parser's keyword-heuristic specifics** (§1.2) are described at the level Master Spec §1.5 supports ("headers, bullets, keyword heuristics") but the exact heuristic rules (which words trigger which classification) are an implementation-tunable detail, not a product decision — consistent with how `04` §2.5 treats its dependency-lookahead depth as tunable rather than requiring product sign-off. Not blocking.
