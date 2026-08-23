# JIRA_EPICS.md — Final Epic Structure

13 Epics, derived from the engineering documentation package's own document boundaries (`00_DOCUMENTATION_INDEX.md`) — each Epic maps to one or two owning documents so Story source-references stay clean and non-overlapping.

| Epic Key | Name | Purpose | Product Outcome | Major Dependencies | Source Docs | Relative Size | Sequence |
|---|---|---|---|---|---|---|---|
| `ATLAS-FOUND` | Foundation & Walking Skeleton | Repo, CI, auth, and the narrowest possible end-to-end loop | Prove the core Atlas loop works before building breadth | None — first Epic | `01`, `18` §1 Phase 0 | M | 1 |
| `ATLAS-DOM` | Domain Model & Entity Foundation | Full entity schema: Goal, Roadmap, Milestone, Commitment, Recurring Intention, Category, Fixed Commitment, three-axis state model | Every downstream Epic has real data to work against | FOUND | `02` | L | 2 |
| `ATLAS-EVT` | Event Log & Audit | Append-only event schema, transaction-boundary enforcement, compensating-event undo | Every subsequent Epic can record and explain what it does | DOM | `10` | M | 3 |
| `ATLAS-SCH` | Scheduling Engine | Stage 0–8 hierarchy (Problem A) + candidate-slot scoring (Problem B) + capacity model | Atlas can actually place work on a realistic calendar | DOM, EVT | `04` | XL | 4 |
| `ATLAS-UI` (core) | Today / Calendar / Onboarding Experience | Now/Next/Later, calendar view, onboarding, manual override UI | The product is usable, not just functional on the backend | SCH | `14` | L | 5 (core screens) / ongoing |
| `ATLAS-EXEC` | Focus & Execution | Scheduled Block → Actual Session runtime: start/pause/resume/finish, overrun, completion report | The loop closes — planning becomes real recorded work | SCH, UI (core) | `09` | M | 6 |
| `ATLAS-RESC` | Rescheduling & Recovery | Missed/partial/forgotten handling, overload resolution, Goal Risk/At-Risk, autonomy tiers | Atlas behaves sensibly when real life doesn't follow the plan | SCH, EVT, EXEC | `05` | XL | 7 |
| `ATLAS-ROAD` | Roadmap Ingestion & Resources | Import→Interpret→Review→Approve→Schedule, typed nodes, Resource entity/feedback, screenshot import | Two of Atlas's three entry points become real | DOM, SCH | `06` | L | 8 |
| `ATLAS-AI` | AI Proposal Layer | Structured proposals, validation, confidence bands, failure handling, all AI-assisted suggestion types | Atlas gets smarter without ever risking state integrity | DOM, EVT, ROAD (for extract_roadmap) | `07` | L | 9 |
| `ATLAS-MEM` | Memory & Preferences | Preference entity, explicit confirmation flow, contextual application, category defaults | Atlas personalizes without silently assuming | DOM, AI (for observation surfacing) | `08` | S | 10 |
| `ATLAS-ANLY` | Analytics & Learning | Planned/Executed/Achieved, headline metrics, why-falling-behind, historical calibration feed | The user (and Stage 7 / capacity / risk math) gets real signal from real usage | EXEC, EVT | `11` | M | 11 (starts once real session data exists) |
| `ATLAS-SEC` | Security & Privacy | Authorization enforcement, export, 30-day-window deletion, AI data-minimization, idempotency | Atlas is safe to actually use with real personal data | DOM, AI | `15` | M | 12 |
| `ATLAS-OPS` | Observability & Production Hardening | Structured logging, health checks, retry/cost config, full test-suite gating, deployment | Atlas is operable, debuggable, and shippable | All prior Epics | `17` | M | 13 (final) |

## Notes on Sequencing

`ATLAS-UI` is listed once but its Stories are split across the timeline in `JIRA_SPRINT_PLAN.md` — core screens (Today, onboarding) land early (needed to demo Phase 0/1 loops), while explanation/notification/deferred-review UI lands later, once the Epics that generate that content (`RESC`, `ANLY`) exist. `ATLAS-ANLY` genuinely cannot start meaningfully until `ATLAS-EXEC` has produced real Actual Session data to aggregate — this is a hard dependency, not just a sequencing preference.
