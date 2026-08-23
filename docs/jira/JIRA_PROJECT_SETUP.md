# JIRA_PROJECT_SETUP.md — Jira Free Setup Guide

Everything below uses only Jira Free capabilities. No paid plan, premium automation, advanced roadmaps, or paid marketplace apps required.

## 1. Project Creation

Create a **Team-managed Software project**, template **Scrum**. Team-managed projects are the correct choice for Free — they're simpler to configure directly (workflow, fields, board) without needing admin-level project-scheme access, which matters for a solo developer who is also the Jira admin.

- Project name: `Atlas`
- Project key: `ATLAS`

## 2. Issue Hierarchy

Jira Free (team-managed Scrum) supports: **Epic → Story/Task/Bug → Sub-task**. Use exactly this — no custom issue types needed.

- **Epic** — a capability area (see `JIRA_EPICS.md`). Create via Backlog view → Epics panel → Create epic.
- **Story** — a meaningful vertical slice of user-facing or system behavior (see `JIRA_BACKLOG.md`). Set its Epic via the Epic field on the issue.
- **Task** — non-user-facing engineering work not meaningful enough to be its own Story (rare in this backlog — most work is captured as Sub-tasks under a Story instead, per §8 of the source brief: don't create a Story per implementation detail).
- **Sub-task** — concrete implementation steps under a Story (domain model, migration, service, controller, frontend, tests). Create from within the parent Story via "Add sub-task."

## 3. Workflow

Team-managed projects let you edit the workflow directly (Project settings → Workflow) without a global workflow scheme. Create these statuses, in this order:

`Backlog → Ready → In Progress → Code Review → Testing → Done`

Add **Blocked** as an additional status (not inserted into the main flow — issues move into Blocked from wherever they are, and back out again) — this is Free-compatible since custom statuses and simple transitions are supported on team-managed boards.

## 4. Board Configuration

Project settings → Board → configure columns to map: `Backlog`+`Ready` → **To Do** column (Ready can also be its own column if you prefer more granularity — Free supports up to the practical column count needed here, well within limits), `In Progress` → **In Progress**, `Code Review`+`Testing` → **In Review** (or two separate columns if preferred), `Done` → **Done**. `Blocked` issues stay visible in their real column with the Blocked status/flag applied (Free supports the built-in "Flag" feature for this — right-click an issue → Flag — which is simpler than a full custom status and works on Free).

## 5. Backlog Organization

Backlog view groups by Epic automatically once each Story has its Epic field set. Order Stories within each Sprint by drag-and-drop priority (top = do first). Use the Epics panel filter to check any single Epic's full story list at a glance.

## 6. Sprints

Create Sprints directly from the Backlog view ("Create sprint"). Name them by number and goal, e.g. `Sprint 0 — Walking Skeleton`. Drag Stories from Backlog into the active/planned Sprint per `JIRA_SPRINT_PLAN.md`. Start/complete sprints from the same view — standard Free Scrum functionality, no add-on needed.

## 7. Labels / Components

Use **Labels** (not Components) for the small tag set in `JIRA_WORKFLOW_AND_CONVENTIONS.md` §2 — Labels are simpler to manage solo and fully Free. Components are optional and skippable for a solo project; if the team grows, Components can be introduced later to mirror the Epic groupings without needing a plan upgrade.

## 8. Versions / Releases

Marginally useful here. Atlas isn't shipping discrete numbered releases to external customers during this build phase — Sprints already serve as the practical increments. Skip Versions/Releases entirely for now; revisit only if Atlas reaches a point where distinct release tagging (e.g. "v1.0 public") becomes meaningful, which is Free-compatible whenever that happens.

## 9. Reports (Free-Available)

- **Sprint Report** — end-of-sprint completed-vs-carried-over view. Useful every sprint.
- **Burndown Chart** — only meaningful if using Story Points (see `JIRA_WORKFLOW_AND_CONVENTIONS.md` §4); optional for a solo dev, genuinely useful once sprint-to-sprint velocity matters.
- **Velocity Chart** — useful after 3+ sprints of history, to sanity-check future sprint sizing.
- **Cumulative Flow Diagram** — good for spotting a bottleneck status (e.g. things piling up in Code Review) once the team grows past one person.

All four are included in Jira Free team-managed Scrum projects — no report here requires an upgrade.

## 10. GitHub Connection

See `JIRA_WORKFLOW_AND_CONVENTIONS.md` §6 for the full manual/free-tier workflow (smart commits + the free GitHub-for-Jira app). No paid integration required.
