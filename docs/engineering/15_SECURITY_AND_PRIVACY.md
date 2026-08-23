# 15 — Security & Privacy

**Authoritative for:** authentication, authorization, data isolation, AI-provider data boundaries, export/deletion, and audit logging.
**Source:** Master Spec §1.24, §3 rule 15.

---

## 1. Authentication

JWT-based, per original backlog Sprint 1 (Master Spec §2, unchanged). Password hashing via a standard modern algorithm (bcrypt/argon2 — implementation detail, no product-level constraint dictates which). Session/token expiry and refresh mechanics are a standard implementation concern, not further specified here since no product decision constrains it.

## 2. Authorization

Every API request scopes to `user_id` derived from the JWT, never accepted from the request body (`12_API_SPECIFICATION.md` §1). All entity queries in `13_DATABASE_SPECIFICATION.md` are user-scoped by foreign key; cross-user data access is not a code path that should exist, not merely one that's permission-checked.

## 3. Data Export & Deletion

- **Export:** full account data, JSON baseline (complete, re-importable), Markdown as a secondary human-readable option for roadmaps/goals specifically (Master Spec §1.24). Endpoint: `12_API_SPECIFICATION.md` §12.
- **Deletion (resolved):** account deletion is a two-stage process — immediate logical deactivation (account inaccessible, excluded from all normal queries) followed by a **30-day recovery window** during which the user can reverse the request, followed by permanent hard deletion across all user-scoped tables (`13_DATABASE_SPECIFICATION.md` §3). This is a genuine privacy/product-policy decision, not a pure implementation default — recorded here explicitly rather than left to implementation judgment. After the 30-day window elapses, deletion is unconditional and irreversible; this final step remains a Non-Negotiable Rule (Master Spec §3 rule 15) — the 30-day window governs *when* hard deletion happens, not *whether* it eventually and completely happens.

## 4. AI-Provider Data Boundary

Only the minimum context needed for a given proposal type is sent to the AI provider (`07_AI_ARCHITECTURE.md` §6) — not full user history by default. This document owns the boundary rule; `07` only asserts compliance with it.

## 5. Uploaded Files

Roadmap documents and screenshots (`06_ROADMAP_AND_RESOURCE_SYSTEM.md` §1, §4) are stored per the file-storage configuration in `17_OPERATIONS_AND_DEPLOYMENT.md` §5, scoped to the owning user, and included in the export/deletion guarantees of §3 above.

## 6. Audit Logging

The Event Log (`10_EVENT_LOG.md`) serves as the audit trail for state changes. Authentication events (login, failed login attempts) are a separate operational log, owned by `17_OPERATIONS_AND_DEPLOYMENT.md` §4, not the domain Event Log.

## 7. Rate Limiting / Abuse

API-level rate limiting is an operational concern, detailed in `17_OPERATIONS_AND_DEPLOYMENT.md` §3 (which also covers AI-provider-specific rate/cost limiting) — not duplicated here.

## 8. Genuine Gaps / Requires Product Decision

*(Resolved — see §3 above: 30-day logical-deactivation recovery window, then unconditional hard deletion.)*
