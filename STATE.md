# Atlas build checkpoint — 2026-09-22

CURRENT: Goal planning batch on `feature/ATLAS-BUILD`, main repository checkout.
BASE: `2945715` (develop, PR #19 product entry). Older ATLAS_CURRENT_STATE.md is stale.
STATUS: Implemented and focused verification passed; independent QA/review pending.

DONE:
- Goal → optional roadmap/milestones → permanent Commitment planning UI.
- Milestone creation/rename, direct or grouped task creation/edit, Draft → Ready via criterion.
- Explicit importance/flexibility, paginated reopening, retry/error/session handling.
- Owned GET /goals/{id}/roadmap and GET /goals/{id}/commitments?cursor=&limit=.
- No migrations; existing mutation services/events and legacy task execution loop retained.
- DOM-002/SCRUM-24, DOM-003/SCRUM-25 integration; contributes to UI-006/SCRUM-52.
- API contracts recorded in 12 §3–4; implementation decision DEC-0011.

FILES:
- Backend commitment: GoalCommitmentController (new), CommitmentRepository,
  CommitmentService, CommitmentExceptionHandler; CommitmentIntegrationTest.
- Backend roadmap: RoadmapController, RoadmapRepository, RoadmapService.
- Frontend: GoalPlanScreen.tsx + GoalPlanScreen.test.tsx (new), GoalsScreen.tsx,
  index.css, vite.config.ts. API specification, DECISION_LOG.md, this checkpoint.

VERIFIED:
- backend: mvn -q "-Dtest=CommitmentIntegrationTest,RoadmapIntegrationTest,CommitmentTransactionIntegrationTest" test
- Result: 20 tests, 0 failures/errors/skips (8 + 4 + 8).
- frontend: npm run build — TypeScript + Vite passed.
- frontend: npm test -- --reporter=dot — 14 passed (8 existing + 6 planning).
- git diff --check — passed. No tests weakened, deleted, or skipped.

KNOWN FAILURES: None after fixes. Initial foreign-goal 500 fixed by including the
new controller in CommitmentExceptionHandler; regression now passes.
LIMITS: No full backend suite or live browser/backend verification this batch.
Scheduling/execution integration, AI interview, imports, cancellation remain later work.
No new product questions; no schema or state-machine changes.

NEXT: Antigravity full regression, live integration/browser checks, independent review;
human verification before merge. This batch is ready for a draft PR targeting develop.

ENVIRONMENT: npm ci restored missing lockfile dependencies. Vite requires execution
outside the sandbox (esbuild spawn EPERM inside); Git/gh also need sandbox escalation.
Do not treat sandbox gh auth failure as invalid credentials: escalated gh auth passed.
Do not add existing untracked .cursor/, docs/agent/plans/,
docs/agent/handoffs/ATLAS-PARALLEL-PLAN.md or docs/jira/Jira.csv to this batch.
