# DEVELOPMENT_RULES.md — Engineering Rules for Atlas Agents

These rules govern how code gets written in the Atlas repository. They
implement the invariants in `AGENTS.md` §6. They do not define product
behavior — for that, see the numbered Atlas specification documents
(`01`–`19`) and `03_REQUIREMENTS_TRACEABILITY.md` for which section owns
which Jira story.

---

## Code Ownership

- Inspect existing code before editing. Read the module, its tests, and
  its callers before changing behavior — never edit blind based only on
  the Jira description.
- Preserve existing patterns (naming, layering, error handling style)
  already established in the codebase unless the story specifically
  requires changing the pattern, in which case say so in the handoff.
- Do not duplicate an existing service, utility, or entity because it
  was faster than finding and reusing the existing one. Atlas's own
  domain model (`02_DOMAIN_MODEL_AND_STATE_MACHINES.md`) is explicit
  that every entity has exactly one owning definition — the same
  discipline applies to code: one owning implementation per concern.
- Do not create abstractions (interfaces, base classes, config layers)
  that the current story doesn't need. Build for the requirement in
  front of you.

## Scope Control

- Implement the assigned Jira story — the one cited in your
  Implementation Brief — and nothing else.
- Do not "improve" unrelated components, rename unrelated variables,
  reformat unrelated files, or upgrade unrelated dependencies while
  you're in the area. This is true even if the improvement is obviously
  correct — it makes the diff impossible to review against the story's
  acceptance criteria and violates invariant 13 in `AGENTS.md`.
- If you discover a defect outside the current story's scope (a bug, a
  spec gap, an inconsistency like the ones found in
  `REVIEW_CHANGELOG.md`), **document it** — in the handoff's
  `ARCHITECTURAL CONCERNS` or as a proposed `docs/agent/DECISION_LOG.md`
  entry — rather than silently fixing or silently ignoring it.
- A story that turns out to require touching more than its stated area
  (e.g., a genuinely missing API endpoint blocking the story, as
  `12_API_SPECIFICATION.md` §5 was added to unblock `ROAD-006`-style
  work) is a signal to pause and get the brief adjusted, not to quietly
  expand scope and hope it's fine.

## Architecture

- Follow the Atlas specification's layer map and dependency direction
  (`01_SYSTEM_ARCHITECTURE.md` §1, §3). A lower layer never calls
  upward — e.g., Domain code never calls into Scheduling; Scheduling
  never calls the AI Proposal Layer.
- Respect domain boundaries: entity fields, relationships, and state
  transitions are owned exclusively by `02_DOMAIN_MODEL_AND_STATE_MACHINES.md`.
  Do not redefine or duplicate an entity's shape elsewhere in code
  because it was locally convenient.
- Respect the deterministic-core / AI boundary
  (`01_SYSTEM_ARCHITECTURE.md` §2, `07_AI_ARCHITECTURE.md`): AI proposes
  typed, validated proposals; it never writes directly to
  `commitments`, `goals`, `scheduled_blocks`, or any state-bearing
  table. The Scheduling Engine (`04_SCHEDULING_ENGINE.md` §2.4) contains
  no AI call anywhere in its Stage 0–8 evaluation — do not introduce one,
  even indirectly (e.g., an AI-populated field read synchronously during
  evaluation without the async/default-value handling `02` §1.4 and `04`
  §2.2 already specify for exactly this hazard).
- Do not move business logic into controllers or UI components "just for
  this one case." Business logic lives in the layer the spec assigns it
  to.
- Do not create circular dependencies between layers or modules. The
  dependency diagram in `01_SYSTEM_ARCHITECTURE.md` §3 is authoritative;
  if your change would require a cycle to work, the design is wrong, not
  the diagram.
- Do not bypass event-logging requirements. Per
  `01_SYSTEM_ARCHITECTURE.md` §4 / `10_EVENT_LOG.md` §5 /
  `13_DATABASE_SPECIFICATION.md` §4: any write to `commitments`, `goals`,
  `scheduled_blocks`, or `recurring_intentions` that constitutes a
  meaningful state change must write its Event Log entry in the **same
  database transaction**. There is no code path that updates entity
  state without an atomic Event Log write — not "for now," not "to get
  the test passing."

## Database

- All schema changes go through the established migration mechanism
  (Flyway, per `01_SYSTEM_ARCHITECTURE.md` §5 /
  `13_DATABASE_SPECIFICATION.md` §5). Never modify the database schema
  by any other path.
- Never edit or delete a migration that has already been applied
  anywhere (including CI history) — add a new migration instead. Do not
  silently rewrite migration history.
- Maintain schema/domain consistency: a field added to
  `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` must be reflected in
  `13_DATABASE_SPECIFICATION.md`'s mapping, and vice versa — if you find
  them out of sync (as `REVIEW_CHANGELOG.md` #1 did for Commitment
  `work_state`), that is exactly the kind of defect to flag rather than
  patch around locally.
- Consider rollback and migration safety for every schema change: can
  this migration apply cleanly against a fresh database (the CI
  migration-check gate, `01_SYSTEM_ARCHITECTURE.md` §5), and is it safe
  to run against a populated production table (no destructive column
  drops without a documented, reviewed data-migration plan).
- Respect `13_DATABASE_SPECIFICATION.md` §3's soft-vs-hard-deletion
  rules: `commitments`, `goals`, `scheduled_blocks` are soft-deleted via
  state transition under normal flows; only full account deletion
  (`15_SECURITY_AND_PRIVACY.md` §3) hard-deletes, and only after its
  specified 30-day window.

## API

- Maintain the documented contracts in `12_API_SPECIFICATION.md`. Do not
  change a route's request/response shape without updating that
  document and flagging the change explicitly — the contract is not
  "whatever the code currently does."
- Preserve validation and user-scoping: every request scopes to
  `user_id` from the JWT, never from the request body
  (`12_API_SPECIFICATION.md` §1, `15_SECURITY_AND_PRIVACY.md` §2).
- Maintain idempotency where required: every scheduling-mutation
  endpoint (block move, session start/pause/resume/finish, progress
  report) requires the `Idempotency-Key` header behavior specified in
  `12_API_SPECIFICATION.md` §1 — a retried request with the same key
  returns the original result, it does not re-apply the mutation.
- Update `12_API_SPECIFICATION.md` when an endpoint's actual behavior
  genuinely changes and that change is an approved product/architecture
  decision — not proactively "for clarity," and not without recording
  why in `docs/agent/DECISION_LOG.md` if the change has architectural
  weight.

## Security

- Never bypass authorization to make a feature easier to build or test.
- Never expose one user's data to another — cross-user data access is
  not a code path that should exist at all, not merely one that's
  permission-checked (`15_SECURITY_AND_PRIVACY.md` §2, explicitly).
- Never commit secrets, credentials, or API keys. Configuration is
  supplied via environment variables/secrets per
  `17_OPERATIONS_AND_DEPLOYMENT.md` §6, never hardcoded.
- Never log sensitive values (credentials, tokens, full user content
  where not necessary) — structured logging
  (`17_OPERATIONS_AND_DEPLOYMENT.md` §1) is for tracing request flow,
  not for capturing everything a request touched.

## AI (the product's AI layer, when you are implementing it)

- Never let raw AI-provider output directly mutate Atlas state. All AI
  output is a typed, validated proposal per `07_AI_ARCHITECTURE.md` §3
  passing through Domain-layer validation (`07` §4) before it can
  influence anything.
- Use the typed/validated proposal shapes already defined; do not invent
  a new unvalidated path "just for this integration."
- Preserve fallback behavior: if the AI Proposal Layer is unavailable,
  degraded, or returns malformed output, every other layer must continue
  operating on its last-known-good state (`07_AI_ARCHITECTURE.md` §5,
  `01_SYSTEM_ARCHITECTURE.md` §2). No layer may block on an AI call to
  remain correct.
- Preserve deterministic behavior in the Scheduling Engine — Stage 0–8
  evaluation must produce identical output on identical input, always
  (`04_SCHEDULING_ENGINE.md` §2.4).
- Treat AI failure as an expected, normal, tested condition — not an
  exceptional case handled as an afterthought. See
  `docs/agent/TESTING_RULES.md`'s AI Tests section.

## Error Handling

- Fail explicitly. A caller should be able to tell that something went
  wrong and roughly why.
- Do not swallow exceptions silently (empty catch blocks, blanket
  catch-and-log-and-continue where continuing is unsafe).
- Do not hide failures just to make a test or a build pass. If a test is
  failing because the implementation is genuinely broken, fix the
  implementation — do not adjust the test to stop noticing.

## Documentation

- Update the relevant Atlas specification document only when
  implementation work exposes a **real** requirement mismatch (an
  actual contradiction or gap, in the spirit of
  `REVIEW_CHANGELOG.md`'s entries) or when an approved product/
  architecture decision has genuinely changed the requirement. This is
  not a license to edit the spec to match whatever was easiest to build.
- Any such spec update must be traceable: what changed, why, and where
  the decision authorizing it lives (`docs/agent/DECISION_LOG.md` for
  process-level decisions; the human/product owner for product-level
  ones).
- Record meaningful architectural decisions in
  `docs/agent/DECISION_LOG.md` as they're made — not retroactively, and
  not only in a commit message or PR description that will scroll out
  of view.

## Do Not Refactor for Aesthetics During a Feature Task

If code in the area you're touching is ugly, inconsistent, or
suboptimal but functionally correct and not part of the story's
acceptance criteria, leave it. Note it in the handoff if it seems worth
a future story. A feature branch's diff should be readable against the
Jira story alone.

---

## Git / Branch Policy

Intended branch structure:

- `main` — stable, verified. Never commit directly.
- `develop` — active integration branch.
- `feature/<jira-key>-<short-name>` — individual implementation work.

Rules:

- Coding agents work on a feature branch scoped to one Jira story.
- Never commit directly to `main`.
- Never force-push a branch other agents or humans may also be using.
- Keep commits meaningful — a commit should represent a coherent step,
  not "wip" dumped at session end. If you must stop mid-thought, commit
  what's coherent and describe the rest in the handoff instead of
  committing broken intermediate state without explanation.
- Do not rewrite history unnecessarily (no gratuitous `rebase -i` /
  force-push on a branch someone else might be resuming from).
- Merge only after the verification sequence in
  `docs/agent/AGENT_WORKFLOW.md` has actually completed — Review Agent
  PASS and human verification, not coding-agent self-certification.
- Do not assume automated PR tooling exists. Describe and follow the
  desired workflow (branch → tests → handoff → review → human merge)
  manually if the repository has no PR automation configured yet.

---

## Review Agent Policy

A reviewer is a separate stage from the implementer (see
`docs/agent/AGENT_WORKFLOW.md` §6) and checks, at minimum:

1. Jira acceptance criteria are actually met.
2. The Atlas specification section(s) cited are actually satisfied, not
   just superficially referenced.
3. Code correctness — does it do what it claims, including edge cases.
4. Test adequacy — do the tests actually exercise the behavior, not just
   pad a coverage number (see `docs/agent/TESTING_RULES.md`).
5. No regressions in existing behavior.
6. Security — authorization, user-scoping, no leaked secrets/sensitive
   logs, idempotency where required.
7. Architecture — layer boundaries respected, no AI-writes-state
   violations, no circular dependencies, transaction-boundary rule
   honored.
8. Scope discipline — the diff matches the story, nothing unrelated
   changed.
9. Documentation impact — was a genuine spec/API-contract change made,
   and if so, was it recorded correctly (§Documentation above).

**"Reviewer" means adversarial verification, not approval theater.** A
reviewer is expected to fail work that doesn't meet the bar, and a
reviewer session must be independent of the implementing session (never
the same agent self-certifying).

---

## Definition of Done for an AI Agent

A task may be called complete only when **all** of the following are
true:

- The assigned requirements (Jira + cited spec sections) are
  implemented.
- The relevant tests specified in `docs/agent/TESTING_RULES.md` pass —
  actually run, not assumed.
- No known critical regression exists.
- The full set of changed files is known and listed (handoff `FILES
  CHANGED`).
- Documentation impact has been considered and, if applicable, applied
  per §Documentation above.
- The handoff (`docs/agent/HANDOFF_PROTOCOL.md`) is updated.
- Independent Review Agent requirements are satisfied where the
  workflow calls for review (`docs/agent/AGENT_WORKFLOW.md`).

**"Code exists" is not "task complete."**
