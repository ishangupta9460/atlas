import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { ApiError, Client, jsonBody, messageOf } from "./api";
import { Block, Work, Workspace, ExecutionView, clock, date, duration, elapsed, label, sameDay, time } from "./execution";
import DependencyPanel from "./DependencyPanel";
import TodayScreen from "./TodayScreen";

export default function ExecutionWorkspace({ client, view, navigate, initialTask, openGoal }: {
  client: Client; view: ExecutionView; navigate: (view: ExecutionView) => void;
  initialTask: number | null; openGoal: (id: number) => void;
}) {
  const [data, setData] = useState<Workspace | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const [selected, setSelected] = useState<number | null>(initialTask);
  const [showCapture, setShowCapture] = useState(false);
  const [showLegacy, setShowLegacy] = useState(false);
  const [finishing, setFinishing] = useState(false);
  const [now, setNow] = useState(Date.now());
  const offset = useRef(0);
  const pending = useRef(false);
  // Preserve the key for a failed/ambiguous request so Retry cannot duplicate a mutation.
  const replay = useRef<{ fingerprint: string; key: string } | null>(null);
  const apply = useCallback((value: Workspace) => {
    offset.current = Date.parse(value.serverTime) - Date.now(); setNow(Date.now() + offset.current); setData(value);
  }, []);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError("");
    client<Workspace>("/execution", { signal: controller.signal }).then(value => {
      if (!controller.signal.aborted) apply(value);
    }).catch(e => { if (!controller.signal.aborted) setError(messageOf(e)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [client, retry, apply]);
  useEffect(() => { setSelected(initialTask); }, [initialTask]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now() + offset.current), 1000);
    const refresh = () => { if (!document.hidden && !pending.current) setRetry(n => n + 1); };
    document.addEventListener("visibilitychange", refresh);
    return () => { clearInterval(timer); document.removeEventListener("visibilitychange", refresh); };
  }, []);

  async function mutate(path: string, body?: unknown): Promise<boolean> {
    if (pending.current) return false;
    pending.current = true; setBusy(true); setError(""); setNotice("");
    const fingerprint = path + JSON.stringify(body);
    if (replay.current?.fingerprint !== fingerprint) replay.current = { fingerprint, key: crypto.randomUUID() };
    try {
      await client(path, { method: "POST", ...jsonBody(body ?? {}), headers: { "Content-Type": "application/json", "Idempotency-Key": replay.current.key } });
      replay.current = null;
      try { apply(await client<Workspace>("/execution")); }
      catch (e) { setError(`Saved, but the view could not refresh. ${messageOf(e)}`); setData(null); }
      return true;
    } catch (e) {
      if (e instanceof ApiError && e.status < 500) replay.current = null;
      setError(messageOf(e)); return false;
    } finally { pending.current = false; setBusy(false); }
  }

  const tasks = data?.tasks ?? [];
  const taskFor = (block: Block) => tasks.find(t => t.id === block.commitmentId);
  const active = data?.blocks.find(b => b.state === "active");
  const scheduled = data?.blocks.filter(b => b.state === "scheduled") ?? [];
  const today = scheduled.filter(b => (sameDay(b.startTime, now) || Date.parse(b.startTime) <= now) && Date.parse(b.endTime) > now);
  const current = active ?? today[0];
  const next = today.filter(b => b.id !== current?.id);
  const missed = scheduled.filter(b => Date.parse(b.endTime) <= now);
  const unplanned = tasks.filter(t => t.workState === "ready" && !scheduled.some(b => b.commitmentId === t.id));
  const selectedTask = tasks.find(t => t.id === selected);
  const [overrunPrompt, setOverrunPrompt] = useState<number | null>(null);
  const seenOverrun = useRef(new Set<number>());
  useEffect(() => {
    if (!active || active.sessionState !== "running" || now <= Date.parse(active.endTime) + 300000 || (view !== "today" && view !== "focus")) return;
    if (seenOverrun.current.has(active.id)) return;
    seenOverrun.current.add(active.id);
    try {
      if (sessionStorage.getItem(`atlas.overrun.${active.id}`)) return;
      sessionStorage.setItem(`atlas.overrun.${active.id}`, "shown");
    } catch { /* In-memory guard still prevents repeated prompts when storage is unavailable. */ }
    setOverrunPrompt(active.id);
  }, [active, now, view]);

  async function transition(block: Block, action: "start" | "pause" | "resume") {
    if (await mutate(`/blocks/${block.id}/session/${action}`)) {
      if (action !== "pause") { setSelected(null); navigate("focus"); }
    }
  }
  function actions(block: Block) {
    if (block.sessionState === "running" || block.sessionState === "paused") return <>
      <button disabled={busy} onClick={() => void transition(block, block.sessionState === "running" ? "pause" : "resume")}>{block.sessionState === "running" ? "Pause" : "Resume"}</button>
      <button className="primary" disabled={busy} onClick={() => { setFinishing(true); navigate("focus"); }}>Finish</button>
      {view !== "focus" && <button className="text-button" onClick={() => navigate("focus")}>Open Focus →</button>}
    </>;
    return <button className="primary" disabled={busy || !!active || now < Date.parse(block.startTime) || now >= Date.parse(block.endTime)} onClick={() => void transition(block, "start")}>Start</button>;
  }
  function row(block: Block) {
    const task = taskFor(block);
    return <li className="row-item" key={block.id}>
      <div className="execution-row-time">{time(block.startTime)}<span className="hint">{duration(Date.parse(block.endTime) - Date.parse(block.startTime))}</span></div>
      <div className="row-item-main"><h3>{task?.title ?? "Task"}</h3><p className="hint">{task?.goalTitle ?? "Independent task"}</p></div>
      <button onClick={() => setSelected(block.commitmentId)}>Task brief</button>
    </li>;
  }
  function hero(block: Block) {
    const task = taskFor(block);
    const conflicts = data?.fixed.filter(f => Date.parse(f.startTime) < Date.parse(block.endTime) && Date.parse(f.endTime) > Math.max(now, Date.parse(block.startTime))) ?? [];
    return <section className={`active-task-panel ${view === "focus" ? "focus-panel" : ""}`} aria-label="Current task">
      <div className="task-breadcrumb"><span className="active-task-label">{block.sessionState === "paused" ? "Paused · continue when ready" : block.sessionState === "running" ? "In progress" : "Your next work window"}</span><span className="hint">{time(block.startTime)}–{time(block.endTime)}</span></div>
      {task?.goalTitle && <button className="text-button goal-context" onClick={() => openGoal(task.goalId!)}>Goal · {task.goalTitle}</button>}
      {task?.goalState === "at_risk" && <span className="badge risk">Goal at risk</span>}
      <h1>{task?.title}</h1>
      <p className="completion-criterion">{task?.completionCriterion}</p>
      {block.sessionState && <>
        <div className="session-clock" aria-label="Active time">{clock(elapsed(block, now))}</div>
        <p className="hint">Active time · pauses excluded</p>
        <progress aria-label="Time against planned window" max={Math.max(1, Date.parse(block.endTime) - Date.parse(block.startTime))} value={elapsed(block, now)} />
        {task && task.completionPct > 0 && <p className="hint">Last reported task progress · {task.completionPct}%</p>}
      </>}
      <div className="active-task-actions">{actions(block)}<button className="text-button" onClick={() => setSelected(block.commitmentId)}>Task context</button></div>
      {conflicts.length > 0 && <p role="status" className="execution-notice">This window overlaps {conflicts.map(f => f.title).join(", ")}. Nothing has been moved. {block.sessionState ? "Pause or finish when you need to step away." : "Choose a new time in the task brief."}</p>}
      {!block.sessionState && now < Date.parse(block.startTime) && <p className="hint">Your window starts at {time(block.startTime)}. You can adjust it in the task brief.</p>}
      {block.sessionState === "running" && overrunPrompt === block.id && <aside className="execution-notice">
        <p>Still working on this? Wrap up or keep going.</p><button className="text-button" onClick={() => setOverrunPrompt(null)}>Keep going</button>
      </aside>}
    </section>;
  }

  return <>
    <div className="page-heading execution-heading"><div><p className="eyebrow">{view === "today" ? date(new Date(now).toISOString()) : "Your space to make progress"}</p><h1>{label(view).replace(/^./, s => s.toUpperCase())}</h1><p>{view === "today" ? "One thing in front of you. Room for what comes next." : view === "focus" ? "Everything else can wait a moment." : view === "schedule" ? "The work windows you chose, alongside your fixed commitments." : "What you planned, what you worked on, and what moved forward."}</p></div>
      <button className="text-button" disabled={busy || loading} onClick={() => setRetry(n => n + 1)}>Refresh</button></div>
    {error && <p role="alert" className="error">{error}</p>}
    {notice && <p role="status" className="notice">{notice}</p>}
    {loading && <p role="status" className="hint">Loading your work…</p>}
    {!loading && data && <>
      {view === "today" && <>
        <div className="section-heading"><h2>Now</h2><button className="text-button" onClick={() => setShowCapture(true)}>Add a task</button></div>
        {current ? hero(current) : <div className="empty-state"><h3>A clear space to begin.</h3><p>Choose a ready task below and give it a work window.</p><button onClick={() => setShowCapture(true)}>Capture a next step</button></div>}
        <section aria-label="Next"><div className="section-heading"><h2>Next</h2><button className="text-button" onClick={() => navigate("schedule")}>View schedule →</button></div>
          {next.length ? <ul className="row-list">{next.slice(0, 3).map(row)}</ul> : <p className="hint">No more work windows today. Leave room, or choose another task.</p>}
        </section>
        {next.length > 3 && <details className="disclosure"><summary>Later · {next.length - 3} more today</summary><ul className="row-list">{next.slice(3).map(row)}</ul></details>}
        {missed.length > 0 && <details className="disclosure"><summary>Pick up where you left off · {missed.length} unstarted {missed.length === 1 ? "window" : "windows"}</summary><p className="hint">These windows have passed. Nothing has been marked as failed. Open a task to choose a new time.</p><ul className="row-list">{missed.map(row)}</ul></details>}
        <details className="disclosure" open={!current}><summary>Ready when you are · {unplanned.length} unplanned tasks</summary><p className="hint">In capture order. These tasks do not have scheduled times yet.</p>
          <ul className="row-list">{unplanned.map(task => <li className="row-item" key={task.id}><div className="row-item-main"><h3>{task.title}</h3><p className="hint">{task.goalTitle ?? "Independent task"}{task.blockers > 0 ? ` · ${task.blockers} unfinished prerequisites` : ""}</p></div><button onClick={() => setSelected(task.id)}>Task brief</button></li>)}</ul>
          {!unplanned.length && <p className="hint">Add a task, or shape your next step in Goals.</p>}
        </details>
        <details className="disclosure" onToggle={e => setShowLegacy(e.currentTarget.open)}><summary>Earlier quick tasks</summary><p className="hint">Tasks captured before goal planning are kept here with their original start and finish actions.</p>{showLegacy && <TodayScreen client={client} />}</details>
      </>}
      {view === "focus" && <>
        {active ? hero(active) : <div className="empty-state"><h3>{notice ? "A moment of closure." : "Choose one thing."}</h3><p>No session is running. Your next work window is waiting on Today.</p><button className="primary" onClick={() => navigate("today")}>See what’s next →</button></div>}
        {active && finishing && <FinishForm key={active.id} task={taskFor(active)!} busy={busy} onCancel={() => setFinishing(false)} onFinish={async (report, completionPct) => {
          if (await mutate(`/blocks/${active.id}/session/finish`, { report, completionPct })) {
            setFinishing(false); setNotice(completionPct === 100 ? "Task complete. Your progress is saved." : "Session saved. The remaining work is ready for another window.");
          }
        }} />}
        <button className="text-button" onClick={() => navigate("today")}>← Back to Today</button>
      </>}
      {view === "schedule" && <Schedule data={data} now={now} select={setSelected} />}
      {view === "progress" && <Progress data={data} openGoal={openGoal} />}
      {selectedTask && <TaskBrief key={selectedTask.id} task={selectedTask} client={client} blocks={data.blocks} now={now} busy={busy} close={() => setSelected(null)} openGoal={openGoal} onPlace={async (startTime, endTime, oldId) => {
        const saved = await mutate(oldId ? `/schedule/blocks/${oldId}/move` : "/schedule/blocks", { commitmentId: selectedTask.id, startTime, endTime });
        if (saved) {
          setSelected(null); setNotice("Work window saved. It’s ready on Today and Schedule."); navigate("today");
        }
        return saved || replay.current !== null;
      }} />}
    </>}
    {showCapture && <Capture client={client} close={() => setShowCapture(false)} saved={() => { setShowCapture(false); setRetry(n => n + 1); }} />}
  </>;
}

function TaskBrief({ task, blocks, client, now, busy, close, onPlace, openGoal }: {
  task: Work; blocks: Block[]; client: Client; now: number; busy: boolean; close: () => void;
  onPlace: (start: string, end: string, oldId?: number) => Promise<boolean>; openGoal: (id: number) => void;
}) {
  const existing = blocks.find(b => b.commitmentId === task.id && b.state === "scheduled");
  const [minutes, setMinutes] = useState(25);
  const [when, setWhen] = useState("");
  const [dependencies, setDependencies] = useState(false);
  const [localError, setLocalError] = useState("");
  const placementAttempt = useRef<{ signature: string; start: string; end: string } | null>(null);
  const brief = useRef<HTMLElement>(null);
  useEffect(() => { brief.current?.focus(); brief.current?.scrollIntoView?.({ behavior: "smooth", block: "start" }); }, []);
  return <section className="task-brief panel" aria-label="Task brief" tabIndex={-1} ref={brief}>
    <div className="section-heading"><h2>Task brief</h2><button disabled={busy} onClick={close}>Close</button></div>
    <h1>{task.title ?? "Untitled task"}</h1>
    {task.goalTitle && <button className="text-button goal-context" onClick={() => openGoal(task.goalId!)}>Goal · {task.goalTitle} →</button>}
    {task.milestoneTitle && <p className="hint">Milestone · {task.milestoneTitle}</p>}
    <h2 className="brief-label">What done looks like</h2><p>{task.completionCriterion ?? "Add a completion criterion in the goal plan to make this task ready."}</p>
    <p className="hint">{label(task.workState)} · {task.completionPct}% reported complete</p>
    <details className="disclosure"><summary>More context</summary><dl className="context-grid"><dt>Importance</dt><dd>{label(task.importance)}</dd><dt>Flexibility</dt><dd>{label(task.flexibilityTier)}</dd><dt>Category</dt><dd>{task.categoryName ?? "None"}</dd><dt>Deadline</dt><dd>{task.deadline ? `${date(task.deadline)} · ${time(task.deadline)}` : "No deadline"}</dd></dl>{task.description && <p className="report-text">{task.description}</p>}</details>
    <details className="disclosure" onToggle={e => setDependencies(e.currentTarget.open)}><summary>Prerequisites · {task.blockers} unfinished</summary>{dependencies && <DependencyPanel task={task} client={client} />}</details>
    {existing && <p className="execution-notice">{date(existing.startTime)} · {time(existing.startTime)}–{time(existing.endTime)}. {existing.placementReason}</p>}
    {task.workState === "ready" && <form onSubmit={e => {
      e.preventDefault(); setLocalError("");
      const start = when ? new Date(when).getTime() : now;
      if (!Number.isFinite(start) || minutes < 1 || minutes > 1440) { setLocalError("Choose a valid time and duration."); return; }
      const signature = `${when}:${minutes}`;
      if (placementAttempt.current?.signature !== signature) placementAttempt.current = { signature, start: new Date(start).toISOString(), end: new Date(start + minutes * 60000).toISOString() };
      void onPlace(placementAttempt.current.start, placementAttempt.current.end, existing?.id).then(keepAttempt => { if (!keepAttempt) placementAttempt.current = null; });
    }}><h2 className="brief-label">{existing ? "Choose a new window" : "Make room for this"}</h2><p className="hint">You choose the time. Atlas keeps this window where you put it.</p>
      <div className="form-grid"><div><label htmlFor="window-time">Start time <span className="hint">Leave empty for now</span></label><input id="window-time" type="datetime-local" value={when} onChange={e => setWhen(e.target.value)} disabled={busy} /></div><div><label htmlFor="window-duration">Minutes to set aside</label><input id="window-duration" type="number" min="1" max="1440" required value={minutes} onChange={e => setMinutes(Number(e.target.value))} disabled={busy} /></div></div>
      {localError && <p role="alert" className="error">{localError}</p>}
      {task.blockers > 0 && <p className="hint">Finish the prerequisites first. If you update them here, close this brief and refresh.</p>}
      <div className="actions"><button className="primary" disabled={busy || task.blockers > 0}>{busy ? "Saving…" : existing ? "Move work window" : "Set work window"}</button></div>
    </form>}
  </section>;
}

function FinishForm({ task, busy, onCancel, onFinish }: { task: Work; busy: boolean; onCancel: () => void; onFinish: (report: string, percentage: number) => Promise<void> }) {
  const [report, setReport] = useState(""); const [complete, setComplete] = useState(true); const [percentage, setPercentage] = useState(task.completionPct);
  return <form className="panel finish-form" onSubmit={e => { e.preventDefault(); void onFinish(report.trim(), complete ? 100 : percentage); }}>
    <h2>Where did you get to?</h2><p className="hint">A few words is enough. Stopping here can still leave work for later.</p>
    <label htmlFor="session-report">What you accomplished</label><textarea id="session-report" autoFocus required maxLength={8000} value={report} onChange={e => setReport(e.target.value)} disabled={busy} />
    <label htmlFor="session-outcome">Task outcome</label><select id="session-outcome" value={complete ? "complete" : "partial"} onChange={e => setComplete(e.target.value === "complete")} disabled={busy}><option value="complete">Task complete</option><option value="partial">More work remains</option></select>
    {!complete && <><label htmlFor="session-progress">Your estimate of task completion (%)</label><input id="session-progress" type="number" min="0" max="99.99" step="0.01" required value={percentage} onChange={e => setPercentage(Number(e.target.value))} disabled={busy} /><p className="hint">Keep the previous estimate if it hasn’t changed. This doesn’t alter earlier sessions.</p></>}
    <div className="actions"><button className="primary" disabled={busy || !report.trim()}>Save and finish</button><button type="button" disabled={busy} onClick={onCancel}>Keep working</button></div>
  </form>;
}

function Capture({ client, close, saved }: { client: Client; close: () => void; saved: () => void }) {
  const [title, setTitle] = useState(""); const [criterion, setCriterion] = useState(""); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault(); if (busy) return; setBusy(true); setError("");
    try { await client("/commitments", { method: "POST", ...jsonBody({ title: title.trim(), completionCriterion: criterion.trim(), importance: "medium", flexibilityTier: "flexible" }) }); saved(); }
    catch (e) { setError(messageOf(e)); } finally { setBusy(false); }
  }
  return <form className="panel task-brief" onSubmit={submit}><div className="section-heading"><h2>A concrete next step</h2><button type="button" onClick={close} disabled={busy}>Close</button></div><label htmlFor="capture-title">What needs doing?</label><input id="capture-title" autoFocus maxLength={255} required value={title} onChange={e => setTitle(e.target.value)} disabled={busy} /><label htmlFor="capture-criterion">What will done look like?</label><textarea id="capture-criterion" required value={criterion} onChange={e => setCriterion(e.target.value)} disabled={busy} /><p className="hint">Saved as a flexible task with medium importance. For goal-linked work, add a task in your goal plan.</p>{error && <p className="error" role="alert">{error}</p>}<div className="actions"><button className="primary" disabled={busy || !title.trim() || !criterion.trim()}>Add task</button></div></form>;
}

function Schedule({ data, now, select }: { data: Workspace; now: number; select: (id: number) => void }) {
  const [dayOffset, setDayOffset] = useState(0);
  const start = new Date(now); start.setHours(0, 0, 0, 0); start.setDate(start.getDate() + dayOffset);
  const end = new Date(start); end.setDate(end.getDate() + 7);
  const items = [
    ...data.blocks.filter(b => b.state !== "superseded").map(b => ({ ...b, kind: "work", title: data.tasks.find(t => t.id === b.commitmentId)?.title ?? "Task", taskId: b.commitmentId })),
    ...data.fixed.map(f => ({ ...f, kind: "fixed", state: "fixed", title: f.title, taskId: null })),
  ].filter(b => Date.parse(b.startTime) < end.getTime() && Date.parse(b.endTime) > start.getTime()).sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime));
  return <><div className="section-heading"><h2>{date(start.toISOString())} – {date(new Date(end.getTime() - 1).toISOString())}</h2><div className="actions"><button aria-label="Previous week" onClick={() => setDayOffset(n => n - 7)}>←</button><button onClick={() => setDayOffset(0)}>This week</button><button aria-label="Next week" onClick={() => setDayOffset(n => n + 7)}>→</button></div></div>
    <p className="hint">Times shown in {Intl.DateTimeFormat().resolvedOptions().timeZone}. Work windows are manually planned. Automatic placement and buffers are not available yet.</p>
    {!items.length && <div className="empty-state"><h3>Room to make a plan.</h3><p>Set a work window from a task brief to see it here.</p></div>}
    <ul className="row-list schedule-list">{items.map(b => <li className="row-item" key={`${b.kind}-${b.id}`}><div className="execution-row-time">{date(b.startTime)}<span className="hint">{time(b.startTime)}–{time(b.endTime)}</span></div><div className="row-item-main"><span className={`badge ${b.state === "active" ? "now" : ""}`}>{b.kind === "fixed" ? "Fixed" : b.state === "scheduled" && Date.parse(b.endTime) < now ? "Ready to revisit" : label(b.state)}</span><h3>{b.title}</h3>{b.kind === "work" && <p className="hint">You chose this window</p>}</div>{b.taskId !== null && <button onClick={() => select(b.taskId!)}>Task brief</button>}</li>)}</ul></>;
}

function Progress({ data, openGoal }: { data: Workspace; openGoal: (id: number) => void }) {
  const planned = data.blocks.filter(b => b.state !== "superseded").reduce((sum, b) => sum + Date.parse(b.endTime) - Date.parse(b.startTime), 0);
  const executed = data.history.reduce((sum, h) => sum + h.activeMillis, 0);
  const completed = data.tasks.filter(t => t.workState === "completed").length;
  const goalIds = [...new Set(data.tasks.flatMap(t => t.goalId === null ? [] : [t.goalId]))];
  return <><p className="hint">All recorded work · finished sessions only · current task progress</p><dl className="progress-summary"><div><dt>Planned</dt><dd>{duration(planned)}</dd><p className="hint">Work windows, excluding replaced windows</p></div><div><dt>Executed</dt><dd>{duration(executed)}</dd><p className="hint">Active time, excluding pauses</p></div><div><dt>Achieved</dt><dd>{completed} <span>tasks complete</span></dd><p className="hint">Your reported outcomes</p></div></dl>
    <div className="section-heading"><h2>Goal progress</h2></div><ul className="row-list">{goalIds.map(id => {
      const tasks = data.tasks.filter(t => t.goalId === id && t.workState !== "cancelled");
      if (!tasks.length) return null;
      const pct = tasks.reduce((sum, t) => sum + t.completionPct, 0) / tasks.length;
      return <li className="row-item" key={id}><div className="row-item-main"><h3>{tasks[0].goalTitle}</h3><p className="hint">{Math.round(pct)}% · average reported task completion · {tasks.filter(t => t.workState === "completed").length}/{tasks.length} tasks complete</p><progress aria-label={`${tasks[0].goalTitle} progress`} max={100} value={pct} /></div><button onClick={() => openGoal(id)}>Open plan</button></li>;
    })}</ul>{!goalIds.length && <p className="hint">Goal-linked task progress will appear here as you build your plans.</p>}
    <div className="section-heading"><h2>Recent execution</h2></div>{!data.history.length && <p className="hint">Your first finished session will appear here, including what you accomplished.</p>}
    <ul className="row-list">{data.history.slice(0, 20).map(h => <li className="row-item" key={h.blockId}><div className="row-item-main"><h3>{h.title}</h3><p className="hint">{date(h.endTime)} · {time(h.endTime)} · {duration(h.activeMillis)} active · {h.completionPct}% reported</p><p className="report-text">{h.report}</p></div></li>)}</ul></>;
}
