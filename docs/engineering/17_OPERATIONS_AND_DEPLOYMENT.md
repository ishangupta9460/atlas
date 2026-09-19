# 17 — Operations and Deployment

**Status: RECONSTRUCTED.** This document did not exist in the reviewed package, despite being cited by `07` §5/§7, `10` §4, `15` §5/§6/§7, `18`, and Jira epic `Operations` (`OPS-001`–`OPS-005`) plus `FOUND-001` and `AI-012`. Section numbers below (`§1`, `§3`, `§4`, `§5`–`7`, `§8`) are fixed by those existing citations — do not renumber. See `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §8 for the evidence trail.

**Authoritative for:** structured logging and correlation IDs, AI rate/cost protection's operational tuning, the authentication/audit log (as distinct from the domain Event Log), file storage configuration, deployment configuration and secrets management, and health checks. This document is a cross-cutting operational concern wrapping every layer (`01` §3) — it owns no product behavior.
**Source:** Master Spec §2 Sprint 0/15 (infrastructure baseline), `01_SYSTEM_ARCHITECTURE.md` §5 (technology commitments, not restated here).
**Depends on:** `01_SYSTEM_ARCHITECTURE.md` §5 (Spring Boot/Java 17/MySQL-Aiven/Flyway/GitHub Actions — this document configures and operates that stack, it does not choose it independently).

---

## 1. Structured Logging & Correlation IDs

**[EXPLICIT]** — this is the section `10` §4's consumer table cites for audit/debugging ("Raw query access for developers answering 'why did Atlas schedule/move this'") as distinct from the domain Event Log, and Jira `OPS-001` acceptance criteria: "Traceable end-to-end per request," depending only on `FOUND-001`.

Every inbound request is assigned a correlation ID at the API boundary (`12`) and that ID is propagated through every downstream call this request triggers — including any AI Proposal Layer call (`07`), any Scheduling Engine invocation (`04`), and any Event Log write (`10`) — so that a single request's full path through the system can be reconstructed from logs alone, independent of the Event Log's own `entity_type`/`entity_id` trail. **[STRONGLY INFERRED framing]:** this is deliberately a separate mechanism from the Event Log, not a duplicate of it — the Event Log answers "what changed and why, from the product's perspective" (`10` §1); structured logging with correlation IDs answers "what did the system actually do, technically, to serve this specific request," which matters for debugging failures the Event Log wouldn't capture (e.g., a request that failed before any state-changing write occurred at all).

## 2. Observability (General)

**[STRONGLY INFERRED]** — `18` Phase 7 lists "observability (`17`)" as a general Phase 7 hardening item without a specific sub-section citation, distinct from the specifically-cited §1 (logging) and §8 (health checks). This section covers what sits between those two: metrics/monitoring on the operational signals the rest of the package already implies matter — AI proposal latency and failure rate (feeding `17` §3's rate/cost tuning), scheduling-engine evaluation time (relevant to `04` §2.4's determinism requirement holding under load), and Event Log write-transaction success rate (directly monitoring `01` §4's transaction-boundary invariant in production, not just testing it per `16` §2). No specific metrics platform is named anywhere in the package, so none is invented here.

## 3. AI Rate/Cost Protection — Operational Configuration

**[EXPLICIT]** — cited by `07` §5's failure-handling table ("Rate limit hit... request queued or deferred per `17_OPERATIONS_AND_DEPLOYMENT.md` §3 retry policy") and `07` §7 ("Rate/cost limiting mechanics (quotas, backoff) are an operational concern owned by `17_OPERATIONS_AND_DEPLOYMENT.md` §3"). Jira `AI-012` ("Rate/cost protection") acceptance criteria: "Quota tracking, backoff," sourced to `` `07` §7, `17` §3 ``. Jira `OPS-003` ("AI retry/backoff/cost config finalization") acceptance criteria: "Operational tuning of `AI-012`'s config," depending on `AI-012` — confirming this section's role is to *operate and tune* a mechanism `07`/`AI-012` build, not to design a second one.

**Retry policy [STRONGLY INFERRED from the "queued or deferred" + "backoff" phrasing shared across `07` §5 and `AI-012`'s acceptance criteria]:** a rate-limited AI call is retried with exponential backoff up to a configured attempt ceiling; if the ceiling is reached, the call is treated identically to "AI unavailable" per `07` §5's failure table — this section does not introduce a different fallback path, it only owns the specific backoff timing/quota numbers as tunable operational configuration, consistent with `07` §4's own pattern of keeping exact numeric thresholds (there, confidence-band boundaries) in config rather than business logic.

**Quota tracking:** per-user and/or global request quotas against the AI provider, tracked here so that `12` §1 API-level rate limiting (§7 below) and AI-provider-specific quota tracking remain two distinct mechanisms that happen to share this document, not one conflated concern — `15` §7 already makes this same distinction explicitly ("API-level rate limiting is an operational concern... which also covers AI-provider-specific rate/cost limiting — not duplicated here").

## 4. Authentication & Audit Logging

**[EXPLICIT]** — `15` §6: "Authentication events (login, failed login attempts) are a separate operational log, owned by `17_OPERATIONS_AND_DEPLOYMENT.md` §4, not the domain Event Log." `10` §4's consumer table also cites this section for "Audit/debugging."

This log records authentication-layer events — successful logins, failed login attempts, token issuance/refresh/expiry — as a security-operational concern distinct from both the domain Event Log (`10`, which records product state changes: task created, block moved, session finished) and the structured request logging in §1 above (which records technical request flow, not security-relevant auth events specifically). **[STRONGLY INFERRED boundary, consistent with `15` §6's explicit "not the domain Event Log" framing]:** a failed login attempt is never written to `10`'s `events` table, because it isn't a change to any Domain entity (`02`) — it belongs here instead.

## 5. File Storage Configuration

**[EXPLICIT]** — `15` §5: "Roadmap documents and screenshots (`06_ROADMAP_AND_RESOURCE_SYSTEM.md` §1, §4) are stored per the file-storage configuration in `17_OPERATIONS_AND_DEPLOYMENT.md` §5, scoped to the owning user, and included in the export/deletion guarantees of §3 above [in `15`]."

Uploaded files (roadmap documents/screenshots per `06` §1.1, and fixed-schedule screenshots per `06` §4) are stored in a file store scoped by `user_id`, matching the same user-scoping guarantee the database enforces (`15` §2, `13`). **[OPEN PRODUCT DECISION]** — no specific storage backend (e.g., a named cloud object-storage product) is committed to anywhere in the package, unlike the Sprint-0-committed database/backend/frontend stack (`01` §5). This document does not invent one; it specifies only the *shape* of the requirement (user-scoped, included in export/deletion per `15` §3) and leaves the concrete backend as an implementation choice consistent with whatever hosting decision resolves `01` §7's open hosting-target question.

## 6. Deployment Configuration & Secrets Management

**[EXPLICIT]** — Jira `OPS-005` ("Production deployment config") acceptance criteria: "Env config, migrations, secrets," depending on `OPS-001` and `OPS-002`, sourced to `` `17` §5–7 ``.

Three environments are implied throughout the package and are made explicit here per the review brief's instruction: **development**, **test/CI**, and **production**. Each environment has its own configuration (database connection, AI-provider credentials, file-storage location) supplied via environment variables/secrets, never hardcoded — **[STRONGLY INFERRED]**, consistent with `15` §1's treatment of password hashing and session mechanics as "standard implementation concern, not further specified here since no product decision constrains it": secrets management follows the same pattern, a standard operational practice rather than a product decision, and this document does not name a specific secrets-management product since none is committed to in evidence. Migrations (Flyway, `01` §5, `13` §5) are applied as part of the deployment pipeline in every environment identically — the same migration set, in the same order, from local development through to production — which is also why `FOUND-001`'s CI acceptance criteria requires "migration check wired": a migration that fails to apply cleanly in CI would otherwise be discovered only in production.

## 7. CI/CD Pipeline

**[EXPLICIT]** — cited by Jira `FOUND-001` ("Repo & CI setup," Source: `` `01` §5, `17` §7 ``, sub-task "GitHub Actions config") and by `OPS-004` ("Full test suite completion & CI gating," acceptance criteria: "Every category in `16` green, CI blocks merge on failure").

GitHub Actions (confirmed by `FOUND-001`'s own sub-task, and consistent with `01` §5's tooling commitments) runs on every push: build, the full test suite by category (`16`, reconstructed — unit, integration, contract, determinism/property, state-machine, AI-failure-mode, end-to-end/regression, security), and the migration-check step described in §6 above. `OPS-004`'s acceptance criterion that "CI blocks merge on failure" means this pipeline is a hard merge gate, not an advisory check — consistent with `18` §3's Definition of Done requiring "CI green, migrations applied cleanly, no regression in the existing test suite" for every feature in every phase, not only at the end of the project.

## 8. Health Checks

**[EXPLICIT]** — this is the section Jira `OPS-002` cites by number: "Health checks," acceptance criteria "Liveness/readiness endpoints," depending only on `FOUND-001`.

Two standard endpoint types — **[STrongly inferred nothing further specified]** — a liveness check (is the process running at all) and a readiness check (can the process currently serve traffic — e.g., database connection healthy, AI-provider reachability not itself gating readiness since `07` §5 requires the system to degrade gracefully rather than report unhealthy when AI is down). The readiness check's exclusion of AI-provider health is a direct consequence of `07` §5's own requirement that AI unavailability never blocks core flows — a readiness probe that failed because the AI provider was down would contradict that requirement at the infrastructure level.

## 9. Genuine Gaps / Requires Product Decision

1. **Hosting/runtime target** (§5 above) — the same open question already recorded in `01` §7; this document inherits it rather than duplicating a separate decision.
2. **File storage backend** (§5) — dependent on the hosting decision above; not resolved independently here.
3. **Specific metrics/observability platform** (§2) — an implementation choice, not flagged as blocking, consistent with how `16` §9 treats the absence of a named test framework as non-blocking for the same reason (no evidence commits to one, so none is invented).

## 10. Post-DOM-006 Real-Database Verification Record

The final real Aiven-backed smoke test on 2026-09-14 passed. These observed
results supersede the earlier incomplete verification record.

| Check | Result | HTTP status |
|---|---|---|
| Backend health | PASS — `{"status":"ok"}` | 200 |
| Register | PASS | 201 |
| Login | PASS | 200 |
| `/api/auth/me` | PASS | 200 |
| Fixed Commitment create | PASS | 201 |
| Fixed Commitment get | PASS | 200 |
| Fixed Commitment patch | PASS | 200 |
| Fixed Commitment delete | PASS | 204 |
| Post-delete GET | PASS | 404 |
| Non-null recurrence rejection | PASS | 400 |
| Overlapping Fixed Commitment | PASS | 201 |
| Missing ID | PASS | 404 |
| Event-history verification against real Aiven MySQL | PASS | Not applicable — read-only SQL |

Direct read-only inspection of real Aiven MySQL confirmed that both temporary
Fixed Commitment rows were deleted while their event history remained.
Commitment 1 retained `fixed_commitment.created`, `fixed_commitment.updated`
and `fixed_commitment.deleted`; commitment 2 retained
`fixed_commitment.created` and `fixed_commitment.deleted` (it was not patched).
Event entity IDs and payload snapshots matched the deleted commitments and
the smoke-test account. Database inspection used TLS and modified no data.

The Aiven smoke test verified persisted event history after deletion.
Transaction rollback atomicity was established by the automated test suite,
not by this smoke test itself.

The importable Postman artifacts are
`docs/postman/Atlas-Smoke.postman_collection.json` and
`docs/postman/Atlas-Local-Aiven.postman_environment.json`. The smoke test was
executed using a local HTTP client without depending on Postman. Supply local
credentials when using the artifacts; do not commit passwords or JWTs.
