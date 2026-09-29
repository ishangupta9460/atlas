import useReplayClient from "./useReplayClient";
import { useEffect, useState } from "react";
import { Client, messageOf } from "./api";
import { Workspace } from "./execution";
import { mutation } from "./ResourcePanel";

export default function ImportConflictReview({ client, importId, fixedIds }: { client: Client; importId: number; fixedIds: number[] }) {
  const send = useReplayClient(client);
  const [data, setData] = useState<Workspace | null>(null), [fixed, setFixed] = useState<{ id: number; title: string; startTime: string; endTime: string }[]>([]);
  const [minutes, setMinutes] = useState<Record<number, string>>({}), [error, setError] = useState(""), [notice, setNotice] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { let live = true; Promise.all([client<Workspace>("/execution"), Promise.all(fixedIds.map(id => client<{ id: number; title: string; startTime: string; endTime: string }>(`/fixed-commitments/${id}`)))]).then(([a, b]) => { if (live) { setData(a); setFixed(b); } }).catch(e => { if (live) setError(messageOf(e)); }); return () => { live = false; }; }, [client, importId, fixedIds]);
  return <section aria-label="Imported schedule conflicts"><h3>Review conflicting work</h3>
    <p>Provide total-work estimates to use the existing Recovery flow. Consequential changes await a separate recovery approval in Schedule. Active work and recurring instances need your attention.</p>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {fixed.map(f => {
      const conflicts = data?.blocks.filter(b => b.state === "scheduled" && !b.sessionState && b.commitmentId && data.tasks.some(t => t.id === b.commitmentId && t.workState === "ready") && Date.parse(b.startTime) < Date.parse(f.endTime) && Date.parse(b.endTime) > Date.parse(f.startTime)) ?? [];
      return <form key={f.id} onSubmit={e => { e.preventDefault(); setBusy(true); setError(""); void send<{ state: string }>(`/fixed-commitments/import/${importId}/recovery`, { method: "POST", ...mutation({ fixedCommitmentId: f.id, items: conflicts.map(b => ({ blockId: b.id, totalWorkMinutes: Number(minutes[b.id]) })) }) }).then(async result => { setNotice(result.state === "applied" ? "Recovery applied using the normal scheduler." : "Recovery proposal is awaiting your approval in Schedule."); setData(await client<Workspace>("/execution")); }).catch(e => setError(messageOf(e))).finally(() => setBusy(false)); }}>
        <h4>{f.title}</h4>{conflicts.map(b => <label key={b.id}>Total work minutes for {data?.tasks.find(t => t.id === b.commitmentId)?.title}<input required type="number" min="1" max="10080" value={minutes[b.id] ?? ""} onChange={e => setMinutes({ ...minutes, [b.id]: e.target.value })} /></label>)}
        {conflicts.length > 0 && <button disabled={busy}>Recover conflicting work</button>}
      </form>;
    })}
  </section>;
}
