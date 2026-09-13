# HANDOFF_PROTOCOL.md — Persistent Handoff Format

Atlas is built by a rotating pool of AI coding agents with no shared
memory across sessions. The handoff is how work state survives an agent
switch, a quota exhaustion, a context limit, or an outage. It is not
optional and it is not a formality.

**Every agent leaving a task before final completion — and every agent
completing a task — MUST update the handoff for that task before ending
its session.**

The handoff lives in the repository (e.g., alongside the feature branch
or in a per-story location your repository has established), not in
Jira comments, not in chat history, not in any agent's memory. If the
repository has not yet established a location, create
`docs/agent/handoffs/<jira-key>.md` and use that consistently going
forward — record this choice once in `docs/agent/DECISION_LOG.md` as an
implementation decision if it isn't already recorded there.

---

## The Handoff Must Be Factual

Agents must not write **"Everything looks good"** — or any equivalent
unverified summary — without evidence. Every claim in a handoff must be
backed by something concrete:

- The actual command(s) run.
- The actual test results (pass count, fail count, specific failing
  test names) — not "tests pass."
- The actual list of files changed — not "updated the scheduling logic."
- The actual remaining work — not "mostly done."

A handoff that cannot be independently verified from the repository is
not a valid handoff. A resuming agent is expected to check these claims
(`docs/agent/AGENT_WORKFLOW.md` §5), not trust them outright — write the
handoff as if it will be checked, because it will be.

---

## Standard Template

```
TASK
Jira key:
Title:
Objective:

STATUS
[ ] Not started
[ ] In progress
[ ] Blocked
[ ] Ready for review
[ ] Complete

WHAT WAS DONE
- (concrete, specific actions — not intentions)

FILES CHANGED
- (full list, with a one-line description of each change)

DATABASE CHANGES
- (migration file(s) added/changed, what they do, rollback
  considerations — or "none")

API CHANGES
- (endpoint(s) added/changed, contract impact, whether
  12_API_SPECIFICATION.md was updated — or "none")

TESTS RUN
- (exact commands / test suite names actually executed)

TEST RESULTS
- (exact pass/fail counts and names of any failures — not "all good")

KNOWN FAILURES
- (anything not passing, and why, if known)

KNOWN RISKS
- (anything you're not confident about, even if tests pass — e.g.,
  an edge case you didn't get to, a performance concern, an assumption
  you made)

OPEN QUESTIONS
- (anything requiring product/architecture decision — see
  docs/agent/DECISION_LOG.md — that you could not resolve yourself)

ARCHITECTURAL CONCERNS
- (anything you noticed that seems off relative to the Atlas spec or
  prior code, that you did NOT fix because it was out of scope —
  see docs/agent/DEVELOPMENT_RULES.md Scope Control)

NEXT STEP
- (the single most useful next action for whoever picks this up)

DO NOT REPEAT
- (anything you tried that didn't work, so the next agent doesn't
  waste time re-discovering it)
```

Blank or "N/A" is an acceptable, honest answer for a section that
genuinely doesn't apply (e.g., `DATABASE CHANGES: none`). A missing
section, or a vague one, is not.

---

## How a New Agent Resumes

Before writing any code, read, in this order:

1. **The Jira story** — full description, acceptance criteria, `Source`
   and `Dependencies` fields.
2. **The relevant Atlas specification** section(s) the story cites
   (use `03_REQUIREMENTS_TRACEABILITY.md` if the mapping isn't obvious).
3. **The current handoff** for this task.
4. **The code/diff** as it currently stands — not as the handoff
   describes it, though they should match.
5. **The relevant tests** — read them, don't just note they exist.
6. **The decision log** (`docs/agent/DECISION_LOG.md`), if the handoff
   references an open question or a prior architectural decision.

Then **independently verify the state before continuing**: run the
tests the handoff claims pass, and actually look at the diff against
what the handoff claims changed. If everything checks out, proceed. If
something doesn't match, record the discrepancy in your own handoff
before doing anything else — do not silently "fix" the record and
pretend the prior claim was accurate, and do not silently continue as
if you hadn't noticed.

This mirrors `AGENTS.md`'s startup sequence
(`READ → UNDERSTAND → INSPECT → PLAN → IMPLEMENT → TEST → REVIEW →
HANDOFF`) — the handoff is read as part of INSPECT, not trusted as a
substitute for it.
