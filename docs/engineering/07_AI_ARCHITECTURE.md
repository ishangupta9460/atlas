# 07 — AI Architecture

**Status: RECONSTRUCTED.** This document was not present in the uploaded package, despite being one of the most heavily cross-referenced documents in it — `01`, `02`, `04`, `06`, `08`, `11`, `12`, `14`, `15`, `16`, `17`, `18`, `19`, and Jira epic `AI Architecture` (`AI-001`–`AI-012`) all cite it by section number. Section numbers below (`§1`–`§8`) are fixed by those existing citations — do not renumber without updating every document listed above. Every claim below is tagged EXPLICIT / STRONGLY INFERRED / OPEN per the evidence actually available; see the companion `RECONSTRUCTION_07_09_18_REPORT.md` for the full citation-by-citation trail.

**Depends on:** `01_SYSTEM_ARCHITECTURE.md` §2 (the AI/Domain boundary invariant is defined authoritatively there — this document elaborates it, never restates it), `02_DOMAIN_MODEL_AND_STATE_MACHINES.md` (the entities proposals reference), `06_ROADMAP_AND_RESOURCE_SYSTEM.md` (conservative-default rule this document's confidence banding must stay consistent with), `08_MEMORY_AND_PREFERENCES.md` (the confirmation write path AI proposals feed into, never bypass).

**Governing invariant (defined authoritatively in `01_SYSTEM_ARCHITECTURE.md` §2, not restated here in full):** AI proposes; the Domain layer validates; the deterministic core remains authoritative for all state. The AI Proposal Layer never writes to `commitments`, `goals`, `scheduled_blocks`, `user_preferences`, or any state-bearing table directly (`01` §2). This document owns *what* AI is allowed to propose and how those proposals are shaped, validated, and degrade — not *whether* AI may ever hold authority, which `01` §2 already settles.

## 1. AI Responsibilities

**[EXPLICIT]** — synthesized from the proposal-type list (§3) and the clarifying-question mechanism cited by `08` §1 ("surfaced conversationally by the AI Proposal Layer as a clarifying question (`07` §1) tagged with one of `02`'s reason categories") and by `14` §1/§3 (progressive onboarding questions, `AI-003`'s acceptance criteria: "1–2 at a time, each tagged with a reason").

Atlas's AI layer is responsible for:
- **Ask clarifying questions** — one or two at a time (`AI-003`), each tagged with a reason per `02`'s question-reason categories (`task_creation`, `scheduling`, `conflict_resolution`, `uncertainty`, `goal_risk` — Master Spec §1.9) — see `08_MEMORY_AND_PREFERENCES.md` §2 for how a confirmed answer becomes memory.
- **Produce structured proposals** (§3) for estimation, splitting, resource suggestion, hard-consequence classification, roadmap extraction, goal-revision options, and progress summarization — never free-form instructions and never a proposal type not listed in §3.
- **Improve classification accuracy on top of deterministic output** — e.g., `extract_roadmap` (`AI-008`) re-classifies or re-titles nodes the deterministic roadmap parser (`06` §1.2) already produced; it does not replace that pass.

## 2. AI Non-Responsibilities

**[EXPLICIT]** — this is the section `06` §1.4's confirmation gate and `08` §1's single-write-path rule both cite compliance with ("AI never converts a Tier-1 reaction directly into a Tier-3 preference — it may only surface the Tier-2 observation, exactly as `07` §2 states"). This list exists because every one of these boundaries is independently load-bearing elsewhere in the package, and stating them once here removes any ambiguity about where the line sits.

AI **never**:
- Writes directly to any state-bearing table (`01` §2) — every effect passes through a typed proposal (§3) and Domain-layer validation (§4).
- Commits an imported roadmap or a screenshot-parsed Fixed Commitment candidate without the user's explicit Review/Approve action (`06` §1.4–1.5, `06` §4 — resolved per Final Product Decision #1) — no proposal type carries a `commit=true`-style field that would let it (§4).
- Silently converts an observation into a saved preference (`06` §3.2's Tier-2 pattern, `08` §1, `08` §5's resolved repeated-manual-move behavior per Final Product Decision #2) — the confirmation step is the *only* write path, not a formality layered on top of an AI write.
- Silently cascades a cancellation through a dependency graph (`05` §10, resolved per Final Product Decision #3) — cancellation-impact selection is a user action mediated by the API/UI layer (`12` §4, `14` §6.1), never an AI proposal type.
- Blocks a core deterministic flow on its own availability (§5) — Stage 0–8 scheduling evaluation, task creation, and event logging all complete with or without AI.
- Introduces nondeterminism into Stage 0–8 evaluation (`04` §2.4) — `is_hard_consequence` defaults `false` until AI classification returns, and a placement decided before that return is not retroactively invalidated; the later classification is handled as a normal Autonomous/Collaborative re-placement (§3 `classify_hard_consequence`, `04` §2.2, `05` §7).

## 3. Proposal Types & Schema

**[EXPLICIT]** — `AI-001`'s acceptance criteria: "Every AI call returns a validated typed proposal, never free-form control," sourced to `` `07` §3–4, `01` §2 ``. Every proposal shares a base envelope; individual types add their own payload fields.

**Base envelope (`AI-001`):**
```
{
  proposal_type: enum,      // one of the ten types below
  target_entity_id: uuid,   // what this proposal is about (nullable for extraction-time proposals with no entity yet, e.g. extract_roadmap)
  confidence: enum(High|Medium|Low),  // per-item where a proposal produces multiple items (e.g. extract_roadmap's per-node confidence)
  payload: { ... }          // type-specific fields, listed below
}
```
No proposal type may carry a field that directly commits state (e.g., no `commit=true`, no `apply_immediately`) — every proposal is read by the Domain layer and, where relevant, surfaced to the user; nothing in this schema allows a proposal to skip validation (§4) or a required Review/Approve gate (§2).

| `proposal_type` | Jira | Payload (evidence) | Consumed by |
|---|---|---|---|
| `estimate_duration` | `AI-004` | `{estimated_minutes, confidence}` | Task creation flow (`14` §1) |
| `suggest_split` | `AI-005` | `{proposed_sub_sessions: [{title, minutes}], confidence}` | Session planning |
| `suggest_resource` | `AI-006` | `{resource_ref, confidence}` | `06` §3 Resource attachment; feeds `06` §3.2's Tier-2 feedback loop |
| `classify_hard_consequence` | `AI-007` | `{is_hard_consequence: boolean, rationale_text, confidence}` | `02` §1.4's `is_hard_consequence` field, `04` §2.2's Stage 2 gate — **only surfaced to the user for confirmation when the classification is about to materially change a scheduling decision** (`04` §2.2), not at task creation by default |
| `extract_roadmap` | `AI-008` | `{nodes: [{type, title, parent_ref, confidence}]}` | `06` §1.3's AI-assisted ingestion layer, re-classifying/re-titling the deterministic pass's output |
| `suggest_goal_revision` | `AI-009` | `{options: [{description, tradeoff}]}` — **explicitly a list of options, never a single ranked answer** (`AI-009`'s own acceptance criteria) | `05` §5's At-Risk options list |
| `summarize_progress` | `AI-010` | `{summary_text, dominant_factor}` | `11` §4's why-falling-behind analysis — this proposal type phrases the factor `11` computes; it does not compute it itself |
| *(clarifying question)* | `AI-003` | `{question_text, reason_tag}` | `08` §2 (confirmed answer → memory), `14` §1/§3 |

**Note on count:** the decision brief's evidence list names ten concepts (`AI-001`–`AI-012`, with `AI-002` and `AI-011`/`AI-012` being cross-cutting mechanisms rather than proposal *types*). `AI-002` (failure handling, §5), `AI-011` (confidence banding, §4/§8), and `AI-012` (rate/cost protection, §7) are not themselves proposal types — they are properties every proposal type in the table above must satisfy. This is why the table has seven rows plus the clarifying-question mechanism, not twelve.

## 4. Validation, Confidence Banding & Progressive Clarifying Questions

**[EXPLICIT]** — `AI-001`'s validation layer sub-task, `AI-011`'s "High/Medium/Low, config-driven boundaries," and `AI-003`'s "1–2 at a time, each tagged with a reason."

**Validation (`AI-001`):** every proposal is checked, before it can influence anything, against: (a) schema conformance to §3's envelope and the type-specific payload shape; (b) entity ownership — `target_entity_id`, if present, must belong to the requesting user's own data (same user-scoping principle as `15` §2, enforced here at the proposal boundary rather than only at the API boundary); (c) Non-Negotiable Rule conflict check — a proposal that would, if acted on, violate a Master Spec Non-Negotiable Rule (e.g., a `classify_hard_consequence` payload somehow implying a silent cascade) is rejected outright, not degraded to Low confidence. A proposal failing any of these three checks is treated identically to a malformed-output failure (§5) — logged, discarded, and the flow that requested it falls back to its deterministic or user-prompted path.

**Confidence banding (`AI-011`, resolved — see §8):** every proposal's `confidence` field is one of `High` / `Medium` / `Low`, not a raw numeric score, replacing what would otherwise be an arbitrary magic-number threshold. This is the same three-band model `06` §1.3's conservative-default rule (`06` §2) explicitly builds on ("Low confidence band per `07` §4's three-band model"). Consuming documents apply the bands differently by context: `06` §2 uses Low confidence to trigger conservative under-classification during roadmap ingestion; `04` §2.2 uses it to decide whether `classify_hard_consequence`'s result is surfaced for confirmation or applied silently.

**Progressive clarifying questions (`AI-003`):** at most one or two questions are asked at once, never a batch form, each carrying a `reason_tag` from `02`'s question-reason categories (Master Spec §1.9) so the UI (`14` §1, §3) can explain *why* Atlas is asking. A confirmed answer becomes memory through `08` §2's single write path (`08` §1) — this document's clarifying-question mechanism is the *source* of the raw observation `08` §1 describes; it is never itself a write.

## 5. Failure Handling (AI Unavailable, Timeout, Malformed Output, Rate Limit)

**[EXPLICIT]** — this is the table `16` §6 cites by number as its AI-down test matrix, and `AI-002`'s acceptance criterion: "Core flows unaffected by any AI failure mode."

| Scenario | Handling |
|---|---|
| AI unavailable / provider down | Domain and Scheduling Engine continue normally on deterministic paths and last-known-good state; UI indicates AI-suggestions are temporarily unavailable (`14`, reconstructed) rather than blocking; no core flow (task creation, scheduling, execution, event logging) blocks. |
| Rate limit hit | Request queued or deferred per `17_OPERATIONS_AND_DEPLOYMENT.md` §3's retry policy — an operational concern owned there, not redesigned here (§7). |
| Timeout | Treated identically to "unavailable" for that call. Where a deterministic fallback exists (e.g., roadmap ingestion's deterministic pass, `06` §1.2, which stands alone regardless of AI availability), that fallback is used; where none exists (e.g., `estimate_duration` returning nothing), the flow proceeds without the AI-supplied value rather than blocking on it. |
| Malformed / unparseable output | Rejected at §4's validation step; logged; treated as unavailable for that call. This includes any payload attempting a field §3 explicitly disallows (e.g., an attempted `commit=true`-style field) — such an attempt is malformed by definition, not merely low-confidence. |

**Interaction with the hard-consequence timing race (`02` §1.4, `04` §2.2, preserved unchanged by this reconstruction):** `is_hard_consequence` defaults `false` before `classify_hard_consequence` returns. Scheduling does not block on AI — initial placement proceeds under the default. If classification later returns `true`, the resulting placement change is handled through the existing Autonomous/Collaborative re-placement mechanism (`05` §7), not a special-cased retroactive correction, and it does not corrupt or duplicate the task's Scheduled Block history (`02` §2.4's Superseded-not-deleted model). AI timing must not, and per this design does not, make Stage 0–8 evaluation nondeterministic (`04` §2.4) — a mocked AI failure or hang must produce the same Stage 0–8 result as AI success, since the engine never reads a value AI hasn't yet supplied as anything other than its documented default.

## 6. Context Minimization

**[EXPLICIT]** — `15_SECURITY_AND_PRIVACY.md` §4 states the rule and explicitly frames this document as only asserting compliance with it: "Only the minimum context needed for a given proposal type is sent to the AI provider (`07_AI_ARCHITECTURE.md` §6) — not full user history by default. This document owns the boundary rule; `07` only asserts compliance with it." Accordingly: each proposal type in §3's table receives only the fields its payload construction actually requires (e.g., `estimate_duration` receives the task's title/description/category, not the user's full task history; `summarize_progress` receives the specific analytics output `11` §4 already computed, not raw Event Log rows). `SEC-004`'s acceptance criterion ("AI context minimization enforcement") is verified by auditing outgoing AI requests against this per-type minimum (`16` §8's security test suite).

**[OPEN / genuinely unresolved]** — whether the AI Proposal Layer runs in-process or as an external provider call is recorded as an open, low-stakes architectural question in `01` §5 ("`07` §6 and `15` §4's 'minimum-necessary-context' framing reads more naturally as an external-provider boundary... but no document states this explicitly"). This document does not resolve it either; the context-minimization rule above applies identically under either topology.

## 7. Rate & Cost Protection

**[EXPLICIT]** — `AI-012`'s acceptance criteria: "Quota tracking, backoff," sourced to `` `07` §7, `17` §3 ``; and this section's own citation from `17` §3: "Rate/cost limiting mechanics (quotas, backoff) are an operational concern owned by `17_OPERATIONS_AND_DEPLOYMENT.md` §3." This section owns *that* rate/cost protection exists as an architectural requirement on every AI call; `17` §3 owns the specific numeric quota/backoff configuration and tunes it operationally (`OPS-003`). A rate-limited call is retried with exponential backoff up to a configured ceiling (`17` §3); reaching that ceiling is treated identically to "AI unavailable" per §5's table — this section does not introduce a second fallback path.

## 8. Resolved Configuration Values

**[RESOLVED]** — this section holds the specific numeric/config values that §4's and §7's *shapes* deliberately left tunable, matching the same pattern `06` §2, `05` §5, and `08` §5 use elsewhere in the package (fix the shape of a rule in the owning section, keep the number itself in externalized configuration rather than hardcoded business logic — `19` gap items 3, 6, 7).

- **Confidence-band boundaries (`AI-011`, `19` gap item 7):** the three-band model (High/Medium/Low) is the resolved *shape*; the exact numeric cutoffs a given AI provider's own confidence score maps to are externalized configuration, not hardcoded here — consistent with `06` §2's citation of "Low confidence band per `07` §4's three-band model" needing only the band name, never a raw number, to make its own conservative-default rule work.
- **Rate/cost quota and backoff numbers (`AI-012`, §7):** owned and tuned operationally by `17` §3 / `OPS-003`, not fixed in this document.

## 9. Interaction Summary — AI's Boundary With Every Consuming Domain

**[STRONGLY INFERRED]** — no single existing document states this table, but every row is a direct restatement of a citation already established above and in the consuming document itself; this section exists only to make the full picture visible in one place, not to introduce new behavior.

| Consuming domain | What AI may propose | What AI never does there |
|---|---|---|
| Scheduling (`04`) | `classify_hard_consequence` (§3) informs Stage 2's gate | Never places, moves, or ranks a Scheduled Block itself — Stage 0–8 is pure deterministic evaluation (`04` §2.4) |
| Recovery (`05`) | `suggest_goal_revision` (§3) supplies the At-Risk options list (`05` §5); pattern-detection observations (`05` §6) may be phrased via `summarize_progress` | Never decides which option is chosen, never auto-applies a revision, never auto-applies a detected pattern to future scheduling (`05` §6, `08` §5) |
| Roadmap/Resource ingestion (`06`) | `extract_roadmap`, `suggest_resource` (§3) enhance the deterministic pass's classification | Never commits an ingested node or a screenshot-import candidate without Review/Approve (`06` §1.4–1.5, `06` §4) |
| Memory/Preferences (`08`) | Clarifying questions (§1) and pattern observations feed `08`'s single confirmation-gated write path | Never writes a `user_preferences` row directly (`08` §1) |

## 10. Genuine Gaps / Requires Product Decision

1. **In-process vs. external-provider AI topology (§6)** — recorded as `[OPEN / genuinely unresolved]` in `01` §5; not resolved here since neither the proposal/validation contract (§3–4) nor the context-minimization rule (§6) depends on which topology is chosen. Low-stakes for v1.
