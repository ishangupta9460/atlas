# JIRA_SPRINT_PLAN.md — Sprint Sequence

16 sprints (0–15), each sized for a solo developer, each ending in something demonstrable. Sprint length is a team decision (1–2 weeks is typical for solo pace) — not fixed here since Jira Free doesn't require a fixed cadence to function.

---

### Sprint 0 — Walking Skeleton
**Goal:** prove the full Atlas loop end-to-end, as narrow as possible.
**Epics:** FOUND, DOM (minimal Commitment only), EVT (minimal), SCH (trivial placeholder, not full hierarchy), EXEC (minimal), UI (bare Today screen).
**Stories:** FOUND-001, FOUND-002, FOUND-003.
**Dependencies:** none.
**Demo:** create one task → see it "scheduled" (naive placement) → start it → finish it → see the event recorded.
**Definition of Done:** the loop works for exactly one task, manually created, with no roadmap/AI/recovery involved.
**Explicitly NOT included:** Stage 0–8 hierarchy, any AI, any recovery logic, any UI polish.

### Sprint 1 — Domain Foundation (Breadth)
**Goal:** every core entity exists with the correct three-axis state model.
**Epics:** DOM.
**Stories:** DOM-001 through DOM-007.
**Dependencies:** Sprint 0.
**Demo:** CRUD on Goal/Roadmap/Milestone/Commitment/Recurring Intention/Category/Fixed Commitment via API, with Work/Planning/Lifecycle state fields correctly independent.
**Definition of Done:** schema matches `02` exactly; state-axis independence covered by tests.
**Explicitly NOT included:** scheduling logic beyond Sprint 0's placeholder.

### Sprint 2 — Event Log Foundation + Scheduling Engine Problem A
**Goal:** the real Stage 0–8 hierarchy replaces Sprint 0's placeholder, backed by a real event log.
**Epics:** EVT, SCH (Problem A only).
**Stories:** EVT-001–004, SCH-001–011.
**Dependencies:** Sprint 1.
**Demo:** create a task with a real deadline/importance/flexibility; see it correctly gated/tiered against a Fixed commitment and another competing task; query "why" and get a real Event Log-backed reason.
**Definition of Done:** all 10 stress-test scenarios from `04_SCHEDULING_ENGINE.md`'s design basis pass as tests; determinism verified.
**Explicitly NOT included:** Problem B slot scoring (naive slot choice is fine this sprint), capacity model beyond a flat default.

### Sprint 3 — Problem B + Capacity + Core UI
**Goal:** scheduling picks a *good* slot, not just a valid one, and the product is visibly usable.
**Epics:** SCH (Problem B, capacity), UI (Today/Calendar/onboarding core).
**Stories:** SCH-012–015, UI-001, UI-002, UI-003, UI-006, UI-007.
**Dependencies:** Sprint 2.
**Demo:** the onboarding single-question flow → a task lands in a sensible time slot → Today screen shows Now/Next/Later → manual drag sets the sticky user-moved flag.
**Definition of Done:** capacity model matches `04` §4 defaults; manual override never gets silently reverted (tested).

### Sprint 4 — Focus & Execution
**Goal:** the loop closes with real recorded work, not placeholder completion.
**Epics:** EXEC.
**Stories:** EXEC-001–005.
**Dependencies:** Sprint 3.
**Demo:** full session lifecycle including a real 5-minute-grace overrun prompt; Planned/Executed/Achieved all populate correctly after a session.
**Definition of Done:** Actual Session immutability verified; belief-state correction doesn't touch session history (tested).

### Sprint 5 — Recovery Engine (Missed/Partial/Forgotten)
**Goal:** Atlas handles the first class of real-life disruption.
**Epics:** RESC (core recovery).
**Stories:** RESC-001–006, RESC-012.
**Dependencies:** Sprint 4 (needs real Scheduled Block/Actual Session data to have something to recover).
**Demo:** simulate a missed task → watch the progressive today→tomorrow→week search → see it land, with an Autonomous-tier silent move for a low-stakes case and a Collaborative-tier prompt for a higher-stakes one.
**Definition of Done:** all Autonomous/Collaborative/Critical boundary examples in `05` §7 produce correct tier classification (tested).

### Sprint 6 — Goal Risk & Overload
**Goal:** Atlas protects long-term goals honestly.
**Epics:** RESC (risk/overload).
**Stories:** RESC-007–011.
**Dependencies:** Sprint 5.
**Demo:** simulate a goal falling behind pace → At-Risk transition fires → Critical-tier conversation with the five options appears; simulate several deferred low-priority items accumulating → batched Deferred Backlog Review surfaces instead of individual nags.
**Definition of Done:** feasibility formula matches `05` §5 (config-driven threshold, not hardcoded).

### Sprint 7 — Roadmap Ingestion & Resources
**Goal:** two more entry points become real.
**Epics:** ROAD (deterministic pass only).
**Stories:** ROAD-001–006.
**Dependencies:** Sprint 6 (needs the full scheduling+recovery stack to safely schedule imported work).
**Demo:** upload a Markdown roadmap → see typed-node interpretation → edit/strike/approve in Review → approved tasks get scheduled through the real engine.
**Definition of Done:** nothing from an import reaches the schedule without explicit Approve (tested).

### Sprint 8 — Recurring Intentions
**Goal:** the parallel recurring-target entity works end-to-end.
**Epics:** RESC (integration), building on entities already modeled in DOM.
**Stories:** RESC-013.
**Dependencies:** Sprint 6.
**Demo:** set a "3x/week" target → see instances placed → miss one → confirm no backlog accumulates next week.

### Sprint 9 — AI Proposal Layer (Core)
**Goal:** the AI/deterministic-engine boundary is built correctly from the start.
**Epics:** AI (core infrastructure).
**Stories:** AI-001, AI-002, AI-011, AI-012.
**Dependencies:** Sprint 7 (roadmap ingestion needs a deterministic baseline to enhance, established already; AI now layers on top).
**Demo:** simulate an AI outage — every existing flow (scheduling, execution, recovery) continues working unaffected.
**Definition of Done:** every failure mode in `07` §5 is tested and passes.

### Sprint 10 — AI-Assisted Features
**Goal:** the specific AI capabilities the Master Spec actually calls for.
**Epics:** AI (proposal types), ROAD (AI-assisted ingestion enhancement).
**Stories:** AI-003–010, ROAD-007.
**Dependencies:** Sprint 9.
**Demo:** progressive clarifying questions during task creation, each tagged with a real reason; AI-assisted roadmap parsing improves on the deterministic baseline for a messy document; hard-consequence classification correctly proposes and confirms.

### Sprint 11 — Memory & Preferences
**Epics:** MEM.
**Stories:** MEM-001–004.
**Dependencies:** Sprint 10 (needs AI-surfaced observations to have something to confirm into memory).
**Demo:** a resource-dislike pattern gets surfaced as an observation, user confirms it, it becomes a real applied preference next time it's relevant — never applied silently.

### Sprint 12 — Analytics & Learning
**Epics:** ANLY.
**Stories:** ANLY-001–004.
**Dependencies:** Sprint 4 onward for data volume; scheduled here once enough real usage data exists to make the metrics meaningful.
**Demo:** weekly review screen showing Consistency/Planned-vs-Completed/Focused Hours/Goal Progress, plus a real "why am I falling behind" dominant-factor answer from simulated usage history.

### Sprint 13 — Explanation, Notification & Remaining UI
**Epics:** UI (remaining), RESC (explanation surfacing already built, wiring the UI now).
**Stories:** UI-004, UI-005.
**Dependencies:** Sprint 6, 12 (needs risk/analytics content to notify about).
**Demo:** a Collaborative/Critical-tier change shows its real plain-language reason; notification philosophy correctly suppresses the excluded patterns (tested).

### Sprint 14 — Security & Privacy
**Epics:** SEC.
**Stories:** SEC-001–005.
**Dependencies:** all prior Epics (auditing the full surface).
**Demo:** cross-user access attempts rejected across every endpoint; export produces complete data; deletion request enters the 30-day window and is genuinely reversible until it isn't.

### Sprint 15 — Production Hardening
**Epics:** OPS.
**Stories:** OPS-001–005.
**Dependencies:** everything.
**Demo:** full test suite (`16_TEST_STRATEGY.md`, all categories) green in CI; health checks live; structured logs traceable end-to-end for a real request.
