# 13 — Database Specification

**Authoritative for:** table structure, keys, indexes, and transaction-boundary rules. Entity semantics (purpose, invariants) are owned by `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` — this document is the physical mapping only.

---

## 1. Core Tables (mapping `02`'s entities 1:1 unless noted)

| Table | Notes |
|---|---|
| `users` | Standard auth fields, owned in detail by `15_SECURITY_AND_PRIVACY.md` §1 |
| `goals` | FK `user_id`; `target_deadline` nullable; `lifecycle_state`, `planning_state` as separate enum columns (never merged — Master Spec §3 rule 12) |
| `roadmaps` | FK `goal_id` |
| `milestones` | FK `roadmap_id` |
| `commitments` | FK `milestone_id` nullable, `goal_id` nullable (derivable but denormalized for query performance), `category_id`, `flexibility_tier` enum, `work_state` enum, `is_hard_consequence` boolean (population mechanism = Genuine Gap, `02` §4), `user_moved_flag` boolean, `current_completion_pct` |
| `recurring_intentions` | FK `user_id`, `goal_id` nullable, `target_count_per_week` |
| `scheduled_blocks` | FK `commitment_id` XOR `recurring_intention_id` (enforced via check constraint), `start_time`, `end_time`, `user_moved_flag`, `superseded_by_block_id` nullable (self-referential — supports `02` §2.4 history without deletion) |
| `actual_sessions` | FK `scheduled_block_id`, immutable after creation except via corrective new-row inserts, never UPDATE on core fields (`02` §1.7) |
| `resources` | FK `user_id` (owner), `type`, `url_or_file_ref` |
| `task_resource` | Join table, FK `commitment_id`, `resource_id` |
| `commitment_dependency` | Self-referential join on `commitments`: `blocking_commitment_id`, `blocked_commitment_id` |
| `categories` | FK `user_id`, `default_flexibility_tier` |
| `user_preferences` | FK `user_id`, `domain`, `statement`, `active` boolean |
| `fixed_commitments` | V8: generated `id`; required FK `user_id`, `title` VARCHAR(255), UTC `start_time`/`end_time` DATETIME(6), constrained `source` VARCHAR(32) (`manual`/`screenshot_import`), nullable `recurrence_rule` TEXT; check `end_time > start_time`; index `(user_id, start_time, end_time)`. Fixed tier is intrinsic, not a writable column. No overlap uniqueness constraint. |
| `goal_risk_snapshots` | FK `goal_id`, append-only (no updates — historical trend data per `02` §1.13) |
| `events` | Schema owned entirely by `10_EVENT_LOG.md` §2 — not restated here beyond noting it is append-only with no update/delete permitted at the application layer |
| `resource_feedback` | FK `resource_id`, `tier` enum (`single_reaction` only — cross-instance patterns and saved preferences are computed/stored elsewhere per `06` §3.2, not as rows in this table) |
| `idempotency_keys` | (Resolved, see `12_API_SPECIFICATION.md` §1) `key` (unique, client-supplied), `user_id`, `endpoint`, `response_payload`, `created_at` — a duplicate request with the same key returns the stored response rather than re-executing the mutation |

## 2. Indexes (non-exhaustive, performance-critical only)

- `scheduled_blocks(start_time, end_time)` per user — supports Today/Week view queries (`14` §2).
- `events(entity_type, entity_id, timestamp)` — supports "what changed" and undo queries (`10` §4).
- `commitments(goal_id, work_state)` — supports Goal Risk feasibility calculation (`05` §5).
- `goal_risk_snapshots(goal_id, calculated_at)` — supports risk-trend analytics.

## 3. Soft vs. Hard Deletion

- `events`, `actual_sessions`, `goal_risk_snapshots`: never deleted (historical fact, Master Spec §1.15 immutability principle).
- `commitments`, `goals`, `scheduled_blocks`: soft-deleted (status transition, e.g. Abandoned) under normal product flows — never a row DELETE from user action.
- Full account deletion (`15_SECURITY_AND_PRIVACY.md` §3): hard delete of all user-scoped rows across every table — this is the one case where hard deletion is required, and it must be genuinely complete, not a soft flag (Master Spec §3 rule 15).

## 4. Transaction Boundaries

**DOM-006 approval (DEC-0008):** `fixed_commitments` permits physical user deletion, with no cancellation state. Creation, meaningful update and deletion each insert their immutable event in the same database transaction. Existing events are never deleted with the reservation; deletion payload retains its prior snapshot. The owner FK is restrictive and does not cascade deletions. Recurrence is nullable storage only; the public API accepts only null (`12` §5.1).

Any write to `commitments`, `goals`, `scheduled_blocks`, or `recurring_intentions` that constitutes a meaningful state change must occur in the same database transaction as the corresponding `events` insert (`10_EVENT_LOG.md` §5, `01_SYSTEM_ARCHITECTURE.md` §4). No commit path is permitted to update entity state without an atomic Event Log write.

## 5. Migration Strategy

Flyway-managed, versioned migrations, consistent with the original backlog's Sprint 0 tooling choice (Master Spec §2, no documented reason to change). No document in this package prescribes migration file naming — that is a development-workflow detail, not a specification requirement.

## 6. Genuine Gaps / Requires Product Decision

*(Resolved — see §1 above, `idempotency_keys` table.)*

## DOM-007 Physical Mapping (DEC-0010, V10)

V10 adds `commitment_dependency`: generated BIGINT id; required FKs `blocking_commitment_id`
and `blocked_commitment_id` to `commitments` (no ON DELETE CASCADE); unique edge
`(blocking_commitment_id, blocked_commitment_id)`; CHECK rejecting self-edges; index on
`blocked_commitment_id`. V1–V9 remain unchanged. Owner equality and cycle rejection are
application-enforced in addition to FKs.

## DOM-003 Physical Mapping (DEC-0009, V9)

V9 adds nullable checked `categories.default_importance` (VARCHAR(16), low/medium/high/critical)
and creates `commitments`: generated BIGINT id; required owner FK; nullable goal/milestone/category
FKs; nullable VARCHAR(255) title and TEXT description/completion_criterion; nullable UTC
DATETIME(6) own_deadline; checked required importance/flexibility/work_state strings;
false-default hard-consequence/movement booleans; DECIMAL(5,2) current_completion_pct default 0
and constrained 0..100; required UTC DATETIME(6) created_at. Work State permits draft/ready/
deferred/in_progress/completed/cancelled. Ready/in_progress/completed require defining text;
Milestone linkage requires a Goal. Application validation also checks Unicode blankness and
same-owner/consistent Goal chains, which ordinary FKs alone do not establish.
Indexes: (goal_id,work_state), (user_id,work_state,id), plus FK-supporting indexes.
Instant fields use explicit UTC conversion to DATETIME wall values and microsecond truncation;
no JVM/session default timezone interpretation. Existing categories receive null importance.
V1–V8 remain unchanged. V9 does not rename/drop/convert tasks or rewrite events; the legacy
Event.task_id FK remains intact. No cascade deletion is added. MySQL DDL may implicitly commit;
upgrade verification must not assume transactional rollback of schema operations.
