# Atlas Current State

## 1. Last Updated

2026-09-13

## 2. Current Phase

Phase 0 — Foundation & Walking Skeleton — is complete: FOUND-001, FOUND-002, and FOUND-003 were reviewed and merged into `develop`. Phase 1 — Domain Breadth, Part 1 — is next, beginning with DOM-001.

## 3. Current Repository

Repository path:
`E:\ISHAN-WORK\atlas\project-atlas`

GitHub remote:
`https://github.com/ishangupta9460/atlas`

Main branch:
`main`

Development branch:
`develop`

Current `develop` commit:
`198b50d`

Commit message:
`docs(FOUND-003): record final review pass`

Verified from the repository on 2026-09-13. This snapshot must be updated when `develop` advances.

## 4. Product Specification State

Atlas specification is currently baselined. Reference:
- `docs/engineering/01–19`
- `docs/product/atlas-master-product-spec-v1.md`
- `docs/review/`

(Contents of those documents are not duplicated here.)

## 5. Agent Governance State

The following are recorded as existing:
- `AGENTS.md`
- `docs/agent/AGENT_WORKFLOW.md`
- `docs/agent/DEVELOPMENT_RULES.md`
- `docs/agent/TESTING_RULES.md`
- `docs/agent/HANDOFF_PROTOCOL.md`
- `docs/agent/DECISION_LOG.md`
- `docs/agent/ATLAS_CURRENT_STATE.md`

Verified present in the repository on 2026-09-13.

## 6. Approved Product Decisions

### Screenshot Fixed-Commitment Import
Review → edit → explicit approval → active Fixed Commitment.

### Repeated Manual Movement
Repeated behavior → observation → user confirmation → preference saved. No silent learning.

### Task Cancellation
Show affected dependents → user selects which to cancel → explicit confirmation → no silent cascade. Cancelled ≠ Deleted.

## 7. Implementation Status

| Story | Status | Evidence |
|---|---|---|
| FOUND-001 | Complete / merged | Foundation and CI work is in `develop`. |
| FOUND-002 | Complete / merged | JWT authentication, V1 user migration, and approved auth API contracts are in `develop`. |
| FOUND-003 | Complete / merged | The V2/V3 walking-skeleton task/event migrations and bare Today loop are in `develop`; final review passed. |

Verified against `develop` history and the current V1–V3 Flyway migration chain on 2026-09-13.

## 8. Verified Environment

Recorded baseline:
- Java 17
- Maven 3.9.16
- Spring Boot 4.1.1
- Backend tests passing
- Frontend build passing

Verified by the completed FOUND-002/003 handoffs and final review records.

## 9. Current Next Action

**DOM-001 — Goal entity + Lifecycle/Planning state**

Start Phase 1 — Domain Breadth, Part 1 — with DOM-001, which is unblocked by FOUND-003 and precedes DOM-002, DOM-004, DOM-005, and DOM-006.

## 10. Development Workflow

Jira → implementation brief → coding agent → tests → handoff → independent review → human verification → merge → Jira update

See:
- `docs/agent/AGENT_WORKFLOW.md`
- `docs/agent/HANDOFF_PROTOCOL.md`

## 11. Current Blockers

None known.

## 12. Important Open Questions

- Hosting/runtime target — see relevant architecture/review doc (open product decision, not yet resolved).
- Whether the AI Proposal Layer runs in-process or as a separate service — see architecture doc once reconstructed (open product decision, not yet resolved).

(The three decisions in Section 6 are resolved and not reopened here.)

## 13. Last Completed Work

- FOUND-001, FOUND-002, and FOUND-003 completed, independently reviewed, and merged into `develop`.
- Phase 0's minimal authenticated create → Today → start → finish → event loop is complete; its V1–V3 migrations remain explicitly temporary until the later Domain/Event Log work supersedes them.

## 14. Resume Instructions

1. Read this file.
2. Read `AGENTS.md`.
3. Read the relevant Jira story.
4. Read the relevant specification sections.
5. Inspect actual repository state.
6. Never assume status from this file alone when repository evidence can be checked.

## 15. State Maintenance Rule

At the end of every major implementation cycle, update this file with:
- current phase
- current Jira story
- completed story
- current branch
- latest important decision
- blocker
- next action

This file must remain a CURRENT STATE snapshot, not a history log.
