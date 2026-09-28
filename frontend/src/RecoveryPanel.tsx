import { useEffect, useRef, useState } from "react";
import { ApiError, Client, jsonBody, messageOf } from "./api";
import { Workspace, date, time } from "./execution";
import { localInput } from "./executionTime";

type Decision = { id: number; state: string; proposal: { tier: string; reason: string; deferred: number[]; plan: { placements: { commitmentId: number; startTime: string; endTime: string }[] } } };
type Risk = { goalId: number; awaitingResponse: boolean; planningState: string; inputs: unknown; calculation: { confidence: number; threshold: number; remainingMinutes: number; capacityMinutes: number; result: string } };
type Recovery = { deferredReview?: { reason: string; proposedAt: string } | null; goals?: { id: number; title: string }[]; decisions: Decision[]; risks: Risk[]; recurring: { id: number; title: string; target: number; remaining: number; goalId: number | null; flexibilityTier: string }[]; reportedBlocks: number[]; claimedBlocks: number[]; patternStatus: string; patterns?: { configured: boolean; observations: { categoryId: number | null; localHour: number; observation: { message: string } }[] } };

export default function RecoveryPanel({ client, data, refresh, now = Date.parse(data.serverTime) }: { client: Client; data: Workspace; now?: number; refresh: () => void }) {
  const [state, setState] = useState<Recovery | null>(null);
  const [error, setError] = useState(""); const [notice, setNotice] = useState(""); const [busy, setBusy] = useState(false);
  const [minutes, setMinutes] = useState<Record<number, string>>({});
  const [reports, setReports] = useState<Record<number, { outcome: string; text: string; pct: string }>>({});
  const [start, setStart] = useState(""); const [end, setEnd] = useState("");
  const [goal, setGoal] = useState(""); const [hour, setHour] = useState("");
  const [option, setOption] = useState("increase_effort"); const [note, setNote] = useState(""); const [deadline, setDeadline] = useState("");
  const [recurringMinutes, setRecurringMinutes] = useState<Record<number, string>>({});
  const [recurringEffort, setRecurringEffort] = useState<Record<number, string>>({});
  const [importance, setImportance] = useState("medium");
  const [reviewEnd, setReviewEnd] = useState("");
  const [deferredMinutes, setDeferredMinutes] = useState<Record<number, string>>({});
  const [deferredFit, setDeferredFit] = useState<{ placements: { commitmentId: number; startTime: string }[]; unplaced: number[] } | null>(null);
  const pending = useRef(false); const replay = useRef<{ fingerprint: string; key: string } | null>(null);
  async function load() { setState(await client<Recovery>("/recovery/workspace")); }
  useEffect(() => { void load().catch(e => setError(messageOf(e))); }, [client]);
  async function mutate(path: string, body: unknown, message: string) {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError(""); setNotice("");
    const fingerprint = path + JSON.stringify(body);
    if (replay.current?.fingerprint !== fingerprint) replay.current = { fingerprint, key: crypto.randomUUID() };
    try {
      await client(path, { method: "POST", ...jsonBody(body), headers: { "Content-Type": "application/json", "Idempotency-Key": replay.current.key } });
      replay.current = null; setNotice(message); await load(); refresh();
    } catch (e) { if (e instanceof ApiError && e.status < 500) replay.current = null; setError(messageOf(e)); }
    finally { pending.current = false; setBusy(false); }
  }
  const candidates = data.blocks.filter(b => !state?.claimedBlocks.includes(b.id) && data.tasks.some(t => t.id === b.commitmentId && t.workState === "ready")
    && (b.state === "unresolved" || b.state === "completed" || (b.state === "scheduled" && Date.parse(b.endTime) <= Date.parse(data.serverTime))));
  const selected = candidates.filter(b => Number(minutes[b.id]) > 0);
  const goals: [number, string | null][] = state?.goals?.map(g => [g.id, g.title]) ?? [...new Map(data.tasks.filter(t => t.goalId).map(t => [t.goalId!, t.goalTitle])).entries()];
  const deferred = data.tasks.filter(t => t.workState === "deferred");
  const taskName = (id: number) => data.tasks.find(t => t.id === id)?.title ?? "Work";
  function riskInputs(id: number) {
    return { estimates: data.tasks.filter(t => t.goalId === id && !["completed", "cancelled"].includes(t.workState)).map(t => ({ commitmentId: t.id, totalWorkMinutes: Number(minutes[-t.id]), localHour: Number(hour) })),
      recurringEstimates: state?.recurring.filter(r => r.goalId === id).map(r => ({ recurringIntentionId: r.id, remainingWorkMinutes: Number(recurringEffort[r.id]), localHour: Number(hour) })) ?? [] };
  }
  return <section aria-label="Recovery" className="task-context-panel">
    <h2>Continue from what happened</h2><p>Review missed windows, remaining work and choices that need your input.</p>
    {state?.deferredReview && <p>Deferred backlog review · {date(state.deferredReview.proposedAt, data.timezone)}: {state.deferredReview.reason}</p>}
    {state?.patterns?.observations.map((f, i) => <p key={i}>Around {f.localHour}:00: {f.observation.message} Your preferences have not changed.</p>)}
    {error && <p role="alert" className="error">{error}</p>}{notice && <p role="status">{notice}</p>}
    <button disabled={busy} onClick={() => void mutate("/recovery/detect", {}, "Passed windows checked. An unstarted timer does not tell us what happened.")}>Check passed windows</button>
    {candidates.map(b => {
      const task = data.tasks.find(t => t.id === b.commitmentId)!;
      const r = reports[b.id] ?? { outcome: "partial", text: "", pct: String(task.completionPct) };
      const change = (patch: Partial<typeof r>) => setReports(v => ({ ...v, [b.id]: { ...r, ...patch } }));
      return <article key={b.id}><h3>{task.title}</h3><p>{date(b.startTime, data.timezone)} · {time(b.startTime, data.timezone)} · {task.completionPct}% currently complete</p>
        {!b.sessionState && !state?.reportedBlocks.includes(b.id) && <form onSubmit={e => { e.preventDefault(); void mutate(`/blocks/${b.id}/report`, { outcome: r.outcome, report: r.text, completionPct: r.outcome === "completed" ? 100 : r.outcome === "skipped" ? task.completionPct : Number(r.pct) }, "Your report was saved; no timer duration was invented."); }}>
          <label>What happened?<select value={r.outcome} onChange={e => change({ outcome: e.target.value })}><option value="partial">Some work remains</option><option value="completed">Completed</option><option value="skipped">Skipped this window</option></select></label>
          <label>Report<textarea required value={r.text} onChange={e => change({ text: e.target.value })} /></label>
          {r.outcome === "partial" && <label>Current task completion %<input type="number" min="0" max="99.99" step="0.01" required value={r.pct} onChange={e => change({ pct: e.target.value })} /></label>}
          <button disabled={busy}>Save report</button>
        </form>}
        <label>Total task estimate (minutes, before progress)<input aria-label={`Recovery estimate for ${task.title}`} type="number" min="1" max="1440" value={minutes[b.id] ?? ""} onChange={e => setMinutes(v => ({ ...v, [b.id]: e.target.value }))} /></label>
      </article>;
    })}
    {!!selected.length && <button disabled={busy} onClick={() => void mutate("/recovery", { items: selected.map(b => ({ blockId: b.id, totalWorkMinutes: Number(minutes[b.id]) })) }, "Recovery evaluated. Review any pending choice below.")}>Find suitable time for selected work</button>}
    {state?.decisions.map(d => <article key={d.id}><h3>{d.proposal.tier === "CRITICAL" ? "Your decision is needed" : "Review recovery proposal"}</h3><p>{d.proposal.reason}</p>
      <p>Approval searches current suitable capacity again. Times below are a preview; the same work and decision tier must still apply.</p>
      <ul>{d.proposal.plan.placements.map(p => <li key={p.commitmentId}>{taskName(p.commitmentId)}: {date(p.startTime, data.timezone)} {time(p.startTime, data.timezone)}</li>)}{d.proposal.deferred.map(id => <li key={id}>{taskName(id)}: defer for later review</li>)}</ul>
      <button disabled={busy || (!d.proposal.plan.placements.length && !d.proposal.deferred.length)} onClick={() => void mutate(`/recovery/${d.id}/response`, { accept: true }, "Approved recovery applied.")}>Approve recovery</button>
      <button disabled={busy} onClick={() => void mutate(`/recovery/${d.id}/response`, { accept: false }, "Proposal dismissed. Work remains available.")}>Dismiss proposal</button><p>No response leaves this proposal unapplied.</p>
    </article>)}
    <details><summary>Report unavailable time</summary><form onSubmit={e => {
      e.preventDefault(); const a = localInput(start, data.timezone), z = localInput(end, data.timezone);
      if (!Number.isFinite(a) || !Number.isFinite(z) || z <= a) { setError("Choose a valid, unambiguous interval in your scheduling timezone."); return; }
      const affected = data.blocks.filter(b => b.state === "scheduled" && b.commitmentId && Date.parse(b.startTime) < z && Date.parse(b.endTime) > a);
      void mutate("/recovery/interruption", { startTime: new Date(a).toISOString(), endTime: new Date(z).toISOString(), items: affected.map(b => ({ blockId: b.id, totalWorkMinutes: Number(minutes[b.id]) })) }, "Unavailable time recorded. Review affected work; active sessions and recurring windows remain for your attention.");
    }}><p>Times use {data.timezone ?? "your scheduling timezone"}. Fixed commitments stay in place.</p>
      <label>Unavailable from<input type="datetime-local" required value={start} onChange={e => setStart(e.target.value)} /></label><label>Until<input type="datetime-local" required value={end} onChange={e => setEnd(e.target.value)} /></label>
      {data.blocks.filter(b => b.state === "scheduled" && b.commitmentId).map(b => <label key={b.id}>Total estimate for {taskName(b.commitmentId)} (minutes)<input type="number" min="1" max="1440" value={minutes[b.id] ?? ""} onChange={e => setMinutes(v => ({ ...v, [b.id]: e.target.value }))} /></label>)}
      <button disabled={busy}>Record interruption</button></form></details>
    <details><summary>Review deferred work ({deferred.length})</summary><p>These tasks remain yours. Returning them to active planning does not guarantee a slot; scheduling will check current capacity and constraints.</p>
      <ul>{deferred.map(t => <li key={t.id}>{t.title} · {t.importance} importance</li>)}</ul>
      {!!deferred.length && <form onSubmit={async e => {
        e.preventDefault(); const endTime = localInput(reviewEnd, data.timezone);
        if (!Number.isFinite(endTime) || endTime <= now) { setError("Choose a future, unambiguous review horizon."); return; }
        setBusy(true); setError("");
        try { setDeferredFit(await client("/recovery/deferred/preview", { method: "POST", ...jsonBody({ startTime: new Date(now).toISOString(), endTime: new Date(endTime).toISOString(), work: deferred.map(t => ({ commitmentId: t.id, workMinutes: Number(deferredMinutes[t.id]) })) }) })); }
        catch (error) { setError(messageOf(error)); } finally { setBusy(false); }
      }}><label>Check capacity until<input type="datetime-local" required value={reviewEnd} onChange={e => setReviewEnd(e.target.value)} /></label>
        {deferred.map(t => <label key={t.id}>Remaining minutes for {t.title}<input required type="number" min="1" max="1440" value={deferredMinutes[t.id] ?? ""} onChange={e => setDeferredMinutes(v => ({ ...v, [t.id]: e.target.value }))} /></label>)}
        <button disabled={busy}>Preview available capacity</button>
      </form>}
      {deferredFit && <ul>{deferredFit.placements.map(p => <li key={p.commitmentId}>{taskName(p.commitmentId)} can fit: {date(p.startTime, data.timezone)} {time(p.startTime, data.timezone)}. Preview only.</li>)}{deferredFit.unplaced.map(id => <li key={id}>{taskName(id)} still has no suitable slot in this review window.</li>)}</ul>}
      <button disabled={busy || !deferred.length} onClick={() => void mutate("/recovery/deferred/reactivate", deferred.map(t => t.id), "Deferred tasks returned to active planning.")}>Return this batch to active planning</button></details>
    {state?.risks.map(r => <article key={r.goalId}><h3>Goal at risk · {goals.find(g => g[0] === r.goalId)?.[1] ?? r.goalId}</h3>
      <p>Feasibility {Math.round(r.calculation.confidence * 100)}%; threshold {r.calculation.threshold * 100}%. Remaining effort {Math.round(r.calculation.remainingMinutes)} minutes; realistic capacity {Math.round(r.calculation.capacityMinutes)} minutes.</p>
      <p>The risk stays visible until you act and feasibility is restored. Your goal is not paused or abandoned by silence.</p>
      <form onSubmit={e => { e.preventDefault(); void mutate(`/goals/${r.goalId}/risk/response`, { option, note, deadline: deadline || null }, "Your choice was recorded and risk reviewed."); }}>
        <label>Choose a next step<select value={option} onChange={e => setOption(e.target.value)}><option value="increase_effort">Increase effort</option><option value="extend_deadline">Extend deadline</option><option value="reduce_scope">Reduce scope</option><option value="change_method">Change method</option><option value="defer_pause">Pause this goal</option></select></label>
        <label>What are you changing?<textarea required value={note} onChange={e => setNote(e.target.value)} /></label>
        {option === "extend_deadline" && <label>New deadline<input type="date" required value={deadline} onChange={e => setDeadline(e.target.value)} /></label>}
        <p>Effort, scope and method choices record your plan; update task estimates or working hours before recalculating.</p><button disabled={busy}>Confirm choice</button>
      </form></article>)}
    <details><summary>Evaluate goal feasibility</summary><form onSubmit={e => { e.preventDefault(); void mutate(`/goals/${goal}/risk`, riskInputs(Number(goal)), "Feasibility snapshot recorded."); }}>
      <label>Goal<select required value={goal} onChange={e => setGoal(e.target.value)}><option value="">Choose a goal</option>{goals.map(([id, title]) => <option key={id} value={id}>{title}</option>)}</select></label>
      <label>Intended local hour (0–23)<input required type="number" min="0" max="23" value={hour} onChange={e => setHour(e.target.value)} /></label>
      {data.tasks.filter(t => t.goalId === Number(goal) && !["completed", "cancelled"].includes(t.workState)).map(t => <label key={t.id}>{t.title}: total estimate before progress (minutes)<input required type="number" min="1" value={minutes[-t.id] ?? ""} onChange={e => setMinutes(v => ({ ...v, [-t.id]: e.target.value }))} /></label>)}
      {state?.recurring.filter(r => r.goalId === Number(goal)).map(r => <label key={r.id}>{r.title}: remaining effort through the goal deadline (minutes)<input required type="number" min="0" value={recurringEffort[r.id] ?? ""} onChange={e => setRecurringEffort(v => ({ ...v, [r.id]: e.target.value }))} /></label>)}
      <p>Uses contextual recorded completion evidence. Without that evidence, Atlas cannot yet give a supported confidence estimate.</p><button disabled={busy}>Evaluate feasibility</button></form></details>
    <details><summary>Recurring weekly targets</summary><p>Missed instances never add debt to next week.</p><button disabled={busy} onClick={() => void mutate("/recovery/weekly-reset", {}, "Current weekly targets reconciled.")}>Reconcile this week</button>
      {state?.recurring.map(r => <article key={r.id}><h3>{r.title}</h3><p>{r.remaining} remaining of {r.target} this week</p><label>Minutes per instance<input type="number" min="1" max="1440" value={recurringMinutes[r.id] ?? ""} onChange={e => setRecurringMinutes(v => ({ ...v, [r.id]: e.target.value }))} /></label>
        <label>Importance for this scheduling request<select value={importance} onChange={e => setImportance(e.target.value)}>{["low", "medium", "high", "critical"].map(v => <option key={v}>{v}</option>)}</select></label>
        <button disabled={busy || !Number(recurringMinutes[r.id])} onClick={() => void mutate("/schedule/recurring", { work: [{ recurringIntentionId: r.id, workMinutes: Number(recurringMinutes[r.id]), importance }] }, "Suitable recurring windows scheduled for this week.")}>Schedule this week's remaining target</button>
        {data.blocks.filter(b => b.recurringIntentionId === r.id && b.state === "scheduled").map(b => <p key={b.id}>{date(b.startTime, data.timezone)} {time(b.startTime, data.timezone)} <button disabled={busy || Date.parse(b.startTime) > now || Date.parse(b.endTime) <= now} onClick={() => void mutate(`/blocks/${b.id}/session/start`, {}, "Recurring session started. Continue in Focus.")}>Start recurring session</button></p>)}
        {data.blocks.filter(b => b.recurringIntentionId === r.id && ["scheduled", "unresolved"].includes(b.state) && Date.parse(b.endTime) <= now && !b.sessionState && !state.reportedBlocks.includes(b.id)).map(b => {
          const report = reports[b.id] ?? { outcome: "partial", text: "", pct: "0" };
          const change = (patch: Partial<typeof report>) => setReports(v => ({ ...v, [b.id]: { ...report, ...patch } }));
          return <form key={b.id} onSubmit={e => { e.preventDefault(); void mutate(`/blocks/${b.id}/report`, { outcome: report.outcome, report: report.text, completionPct: report.outcome === "completed" ? 100 : report.outcome === "skipped" ? 0 : Number(report.pct) }, "Recurring report saved without inventing a session duration."); }}>
            <p>Passed instance: {date(b.startTime, data.timezone)} {time(b.startTime, data.timezone)}</p>
            <label>Recurring outcome<select value={report.outcome} onChange={e => change({ outcome: e.target.value })}><option value="partial">Partially completed</option><option value="completed">Completed</option><option value="skipped">Skipped</option></select></label>
            <label>What happened in this instance?<textarea required value={report.text} onChange={e => change({ text: e.target.value })} /></label>
            {report.outcome === "partial" && <label>Instance completion %<input type="number" required min="0" max="99.99" step="0.01" value={report.pct} onChange={e => change({ pct: e.target.value })} /></label>}
            <button disabled={busy}>Save recurring report</button>
          </form>;
        })}
      </article>)}
    </details>
  </section>;
}
