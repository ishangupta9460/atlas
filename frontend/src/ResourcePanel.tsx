import useReplayClient from "./useReplayClient";
import { useEffect, useState } from "react";
import { Client, jsonBody, messageOf } from "./api";

export type Resource = { id: number; type: string; title: string; urlOrFileRef: string; addedBy: string };
export function mutation(value?: unknown): RequestInit {
  const body = value === undefined ? {} : jsonBody(value);
  return { ...body, headers: { ...body.headers, "Idempotency-Key": crypto.randomUUID() } };
}
export default function ResourcePanel({ client, taskId }: { client: Client; taskId: number }) {
  const send = useReplayClient(client);
  const [attached, setAttached] = useState<Resource[]>([]), [library, setLibrary] = useState<Resource[]>([]);
  const [selected, setSelected] = useState(""), [title, setTitle] = useState(""), [reference, setReference] = useState(""), [type, setType] = useState("link");
  const [error, setError] = useState(""), [notice, setNotice] = useState(""), [busy, setBusy] = useState(false);
  const [replacement, setReplacement] = useState<Record<number, string>>({});
  useEffect(() => {
    let live = true;
    Promise.all([client<Resource[]>(`/commitments/${taskId}/resources`), client<Resource[]>("/resources")]).then(([a, b]) => { if (live) { setAttached(a); setLibrary(b); } }).catch(e => { if (live) setError(messageOf(e)); });
    return () => { live = false; };
  }, [client, taskId]);
  async function act(action: () => Promise<void>) {
    setBusy(true); setError(""); setNotice("");
    try { await action(); } catch (e) { setError(messageOf(e)); } finally { setBusy(false); }
  }
  const options = library.map(r => <option key={r.id} value={r.id}>{r.title}</option>);
  return <section aria-label="Task resources" className="disclosure">
    <h2 className="brief-label">Resources</h2>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {!attached.length && <p className="hint">No resources attached yet.</p>}
    <ul>{attached.map(r => <li key={r.id}>
      {/^(https?:\/\/)/.test(r.urlOrFileRef) ? <a href={r.urlOrFileRef} target="_blank" rel="noreferrer">{r.title}</a> : <span>{r.title} · {r.urlOrFileRef}</span>}
      <span className="hint"> · {r.type}</span>
      <div className="actions">
        <button disabled={busy} onClick={() => void act(async () => { await send(`/resources/${r.id}/feedback`, { method: "POST", ...mutation({ reaction: "liked" }) }); setNotice("Reaction recorded. No preference was created."); })}>Helpful</button>
        <button disabled={busy} onClick={() => void act(async () => { await send(`/resources/${r.id}/feedback`, { method: "POST", ...mutation({ reaction: "disliked" }) }); setNotice("Reaction recorded. No preference was created."); })}>Not helpful</button>
        <label>Replacement for {r.title}<select value={replacement[r.id] ?? ""} onChange={e => setReplacement({ ...replacement, [r.id]: e.target.value })}><option value="">Choose resource</option>{options}</select></label>
        <button disabled={busy || !replacement[r.id]} onClick={() => void act(async () => { setAttached(await send<Resource[]>(`/commitments/${taskId}/resources/${r.id}/replace`, { method: "POST", ...mutation({ resourceId: Number(replacement[r.id]) }) })); setNotice("Resource replaced. Task progress and history are unchanged."); })}>Replace</button>
        <button disabled={busy} onClick={() => void act(async () => { setAttached(await send<Resource[]>(`/commitments/${taskId}/resources/${r.id}`, { method: "DELETE", ...mutation() })); })}>Detach</button>
      </div>
    </li>)}</ul>
    <form onSubmit={e => { e.preventDefault(); void act(async () => { setAttached(await send<Resource[]>(`/commitments/${taskId}/resources`, { method: "POST", ...mutation({ resourceId: Number(selected) }) })); }); }}>
      <label>Existing resource<select value={selected} onChange={e => setSelected(e.target.value)} required><option value="">Choose resource</option>{options}</select></label><button disabled={busy || !selected}>Attach resource</button>
    </form>
    <details><summary>Add a resource</summary><form onSubmit={e => { e.preventDefault(); void act(async () => {
      const r = await send<Resource>("/resources", { method: "POST", ...mutation({ type, title, urlOrFileRef: reference }) });
      setLibrary([...library, r]); setSelected(String(r.id)); setTitle(""); setReference(""); setNotice("Resource saved. Select Attach resource to connect it to this task.");
    }); }}>
      <label>Resource title<input required maxLength={255} value={title} onChange={e => setTitle(e.target.value)} /></label>
      <label>Resource type<select value={type} onChange={e => setType(e.target.value)}>{["video", "pdf", "doc", "link", "book", "course"].map(t => <option key={t}>{t}</option>)}</select></label>
      <label>URL or reference<input required maxLength={2048} value={reference} onChange={e => setReference(e.target.value)} /></label><button disabled={busy}>Save resource</button>
    </form></details>
    <p className="hint">Reactions are saved as history. Pattern detection is disabled pending product policy; saved preferences are deferred.</p>
  </section>;
}
