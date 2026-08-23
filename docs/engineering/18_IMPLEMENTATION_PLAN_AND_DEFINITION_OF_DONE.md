# 18 — Implementation Plan & Definition of Done

**Authoritative for:** build sequencing and per-phase completion criteria. Reorganizes the original Sprint 0–15 backlog (full item-by-item disposition in `03_REQUIREMENTS_TRACEABILITY.md` §2) around actual dependency order rather than the original numbering.

---

## 1. Phased Build Plan

### Phase 0 — Walking Skeleton (Resolved Addition — Thin Vertical Slice Before Breadth)
Repo/CI, auth (JWT), and the **narrowest possible end-to-end path** through the core Atlas loop: create a Commitment directly (no roadmap, no AI, no recovery flow) → place it via a minimal version of Stage 0–8 (Fixed-gate + a trivial ranking, not the full hierarchy) → show it on a bare Today screen → Start/Finish via Focus Mode → Actual Session recorded → Event Log entry written. Full breadth (Goal/Roadmap/Milestone/Recurring Intention schema, complete state-axis model, every Flexibility tier) is deliberately deferred past this phase.
**Why this phase exists:** building full CRUD across every entity before proving the loop end-to-end risks a long stretch of infrastructure work without validating that the core Atlas experience (schedule → execute → record) actually holds together. This phase exists to prove the loop first, expand intelligence and breadth after.
**Demonstrably working after:** a user can create one task, see it scheduled, start it, finish it, and see the resulting event — the whole loop, narrow but real.

### Phase 1 — Foundation (Breadth)
Full domain schema for Goal/Roadmap/Milestone/Commitment/Recurring Intention/Category with the three-axis state model (`02` §2) and Flexibility tiers (`02` §1.4) — built out from Phase 0's minimal Commitment model, not from scratch.
**Demonstrably working after:** authenticated CRUD on all core entities, correct state-axis independence enforced at the schema level.

### Phase 2 — Event Log & Full Scheduling
Full Event Log (`10`, including the compensating-event Undo model), complete Stage 0–8 hierarchy and Problem B slot scoring (`04`), screenshot/manual fixed-commitment input (`06` §4), capacity model v1 default (`04` §4).
**Demonstrably working after:** a user can create a task, have it placed on a realistic schedule respecting Fixed commitments, and see why (Event Log-backed explanation) — the same loop as Phase 0, now with the real hierarchy instead of a trivial placeholder.

### Phase 3 — Entry Points & Roadmap Ingestion
Goal interview flow (`14` §3), direct task capture, deterministic roadmap ingestion pass (`06` §1.2), Review/Approve UI (`14` §4). AI-assisted ingestion enhancement layered in once the AI Proposal Layer (§5) exists — deterministic pass must work standalone first, per the Master Spec's "Atlas works without AI first" principle.
**Demonstrably working after:** all three entry points produce correctly-typed, schedulable Commitments without requiring AI.

### Phase 4 — AI Proposal Layer
Structured proposal schema and validation (`07`), integrated into task creation (clarifying questions), roadmap ingestion (AI-assisted enhancement), and duration/split/resource suggestions. Failure-handling matrix (`07` §5) built and tested from the start, not retrofitted.
**Demonstrably working after:** AI suggestions appear where expected, and every AI-down scenario in `16_TEST_STRATEGY.md` §6 passes.

### Phase 5 — Execution & Recovery
Focus Mode (`09`), Actual Session tracking, Planned/Executed/Achieved split (`11` §1), recovery flow (`05` §1–4), Goal Risk calculation and Critical-tier escalation (`05` §5).
**Demonstrably working after:** a full loop — schedule, execute, miss, recover, risk-flag — works end-to-end.

### Phase 6 — Resources, Memory, Analytics
Resource system and feedback tiers (`06` §3), Preferences (`08`), headline analytics and "why falling behind" (`11` §2–4).
**Demonstrably working after:** resource replacement, preference confirmation flow, and weekly review reporting all function against real usage data from Phase 5.

### Phase 7 — Notifications, Privacy, Hardening
Notification-relevance filter (`14` §7), export/deletion (`15` §3), security test suite (`16` §8), observability (`17`), production hardening per original Sprint 15.
**Demonstrably working after:** full test suite (`16`, all categories) passes; export and hard-deletion verified complete.

## 2. Dependency Rationale (Why This Order, Not Sprint 0–15 As-Written)

Roadmap ingestion (originally Sprint 13) moves to Phase 3 because it's a primary entry point (Master Spec §1.3, §2 backlog reconciliation) — deferring it to the same phase as advanced AI features would leave two of Atlas's three entry points unbuildable until late in the project. AI Proposal Layer architecture (originally embedded piecemeal across Sprint 13) is pulled into its own phase (4) specifically so the proposal/validation contract (`01` §2) is established once, correctly, rather than being retrofitted onto features built without it.

## 3. Definition of Done (Applies to Every Feature, Every Phase)

A feature is complete only when all of the following hold — "the code compiles" is not sufficient:

- Requirement traced to its Master Spec section and technical home (`03_REQUIREMENTS_TRACEABILITY.md`).
- Backend behavior implemented per the owning document's rules (no local reinterpretation of a rule defined elsewhere).
- Frontend behavior implemented per `14_UI_UX_SPECIFICATION.md`, including all four screen states (empty/loading/error/confirmation) where applicable.
- Input validation matches the API contract (`12_API_SPECIFICATION.md`).
- Relevant test categories from `16_TEST_STRATEGY.md` pass (unit, integration, and the specific category — scheduling/rescheduling/state-machine/AI-contract — that applies).
- Security requirements met (`15_SECURITY_AND_PRIVACY.md`) — user-scoping verified, no data leakage.
- Event Log entries written for every state-changing action, with `reason` populated where required (`10_EVENT_LOG.md` §5).
- Documentation updated if the feature reveals a Genuine Gap was resolved (the resolving document's Genuine Gaps section is edited to reflect the decision, not left stale).
- CI green, migrations applied cleanly, no regression in the existing test suite.

## 4. Genuine Gaps / Requires Product Decision

None specific to sequencing — all referenced gaps trace to their owning documents.
