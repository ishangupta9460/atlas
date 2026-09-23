import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { ApiError, Client, jsonBody, messageOf } from "./api";
import { Block, Work, Workspace, ExecutionView, clock, date, duration, elapsed, label, sameDay, time } from "./execution";
import DependencyPanel from "./DependencyPanel";
import TodayScreen from "./TodayScreen";
import ScheduleTimeline from "./ScheduleTimeline";
import { dayBounds, dayItems, timelineItems } from "./dayTimeline";

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
  const [finishedId, setFinishedId] = useState<number | null>(null);
  const [scheduleDay, setScheduleDay] = useState<string | null>(null);
  const closureRef = useRef<HTMLElement>(null);
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
  const scheduled = (data?.blocks.filter(b => b.state === "scheduled") ?? []).sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime));
  const today = scheduled.filter(b => (sameDay(b.startTime, now) || Date.parse(b.startTime) <= now) && Date.parse(b.endTime) > now);
  const current = active ?? today.find(b => Date.parse(b.startTime) <= now);
  const next = today.filter(b => b.id !== current?.id);
  const missed = scheduled.filter(b => Date.parse(b.endTime) <= now);
  const unplanned = tasks.filter(t => t.workState === "ready" && !scheduled.some(b => b.commitmentId === t.id));
  const selectedTask = tasks.find(t => t.id === selected);
  const drafts = tasks.filter(t => t.workState === "draft");
  const finished = data?.history.find(h => h.blockId === finishedId);
  useEffect(() => { if (view === "focus" && finished) closureRef.current?.focus(); }, [view, finished?.blockId]);
  const nextWindow = scheduled.find(b => Date.parse(b.endTime) > now);
  const fixedNow = data?.fixed.filter(f => Date.parse(f.startTime) <= now && Date.parse(f.endTime) > now) ?? [];
  const upcoming = data ? timelineItems(data).filter(i => i.start > now && i.state !== "completed")[0] : undefined;
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
      if (action !== "pause") { setFinishedId(null); setSelected(null); navigate("focus"); }
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
      <div className="execution-row-time">{!sameDay(block.startTime, now) && <span>{date(block.startTime)}</span>}{time(block.startTime)}–{time(block.endTime)}<span className="hint">{Date.parse(block.endTime) <= now ? "Choose a new time" : Date.parse(block.startTime) > now ? `In ${duration(Date.parse(block.startTime) - now)}` : "Window open"}</span></div>
      <div className="row-item-main"><h3>{task?.title ?? "Task"}</h3><p className="hint">{task?.goalTitle ?? "Independent task"}</p></div>
      <button onClick={() => setSelected(block.commitmentId)}>Task brief</button>
    </li>;
  }
  function hero(block: Block) {
    const task = taskFor(block);
    const conflicts = data?.fixed.filter(f => Date.parse(f.startTime) < Date.parse(block.endTime) && Date.parse(f.endTime) > Math.max(now, Date.parse(block.startTime))) ?? [];
    return <section className={`active-task-panel ${view === "focus" ? "focus-panel" : ""}`} aria-label="Current task">
      <div className="task-breadcrumb"><span className="active-task-label">{block.sessionState === "paused" ? "Paused · continue when ready" : block.sessionState === "running" ? "In progress" : "Your window is open"}</span><span className="hint">{time(block.startTime)}–{time(block.endTime)}</span></div>
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
        <DayOverview data={data} now={now} />
        <div className="section-heading"><h2>Now</h2><button className="text-button" onClick={() => setShowCapture(true)}>Add a task</button></div>
        {current ? hero(current) : <div className="day-breathing-room"><h3>{fixedNow.length ? fixedNow.map(f => f.title).join(" · ") : "A little room in your day."}</h3><p>{fixedNow.length ? `Fixed commitment · until ${time(new Date(Math.max(...fixedNow.map(f => Date.parse(f.endTime)))).toISOString())}` : upcoming ? `${duration(upcoming.start - now)} until ${upcoming.title} at ${time(new Date(upcoming.start).toISOString())}. No work window is open right now.` : "No work window is open right now. Choose a task when you’re ready."}</p><button className="text-button" onClick={() => unplanned[0] ? setSelected(unplanned[0].id) : setShowCapture(true)}>{unplanned.length ? "Choose work for this time →" : "Capture a next step"}</button></div>}
        <section aria-label="Next"><div className="section-heading"><h2>Next</h2><button className="text-button" onClick={() => navigate("schedule")}>View schedule →</button></div>
          {next.length ? <ul className="row-list">{next.slice(0, 3).map(row)}</ul> : <p className="hint">No more work windows today. Leave room, or choose another task.</p>}
        </section>
        {next.length > 3 && <details className="disclosure"><summary>Later · {next.length - 3} more today</summary><ul className="row-list">{next.slice(3).map(row)}</ul></details>}
        {missed.length > 0 && <details className="disclosure"><summary>Pick up where you left off · {missed.length} unstarted {missed.length === 1 ? "window" : "windows"}</summary><p className="hint">These windows have passed. Nothing has been marked as failed. Open a task to choose a new time.</p><ul className="row-list">{missed.map(row)}</ul></details>}
        <details className="disclosure" open={!current}><summary>Ready when you are · {unplanned.length} unplanned tasks</summary><p className="hint">In capture order. These tasks do not have scheduled times yet.</p>
          <ul className="row-list">{unplanned.map(task => <li className="row-item" key={task.id}><div className="row-item-main"><h3>{task.title}</h3><p className="hint">{task.goalTitle ?? "Independent task"}{task.blockers > 0 ? ` · ${task.blockers} unfinished prerequisites` : ""}</p></div><button onClick={() => setSelected(task.id)}>{task.blockers ? "Review prerequisites" : "Schedule this"}</button></li>)}</ul>
          {!unplanned.length && <p className="hint">Add a task, or shape your next step in Goals.</p>}
        </details>
        {drafts.length > 0 && <details className="disclosure" open={!current && !unplanned.length}><summary>Captured · {drafts.length} to make ready</summary><p className="hint">Your ideas are saved. Define done when you want to work on one.</p><ul className="row-list">{drafts.map(task => <li className="row-item" key={task.id}><h3 className="row-item-main">{task.title ?? "Untitled draft"}</h3><button onClick={() => setSelected(task.id)}>Make ready</button></li>)}</ul></details>}
        <details className="disclosure" onToggle={e => setShowLegacy(e.currentTarget.open)}><summary>Earlier quick tasks</summary><p className="hint">Tasks captured before goal planning are kept here with their original start and finish actions.</p>{showLegacy && <TodayScreen client={client} />}</details>
      </>}
      {view === "focus" && <>
        {active ? hero(active) : finished ? <section className="session-closure" aria-label="Session saved" tabIndex={-1} ref={closureRef}>
          <p className="eyebrow">{finished.completionPct === 100 ? "Done. A step forward." : "A good place to pause."}</p><h2>{finished.title}</h2><p className="report-text">{finished.report}</p><p className="hint">{duration(finished.activeMillis)} of focused work · {finished.completionPct}% complete · saved in Progress</p>
          <div className="closure-next"><p className="eyebrow">What comes next</p>{nextWindow ? <><h3>{taskFor(nextWindow)?.title}</h3><p className="hint">{date(nextWindow.startTime)} · {time(nextWindow.startTime)}–{time(nextWindow.endTime)}</p>{actions(nextWindow)}</> : unplanned.length ? <><h3>{unplanned[0].title}</h3><p className="hint">Ready when you are · choose a work window</p><button onClick={() => setSelected(unplanned[0].id)}>Schedule this</button></> : <p>{finished.completionPct < 100 ? "Pick up where you left off with another work window." : "There’s room to pause, or choose another task."}</p>}</div>
          <div className="actions"><button className="primary" onClick={() => navigate("today")}>See what’s next →</button>{finished.completionPct < 100 && <button onClick={() => setSelected(finished.commitmentId)}>Plan remaining work</button>}<button className="text-button" onClick={() => navigate("progress")}>View progress →</button></div>
        </section> : <div className="empty-state"><h3>Choose one thing.</h3><p>No session is running. Choose your next step on Today.</p><button className="primary" onClick={() => navigate("today")}>See what’s next →</button></div>}
        {active && finishing && <FinishForm key={active.id} task={taskFor(active)!} busy={busy} onCancel={() => setFinishing(false)} onFinish={async (report, completionPct) => {
          if (await mutate(`/blocks/${active.id}/session/finish`, { report, completionPct })) {
            setFinishedId(active.id); setFinishing(false); setNotice(completionPct === 100 ? "Task complete. Your progress is saved." : "Session saved. The remaining work is ready for another window.");
          }
        }} />}
        <button className="text-button" onClick={() => navigate("today")}>← Back to Today</button>
      </>}
      {view === "schedule" && <ScheduleTimeline data={data} now={now} selectedDay={scheduleDay} changeDay={setScheduleDay} select={setSelected} capture={() => setShowCapture(true)} />}
      {view === "progress" && <Progress data={data} openGoal={openGoal} />}
      {selectedTask && <TaskBrief key={selectedTask.id} task={selectedTask} client={client} blocks={data.blocks} now={now} busy={busy} refresh={() => setRetry(n => n + 1)} close={() => setSelected(null)} openGoal={openGoal} work={block => void transition(block, block.sessionState === "paused" ? "resume" : "start")} focus={() => navigate("focus")} onPlace={async (startTime, endTime, oldId) => {
        const saved = await mutate(oldId ? `/schedule/blocks/${oldId}/move` : "/schedule/blocks", { commitmentId: selectedTask.id, startTime, endTime });
        if (saved) {
          setScheduleDay(startTime);
          setSelected(null); setNotice("Work window saved. It’s ready on Today and Schedule."); navigate(sameDay(startTime, now) ? "today" : "schedule");
        }
        return saved || replay.current !== null;
      }} />}
    </>}
    {showCapture && <Capture client={client} close={() => setShowCapture(false)} saved={id => { setShowCapture(false); setSelected(id); setRetry(n => n + 1); }} />}
  </>;
}

function TaskBrief({ task, blocks, client, now, busy, close, onPlace, openGoal, refresh, work, focus }: {
  task: Work; blocks: Block[]; client: Client; now: number; busy: boolean; close: () => void;
  onPlace: (start: string, end: string, oldId?: number) => Promise<boolean>; openGoal: (id: number) => void;
  refresh: () => void; work: (block: Block) => void; focus: () => void;
}) {
  const existing = blocks.find(b => b.commitmentId === task.id && b.state === "scheduled");
  const session = blocks.find(b => b.commitmentId === task.id && b.state === "active");
  const otherSession = blocks.some(b => b.state === "active" && b.commitmentId !== task.id);
  const [minutes, setMinutes] = useState(25);
  const [when, setWhen] = useState("");
  const [dependencies, setDependencies] = useState(false);
  const [contextOpen, setContextOpen] = useState(false);
  const [localError, setLocalError] = useState("");
  const placementAttempt = useRef<{ signature: string; start: string; end: string } | null>(null);
  const brief = useRef<HTMLElement>(null);
  useEffect(() => { brief.current?.focus(); brief.current?.scrollIntoView?.({ behavior: window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" }); }, []);
  return <section className="task-brief panel" aria-label="Task brief" tabIndex={-1} ref={brief}>
    <div className="section-heading"><h2>Task brief</h2><button disabled={busy} onClick={close}>Close</button></div>
    <p className="eyebrow">{task.workState === "draft" ? "Captured → Define done → Schedule" : session ? "Ready → Scheduled → In progress" : existing ? "Ready → Scheduled → Start" : task.workState === "ready" ? "Ready → Choose a work window" : label(task.workState)}</p>
    <h1>{task.title ?? "Untitled task"}</h1>
    {task.goalTitle && <button className="text-button goal-context" onClick={() => openGoal(task.goalId!)}>Goal · {task.goalTitle} →</button>}
    {task.milestoneTitle && <p className="hint">Milestone · {task.milestoneTitle}</p>}
    {task.workState === "draft" ? <ReadyForm task={task} client={client} saved={refresh} /> : <><h2 className="brief-label">What done looks like</h2><p>{task.completionCriterion}</p></>}
    <p className="hint">{label(task.workState)} · {task.completionPct}% reported complete</p>
    <details className="disclosure" onToggle={e => setContextOpen(e.currentTarget.open)}><summary>More context</summary><dl className="context-grid"><dt>Importance</dt><dd>{label(task.importance)}</dd><dt>Flexibility</dt><dd>{label(task.flexibilityTier)}</dd><dt>Category</dt><dd>{task.categoryName ?? "None"}</dd><dt>Deadline</dt><dd>{task.deadline ? `${date(task.deadline)} · ${time(task.deadline)}` : "No deadline"}</dd></dl>{task.description && <p className="report-text">{task.description}</p>}{contextOpen && <TaskPreferences task={task} client={client} saved={refresh} />}</details>
    <details className="disclosure" onToggle={e => setDependencies(e.currentTarget.open)}><summary>Prerequisites · {task.blockers} unfinished</summary>{dependencies && <DependencyPanel task={task} client={client} onChanged={refresh} />}</details>
    {existing && <p className="execution-notice">{date(existing.startTime)} · {time(existing.startTime)}–{time(existing.endTime)}. {existing.placementReason}</p>}
    {session && <div className="actions"><button className="primary" disabled={busy} onClick={focus}>Continue in Focus →</button></div>}
    {existing && Date.parse(existing.startTime) <= now && Date.parse(existing.endTime) > now && <div className="actions"><button className="primary" disabled={busy || otherSession || task.blockers > 0} onClick={() => work(existing)}>Start this window →</button>{otherSession && <p className="hint">Finish your current session first.</p>}</div>}
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
      {task.blockers > 0 && <p className="hint">Finish the prerequisites first. Review them above, then come back to choose a window.</p>}
      <div className="actions"><button className="primary" disabled={busy || task.blockers > 0}>{busy ? "Saving…" : existing ? "Move work window" : "Set work window"}</button></div>
    </form>}
  </section>;
}

function TaskPreferences({ task, client, saved }: { task: Work; client: Client; saved: () => void }) {
  const [importance, setImportance] = useState(task.importance);
  const [flexibility, setFlexibility] = useState(task.flexibilityTier);
  const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  return <form onSubmit={async e => {
    e.preventDefault(); if (busy) return; setBusy(true); setError("");
    try { await client(`/commitments/${task.id}`, { method: "PATCH", ...jsonBody({ importance, flexibilityTier: flexibility }) }); saved(); }
    catch (e) { setError(messageOf(e)); } finally { setBusy(false); }
  }}><div className="form-grid"><div><label htmlFor="brief-importance">Importance</label><select id="brief-importance" value={importance} disabled={busy} onChange={e => setImportance(e.target.value)}>{["low", "medium", "high", "critical"].map(v => <option key={v} value={v}>{label(v)}</option>)}</select></div><div><label htmlFor="brief-flexibility">Flexibility</label><select id="brief-flexibility" value={flexibility} disabled={busy} onChange={e => setFlexibility(e.target.value)}>{["fixed", "protected", "flexible", "optional"].map(v => <option key={v} value={v}>{label(v)}</option>)}</select></div></div><p className="hint">These choices describe the task. Your manually placed window stays where you put it.</p>{error && <p className="error" role="alert">{error}</p>}<div className="actions"><button disabled={busy}>Save details</button></div></form>;
}

function ReadyForm({ task, client, saved }: { task: Work; client: Client; saved: () => void }) {
  const [title, setTitle] = useState(task.title ?? "");
  const [criterion, setCriterion] = useState(task.completionCriterion ?? "");
  const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  return <form onSubmit={async e => {
    e.preventDefault(); if (busy || !title.trim() || !criterion.trim()) return;
    setBusy(true); setError("");
    try { await client(`/commitments/${task.id}`, { method: "PATCH", ...jsonBody({ title: title.trim(), completionCriterion: criterion.trim() }) }); saved(); }
    catch (e) { setError(messageOf(e)); } finally { setBusy(false); }
  }}><h2 className="brief-label">Make it ready</h2><p className="hint">Your task is saved. Define a clear finish so you know what to aim for. Then choose when to work.</p>
    {!task.title?.trim() && <><label htmlFor="ready-title">What needs doing?</label><input id="ready-title" maxLength={255} required value={title} disabled={busy} onChange={e => setTitle(e.target.value)} /></>}
    <label htmlFor="ready-criterion">What will done look like?</label><textarea id="ready-criterion" required value={criterion} disabled={busy} onChange={e => setCriterion(e.target.value)} />
    {error && <p className="error" role="alert">{error}</p>}<div className="actions"><button className="primary" disabled={busy || !title.trim() || !criterion.trim()}>{busy ? "Saving…" : "Make ready → Schedule"}</button></div>
  </form>;
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

function Capture({ client, close, saved }: { client: Client; close: () => void; saved: (id: number) => void }) {
  const [title, setTitle] = useState(""); const [criterion, setCriterion] = useState(""); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault(); if (busy) return; setBusy(true); setError("");
    try { const result = await client<{ id: number }>("/commitments", { method: "POST", ...jsonBody({ title: title.trim(), completionCriterion: criterion.trim() || null, importance: "medium", flexibilityTier: "flexible" }) }); saved(result.id); }
    catch (e) { setError(messageOf(e)); } finally { setBusy(false); }
  }
  return <form className="panel task-brief" onSubmit={submit}><div className="section-heading"><h2>A concrete next step</h2><button type="button" onClick={close} disabled={busy}>Close</button></div><label htmlFor="capture-title">What needs doing?</label><input id="capture-title" autoFocus maxLength={255} required value={title} onChange={e => setTitle(e.target.value)} disabled={busy} /><p className="hint">Just capture the task. You can define done and choose a time next.</p><details className="disclosure"><summary>Already know what done looks like?</summary><label htmlFor="capture-criterion">What will done look like?</label><textarea id="capture-criterion" value={criterion} onChange={e => setCriterion(e.target.value)} disabled={busy} /></details><p className="hint">Starts with medium importance and flexible placement.</p>{error && <p className="error" role="alert">{error}</p>}<div className="actions"><button className="primary" disabled={busy || !title.trim()}>Add task</button></div></form>;
}

function DayOverview({ data, now }: { data: Workspace; now: number }) {
  const { start, end } = dayBounds(now);
  const items = dayItems(timelineItems(data), now);
  const percent = (n: number) => (n - start) / (end - start) * 100;
  const sessions = data.history.filter(h => sameDay(h.endTime, now));
  return <section className="day-overview" aria-label="Your day at a glance">
    <div className="day-overview-labels"><time dateTime={new Date(now).toISOString()}>Now · {time(new Date(now).toISOString())}</time><span className="hint">{sessions.length ? `${duration(sessions.reduce((sum, h) => sum + h.activeMillis, 0))} focused today` : `${items.length} recorded ${items.length === 1 ? "window" : "windows"} today`}</span></div>
    <div className="day-track" aria-hidden="true">{items.map(i => <span className={`day-mark ${i.kind} ${i.state}`} key={i.key} style={{ left: `${percent(Math.max(start, i.start))}%`, width: `${percent(Math.min(end, i.end)) - percent(Math.max(start, i.start))}%` }} />)}<span className="day-cursor" style={{ left: `${percent(now)}%` }} /></div>
    <div className="day-overview-labels hint"><span>Midnight</span><span>Midday</span><span>Midnight</span></div>
    <p className="hint">Work windows and fixed commitments · blank space is unplanned</p>
  </section>;
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
