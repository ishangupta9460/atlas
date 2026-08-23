# JIRA_REQUIREMENTS_TRACEABILITY.md

Extends `03_REQUIREMENTS_TRACEABILITY.md` (Master Requirement → Technical Home → Test Home) with the final link: **→ Jira Story**. This document does not restate requirement descriptions — see `03` for those; here only the Jira mapping is added.

| Master Spec § | Technical Home (from `03`) | Jira Story(ies) |
|---|---|---|
| 1.3 Entry points | `02` §1.1–1.4, `06` §1, `14` UI flows | UI-006, FOUND-003, ROAD-001–002 |
| 1.4 Entity hierarchy | `02` (all) | DOM-001–007 |
| 1.5 Roadmap interpretation | `06` §1–2 | ROAD-001–002, ROAD-007 |
| 1.6 Resource ≠ Task | `02` §1.8, `06` §3 | ROAD-003–005 |
| 1.7 Flexibility tiers | `02` §1.4/1.11, `04` §2.1/2.6 | DOM-003, DOM-006, SCH-002, SCH-008 |
| 1.8 Explicit memory confirmation | `02` §1.10, `08` §1 | MEM-001 |
| 1.9 Progressive questioning + reason tags | `07` §4 | AI-003 |
| 1.10 AI proposes/engine commits | `01` §2, `07` §2–3 | AI-001, AI-002 |
| 1.11 Stage 0–8 hierarchy | `04` §1–2 | SCH-001–011 |
| 1.12 Candidate-slot scoring | `04` §3 | SCH-012 |
| 1.13 Capacity/breaks/buffers | `04` §4 | SCH-013 |
| 1.14 Focus mode, overrun | `09` §1–2 | EXEC-001–003 |
| 1.15 Planned/Executed/Achieved | `02` §1.7, `11` §1 | EXEC-004–005, ANLY-002 |
| 1.16 Recovery flow, weekly reset, pattern detection | `05` (all) | RESC-001–006, RESC-010–011, RESC-013 |
| 1.17 Goal risk/feasibility | `05` §5, `02` §1.13 | RESC-007–008 |
| 1.18 Three independent state axes | `02` §2 | DOM-001, DOM-003 |
| 1.19 Today/Now/Next/Later, Minimum Viable Day | `14` §2, `05` §9 | UI-001, UI-003 |
| 1.20 Notification philosophy | `14` §7 | UI-005 |
| 1.21 Manual override sticky flag | `02` §1.4/1.6 | UI-007 |
| 1.22 Event log | `10` (all) | EVT-001–004 |
| 1.23 Analytics, why falling behind | `11` (all) | ANLY-001–004 |
| 1.24 Privacy/portability | `15` §3–4 | SEC-002–004 |
| 1.25 Screenshot timetable import | `06` §4 | ROAD-006 |
| 1.26 Autonomous/Collaborative/Critical | `05` §7 | RESC-006 |
| (New) API idempotency | `12` §1, `13` §1 | SEC-005 |
| (New) Undo compensating-event model | `10` §4 | EVT-003 |

## Reverse Lookup — Every Epic's Master Spec Coverage

| Epic | Master Spec §§ covered |
|---|---|
| FOUND | 1.3 (partial, skeleton only) |
| DOM | 1.4, 1.7, 1.18 |
| EVT | 1.22, (new) undo model |
| SCH | 1.11, 1.12, 1.13 |
| UI | 1.19, 1.20, 1.21, 1.3 (onboarding) |
| EXEC | 1.14, 1.15 |
| RESC | 1.16, 1.17, 1.26 |
| ROAD | 1.3, 1.5, 1.6, 1.25 |
| AI | 1.9, 1.10 |
| MEM | 1.8 |
| ANLY | 1.15, 1.23 |
| SEC | 1.24, (new) idempotency |
| OPS | (operational — no direct Master Spec §, supports all) |

No Master Spec subsection (1.1–1.26) is without Jira coverage. §1.1–1.2 (What Atlas Is, Product Philosophy) are cross-cutting principles enforced across every Story's Acceptance Criteria rather than owned by a single Story — consistent with how `03_REQUIREMENTS_TRACEABILITY.md` itself treats them.
