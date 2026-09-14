# 10 — Event Log

**Authoritative for:** the append-only event log schema and its role as the single source powering undo, "what changed" explanations, audit/debugging, and analytics aggregation.
**Source:** Master Spec §1.22.

---

## 1. Why One Log, Not Three Systems

Undo, explainability, and analytics are all queries over the same underlying event stream, aggregated differently — not three separately maintained systems that could drift out of sync (Master Spec §1.22, §2 Sprint 7 reconciliation note).

## 2. Event Schema

| Field | Description |
|---|---|
| `event_id` | Unique identifier |
| `type` | e.g. `task.created`, `block.generated`, `block.user_moved`, `block.atlas_moved`, `session.started`, `session.finished`, `task.progress_changed`, `goal.at_risk`, `goal.deferred`, etc. (full type list matches the transition tables in `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` §2 — not restated here) |
| `actor` | `user` or `atlas` |
| `entity_type` / `entity_id` | The entity affected |
| `timestamp` | — |
| `reason` | Free-text explanation, required for every `atlas`-actor event that reaches Collaborative or Critical tier (`05_RESCHEDULING_AND_RECOVERY.md` §7); optional for Autonomous-tier events but still recommended |
| `payload` | Type-specific structured data (e.g., old/new time for a move event) |

## 3. Immutability

**DOM-006 event contract (DEC-0008):** `fixed_commitment.created`, `fixed_commitment.updated`, and `fixed_commitment.deleted` use actor `user` and entity type `fixed_commitment`. Payloads hold respectively `after`, `before`/`after`, and `before` snapshots including identity, owner, title, UTC times, source, nullable recurrence and Fixed tier. These writes are atomic with the reservation mutation. No-op PATCH adds no event. Physical reservation deletion preserves every prior event and adds its deletion snapshot; it never rewrites history.

Event Log entries are never edited or deleted after creation. A correction (e.g., progress belief-state change, `02` §1.7) is itself a new event, not a rewrite of a prior one — this preserves the "what actually happened" record even when current belief changes.

## 4. Consumers

| Feature | How it uses the log |
|---|---|
| Undo | **Resolved/clarified:** Undo never deletes or rewrites a prior event — that would violate §3's immutability rule. Instead, pressing Undo generates a **new compensating event** (e.g. a `block.moved` event is undone by a new `block.move_reverted` event, not by removing the original). The audit trail always shows the full true history, including the fact that an undo occurred. Reads that need "current state" compute it as the net effect of the full event sequence, not just the latest event in isolation. |
| "What changed" (`14_UI_UX_SPECIFICATION.md` §5) | Renders recent `atlas`-actor events in plain language using their `reason` field |
| Audit/debugging (`17_OPERATIONS_AND_DEPLOYMENT.md` §4) | Raw query access for developers answering "why did Atlas schedule/move this" |
| Analytics (`11_ANALYTICS_AND_LEARNING.md`) | Aggregates events (e.g., session events → Executed hours; move events → disruption frequency) |

## 5. Every Commit Writes an Event

Per `01_SYSTEM_ARCHITECTURE.md` §4: any transaction that changes schedule state, progress, or lifecycle/planning/work state must write its corresponding Event Log entry in the same transaction. This is a database-transaction-boundary requirement, detailed further in `13_DATABASE_SPECIFICATION.md` §4.

## 6. Genuine Gaps / Requires Product Decision

None identified — this is a purely infrastructural document with no open product questions.
