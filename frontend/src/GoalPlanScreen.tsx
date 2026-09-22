import { FormEvent, useEffect, useState } from "react";
import { ApiError, Client, jsonBody, messageOf } from "./api";

type Milestone = { id: number; title: string; order: number };
type Roadmap = { id: number; milestones: Milestone[] };
type Commitment = {
  id: number; title: string | null; completionCriterion: string | null;
  milestoneId: number | null; importance: string; flexibilityTier: string; workState: string;
};
type Page = { commitments: Commitment[]; nextCursor: number | null };
const label = (value: string) => value.replace(/_/g, " ");

export default function GoalPlanScreen({ goal, client, onBack }: {
  goal: { id: number; title: string }; client: Client; onBack: () => void;
}) {
  const [roadmap, setRoadmap] = useState<Roadmap | null>(null);
  const [tasks, setTasks] = useState<Commitment[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [retry, setRetry] = useState(0);
  const [milestoneTitle, setMilestoneTitle] = useState("");
  const [editor, setEditor] = useState<Commitment | "new" | null>(null);
  const [editingMilestone, setEditingMilestone] = useState<number | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setLoaded(false); setError("");
    Promise.all([
      client<{ roadmap: Roadmap | null }>(`/goals/${goal.id}/roadmap`, { signal: controller.signal }),
      client<Page>(`/goals/${goal.id}/commitments`, { signal: controller.signal }),
    ]).then(([plan, page]) => {
      if (controller.signal.aborted) return;
      setRoadmap(plan.roadmap); setTasks(page.commitments); setCursor(page.nextCursor); setLoaded(true);
    }).catch(failure => { if (!controller.signal.aborted) setError(messageOf(failure)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [client, goal.id, retry]);

  async function addMilestone(event: FormEvent) {
    event.preventDefault();
    if (pending || !milestoneTitle.trim()) return;
    setPending(true); setError(""); setNotice("");
    try {
      let plan = roadmap;
      if (!plan) {
        try { plan = await client<Roadmap>(`/goals/${goal.id}/roadmaps`, { method: "POST", ...jsonBody({}) }); }
        catch (failure) {
          if (!(failure instanceof ApiError) || failure.status !== 409) throw failure;
          plan = (await client<{ roadmap: Roadmap | null }>(`/goals/${goal.id}/roadmap`)).roadmap;
          if (!plan) throw failure;
        }
        // Keep the successfully created roadmap even if saving its first milestone fails.
        setRoadmap(plan);
      }
      const milestone = await client<Milestone>(`/roadmaps/${plan.id}/milestones`, {
        method: "POST", ...jsonBody({ title: milestoneTitle.trim(), order: Math.max(0, ...plan.milestones.map(item => item.order)) + 1 }),
      });
      setRoadmap({ ...plan, milestones: [...plan.milestones, milestone] }); setMilestoneTitle(""); setNotice("Milestone saved.");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  async function loadMore() {
    if (pending || cursor === null) return;
    setPending(true); setError("");
    try {
      const page = await client<Page>(`/goals/${goal.id}/commitments?cursor=${cursor}`);
      setTasks(previous => [...previous, ...page.commitments.filter(item => !previous.some(existing => existing.id === item.id))]);
      setCursor(page.nextCursor);
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  return <>
    <button className="text-button" onClick={onBack}>← All goals</button>
    <div className="page-heading"><p className="eyebrow">MAKE THE NEXT STEP CLEAR</p><h1>{goal.title}</h1><p>Shape a few milestones, or go straight to a task.</p></div>
    {loading ? <p role="status">Loading your plan…</p> : <>
      {error && <div className="error" role="alert">{error} <button disabled={pending || editor !== null || editingMilestone !== null} onClick={() => setRetry(value => value + 1)}>Reload plan</button></div>}
      {notice && <p role="status" className="notice">{notice}</p>}
      {loaded && <>
      <section aria-labelledby="milestone-heading" className="plan-section">
        <h2 id="milestone-heading">Milestones <span className="hint">Optional</span></h2>
        <p className="hint">Useful steps along the way. Each can hold a few tasks.</p>
        <ol className="milestone-list">{roadmap?.milestones.map(milestone => <li key={milestone.id}>
          {editingMilestone === milestone.id ? <MilestoneEditor milestone={milestone} client={client} onClose={() => setEditingMilestone(null)} onSaved={updated => {
            setRoadmap(previous => previous && ({ ...previous, milestones: previous.milestones.map(item => item.id === updated.id ? updated : item) }));
            setEditingMilestone(null); setNotice("Milestone updated.");
          }} /> : <div className="milestone-row"><span>{milestone.title}</span><button className="text-button" disabled={pending || editor !== null || editingMilestone !== null} aria-label={`Rename ${milestone.title}`} onClick={() => setEditingMilestone(milestone.id)}>Rename</button></div>}
        </li>)}</ol>
        <form onSubmit={addMilestone}><label htmlFor="milestone-title">What is a useful milestone?</label><div className="input-row">
          <input id="milestone-title" value={milestoneTitle} onChange={e => setMilestoneTitle(e.target.value)} placeholder="For example, play a first song" maxLength={255} required disabled={pending || editor !== null || editingMilestone !== null} />
          <button disabled={pending || editor !== null || editingMilestone !== null || !milestoneTitle.trim()}>Add milestone</button>
        </div></form>
      </section>
      <section aria-labelledby="plan-tasks-heading" className="plan-section">
        <div className="section-heading"><h2 id="plan-tasks-heading">Actionable tasks</h2><button className="primary" disabled={pending || editor !== null || editingMilestone !== null} onClick={() => { setEditor("new"); setNotice(""); }}>Add a task</button></div>
        <p className="hint">Define what done looks like to make a task ready. Scheduling comes next.</p>
        {editor !== null && <CommitmentEditor key={editor === "new" ? "new" : editor.id} task={editor === "new" ? null : editor} goalId={goal.id} milestones={roadmap?.milestones ?? []} client={client} onClose={() => setEditor(null)} onSaved={saved => {
          setTasks(previous => previous.some(item => item.id === saved.id) ? previous.map(item => item.id === saved.id ? saved : item) : [saved, ...previous]);
          setEditor(null); setNotice(saved.workState === "draft" ? "Draft saved. You can define done when you're ready." : "Task saved.");
        }} />}
        {!tasks.length && !error && editor === null && <p className="plan-empty">What is one small action that would move this goal forward?</p>}
        <ul className="plan-task-list">{tasks.map(task => <li key={task.id} className="plan-task">
          <div className="badges"><span className="badge">{label(task.workState)}</span><span className="hint">{label(task.importance)} importance · {label(task.flexibilityTier)}</span></div>
          <h3>{task.title || "Untitled draft"}</h3>
          <p>{task.completionCriterion ? `Done when: ${task.completionCriterion}` : "Still defining what done looks like."}</p>
          <p className="hint">{roadmap?.milestones.find(item => item.id === task.milestoneId)?.title ?? "Directly supports this goal"}</p>
          <button className="text-button" disabled={pending || editor !== null || editingMilestone !== null} aria-label={`Edit ${task.title || "untitled draft"}`} onClick={() => { setEditor(task); setNotice(""); }}>Edit task</button>
        </li>)}</ul>
        {cursor !== null && <button disabled={pending || editor !== null || editingMilestone !== null} onClick={() => void loadMore()}>Show more tasks</button>}
      </section>
      </>}
    </>}
  </>;
}

function MilestoneEditor({ milestone, client, onSaved, onClose }: { milestone: Milestone; client: Client; onSaved: (value: Milestone) => void; onClose: () => void }) {
  const [title, setTitle] = useState(milestone.title);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function save(event: FormEvent) {
    event.preventDefault(); if (pending || !title.trim()) return;
    setPending(true); setError("");
    try { onSaved(await client<Milestone>(`/milestones/${milestone.id}`, { method: "PATCH", ...jsonBody({ title: title.trim() }) })); }
    catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }
  return <form onSubmit={save}><label htmlFor="rename-milestone">Milestone name</label><input id="rename-milestone" value={title} onChange={e => setTitle(e.target.value)} maxLength={255} required disabled={pending} autoFocus />
    {error && <p role="alert" className="error">{error}</p>}<div className="actions"><button disabled={pending || !title.trim()}>Save milestone</button><button type="button" disabled={pending} onClick={onClose}>Cancel</button></div></form>;
}

function CommitmentEditor({ task, goalId, milestones, client, onSaved, onClose }: {
  task: Commitment | null; goalId: number; milestones: Milestone[]; client: Client; onSaved: (value: Commitment) => void; onClose: () => void;
}) {
  const [title, setTitle] = useState(task?.title ?? "");
  const [criterion, setCriterion] = useState(task?.completionCriterion ?? "");
  const [importance, setImportance] = useState(task?.importance ?? "");
  const [flexibility, setFlexibility] = useState(task?.flexibilityTier ?? "");
  const [milestone, setMilestone] = useState(task?.milestoneId?.toString() ?? "");
  const [step, setStep] = useState(0);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const criterionRequired = task !== null && task.workState !== "draft";
  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending || !title.trim() || (criterionRequired && !criterion.trim())) return;
    if (step === 0) { setStep(1); return; }
    if (!importance || !flexibility) return;
    setPending(true); setError("");
    try { onSaved(await client<Commitment>(task ? `/commitments/${task.id}` : "/commitments", {
      method: task ? "PATCH" : "POST", ...jsonBody({ title: title.trim(), completionCriterion: criterion.trim() || null,
        goalId, milestoneId: milestone ? Number(milestone) : null, importance, flexibilityTier: flexibility }),
    })); }
    catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }
  return <form className="card commitment-editor" onSubmit={save}>
    <h3>{task ? "Shape this task" : "One concrete next step"}</h3>
    {step === 0 ? <>
      <label htmlFor="plan-task-title">What will you do?</label><input id="plan-task-title" value={title} onChange={e => setTitle(e.target.value)} maxLength={255} required disabled={pending} autoFocus />
      <label htmlFor="criterion">What will done look like? <span className="hint">{criterionRequired ? "Required for a ready task" : "Leave empty to save a draft"}</span></label>
      <textarea id="criterion" value={criterion} onChange={e => setCriterion(e.target.value)} required={criterionRequired} disabled={pending} rows={3} />
    </> : <>
      <label htmlFor="importance">How important is this task?</label><select id="importance" required value={importance} onChange={e => setImportance(e.target.value)} disabled={pending}><option value="">Choose importance</option>{["low", "medium", "high", "critical"].map(value => <option key={value} value={value}>{label(value)}</option>)}</select>
      <label htmlFor="flexibility">How flexible is its placement?</label><select id="flexibility" required value={flexibility} onChange={e => setFlexibility(e.target.value)} disabled={pending}><option value="">Choose flexibility</option>{["fixed", "protected", "flexible", "optional"].map(value => <option key={value} value={value}>{label(value)}</option>)}</select>
      <p className="hint">Fixed stays put; protected avoids disruption; flexible can move; optional yields first.</p>
      {milestones.length > 0 && <details><summary>Place under a milestone <span className="hint">Optional</span></summary><label htmlFor="task-milestone">Milestone</label><select id="task-milestone" value={milestone} onChange={e => setMilestone(e.target.value)} disabled={pending}><option value="">Directly under this goal</option>{milestones.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}</select></details>}
    </>}
    {error && <p role="alert" className="error">{error}</p>}
    <div className="actions">{step === 1 && <button type="button" disabled={pending} onClick={() => setStep(0)}>Back</button>}<button className="primary" disabled={pending || !title.trim() || (step === 1 && (!importance || !flexibility))}>{pending ? "Saving…" : step === 0 ? "Continue" : criterion.trim() ? "Save task" : "Save draft"}</button><button type="button" disabled={pending} onClick={onClose}>Cancel</button></div>
  </form>;
}
