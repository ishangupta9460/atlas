# 01 — System Architecture

**Status: RECONSTRUCTED.** This document did not exist in the reviewed package. It has been rebuilt from citations to it in `07`, `10`, `13`, `18`, `19`, the Master Spec, and Jira `FOUND-001`/`AI-001`, which together specify — but never previously stated in one place — the layer boundaries and two hard invariants every other document assumes are defined here. See `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §1 for the full evidence trail and confidence classification of every claim below. Sections are numbered to match existing citations (`01` §2, `01` §4, `01` §5) exactly — do not renumber without updating `07`, `10`, `13`, `18`, `19`, and Jira `FOUND-001`/`AI-001`.

**Authoritative for:** layer boundaries, cross-layer dependency direction, and the two invariants every other document elaborates but does not itself define (the AI-boundary invariant and the event-log transaction-boundary rule).

---

## 1. What Atlas Is Built From — Layer Map

**[EXPLICIT]** — derived directly from the "Authoritative for" / "Referenced by" headers already present at the top of every existing document, and from the Master Spec §1.1 loop (`intention → roadmap → schedule → work → progress → learning`).

| Layer | Owning document(s) | Responsibility |
|---|---|---|
| Domain | `02` | Entity definitions, fields, relationships, state machines. No other layer redefines an entity. |
| Scheduling | `04` | Stage 0–8 "what deserves time" hierarchy and Problem B slot scoring. Deterministic, no AI calls (`04` §2.4). |
| Recovery | `05` | Missed/partial/forgotten work, overload resolution, Goal Risk calculation, Autonomous/Collaborative/Critical tiering. Re-runs Scheduling, does not redefine it. |
| Execution | `09` | Scheduled Block → Actual Session runtime flow, Focus Mode, overrun handling. |
| Event Log | `10` | Single append-only event stream backing undo, explainability, and analytics aggregation. |
| AI Proposal Layer | `07` | Structured, typed, validated proposals only. Never a source of truth for state. |
| Roadmap/Resources | `06` (reconstructed) | Document/screenshot ingestion, typed-node interpretation, Review/Approve gate, Resource entity and feedback. |
| Memory/Preferences | `08` (reconstructed) | Confirmation-gated persistent preferences, contextual application. |
| Analytics | `11` (reconstructed) | Read-only aggregation over the Event Log and Actual Session history. Never a write path. |
| API | `12` | The only boundary through which the UI and any external client reach the system. |
| Persistence | `13` | Physical schema, transaction-boundary enforcement, retention rules. |
| Security | `15` | Authentication, authorization, data isolation, export/deletion. |
| UI | `14` (reconstructed) | Information architecture and interaction behavior. Renders results; owns no business rule. |
| Operations/Infrastructure | `17` (reconstructed) | Logging, health checks, deployment, rate/cost protection, CI. |

## 2. Governing Invariant — AI Proposes, Deterministic Atlas Logic Decides

**[EXPLICIT]** — this section number is load-bearing: `07`'s header says "Governing invariant (defined authoritatively in `01_SYSTEM_ARCHITECTURE.md` §2, not restated here in full)," and Jira `AI-001`'s Source field is `` `07` §3–4, `01` §2 ``. The content is Master Spec Non-Negotiable Rule 1, stated here as the authoritative architectural contract `07` elaborates:

> **AI proposes. Validation occurs at the Domain layer. Deterministic Atlas logic remains the sole source of truth for state.**

Concretely:
- The AI Proposal Layer (`07`) never writes to `commitments`, `goals`, `scheduled_blocks`, or any state-bearing table directly. It emits one of the typed proposal shapes defined in `07` §3.
- Every proposal passes through Domain-layer validation (`07` §4) before it can influence state — entity ownership check, Non-Negotiable Rule conflict check, confidence banding.
- The Scheduling Engine (`04`) contains no AI call anywhere in Stage 0–8 evaluation (`04` §2.4) — this is what makes Stage 0–8 deterministic and testable (`16` §4, reconstructed).
- If the AI Proposal Layer is unavailable, degraded, or returns malformed output, every other layer continues operating on its last-known-good state (`07` §5). No layer blocks on an AI call to remain correct.
- This invariant is why the AI-classification timing race documented in `02` §1.4 and `04` §2.2 (fixed in the September 2026 review pass) is not a defect: a Commitment with an unresolved `is_hard_consequence` classification is not a broken state, it is the deterministic layer correctly operating on the best information available while AI catches up asynchronously.

## 3. Layer Dependency Direction

**[STRONGLY INFERRED]** — no existing document states a dependency graph explicitly, but one is required to make the "Authoritative for / Referenced by" headers across all 19 documents mutually consistent (e.g., `04` is "referenced by" `05` and `14`, never the reverse; `07`'s own header says AI proposals are validated "at the Domain layer," implying Domain sits below AI, not beside or above it).

```
Domain (02)
  ▲
  ├── Scheduling (04) ── reads Domain, capacity (04 §4), and Analytics-supplied learned values (04 §3, 11 §3)
  │      ▲
  │      └── Recovery (05) ── re-runs Scheduling, reads Goal Risk formula inputs
  │
  ├── Execution (09) ── creates Actual Sessions against Domain entities
  │
  ├── Roadmap/Resources (06) ── creates Domain entities on Approve; never schedules directly
  │
  └── AI Proposal Layer (07) ── reads minimum-necessary Domain context (07 §6, 15 §4);
         writes nothing; all output passes back through Domain validation before
         Scheduling/Recovery/Roadmap-Approve act on it

Event Log (10) ── written to, in the same transaction, by every layer above that
                   changes state (02 §4 rule below); read by Analytics (11) and
                   by the "what changed" / undo consumers in the API (12) and UI (14)

Memory/Preferences (08) ── written only on explicit user confirmation surfaced by
                            AI (07) or by direct user action; read by Scheduling (04 §3),
                            Recovery (05 §6 pattern detection), and the AI Proposal
                            Layer (07 §1) for context — never mutated by them directly

API (12) ── the only entry point for UI (14) and any external client; every route
             is user-scoped (15 §2) and depends on Domain/Scheduling/Execution/
             Recovery/AI/Roadmap/Memory/Analytics/Event-Log as the layers above define

Persistence (13) ── the physical substrate under Domain, Event Log, and every
                     other stateful layer; enforces the transaction-boundary rule
                     (§4 below) but does not itself decide what a transaction contains

Security (15) ── a cross-cutting constraint on API (12) and Persistence (13), not a
                  layer other layers call into

Operations/Infrastructure (17) ── a cross-cutting concern (logging, health, deploy,
                                   rate limiting) wrapping every layer above; owns
                                   no product behavior
```

**Rule:** a lower layer never calls upward. Domain never calls Scheduling; Scheduling never calls the AI Proposal Layer; the Event Log never calls its consumers. This is what makes "Atlas works without AI first" (Master Spec §1.10, `18` Phase 3 note) and "core flows survive AI failure" (`07` §5) architecturally true rather than aspirational — the dependency graph makes the reverse call impossible to write correctly, not merely discouraged.

## 4. Transaction-Boundary Rule

**[EXPLICIT]** — quoted verbatim by both `10` §5 ("Per `01_SYSTEM_ARCHITECTURE.md` §4: any transaction that changes schedule state, progress, or lifecycle/planning/work state must write its corresponding Event Log entry in the same transaction") and `13` §4, which cites this section by number. Restated here as the authoritative source both were quoting:

> Any write to `commitments`, `goals`, `scheduled_blocks`, or `recurring_intentions` that constitutes a meaningful state change — schedule placement, progress report, lifecycle/planning/work-state transition — **must occur in the same database transaction as the corresponding Event Log insert.** No commit path may update entity state without an atomic Event Log write in the same transaction. This is what makes the Event Log a reliable single source for undo, explainability, and analytics (`10` §1) rather than an eventually-consistent side channel that can silently drift from the entities it describes.

Jira `EVT-002` ("Transaction-boundary enforcement," source `` `10` §5, `13` §4 ``) implements this rule; it should also cite `01` §4 as its origin now that this document exists (see `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §6).

## 5. Repository, Tooling, and CI Bootstrap

**[EXPLICIT]** — cited by Jira `FOUND-001` ("Repo & CI setup," Source: `` `01` §5, `17` §7 ``, sub-tasks "repo init; GitHub Actions config; Flyway scaffold") and fully specified by the Master Spec §2 Backlog Reconciliation table, Sprint 0 row: *"Repo, Spring Boot, Java 17, MySQL/Aiven, Flyway, React/TS, FullCalendar, CI — **Valid** — infrastructure unaffected."* This is the one place in the package where a concrete technology stack is committed to, and it is committed to explicitly — nothing below is invented:

| Concern | Committed choice | Evidence |
|---|---|---|
| Backend framework/language | Spring Boot, Java 17 | Master Spec §2 Sprint 0, unchanged by the review |
| Database | MySQL (Aiven-hosted) | Master Spec §2 Sprint 0 |
| Migrations | Flyway | Master Spec §2 Sprint 0; `13` §5 ("Flyway-managed, versioned migrations... no documented reason to change") |
| Frontend | React, TypeScript | Master Spec §2 Sprint 0 |
| Calendar UI component | FullCalendar | Master Spec §2 Sprint 0; confirmed independently by Jira `UI-002` acceptance criteria, "Secondary FullCalendar-based view" |
| CI | GitHub Actions | Jira `FOUND-001` sub-task "GitHub Actions config" |
| Auth | JWT bearer | `15` §1, `12` header ("Auth: JWT bearer") |
| Password hashing | A standard modern algorithm (bcrypt/argon2) | `15` §1 — deliberately left as an implementation choice, not a product decision; do not narrow this further without a reason |

**Repo/CI bootstrap sequence (`FOUND-001`), reconstructed from its sub-tasks and `18` Phase 0:** repo init → Flyway scaffold (empty baseline migration) → GitHub Actions pipeline wired to run build + test on every push, with a migration-check step (a migration that doesn't apply cleanly against a fresh database fails CI) → this is the "build+test pipeline green on empty scaffold" acceptance criterion. `FOUND-002` (Auth/JWT) and `FOUND-003` (Walking Skeleton) build on top of this, matching `18`'s Phase 0 dependency order exactly.

**[OPEN PRODUCT DECISION]** — hosting/runtime target beyond "Aiven for MySQL" (e.g., which cloud runs the Spring Boot service itself) is not stated anywhere in the package. `17` §5–6 (reconstructed) documents deployment *configuration shape* (env vars, secrets, migrations) without naming a cloud provider, since none is committed to in evidence. Do not infer one.

## 6. Non-Negotiable Rules — Index

**[EXPLICIT]** — this document does not restate Master Spec §3's fifteen Non-Negotiable Rules; they remain owned there. This section exists only so a reader starting from the architecture document has a pointer: rules 1, 12, and 13 are specifically architectural (AI/Domain boundary, three-axis state model, event-log-backed explainability) and are the ones elaborated as invariants in §2–4 above. Rules 2–11, 14–15 are product/behavioral and are elaborated in `02`, `05`, `06`, `07`, `08`, `15` respectively — this document does not duplicate them.

## 7. Genuine Gaps / Requires Product Decision

1. Hosting/runtime target for the Spring Boot service (§5, OPEN PRODUCT DECISION above).
2. Whether the AI Proposal Layer runs in-process (a module within the same Spring Boot service) or as a separate service reached over the network. `07` §6 and `15` §4's "minimum-necessary-context" framing reads more naturally as an external-provider boundary (an LLM API call) than an in-process module boundary, which would make this question about *deployment topology* rather than *code architecture* — but no document states this explicitly. **[OPEN PRODUCT DECISION]**, low-stakes for v1 since the proposal/validation contract (§2 above) is unaffected either way.
