import { FormEvent, useEffect, useState } from "react";
import { Client, jsonBody, messageOf } from "./api";

type Task = { id: number; title: string; status: "ready" | "in_progress" | "completed" };

/** Preserves the Phase 0 task API until the coordinated scheduling/execution cutover. */
export default function TodayScreen({ client }: { client: Client }) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [title, setTitle] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError("");
    client<Task[]>("/api/tasks/today", { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setTasks(data);
    }).catch(failure => { if (!controller.signal.aborted) setError(messageOf(failure)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [client, retry]);

  async function create(event: FormEvent) {
    event.preventDefault();
    if (pending || !title.trim()) return;
    setPending(true); setError(""); setNotice("");
    try {
      const task = await client<Task>("/api/tasks", { method: "POST", ...jsonBody({ title: title.trim() }) });
      setTasks(previous => [...previous, task]); setTitle(""); setNotice("Task added.");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }
  async function transition(task: Task, action: "start" | "finish") {
    if (pending) return;
    setPending(true); setError(""); setNotice("");
    try {
      const updated = await client<Task>(`/api/tasks/${task.id}/${action}`, { method: "POST" });
      setTasks(previous => previous.map(item => item.id === task.id ? updated : item).filter(item => item.status !== "completed"));
      setNotice(action === "finish" ? "Finished. A little more progress made." : "You're underway.");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }
  return <>
    <div className="page-heading"><p className="eyebrow">MAKE A LITTLE PROGRESS</p><h1>One thing to work on.</h1><p>Capture a task, start when you're ready, and mark it finished.</p></div>
    <form className="capture card" onSubmit={create}><label htmlFor="task-title">What needs doing?</label><div className="input-row"><input id="task-title" value={title} onChange={e => setTitle(e.target.value)} maxLength={255} required disabled={pending || loading} placeholder="A small, concrete next step" /><button className="primary" disabled={pending || loading || !title.trim()}>Add task</button></div></form>
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="notice" role="status">{notice}</p>}
    <div className="section-heading"><h2>Your tasks</h2><button className="text-button" disabled={loading || pending} onClick={() => setRetry(retry + 1)}>Refresh tasks</button></div>
    {loading ? <p role="status">Loading tasks…</p> : tasks.length ? <ul className="task-list">{tasks.map(task => <li className="card task-row" key={task.id}><div><span className="badge">{task.status === "ready" ? "Ready when you are" : "In progress"}</span><h3>{task.title}</h3></div><button className={task.status === "in_progress" ? "primary" : ""} disabled={pending} onClick={() => void transition(task, task.status === "ready" ? "start" : "finish")}>{task.status === "ready" ? "Start" : "Finish"}</button></li>)}</ul> : !error && <div className="empty-state"><h3>A clear space.</h3><p>Add a next step whenever you're ready.</p></div>}
  </>;
}
