# 19 — Consistency Audit & Genuine Gaps (Consolidated)

**Purpose:** the final cross-document consistency check, and a single consolidated list of every gap flagged across the package. Individual documents keep their own Genuine Gaps sections for local context; this document exists so nothing has to be hunted down document-by-document.

---

## 1. Consistency Audit

| Check | Result |
|---|---|
| Every entity has one authoritative definition | Pass — all in `02`; no other document redefines a field or relationship |
| Every state transition has one authoritative definition | Pass — all in `02` §2; `05`, `09` reference, don't restate |
| Every API endpoint corresponds to the domain model | **Corrected during the September 2026 review** — `12` had no endpoints at all for the Fixed Commitment entity (`02` §1.11), despite it being load-bearing for Stage 0 (`04` §2.1) and the recovery-trigger rule (`05` §1). Added `12` §5 (Fixed Commitments / Calendar Events). All other entities have endpoint coverage. |
| Scheduling rules consistent across documents | Pass — Stage 0–8 and Problem A/B split defined once in `04`; `05`, `14`, `16` reference by section — **verified against the reconstructed `14`/`16` directly**, not merely assumed, once those documents were rebuilt (see `MISSING_SPEC_RECONSTRUCTION_REPORT.md`) |
| AI boundaries consistent | Pass — core invariant defined once in `01` §2 (reconstructed and now verified to say exactly what `07` always claimed it said), elaborated (not redefined) in `07` |
| Status axes consistent | Pass — three independent axes (`02` §2, §3) never collapsed anywhere in the package, including `13`'s physical schema (separate columns) |
| Event log usage consistent | Pass — single schema in `10`, consumed identically by `05`, `11`, `14`, `17` — **verified against the reconstructed `11`, `14`, `17` directly** |
| Terminology consistent | Pass — "Commitment/Task," "Scheduled Block," "Actual Session," "Recurring Intention" used identically across all 19 documents; no synonym drift found |
| No document reintroduces superseded backlog behavior | Pass — the Sprint 7 composite-score approach is explicitly marked superseded in `03` §2 and `04` §1, and no other document computes a single importance score |
| Backlog items not silently lost | Pass — every original Sprint 0–15 item has a disposition row in `03` §2 |

**Revised during the current review pass (see `REVIEW_CHANGELOG.md` for full detail):** the original claim that no contradictions existed did not hold up under a second, more adversarial pass. Two genuine cross-document defects were found and fixed:
1. `05` §3 assigned Commitments a "Planning State = Deferred," but Planning State is defined in `02` §2.2 as a Goal-only field; Commitments had no Deferred (or Cancelled) value on their own Work State axis at all. Fixed by adding `Deferred` and `Cancelled` to Commitment Work State (`02` §2.3) and `Cancelled` to Scheduled Block lifecycle (`02` §2.4), and correcting `05` §3's wording.
2. `12` had no API endpoints whatsoever for the Fixed Commitment entity, despite it being referenced constantly by `04` and `05`. Fixed by adding `12` §5.

Additionally, `05` §1's Recovery Trigger list did not cover the case of a new Fixed Commitment overlapping already-scheduled lower-tier work — fixed by adding a fourth trigger condition.

One item resolved *during* the original documentation pass (before this review) is noted below for continuity.

## 2. Gap Resolution — Status: All 10 Resolved

Both the 5 originally-flagged calibration items and the 5 newly-discovered implementation gaps have been resolved and applied throughout the package. Nothing in this package currently blocks implementation.

| # | Gap | Resolution | Owning section |
|---|---|---|---|
| 1 | Stage 2 hard-consequence flag mechanism | AI infers from task context; user confirmation surfaced only when the classification materially affects a scheduling decision, not a mandatory field at creation | `02` §5, `04` §2.2, `07` §3 (`classify_hard_consequence`) |
| 2 | Stage 3 importance UI representation | `low`/`medium`/`high`/`critical` enum, category-defaulted, per-task override — no numeric scale | `02` §5, `04` §1 |
| 3 | Exact numeric At-Risk threshold | Formula-based (projected completion probability from remaining work, capacity, historical rate), cutoff externalized as tunable config rather than hardcoded — shape fixed, number adjustable | `05` §5 |
| 4 | Stage 5 dependency lookahead depth | Full chain traversal (not direct-only), bounded by a tunable max depth/count, reusing existing cycle detection | `04` §2.5 |
| 5 | Per-category capacity granularity | Confirmed: single per-user figure for v1, extensible later — not a gap, a deliberate scope decision | `04` §4 |
| 6 | Deferred-item overload cap | Periodic batched "Deferred Backlog Review" (Collaborative-tier), never silent deletion, never an individual notification per deferral | `05` §8 |
| 7 | AI low-confidence threshold | Three-band model (High/Medium/Low) replacing a single magic number; exact numeric boundaries live in config, not business logic | `07` §4, §8 |
| 8 | Focus overrun grace-window duration | 5 minutes, deliberately short to protect the next block | `09` §4, §8 |
| 9 | API idempotency for scheduling mutations | Mandatory `Idempotency-Key` header on move/session/progress endpoints, deduplicated server-side via `idempotency_keys` table | `12` §1, §14, `13` §1, §6 |
| 10 | Data retention before hard deletion | Immediate logical deactivation → 30-day recovery window → unconditional hard deletion | `15` §3, §8 |

## 3. Additional Correction Made During This Pass

**Undo semantics clarified (not a new gap, a fix to an underspecified interaction):** the original Event Log document left it ambiguous whether Undo rewrites history. Resolved: Undo always generates a new **compensating event** (e.g. `block.move_reverted`), never deletes or rewrites the original event — preserving the append-only immutability guarantee (`10_EVENT_LOG.md` §3–4) while still supporting reversal.

## 4. Implementation Plan Correction

The build sequence now opens with a **Phase 0 walking skeleton** — the narrowest possible end-to-end path through create → schedule → execute → record — before Phase 1's full-breadth entity CRUD. This directly addresses the risk of a long infrastructure-only stretch that never proves the core Atlas loop actually works. See `18_IMPLEMENTATION_PLAN_AND_DEFINITION_OF_DONE.md` §1.

## 5. Master Spec Amendment Note

Per this document's own resolution process: items 1, 2, 3, and 10 above are product-level decisions (not pure implementation defaults) and are recorded as an addendum to the Atlas Master Product Specification v1 rather than left to silently live only in this engineering package — see the Master Spec's Addendum 1. Items 4, 6, 7, 8, and 9 are implementation-level resolutions (algorithm/config/contract shape) that don't require amending the frozen product document itself.

## 6. Current Status

**Implementation readiness: green for the reviewed core; documentation package now complete.** As of the October reconstruction pass, all 19 numbered documents plus the Master Spec exist. The 8 documents that were missing during the September 2026 review (`01`, `03`, `06`, `08`, `11`, `14`, `16`, `17`) have been reconstructed from evidence already present in the package — every citation to them from `02`–`19` and from Jira was checked against the reconstructed section numbers and no contradiction was found (see `MISSING_SPEC_RECONSTRUCTION_REPORT.md` for the full evidence trail and confidence classification of every reconstructed requirement).

**This is not the same as saying every open question is resolved.** The reconstruction surfaced three new open product decisions that did not exist as named questions before (screenshot-import approval-gate policy, repeated-manual-move preference handling, and the cancellation-cascade question already known from the prior pass), all recorded in their owning documents and consolidated in `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §4. Additionally, requirements in the reconstructed documents are tagged EXPLICIT / STRONGLY INFERRED / OPEN PRODUCT DECISION at the point they're stated — a STRONGLY INFERRED tag means the requirement is architecturally necessary given everything else in the package, not that it was stated verbatim anywhere, and should be confirmed with the product owner before being treated as equivalent in authority to an EXPLICIT one. The next decision point is resolving the open product decisions list, followed by tooling/platform selection for turning this package into working code.
