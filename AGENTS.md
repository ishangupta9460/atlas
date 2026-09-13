# AGENTS.md — Start Here

This is the first file any AI coding agent (or human developer acting like
one) must read before touching the Atlas repository. It tells you what
Atlas is, where the truth lives, and how to work here safely across a
rotating pool of agents with no shared memory.

This file governs **how work happens**. It does not define **what Atlas
is**. If anything here appears to conflict with the Atlas product
specification, the specification wins — stop and record the conflict
(see "When requirements conflict" below) rather than guessing.

---

## 1. What Atlas Is (high level only)

Atlas is a scheduling/productivity system built around one loop:
`intention → roadmap → schedule → work → progress → learning`. A
deterministic Scheduling Engine decides what gets time and where; an AI
Proposal Layer may suggest things but never writes state directly; every
state-changing write is paired with an Event Log entry in the same
transaction. That's it for the summary — do not rely on this paragraph
for implementation detail. Go read the real documents.

## 2. Where the Truth Lives

| Question | Authoritative source |
|---|---|
| What should this feature do? | `01`–`19` numbered Atlas specification documents (see `03_REQUIREMENTS_TRACEABILITY.md` for the map from Jira story → owning spec section) |
| What was decided when documents disagreed? | `ATLAS_SPECIFICATION_REVIEW.md`, `REVIEW_CHANGELOG.md`, `MISSING_SPEC_RECONSTRUCTION_REPORT.md`, `19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md` |
| What is currently in scope to build, and in what order? | `Jira.csv` (backlog of record) |
| What has this repo's development process itself decided? | `docs/agent/DECISION_LOG.md` |
| What is the state of an in-progress task? | The most recent handoff for that task (`docs/agent/HANDOFF_PROTOCOL.md`) |
| What actually works right now? | The code and the tests — not a prior agent's claims |

Jira is the implementation backlog. The numbered specification documents
define intended product behavior. Neither this file nor any other
document in `docs/agent/` is a third source of product truth — they only
govern process.

## 3. Mandatory Startup Sequence

Every agent session, before writing any code, follows this sequence:

```
READ → UNDERSTAND → INSPECT → PLAN → IMPLEMENT → TEST → REVIEW → HANDOFF
```

1. **READ** — the assigned Jira story in full, including `Source` and
   `Dependencies` fields; the spec section(s) it cites; any existing
   handoff for this story; the relevant Genuine Gaps / Open Product
   Decision notes in the owning document.
2. **UNDERSTAND** — restate the acceptance criteria in your own words
   before coding. If the spec section cited is marked `[OPEN PRODUCT
   DECISION]` or the story's status in `03` is "Blocked by open product
   decision," stop and escalate (see below) rather than guessing.
3. **INSPECT** — the actual repository: existing services, migrations,
   tests, and patterns touching this area. Never assume; look.
4. **PLAN** — a short, scoped plan limited to this story. See
   `docs/agent/DEVELOPMENT_RULES.md` for scope-control rules.
5. **IMPLEMENT** — per `docs/agent/DEVELOPMENT_RULES.md`.
6. **TEST** — per `docs/agent/TESTING_RULES.md`. No story is complete
   because it compiles.
7. **REVIEW** — self-review against the acceptance criteria and against
   the Atlas invariants (§6 below) before handing off. Self-review is
   not a substitute for the independent Review Agent stage described in
   `docs/agent/AGENT_WORKFLOW.md`.
8. **HANDOFF** — update the handoff record per
   `docs/agent/HANDOFF_PROTOCOL.md`, every time, whether the task is
   finished or not.

## 4. Branch / Worktree Expectations

See `docs/agent/DEVELOPMENT_RULES.md` §Git/Branch Policy for full detail.
Summary: work on `feature/<jira-key>-<short-name>`, never commit directly
to `main`, never force-push a shared branch, merge only after the
required verification in `docs/agent/AGENT_WORKFLOW.md` has actually
happened.

## 5. Coding, Testing, Documentation, Handoff — Where the Rules Live

- Engineering rules (scope, architecture, DB, API, security, error
  handling): `docs/agent/DEVELOPMENT_RULES.md`
- What must be tested and what must pass before merge:
  `docs/agent/TESTING_RULES.md`
- How to leave work for the next agent: `docs/agent/HANDOFF_PROTOCOL.md`
- How decisions get recorded so they outlive any one conversation:
  `docs/agent/DECISION_LOG.md`
- The full multi-agent workflow, review-agent role, and quota/outage
  handling: `docs/agent/AGENT_WORKFLOW.md`

## 6. Non-Negotiable Development Invariants

These apply regardless of which agent is working or why:

1. Jira is the implementation backlog.
2. The Atlas specification defines intended product behavior — code
   implements it, it does not invent a competing product.
3. Tests are evidence that behavior works; claims without evidence are
   not evidence.
4. AI coding agents are workers, not product owners.
5. No agent may silently change product requirements.
6. No agent may silently change architecture.
7. No agent may silently weaken or delete a test to make a build pass.
8. No agent may claim completion without verification.
9. Uncertainty is recorded, never hidden.
10. Deterministic Atlas logic remains authoritative over AI suggestions
    (Atlas's own AI-boundary invariant, `01_SYSTEM_ARCHITECTURE.md` §2 —
    this governs the *product's* AI layer; it also sets the tone for how
    this repository treats coding-agent output: proposed, reviewed,
    never auto-trusted).
11. AI proposes; validation occurs; the deterministic core controls
    state — true both inside Atlas's own architecture and in how a
    coding agent's output is treated before merge.
12. User-facing consequential behavior must be explainable — code that
    can't say why it did something is not done.
13. No unrelated refactoring during a scoped Jira task.
14. Every agent must leave the repository in a usable, honestly
    described state, whether or not the task is finished.

## 7. Prohibited

Do not: invent product features; modify Jira directly; weaken acceptance
criteria; delete or disable tests to make CI pass; silently change
architecture; make destructive/irreversible database changes outside the
established migration mechanism; commit secrets or log sensitive values;
rewrite unrelated code "while you're in there"; treat your own or a prior
agent's reasoning as authoritative over the spec, tests, or code; store
essential context only in conversation memory.

## 8. Reporting Uncertainty

If you are unsure whether a behavior is specified, check `03
_REQUIREMENTS_TRACEABILITY.md`'s status column and the owning document's
Genuine Gaps section first. If it is genuinely unspecified or marked
`[OPEN PRODUCT DECISION]`:

- Do not guess a product answer.
- Do not silently pick the "reasonable" interpretation and proceed as if
  it were settled.
- Record the uncertainty in the handoff (`OPEN QUESTIONS`) and, if it's
  architecturally or product-significant, propose an entry in
  `docs/agent/DECISION_LOG.md` per that document's rules on who has
  authority to decide what.

## 9. When Requirements Conflict

If Jira's acceptance criteria and a spec document disagree, or two spec
documents disagree: stop. Do not silently pick one. Check
`REVIEW_CHANGELOG.md` and `19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md`
first — the conflict may already be resolved there. If it isn't, record
it in the handoff and in `docs/agent/DECISION_LOG.md` as an open
question requiring human/product-owner authority. Implement only the
part of the story that is unambiguous, and say explicitly what you left
out and why.

## 10. When Tests Fail

A failing test is signal, not an obstacle. Do not delete it, skip it, or
loosen its assertions to make it pass unless you can show the test
itself encoded an already-superseded requirement (cite the spec section
that changed). If a test fails and you don't know why, that goes in the
handoff's `KNOWN FAILURES`, not silently past it.

## 11. Stopping Safely (quota / context ending)

When you expect your context or usage quota to run out before the task
is finished:

- Finish the smallest coherent unit of work you can, rather than leaving
  something half-edited.
- Run whatever tests are relevant to what you actually changed.
- Update the handoff (`docs/agent/HANDOFF_PROTOCOL.md`) completely and
  honestly — status, what's done, what isn't, what's risky.
- Never claim "complete" or "everything looks good" without the evidence
  the handoff template requires.
- Leave the repository in a state the next agent (who may be a different
  model from a different provider, with none of this conversation) can
  pick up from cold, using only the repository, Jira, and the handoff.
