# Atlas Current State

## Last Updated

2026-09-15

## Current Phase

Phase 1 — Domain Breadth, Part 1 — is complete. Phase 2 — Domain Breadth, Part 2 — has begun: `DOM-006` is complete; `DOM-003` implementation is on its feature branch, real Aiven verification complete and awaiting human review / merge authorization.

## Repository

- Repository: `E:\ISHAN-WORK\atlas\project-atlas`
- Primary development branch: `develop`
- Current commit: `31562bd` — `Merge pull request #6 from ishangupta9460/feature/DOM-006-fixed-commitment`.
- DOM-003 changes are unstaged and uncommitted; legacy Task/Today and V1�V8 remain unchanged.

## Completed Work

| Story | Status | Evidence |
|---|---|---|
| FOUND-001 | Complete / merged | Foundation and CI work are in `develop`. |
| FOUND-002 | Complete / merged | JWT authentication and the Aiven profile are in `develop`. |
| FOUND-003 | Complete / merged | Walking-skeleton task loop and bare Today screen were merged via `ea33227`, `ce7b838`, and `198b50d`. |
| DOM-001 | Complete / merged | Goal entity and state-machine work are in `develop`. |
| DOM-002 | Complete / merged | Roadmap and Milestone work are in `develop`. |
| DOM-004 | Complete / merged | Recurring Intention work is in `develop`. |
| DOM-005 | Complete / merged | Category work, V6-to-V7 upgrade coverage, and the valid post-V7 link assertion are in `develop`. |
| DOM-006 | Complete / merged | PR #6 merged into `develop` (`31562bd`); automated suite, real Aiven smoke test and event-history verification passed. Temporary account cleanup limitation is recorded below. |

## DOM-003 Verification (2026-09-15)

- On `feature/DOM-003-commitment-full-model`; not merged. Independent review was reported as
  PASS WITH REQUIRED CHANGES solely for missing Aiven evidence. That gap is closed; human
  review / merge authorization remains pending. No new independent-review verdict is claimed.
- **H2 (prior automated evidence):** full backend `mvn verify`, 89 tests, zero failures/errors/skips.
- **Isolated MySQL 8.0.46 (prior automated evidence):** 16 API/migration/transaction tests passed,
  including fresh V9 and populated V8 upgrade. Frontend build passed. These were not rerun for Aiven.
- **Real Aiven MySQL:** 100 smoke checks passed, zero failed. V9 successfully applied and recorded;
  V1-V8 files/checksums unchanged. Backend startup, health, register/login/me passed.
- Commitment create/get/patch, omitted-field preservation, Draft/readiness protections and
  server-controlled field rejection passed. Category defaults, explicit importance precedence
  and unchanged stored importance after Category-default edits passed.
- Same-owner Goal/Milestone/Category relationships and missing-ID rejection passed.
  ownDeadline explicit-offset input, UTC response/storage, null clearing and invalid-input rejection passed.
- Persisted task.created/task.ready/task.updated events had correct commitment identities and order.
  Legacy POST /api/tasks, GET /api/tasks/today and start/finish regression passed; browser UI not exercised.
- Read-only inspection of all 12 tables and foreign-key dependencies passed. Authorized cleanup
  removed only disposable user 3, two Commitments, one Category/Goal/Roadmap/Milestone/legacy Task,
  and 11 associated smoke events. Re-query confirmed all table row counts/content hashes matched
  the pre-smoke baseline; unrelated users/data, five existing events and Flyway history unchanged.
- **Aiven coverage limitations:** true cross-user relationship rejection was not exercised with
  the single smoke account; forced event-insert rollback was not exercised on Aiven. Both remain
  covered by automated/isolated MySQL verification reported by implementation and independent review.
  Neither is an implementation defect; Aiven is not claimed to prove transaction rollback.
- Aiven verification is complete (run result PASS WITH ISSUES for the coverage limits only).
  Verification backend stopped; changes remain unstaged/uncommitted. See the DOM-003 handoff
  for detailed evidence, exact cleanup IDs and environment separation.

## Verified Environment

- Aiven MySQL connection verified.
- Flyway V1–V9 applied successfully to the real Aiven MySQL database, including DOM-003 V9; V1-V8 remained unchanged.
- Backend startup and health verified against the real database.
- DOM-006 full Maven suite passed: 68 tests, 0 failures, 0 errors, 0 skipped; focused H2/isolated MySQL checks and frontend build also passed (recorded in the handoff; not rerun for this documentation update).
- Real Aiven smoke test passed: health, register, login, `/api/auth/me`, Fixed Commitment create/get/patch/delete, post-delete 404, recurrence rejection, allowed overlap and missing-ID behavior.
- Real Aiven event-history verification passed using TLS and read-only SQL: commitments 1 and 2 are absent; their five created/updated/deleted events remain with matching account/entity identities.

## Current Next Action

Human review / merge authorization for DOM-003 using `docs/agent/handoffs/DOM-003.md`, the reported independent review, completed Aiven evidence and approved DEC-0009. Permanent Commitments coexist with the unchanged legacy task loop until the later coordinated cutover.

## Current Blockers

- No remaining DOM-006 implementation or verification blocker.
- DOM-003 missing-Aiven-evidence blocker is closed; documented coverage limits are not implementation defects.

## Explicit Cleanup Limitation

- Temporary user **2**, `dom006-smoke-20260914T172433-7245ae78@example.com`, remains; it has NOT been cleaned.
- Atlas has no implemented supported user-delete API/service. Manual deletion was intentionally NOT performed; any manual cleanup requires an explicit decision. This is a cleanup limitation, not a DOM-006 implementation defect.
- Both temporary Fixed Commitments were deleted through the API. Read-only cleanup checks found zero rows for this user in `fixed_commitments`, `categories`, `goals`, `recurring_intentions` and `tasks`; all five Fixed Commitment events remain. No unrelated users/data were modified.

## Resume Instructions

1. Read `AGENTS.md` and this file.
2. Read the selected Jira story, its specification sections, and any handoff.
3. Verify the repository state before making changes.

## State Maintenance Rule

Keep this file a concise current snapshot. Update it after a major implementation or merge; do not use it as a history log.
