import useReplayClient from "./useReplayClient";
import { useEffect, useState } from "react";
import { Client, messageOf } from "./api";
import { mutation, Resource } from "./ResourcePanel";
import ImportConflictReview from "./ImportConflictReview";

export type ImportNode = { id: number; parentId: number | null; type: string; title: string; text: string | null; included: boolean; completionCriterion: string | null; importance: string | null; flexibilityTier: string | null; resourceType: string | null; reference: string | null; resourceId: number | null; startTime: string | null; endTime: string | null; warning: string | null };
export type ImportView = { id: number; kind: string; filename: string; state: string; revision: number; proposal: { nodes: ImportNode[]; warning: string | null }; result: { roadmapId: number | null; goalId?: number | null; commitmentIds: number[]; resourceIds: number[]; fixedCommitmentIds: number[]; conflictingBlockIds: number[] } | null; scheduledCount: number };
export default function ImportWorkspace({ client, onWork, onGoal }: { client: Client; onWork: (id: number) => void; onGoal: (id: number) => void }) {
  const send = useReplayClient(client);
  const [kind, setKind] = useState("roadmap"), [imports, setImports] = useState<ImportView[]>([]), [view, setView] = useState<ImportView | null>(null);
  const [nodes, setNodes] = useState<ImportNode[]>([]), [dirty, setDirty] = useState(false), [file, setFile] = useState<File | null>(null), [transcript, setTranscript] = useState("");
  const [goals, setGoals] = useState<{ id: number; title: string }[]>([]), [resources, setResources] = useState<Resource[]>([]), [goalId, setGoalId] = useState("");
  const [importance, setImportance] = useState(""), [flexibility, setFlexibility] = useState("");
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  const path = kind === "roadmap" ? "/roadmaps/import" : "/fixed-commitments/import";
  useEffect(() => {
    const abort = new AbortController(); setView(null); setError(""); setFile(null); setTranscript("");
    Promise.all([client<ImportView[]>(path, { signal: abort.signal }), client<{ goals: { id: number; title: string }[] }>("/goals?limit=100", { signal: abort.signal }), client<Resource[]>("/resources", { signal: abort.signal })])
      .then(([a, b, c]) => { if (!abort.signal.aborted) { setImports(a); setGoals(b.goals); setResources(c); } }).catch(e => { if (!abort.signal.aborted) setError(messageOf(e)); });
    return () => abort.abort();
  }, [client, path]);
  function show(v: ImportView) { setView(v); setNodes(v.proposal.nodes); setDirty(false); setImports(previous => [v, ...previous.filter(p => p.id !== v.id)]); }
  async function act(action: () => Promise<void>) { setBusy(true); setError(""); try { await action(); } catch (e) { setError(messageOf(e)); } finally { setBusy(false); } }
  function change(id: number, patch: Partial<ImportNode>) { setNodes(nodes.map(n => n.id === id ? { ...n, ...patch } : n)); setDirty(true); }
  const excluded = new Set<number>(); nodes.forEach(n => { if (!n.included || (n.parentId !== null && excluded.has(n.parentId))) excluded.add(n.id); });
  const counts = nodes.reduce<Record<string, number>>((a, n) => ({ ...a, [n.type]: (a[n.type] ?? 0) + 1 }), {});
  return <section>
    <div className="page-heading"><h1>Import a plan</h1><p>Review and edit before anything becomes real work or reserved time.</p></div>
    <label>Import type<select disabled={busy} value={kind} onChange={e => setKind(e.target.value)}><option value="roadmap">Roadmap document</option><option value="fixed">Schedule screenshot</option></select></label>
    {error && <p role="alert">{error}</p>}
    <form onSubmit={e => { e.preventDefault(); if (!file) return; void act(async () => { const data = new FormData(); data.append("file", file); if (transcript) data.append("transcript", transcript); show(await send<ImportView>(path, { method: "POST", headers: { "Idempotency-Key": crypto.randomUUID() }, body: data })); }); }}>
      <label>{kind === "roadmap" ? "Markdown or text document (up to 256 KiB)" : "PNG or JPEG screenshot (up to 2 MiB)"}<input key={kind} type="file" accept={kind === "roadmap" ? ".md,.markdown,.txt" : ".png,.jpg,.jpeg"} onChange={e => setFile(e.target.files?.[0] ?? null)} /></label>
      {kind === "fixed" && <label>Optional schedule transcript<textarea value={transcript} onChange={e => setTranscript(e.target.value)} placeholder="Class | 2026-10-01T09:00:00+05:30 | 2026-10-01T10:00:00+05:30" /><span className="hint">Local OCR is attempted when no transcript is supplied. Ambiguous entries require editing.</span></label>}
      <button disabled={busy || !file}>Upload for review</button>
    </form>
    <label>Previous imports<select disabled={busy} value={view?.id ?? ""} onChange={e => { if (e.target.value) void act(async () => show(await client<ImportView>(`${path}/${e.target.value}`))); }}><option value="">Select import</option>{imports.map(i => <option key={i.id} value={i.id}>{i.filename} · {i.state}</option>)}</select></label>
    {view && <section aria-label="Import review">
      <h2>{view.filename} · {view.state === "review" ? "Draft / Review" : "Approved / Imported"}</h2>
      <p role="status">{view.scheduledCount > 0 ? `Scheduled · ${view.scheduledCount} work windows` : view.kind === "fixed" && view.state === "approved" ? "Approved fixed commitments now reserve calendar time." : "No work scheduled by this import."}</p>
      {view.proposal.warning && <p className="hint">{view.proposal.warning}</p>}
      <p>Parsed: {Object.entries(counts).map(([type, count]) => `${count} ${type}`).join(" · ") || "No nodes"}. Selected: {nodes.length - excluded.size} of {nodes.length}. Effort: not estimated. Dependencies: none inferred.</p>
      {view.state === "review" && <p className="hint">Uncheck a section to exclude all its descendants. Optional items start excluded; select prerequisites only if you still need to learn them. Tasks without a completion criterion remain Draft.</p>}
      {nodes.map(n => <fieldset key={n.id} disabled={busy || view.state !== "review"} className="panel">
        <legend>#{n.id} · {n.type}{n.parentId ? ` · under #${n.parentId}` : ""}</legend>
        <label><input type="checkbox" checked={n.included} onChange={e => change(n.id, { included: e.target.checked })} />{n.type === "prerequisite" ? "I need to learn this prerequisite" : n.type === "optional" ? "Opt in to this optional item" : "Include in import"}</label>
        {excluded.has(n.id) && <p className="hint">Excluded from approval{n.included ? " by a parent section" : ""}.</p>}
        <label>Title for #{n.id}<input value={n.title} maxLength={255} onChange={e => change(n.id, { title: e.target.value })} /></label>
        {kind === "roadmap" && <label>Type for #{n.id}<select value={n.type} onChange={e => change(n.id, { type: e.target.value, included: !["optional", "prerequisite"].includes(e.target.value), resourceType: e.target.value === "resource" ? "link" : n.resourceType })}>{["task", "resource", "optional", "prerequisite", "note", "milestone", "project"].map(t => <option key={t}>{t}</option>)}</select></label>}
        {["task", "project", "optional", "prerequisite"].includes(n.type) && <label>Completion criterion for #{n.id}<textarea value={n.completionCriterion ?? ""} onChange={e => change(n.id, { completionCriterion: e.target.value || null })} /></label>}
        {n.type === "resource" && <>
          <label>Existing resource for #{n.id}<select value={n.resourceId ?? ""} onChange={e => change(n.id, { resourceId: e.target.value ? Number(e.target.value) : null })}><option value="">Create from this node</option>{resources.map(r => <option key={r.id} value={r.id}>{r.title}</option>)}</select></label>
          {!n.resourceId && <><label>Resource type for #{n.id}<select value={n.resourceType ?? "link"} onChange={e => change(n.id, { resourceType: e.target.value })}>{["link", "video", "pdf", "doc", "book", "course"].map(t => <option key={t}>{t}</option>)}</select></label><label>Reference for #{n.id}<input value={n.reference ?? ""} onChange={e => change(n.id, { reference: e.target.value })} /></label></>}
        </>}
        {kind === "fixed" && <><label>Start with UTC offset for #{n.id}<input placeholder="2026-10-01T09:00:00+05:30" value={n.startTime ?? ""} onChange={e => change(n.id, { startTime: e.target.value })} /></label><label>End with UTC offset for #{n.id}<input placeholder="2026-10-01T10:00:00+05:30" value={n.endTime ?? ""} onChange={e => change(n.id, { endTime: e.target.value })} /></label></>}
        {n.warning && <p className="hint">{n.warning}</p>}<details><summary>Source text</summary><pre>{n.text}</pre></details>
      </fieldset>)}
      {view.state === "review" && <>
        {kind === "fixed" && <button disabled={busy} onClick={() => { setNodes([...nodes, { id: Math.max(0, ...nodes.map(n => n.id)) + 1, parentId: null, type: "fixed", title: "New entry", text: "Manually entered during review", included: true, completionCriterion: null, importance: null, flexibilityTier: null, resourceType: null, reference: null, resourceId: null, startTime: null, endTime: null, warning: null }]); setDirty(true); }}>Add schedule entry</button>}
        <button disabled={busy || !dirty} onClick={() => void act(async () => show(await send<ImportView>(`${path}/${view.id}`, { method: "PATCH", ...mutation({ revision: view.revision, nodes }) })))}>Save review edits</button>
        <form onSubmit={e => { e.preventDefault(); void act(async () => show(await send<ImportView>(`${path}/${view.id}/approve`, { method: "POST", ...mutation({ revision: view.revision, goalId: kind === "roadmap" ? Number(goalId) : null, importance: importance || null, flexibilityTier: flexibility || null }) }))); }}>
          {kind === "roadmap" && <>
            <label>Goal for this roadmap<select required value={goalId} onChange={e => setGoalId(e.target.value)}><option value="">Choose a Goal without a roadmap</option>{goals.map(g => <option key={g.id} value={g.id}>{g.title}</option>)}</select></label><p className="hint">Create a Goal in Goals first if needed. Existing roadmaps are preserved.</p>
            <label>Task importance<select required value={importance} onChange={e => setImportance(e.target.value)}><option value="">Choose importance</option>{["low", "medium", "high", "critical"].map(t => <option key={t}>{t}</option>)}</select></label>
            <label>Task flexibility<select required value={flexibility} onChange={e => setFlexibility(e.target.value)}><option value="">Choose flexibility</option>{["flexible", "protected", "optional"].map(t => <option key={t}>{t}</option>)}</select></label>
          </>}
          <p className="hint">{dirty ? "Save your edits before approval." : "Approval creates the selected domain entities. Flexible work can then enter the normal scheduler."}</p>
          <button className="primary" disabled={busy || dirty || excluded.size === nodes.length}>Approve reviewed import</button>
        </form>
      </>}
      {view.result && <div>
        {view.result.commitmentIds.map(id => <button key={id} onClick={() => onWork(id)}>Open imported task #{id} · schedule next</button>)}
        {view.result.roadmapId && view.result.goalId != null && <button onClick={() => onGoal(view.result!.goalId!)}>Open Goal</button>}
        {view.result.conflictingBlockIds.length > 0 && <p role="alert">Fixed commitments overlap work windows {view.result.conflictingBlockIds.join(", ")}. Review these windows in Schedule / Recovery; active work has not been moved.</p>}
        {view.kind === "fixed" && view.result.conflictingBlockIds.length > 0 && <ImportConflictReview client={client} importId={view.id} fixedIds={view.result.fixedCommitmentIds} />}
      </div>}
    </section>}
  </section>;
}
