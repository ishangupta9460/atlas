# 07 — AI Architecture

**Authoritative for:** AI responsibilities/non-responsibilities, structured proposal schemas, validation, confidence, and failure handling.
**Source:** Master Spec §1.10.
**Governing invariant (defined authoritatively in `01_SYSTEM_ARCHITECTURE.md` §2, not restated here in full):** AI proposes, deterministic Atlas logic validates and commits.

---

## 1. AI Responsibilities

AI may: estimate duration, suggest task splits, suggest/replace resources, extract roadmap structure from documents, suggest goal revisions, summarize progress, ask clarifying questions (tagged per `02` question-reason categories — see `08_MEMORY_AND_PREFERENCES.md` §2 for how confirmed answers become memory).

## 2. AI Non-Responsibilities

AI never: writes to the schedule directly, sets a completion percentage as ground truth, creates or deletes entities without the Domain layer's validation step, decides Stage 0–8 placement (that is `04_SCHEDULING_ENGINE.md`'s deterministic responsibility, and Stage 0–8 evaluation contains no AI call — see `04` §2.4), silently converts an observation into a saved preference (`06` §3.2, `08` §1).

## 3. Structured Proposal Schema

Every AI response is one of the following proposal types. Fields are illustrative of required shape, not exhaustive:

| Proposal type | Input | Output shape | Confidence field? |
|---|---|---|---|
| `estimate_duration` | task description | `{task_ref, estimated_minutes}` | Yes |
| `suggest_split` | task, historical completion data | `{task_ref, sessions: [{minutes, order}]}` | Yes |
| `suggest_resource` | task/topic | `{task_ref, resource: {type, title, url}}` | Yes |
| `extract_roadmap` | uploaded document | `{nodes: [{type, title, parent_ref, confidence}]}` | Per-node |
| `suggest_goal_revision` | goal, risk snapshot | `{goal_ref, options: [...]}` | No (presents options, doesn't rank a single answer) |
| `summarize_progress` | session/event history | `{summary_text, dominant_factor}` | No |
| `classify_hard_consequence` | task title/description/deadline context | `{task_ref, is_hard_consequence, rationale}` | Yes |
| clarifying question | current task-creation/scheduling context | `{question_text, reason_tag}` | N/A |

Every proposal carries: `proposal_id`, `type`, `generated_at`, and the fields above. No proposal type includes a field that would directly mutate persisted state (no `commit=true` flag, no direct entity ID write authority).

## 4. Validation (Domain Layer Responsibility, Executed Here for Documentation Completeness)

Before any proposal influences state:
1. Referenced entities (`task_ref`, `goal_ref`) must exist and belong to the requesting user.
2. Proposal must not conflict with a Non-Negotiable Rule (Master Spec §3) — e.g., an `extract_roadmap` proposal cannot bypass the Review/Approve gate (`06` §1.4–1.5) regardless of confidence.
3. Confidence is resolved via three bands rather than a single universal numeric cutoff: **High** → proposal is safe to apply autonomously (still logged, per `05` §7 Autonomous tier where applicable); **Medium** → surfaced for user confirmation before applying (Collaborative tier); **Low** → not applied at all — either asked about explicitly or left untouched, never silently guessed. The exact numeric boundaries between bands live in the AI implementation/config layer, not in product/business logic, and are tunable without a code change to the Domain layer — consistent with `06` §2's conservative-default rule for uncertain roadmap interpretation.

## 5. Failure Handling

| Failure mode | Behavior |
|---|---|
| AI unavailable / provider down | Domain and Scheduling Engine continue normally; UI indicates AI-assisted suggestions are temporarily unavailable; no blocking of core flows |
| Rate limit hit | Same as above; request queued or deferred per `17_OPERATIONS_AND_DEPLOYMENT.md` §3 retry policy |
| Timeout | Treated as unavailable for that call; deterministic fallback used where one exists (e.g., deterministic roadmap parse stands alone without the AI-assisted enhancement pass) |
| Malformed/unparseable output | Rejected at the validation step (§4); logged; treated as unavailable for that call |

## 6. Context Selection & Privacy Boundary

Only the minimum context needed for a given proposal type is sent to the AI provider — not the user's full history by default (Master Spec §3 rule 15). Full boundary detail and data-minimization rules owned by `15_SECURITY_AND_PRIVACY.md` §4; this document only asserts that AI calls must comply with that boundary, it does not redefine it.

## 7. Cost Controls

Rate/cost limiting mechanics (quotas, backoff) are an operational concern owned by `17_OPERATIONS_AND_DEPLOYMENT.md` §3 — referenced here, not duplicated.

## 8. Genuine Gaps / Requires Product Decision

*(Resolved — see §4 item 3 above: three-band confidence model, exact numeric boundaries tunable in config, not fixed in this document.)*
