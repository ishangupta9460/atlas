# AGENT_WORKFLOW.md — The Atlas AI-Development Workflow

This document defines how work moves from a Jira story to a merged,
verified change in the Atlas repository, across a rotating pool of AI
coding agents (Claude Code, Codex, GitHub Copilot, Gemini/Antigravity,
local models, or future agents) plus human oversight.

It governs **process**. It does not define product behavior — see
`AGENTS.md` §2 for where that lives.

---

## 1. The Workflow

```
Jira Story
    ↓
Human + planning/reasoning model   (produces an Implementation Brief)
    ↓
Implementation Brief
    ↓
Coding Agent                       (READ → UNDERSTAND → INSPECT → PLAN
                                     → IMPLEMENT → TEST → REVIEW)
    ↓
Repository (feature branch, commits, tests, handoff)
    ↓
Handoff                            (docs/agent/HANDOFF_PROTOCOL.md)
    ↓
Independent Review Agent           (adversarial verification)
    ↓
PASS ──────────────────────────────┐
    │                               │
   FAIL                             │
    ↓                               │
Fix (same or different coding agent)│
    ↓                               │
(back to Independent Review Agent)  │
                                     ↓
                          Human verification
                                     ↓
                                  Merge
                                     ↓
                             Jira update
```

No step is optional. A story does not skip the Independent Review Agent
stage just because the coding agent is confident, and it does not skip
human verification just because the Review Agent passed it.

## 2. Roles

**Human / product owner.** Holds authority over product decisions (see
`docs/agent/DECISION_LOG.md` for the Product/Architecture/Implementation
decision distinction). Resolves open product decisions, approves
Implementation Briefs, gives final merge sign-off. Not expected to review
every line of code, but is the backstop when a coding agent or reviewer
flags a genuine ambiguity.

**ChatGPT / reasoning assistant (planning stage).** Turns a Jira story
plus its cited spec sections into a concrete Implementation Brief: what
to build, which files/areas it likely touches, what tests are expected,
what's explicitly out of scope. Does not write the final code. Does not
have authority to change product requirements — if the story and the
spec don't cleanly agree, this stage surfaces that rather than resolving
it unilaterally.

**Coding agent.** Implements the Implementation Brief in the repository,
following `docs/agent/DEVELOPMENT_RULES.md` and
`docs/agent/TESTING_RULES.md`. Produces the handoff. May implement; may
not unilaterally redefine what "done" means for the story, and may not
mark its own work as independently reviewed.

**Review agent.** A separate agent session (ideally a different
model/provider than the implementer, or at minimum a fresh context with
no stake in the implementation) that adversarially verifies the work per
`docs/agent/DEVELOPMENT_RULES.md`'s Review Agent Policy. May critique and
fail work. May not unilaterally redefine product requirements either —
a reviewer who thinks the *requirement* is wrong records that as an open
question, it does not silently approve or reject based on a different
requirement it prefers.

**Repository.** The persistent record of actual state: code, tests,
migrations, commit history, and the handoff file(s). Nothing is true
because an agent said so; it's true because it's in the repository and
the tests demonstrate it.

**Jira.** The backlog of record — what's in scope, in what order, with
what acceptance criteria and dependencies (`03_REQUIREMENTS_TRACEABILITY.md`
is the map from story to owning spec section). Agents read Jira; they do
not edit it (see `AGENTS.md` §7).

**Tests.** Evidence, not ceremony. See `docs/agent/TESTING_RULES.md`.

## 3. Neither Side Redefines the Product

The coding agent may implement. The reviewer may critique. **Neither
gets to unilaterally redefine Atlas product requirements.** If either
believes a requirement is wrong, incomplete, or internally
contradictory, the correct action is:

1. Check whether it's already resolved (`REVIEW_CHANGELOG.md`,
   `19_CONSISTENCY_AUDIT_AND_GENUINE_GAPS.md`,
   `MISSING_SPEC_RECONSTRUCTION_REPORT.md` §4's consolidated open
   decisions).
2. If not, record it as an open question in the handoff and propose an
   entry in `docs/agent/DECISION_LOG.md`.
3. Implement only what's unambiguous; explicitly flag what was left out.
4. Escalate to the human/product owner for anything requiring product
   authority.

## 4. Choosing and Switching Agents

- Any agent capable of following this workflow and the rules in
  `docs/agent/DEVELOPMENT_RULES.md` may be assigned a story. The choice
  of which agent (Claude Code, Codex, Copilot, Gemini/Antigravity, a
  local model, etc.) is an operational decision, not a product one, and
  does not need a `DECISION_LOG.md` entry unless it has architectural
  consequences (e.g., adopting agent-specific tooling into the repo).
- Switching agents mid-task is expected and normal — see §5.
- The **same agent should not review its own implementation.** Review
  Agent independence (§6) is a hard requirement, not a preference.

## 5. Agent Quota Exhaustion / Resuming Work

When an agent's usage limit, context window, or session ends before a
task is complete:

1. It follows `AGENTS.md` §11 (Stopping Safely) and produces a complete,
   honest handoff per `docs/agent/HANDOFF_PROTOCOL.md`.
2. The next agent — regardless of which provider or model — resumes by
   reading, in order: the Jira story, the relevant Atlas specification
   sections, the current handoff, the code/diff so far, the relevant
   tests, and the decision log if referenced.
3. The resuming agent **independently verifies** the claimed state
   (runs the tests said to pass, reads the diff) before continuing. A
   handoff is a claim to check, not a fact to trust blindly — this is
   the same "AI proposes, deterministic logic decides" posture Atlas
   itself uses (`01_SYSTEM_ARCHITECTURE.md` §2), applied to agent output.
4. If the resuming agent finds the handoff inaccurate (tests don't
   actually pass, described changes don't match the diff), it records
   that discrepancy in its own handoff rather than silently correcting
   the record and moving on.

## 6. Reviewer Independence

- The Review Agent must not be the same session, and preferably not the
  same underlying model, that implemented the change.
- The Review Agent has no stake in the implementation being accepted —
  its job is adversarial verification (`docs/agent/DEVELOPMENT_RULES.md`
  Review Agent Policy), not approval theater.
- The Review Agent checks: Jira acceptance criteria, the cited spec
  sections, code correctness, test adequacy (not just test presence),
  regressions, security, architecture/layer-boundary compliance, scope
  discipline (did the change stay inside the story), and documentation
  impact.
- The Review Agent is expected and encouraged to FAIL work that doesn't
  meet these. A pass from a reviewer that never fails anything is not
  a functioning review stage.

## 7. Failed Review

On FAIL, the Review Agent's findings go into the handoff (or a fresh
handoff section) as concretely as the original handoff template
requires — not "needs work," but what specifically failed and why. The
same or a different coding agent then addresses the findings and the
change returns to the Review Agent stage. This can repeat; there is no
cap other than good judgment — a story that fails review repeatedly is
itself a signal worth recording (possibly as a `DECISION_LOG.md`
candidate if it reveals a spec ambiguity rather than an implementation
bug).

## 8. Interrupted Sessions / Context Limits / Emergency Stopping

Same handling whether the interruption is planned (quota nearing) or
unplanned (a crash, an outage, an abrupt provider issue):

- The repository and the last committed handoff are the only things the
  next agent can rely on. If no handoff exists for in-flight work, the
  next agent must treat the code state as unverified and re-inspect from
  scratch before continuing — it must not assume good intent from an
  absent handoff.
- Never leave partially-applied migrations or half-written schema
  changes uncommitted and undocumented — an interrupted database change
  is exactly the situation `docs/agent/DEVELOPMENT_RULES.md`'s migration
  safety rules exist for.
- If a provider outage happens mid-task with no chance to write a
  handoff, the next agent's first job is reconstructing what happened
  from the actual diff/commits before doing anything else — this is
  strictly worse than a good handoff and should be treated as a
  degraded-trust situation.

## 9. Human Verification and Merge

Human verification is not redundant with the Review Agent — the Review
Agent checks correctness and compliance; the human confirms the result
is actually what the business wanted and authorizes merge, per the
branch policy in `docs/agent/DEVELOPMENT_RULES.md`. Merge does not happen
on coding-agent or review-agent authority alone.

## 10. Jira Update

After merge, the story's status and any Jira-recommended changes
surfaced during the work (e.g., an acceptance-criteria gap noticed, per
`03_REQUIREMENTS_TRACEABILITY.md`'s existing "Jira change recommended"
notes) are reported for the human/product owner to apply — agents do not
edit Jira directly (`AGENTS.md` §7).
