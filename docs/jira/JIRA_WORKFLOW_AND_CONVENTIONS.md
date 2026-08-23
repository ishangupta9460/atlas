# JIRA_WORKFLOW_AND_CONVENTIONS.md

## 1. Workflow

`Backlog → Ready → In Progress → Code Review → Testing → Done`, plus **Blocked** as a Flag (not a separate column) applied to an issue wherever it currently sits — see `JIRA_PROJECT_SETUP.md` §3–4 for setup, this document defines what each status means:

| Status | Meaning |
|---|---|
| Backlog | Not yet groomed/estimated |
| Ready | Estimated, dependencies satisfied, pull-ready for a sprint |
| In Progress | Actively being coded |
| Code Review | PR open, awaiting review |
| Testing | Merged, undergoing manual/exploratory verification beyond CI |
| Done | Meets the Definition of Done (`18_IMPLEMENTATION_PLAN_AND_DEFINITION_OF_DONE.md` §3) |
| Blocked (flag) | Cannot proceed — flag carries a comment explaining why and what unblocks it |

## 2. Labels

Small, fixed set — matches the Epic/engineering-document boundaries so a label search roughly mirrors an Epic filter when useful:

`backend` `frontend` `database` `scheduling` `ai` `ux` `security` `testing` `infrastructure`

A Story typically carries 2–3 labels (e.g. a scheduling Story: `backend`, `scheduling`, `testing`). Do not create labels per-Epic in addition to this list — the Epic field already provides that grouping; duplicating it as labels adds maintenance overhead for no query benefit.

## 3. Issue Naming

`ATLAS-<EPIC_ABBR>-<NUM> — <Title>`, e.g. `ATLAS-SCH-004 — Stage 2 Hard-Consequence Urgency Gate`. Epic abbreviations match the Epic keys in `JIRA_EPICS.md` (`FOUND`, `DOM`, `EVT`, `SCH`, `RESC`, `ROAD`, `AI`, `MEM`, `ANLY`, `UI`, `EXEC`, `SEC`, `OPS`). Note: Jira's actual issue key (e.g. `ATLAS-142`) is auto-assigned sequentially by Jira itself and cannot be forced to match this scheme — use this naming convention in the **Summary** field (as shown above) and let Jira's native key be whatever it auto-assigns; the backlog document's `SCH-004`-style keys are stable human references independent of Jira's internal numbering.

## 4. Sizing

Relative T-shirt sizing (`XS`/`S`/`M`/`L`/`XL`) as used throughout `JIRA_BACKLOG.md`. If Story Points are preferred instead (Jira Free supports the standard Points field on team-managed Scrum boards), map: XS=1, S=2, M=3, L=5, XL=8 (Fibonacci-like, standard practice) — deliberately coarse to avoid false precision on a solo project where velocity data doesn't exist yet.

## 5. Priority — Explicitly Separate From Atlas's Runtime Priority

Jira's Priority field (Highest/High/Medium/Low/Lowest, or a simplified subset) governs **development sequencing only** — which Story to pick up next. It has no relationship to Atlas's own Stage 0–8 scheduling hierarchy (`04_SCHEDULING_ENGINE.md`), which governs what the *running application* schedules for its end users. These are conceptually unrelated systems that happen to both use the word "priority" — do not let Jira priority values leak into product logic, and do not name a Jira field or label anything that could be confused with a Stage number.

## 6. GitHub Connection (Free-Compatible)

Two complementary mechanisms, both free:

1. **Smart Commits** — include the Jira issue key in commit messages (e.d., `ATLAS-142 #comment implemented Stage 2 gate #time 3h`) so commits auto-link and can auto-transition status. Requires the free **GitHub for Jira** app (Atlassian Marketplace, free tier) connecting the repo once.
2. **Manual fallback** (if the app isn't installed, or in a pinch): branch names include the issue key (`feature/ATLAS-142-stage-2-gate`), PR titles include the key, and the developer manually drags the Jira card to Code Review on PR-open and to Done on merge. No paid tooling required either way.

**Branch convention:** `feature/ATLAS-<key>-<short-slug>`, `fix/ATLAS-<key>-<short-slug>`. **PR convention:** title includes the Jira key; description links back to the Story's Acceptance Criteria so reviewers check against the real bar, not just "looks fine."

**Flow:** Jira Story (Ready) → developer starts branch → status auto/manually moves to In Progress → PR opened → status moves to Code Review → CI runs (`17_OPERATIONS_AND_DEPLOYMENT.md` §7) → merged → status moves to Testing → verified → Done.
