import { FormEvent, useEffect, useState } from "react";
import { Client, jsonBody, messageOf } from "./api";
import GoalPlanScreen from "./GoalPlanScreen";

type Goal = {
  id: number; title: string; description: string | null; targetDeadline: string | null;
  lifecycleState: "active" | "completed" | "abandoned";
  planningState: "active" | "deferred" | "at_risk" | "paused";
};
type GoalPage = { goals: Goal[]; nextCursor: number | null };
const stateLabel = (state: string) => state.replace(/_/g, " ");

export default function GoalsScreen({ client }: { client: Client }) {
  const [goals, setGoals] = useState<Goal[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [title, setTitle] = useState("");
  const [selected, setSelected] = useState<Goal | null>(null);
  const [retry, setRetry] = useState(0);
  const [planningGoal, setPlanningGoal] = useState<Goal | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError("");
    client<GoalPage>("/goals", { signal: controller.signal }).then(page => {
      if (!controller.signal.aborted) { setGoals(page.goals); setCursor(page.nextCursor); }
    }).catch(failure => { if (!controller.signal.aborted) setError(messageOf(failure)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [client, retry]);

  async function create(event: FormEvent) {
    event.preventDefault();
    if (pending || !title.trim()) return;
    setPending(true); setError(""); setNotice("");
    try {
      const goal = await client<Goal>("/goals", { method: "POST", ...jsonBody({ title: title.trim() }) });
      setGoals(previous => [goal, ...previous]); setTitle(""); setSelected(goal); setNotice("Goal saved. Add a deadline only if you have one.");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  async function loadMore() {
    if (pending || !cursor) return;
    setPending(true); setError("");
    try {
      const page = await client<GoalPage>(`/goals?cursor=${cursor}`);
      setGoals(previous => [...previous, ...page.goals]); setCursor(page.nextCursor);
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  if (planningGoal) return <GoalPlanScreen key={planningGoal.id} goal={planningGoal} client={client} onBack={() => setPlanningGoal(null)} />;

  return <>
    <div className="page-heading"><p className="eyebrow">A DIRECTION, ONE STEP AT A TIME</p><h1>What do you want to achieve?</h1><p>Start small. You can shape the details as you go.</p></div>
    <form className="capture card" onSubmit={create}>
      <label htmlFor="goal-title">Your next goal</label>
      <div className="input-row"><input id="goal-title" placeholder="Something you want to make progress on" maxLength={255} required value={title} onChange={e => setTitle(e.target.value)} disabled={pending || loading} /><button className="primary" disabled={pending || loading || !title.trim()}>Save goal</button></div>
    </form>
    {error && <div className="error" role="alert">{error} <button disabled={pending || loading} onClick={() => setRetry(retry + 1)}>Reload goals</button></div>}
    {notice && <p role="status" className="notice">{notice}</p>}
    <div className="section-heading"><h2>Your goals</h2><span className="hint">Keep the direction. Adjust the details.</span></div>
    {loading ? <p role="status">Loading goals…</p> : <>
      {!goals.length && !error && <div className="empty-state"><span className="empty-mark" aria-hidden="true">↗</span><h3>A little intention goes a long way.</h3><p>Your saved goals will be here when you return.</p></div>}
      <div className="goal-grid">{goals.map(goal => <article className="card goal-card" key={goal.id}>
        <div className="badges"><span className="badge">{stateLabel(goal.lifecycleState)}</span>{goal.lifecycleState === "active" && goal.planningState !== "active" && <span className={`badge ${goal.planningState === "at_risk" ? "risk" : ""}`}>{stateLabel(goal.planningState)}</span>}</div>
        {selected?.id === goal.id ? <GoalEditor key={goal.id} goal={goal} client={client} onCancel={() => setSelected(null)} onSaved={updated => { setGoals(previous => previous.map(item => item.id === updated.id ? updated : item)); setSelected(null); setNotice("Changes saved."); }} /> : <>
          <h3>{goal.title}</h3>{goal.description && <p>{goal.description}</p>}
          <p className="hint">{goal.targetDeadline ? `Target date · ${goal.targetDeadline}` : "No deadline set"}</p>
          <div className="actions"><button onClick={() => { setPlanningGoal(goal); setNotice(""); }}>Open plan</button><button className="text-button" onClick={() => { setSelected(goal); setNotice(""); }}>Edit goal <span aria-hidden="true">↗</span></button></div>
        </>}
      </article>)}</div>
      {cursor && <button className="load-more" disabled={pending} onClick={() => void loadMore()}>{pending ? "Loading…" : "Show more goals"}</button>}
    </>}
  </>;
}
function GoalEditor({ goal, client, onSaved, onCancel }: { goal: Goal; client: Client; onSaved: (goal: Goal) => void; onCancel: () => void }) {
  const [title, setTitle] = useState(goal.title);
  const [deadline, setDeadline] = useState(goal.targetDeadline ?? "");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending || !title.trim()) return;
    setPending(true); setError("");
    try { onSaved(await client<Goal>(`/goals/${goal.id}`, { method: "PATCH", ...jsonBody({ title: title.trim(), targetDeadline: deadline || null }) })); }
    catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }
  return <form onSubmit={save}>
    <label htmlFor={`edit-title-${goal.id}`}>Goal</label><input id={`edit-title-${goal.id}`} value={title} onChange={e => setTitle(e.target.value)} maxLength={255} required disabled={pending} autoFocus />
    <label htmlFor={`deadline-${goal.id}`}>Is there a target date? <span className="hint">Optional</span></label><input id={`deadline-${goal.id}`} type="date" value={deadline} max="9999-12-31" onChange={e => setDeadline(e.target.value)} disabled={pending} />
    <p className="hint">Leave this empty if you're still exploring.</p>
    {error && <p className="error" role="alert">{error}</p>}
    <div className="actions"><button className="primary" disabled={pending || !title.trim()}>{pending ? "Saving…" : "Save changes"}</button><button type="button" disabled={pending} onClick={onCancel}>Close</button></div>
  </form>;
}
