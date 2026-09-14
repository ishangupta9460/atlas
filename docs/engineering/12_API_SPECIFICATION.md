# 12 — API Specification

**Authoritative for:** all v1 API surface. Entity shapes referenced here are owned by `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` — request/response bodies below reference those fields, they don't redefine them.
**Auth:** JWT bearer, per `15_SECURITY_AND_PRIVACY.md` §1 (not redefined here).

---

## 1. Conventions

- All endpoints scoped to the authenticated user (`user_id` from JWT, never from request body) — see `15` §2 for authorization rule.
- Standard error shape: `{error_code, message, details?}`.
- Pagination: `?cursor=&limit=` for list endpoints, applied to Commitments, Event Log queries, and Analytics history.
- Mutations that trigger scheduling side effects return the affected Scheduled Block(s) inline rather than requiring a follow-up fetch.
- **Idempotency (resolved):** every scheduling-mutation endpoint — block move (§6), session start/pause/resume/finish (§7), progress report (§4) — requires an `Idempotency-Key` request header. The server persists the key against the resulting state change (`13_DATABASE_SPECIFICATION.md` §1) and, on a retried request with the same key, returns the original result rather than re-applying the mutation. This is a contract requirement, not an implementation footnote — a client that omits the header on one of these endpoints receives a validation error.

### 1.1 Authentication

**[APPROVED — DEC-0005, 2026-09-13]** — the following routes implement Jira `FOUND-002` and `15_SECURITY_AND_PRIVACY.md` §1. They are the only authentication routes in the current API surface.

| Method | Route | Authentication | Request | Success response |
|---|---|---|---|---|
| POST | `/api/auth/register` | Public | `{ "email": string, "password": string }` | `201 Created` — `{ "id": number, "email": string }` |
| POST | `/api/auth/login` | Public | `{ "email": string, "password": string }` | `200 OK` — `{ "token": string }` |
| GET | `/api/auth/me` | `Authorization: Bearer <token>` | None | `200 OK` — `{ "id": number, "email": string }` |

`register` validates that `email` is non-blank, a valid email address, and at most 255 characters; `password` is non-blank and at least 8 characters. `login` validates that both fields are non-blank and that `email` is a valid email address. Validation failure returns `400 Bad Request`:

```json
{
  "error_code": "VALIDATION_ERROR",
  "message": "Validation failed for request arguments",
  "details": ["field: validation message"]
}
```

Duplicate registration returns `409 Conflict`:

```json
{
  "error_code": "EMAIL_TAKEN",
  "message": "Email address is already registered: <email>"
}
```

Invalid credentials — whether the email is unknown or the password is wrong — return the same `401 Unauthorized` response and never issue a token:

```json
{
  "error_code": "INVALID_CREDENTIALS",
  "message": "Invalid email or password"
}
```

A missing, malformed, expired, tampered, or otherwise invalid bearer token on `/api/auth/me` returns `401 Unauthorized`:

```json
{
  "error_code": "UNAUTHORIZED",
  "message": "Authentication required"
}
```

The current-user identity is derived only from the validated JWT; `/api/auth/me` accepts no user identifier that can select another account. Auth responses never contain a plaintext password or password hash.

### 1.2 Temporary Phase 0 task-loop API

**[TEMPORARY — FOUND-003, 2026-09-13]** — these authenticated `/api/tasks` routes prove the Phase 0 walking skeleton only. They operate on the deliberately minimal `tasks` and `events` tables introduced by Flyway V2/V3; they are **not** the permanent Commitment API and must be superseded when DOM-003 lands. They must not be extended to carry full Commitment, scheduling, Focus Session, or Event Log behavior.

| Method | Route | Purpose |
|---|---|---|
| POST | `/api/tasks` | Create the caller's placeholder task from `{ "title": string }`; returns `201 Created` with a `ready` task and records `task.created`. |
| GET | `/api/tasks/today` | Return only the caller's non-completed placeholder tasks; no scheduling, ranking, or Now/Next/Later behavior. |
| POST | `/api/tasks/{id}/start` | Transition the caller's `ready` task to `in_progress` and record `task.started`. |
| POST | `/api/tasks/{id}/finish` | Transition the caller's `in_progress` task to `completed` and record `task.finished`. |

All four routes require `Authorization: Bearer <token>` and derive the user solely from that JWT. Missing, malformed, expired, or otherwise invalid tokens return `401 UNAUTHORIZED`. `POST /api/tasks` with a blank or over-255-character title returns `400 VALIDATION_ERROR`. Invalid transitions return `409 INVALID_TASK_STATE`; missing or foreign-owned task mutations return `404 TASK_NOT_FOUND`.

## 2. Goals

| Method | Route | Purpose |
|---|---|---|
| POST | `/goals` | Create (from interview flow or direct) |
| GET | `/goals/{id}` | Fetch, incl. current Planning State/Lifecycle |
| PATCH | `/goals/{id}` | Update (title, deadline) — not state transitions |
| POST | `/goals/{id}/pause` | Explicit user action → `02` §2.2 Paused transition |
| POST | `/goals/{id}/abandon` | Explicit user action → `02` §2.1 Abandoned transition |
| POST | `/goals/{id}/risk-response` | User response to an At-Risk prompt (`05` §5 options) |

## 3. Roadmaps / Import

| Method | Route | Purpose |
|---|---|---|
| POST | `/roadmaps/import` | Upload document/screenshot → returns interpretation proposal (`06` §1.2–1.3), not committed |
| GET | `/roadmaps/import/{importId}` | Fetch current interpretation state for Review screen |
| PATCH | `/roadmaps/import/{importId}` | User edits (strike sections, mark known, swap resource) — updates the proposal, not committed entities |
| POST | `/roadmaps/import/{importId}/approve` | Commits approved subset → creates Roadmap/Milestone/Commitment/Resource entities, hands off to Scheduling Engine |

## 4. Commitments / Tasks

| Method | Route | Purpose |
|---|---|---|
| POST | `/commitments` | Direct task creation (entry point C, `06`'s pipeline not required) |
| GET | `/commitments/{id}` | Fetch full detail incl. attached Resources |
| PATCH | `/commitments/{id}` | Update fields (not work_state directly — see below) |
| POST | `/commitments/{id}/progress` | Report completion/partial — triggers `02` §2.3 transition |
| POST | `/commitments/{id}/progress/correct` | Correct belief-state completion % without touching Actual Session history (`02` §1.7) |

## 5. Recurring Intentions

| Method | Route | Purpose |
|---|---|---|
| POST | `/recurring-intentions` | Create |
| GET | `/recurring-intentions/{id}` | Fetch incl. current week's remaining target |
| PATCH | `/recurring-intentions/{id}/target` | User- or Atlas-suggested (Collaborative-tier, requires confirmation) target change |

### 5.1 Fixed Commitments / Calendar Events

**[APPROVED — DOM-006, DEC-0008, 2026-09-14]** — manual reservation CRUD only. This restores the missing contract referenced by the September review; it does not implement screenshot import, scheduling or recovery.

| Method | Route | Request | Success |
|---|---|---|---|
| POST | `/fixed-commitments` | `title`, `startTime`, `endTime`, optional null `recurrenceRule` | `201 Created`, entity |
| GET | `/fixed-commitments/{id}` | None | `200 OK`, entity |
| PATCH | `/fixed-commitments/{id}` | Supplied `title`, `startTime`, `endTime`, or null `recurrenceRule` | `200 OK`, entity |
| DELETE | `/fixed-commitments/{id}` | None | Physical deletion, `204 No Content` |

All four routes require JWT authentication. Ownership comes exclusively from the authenticated principal. Foreign-owned and missing IDs both return `404` with `error_code: FIXED_COMMITMENT_NOT_FOUND` and `message: Fixed Commitment not found`. Missing/invalid JWT returns `401 UNAUTHORIZED`. No collection/range endpoint exists in DOM-006.

Entity responses contain `id`, `title`, `startTime`, `endTime`, `source`, `recurrenceRule`, and `flexibilityTier` (always `fixed`). Manual creation always assigns `source=manual`; clients cannot supply or update `source`, ownership, or flexibility. Unknown request fields, including server-controlled fields, are rejected rather than ignored. The persistence source enum also permits `screenshot_import`, reserved for the future user-approved import flow (DEC-0001); there is no import creation endpoint in this story. Existing imported provenance is preserved during manual edits.

Validation uses the standard `{error_code, message}` shape with `400 VALIDATION_ERROR`. Title must be a string containing non-whitespace text and at most 255 characters. Both times must be ISO-8601 strings with explicit offsets, normalized to UTC and truncated to microsecond precision, within UTC years 1000–9999. The resulting `endTime` must be later than `startTime` after normalization. Invalid JSON, wrong types and malformed path IDs also return sanitized 400 errors without echoing request content.

PATCH omission preserves the field; explicit null is rejected for title/start/end. Empty or unchanged PATCH returns the current entity without another event. `recurrenceRule` accepts only omission or null: creation stores null, omission on PATCH preserves storage, and explicit null clears it. Every non-null recurrence input is rejected; no recurrence syntax, expansion, series editing or recurrent timezone behavior is defined by DOM-006. A future recurrence story must define those contracts before enabling non-null public writes.

Overlapping and identical intervals are allowed, on both creation and update. No overlap detection, automatic movement or recovery occurs. Repeated POST creates distinct entities and events, including when an `Idempotency-Key` header is repeated; no replay guarantee or persisted key infrastructure applies to these CRUD routes. The scheduling-mutation requirement in §1 remains unchanged for its owning endpoints. Repeated DELETE returns 404 after the first successful deletion.

Creation, meaningful update and deletion respectively write `fixed_commitment.created`, `fixed_commitment.updated`, and `fixed_commitment.deleted` with actor `user`, entity type `fixed_commitment`, and structured after/before-and-after/before snapshots. Each event is inserted in the same transaction as its mutation; failure rolls back both. Physical deletion retains prior history and its deletion snapshot. No Cancelled lifecycle state is introduced. Concurrent updates/deletes lock the owned row so partial updates and event snapshots reflect the latest committed state.

## 6. Scheduling

| Method | Route | Purpose |
|---|---|---|
| GET | `/schedule/today` | Now/Next/Later view (`14` §2) |
| GET | `/schedule/week` | Calendar view |
| POST | `/schedule/blocks/{id}/move` | Manual drag/edit — sets `user_moved_flag` (`02` §1.6, `05` §7) |
| GET | `/schedule/blocks/{id}/explanation` | Returns the Event Log `reason` for the current placement (`10` §4) |

## 7. Focus / Execution

| Method | Route | Purpose |
|---|---|---|
| POST | `/blocks/{id}/session/start` | Creates Actual Session, `02` §2.6 |
| POST | `/blocks/{id}/session/pause` | — |
| POST | `/blocks/{id}/session/resume` | — |
| POST | `/blocks/{id}/session/finish` | Prompts completion report (`09` §5) |

## 8. Resources

| Method | Route | Purpose |
|---|---|---|
| POST | `/commitments/{id}/resources` | Attach |
| DELETE | `/commitments/{id}/resources/{resourceId}` | Detach/replace |
| POST | `/resources/{id}/feedback` | Single-reaction feedback (`06` §3.2 tier 1) |

## 9. Preferences

| Method | Route | Purpose |
|---|---|---|
| GET | `/preferences` | List |
| POST | `/preferences` | Create (only via explicit confirmation flow, `08` §2) |
| PATCH | `/preferences/{id}` | Edit/disable |
| DELETE | `/preferences/{id}` | Delete |

## 10. Analytics

| Method | Route | Purpose |
|---|---|---|
| GET | `/analytics/summary` | Headline metrics (`11` §2) |
| GET | `/analytics/progress-breakdown` | Planned/Executed/Achieved (`11` §1) |
| GET | `/analytics/why-falling-behind` | Dominant-factor analysis (`11` §4) |

## 11. Event Log / History

| Method | Route | Purpose |
|---|---|---|
| GET | `/events?entity_type=&entity_id=` | Raw query for "what changed" UI and debugging |
| POST | `/events/undo` | Reverses last N events for a given entity (`10` §4) |

## 12. Data / Privacy

| Method | Route | Purpose |
|---|---|---|
| POST | `/account/export` | Full data export (`15` §3) |
| DELETE | `/account` | Full deletion, not soft-flag (`15` §3) |

## 13. Genuine Gaps / Requires Product Decision

*(Resolved — see §1 above: mandatory `Idempotency-Key` header on scheduling-mutation endpoints, deduplicated server-side.)*
