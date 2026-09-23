import { FormEvent, useEffect, useState } from "react";
import { Client, jsonBody, messageOf } from "./api";

type Task = { id: number; title: string | null; workState: string; completionCriterion: string | null; goalId: number | null };
type Edge = { blockingCommitmentId: number; blockedCommitmentId: number };
type Page = { commitments: Task[]; nextCursor: number | null };

export default function DependencyPanel({ task, client }: {
  task: { id: number; title: string | null }; client: Client;
}) {
  const [blockers,      setBlockers]      = useState<Task[]>([]);
  const [loading,       setLoading]       = useState(true);
  const [readError,     setReadError]     = useState("");
  const [error,         setError]         = useState("");
  const [notice,        setNotice]        = useState("");
  const [retry,         setRetry]         = useState(0);
  const [pending,       setPending]       = useState(false);
  const [searchInput,   setSearchInput]   = useState("");
  const [query,         setQuery]         = useState("");
  const [searchVersion, setSearchVersion] = useState(0);
  const [results,       setResults]       = useState<Task[]>([]);
  const [searching,     setSearching]     = useState(true);
  const [searchError,   setSearchError]   = useState("");
  const [pageCursor,    setPageCursor]    = useState<number | null>(null);
  const [nextCursor,    setNextCursor]    = useState<number | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setReadError("");
    client<Edge[]>(`/commitments/${task.id}/dependencies`, { signal: controller.signal })
      .then(edges => Promise.all(edges.map(edge =>
        client<Task>(`/commitments/${edge.blockingCommitmentId}`, { signal: controller.signal })
      )))
      .then(items => { if (!controller.signal.aborted) setBlockers(items); })
      .catch(failure => { if (!controller.signal.aborted) setReadError(messageOf(failure)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [client, task.id, retry]);

  useEffect(() => {
    const controller = new AbortController();
    setSearching(true); setSearchError("");
    const params = new URLSearchParams({ q: query, excludeId: String(task.id) });
    if (pageCursor !== null) params.set("cursor", String(pageCursor));
    client<Page>(`/commitments?${params}`, { signal: controller.signal }).then(page => {
      if (controller.signal.aborted) return;
      setResults(previous =>
        pageCursor === null
          ? page.commitments
          : [...previous, ...page.commitments.filter(item => !previous.some(existing => existing.id === item.id))]
      );
      setNextCursor(page.nextCursor);
    }).catch(failure => { if (!controller.signal.aborted) setSearchError(messageOf(failure)); })
      .finally(() => { if (!controller.signal.aborted) setSearching(false); });
    return () => controller.abort();
  }, [client, task.id, query, pageCursor, searchVersion]);

  async function add(blocker: Task) {
    if (pending || loading || readError) return;
    setPending(true); setError(""); setNotice("");
    try {
      await client<Edge>(`/commitments/${task.id}/dependencies`, {
        method: "POST", ...jsonBody({ blockingCommitmentId: blocker.id }),
      });
      setBlockers(items =>
        items.some(item => item.id === blocker.id)
          ? items
          : [...items, blocker].sort((a, b) => a.id - b.id)
      );
      setNotice("Prerequisite added.");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  async function remove(blocker: Task) {
    if (pending) return;
    setPending(true); setError(""); setNotice("");
    try {
      await client<void>(`/commitments/${task.id}/dependencies/${blocker.id}`, { method: "DELETE" });
      setBlockers(items => items.filter(item => item.id !== blocker.id));
      setNotice("Prerequisite removed. Both tasks are still saved.");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  function search(event: FormEvent) {
    event.preventDefault();
    setQuery(searchInput.trim());
    setPageCursor(null);
    setSearchVersion(value => value + 1);
    setResults([]);
    setNextCursor(null);
  }

  const available = results.filter(item => item.id !== task.id && !blockers.some(blocker => blocker.id === item.id));

  return (
    <section className="dependency-panel" aria-label={`Prerequisites for ${task.title || "untitled draft"}`}>
      <h4>What needs to happen first?</h4>
      <p className="hint" style={{ marginTop: "var(--space-1)", marginBottom: "var(--space-4)" }}>
        These tasks are prerequisites for "{task.title || "Untitled draft"}". They can belong to any of your goals.
      </p>

      {/* Current blockers */}
      {loading ? (
        <p role="status" className="hint">Loading prerequisites…</p>
      ) : readError ? (
        <p role="alert" className="error">
          {readError}{" "}
          <button className="text-button" disabled={pending} onClick={() => setRetry(value => value + 1)}>Retry</button>
        </p>
      ) : (
        <>
          {!blockers.length && <p className="hint">No prerequisites yet.</p>}
          <ul className="dependency-list">
            {blockers.map(blocker => (
              <li key={blocker.id}>
                <div>
                  <strong style={{ fontSize: "0.875rem" }}>{blocker.title || "Untitled draft"}</strong>
                  <span className="badge" style={{ marginLeft: "var(--space-2)" }}>
                    {blocker.workState.replace(/_/g, " ")}
                  </span>
                  {blocker.workState === "cancelled" && (
                    <p className="hint" style={{ marginTop: "var(--space-1)" }}>
                      This prerequisite was cancelled. Review whether the relationship is still useful.
                    </p>
                  )}
                </div>
                <button
                  disabled={pending}
                  onClick={() => void remove(blocker)}
                  aria-label={`Remove prerequisite ${blocker.title || "untitled draft"}`}
                >
                  Remove link
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      {error  && <p role="alert"  className="error">{error}</p>}
      {notice && <p role="status" className="notice">{notice}</p>}

      {/* Search for prerequisite */}
      <form onSubmit={search} style={{ marginTop: "var(--space-5)" }}>
        <label htmlFor={`dependency-search-${task.id}`}>Find a prerequisite</label>
        <div className="input-row">
          <input
            id={`dependency-search-${task.id}`}
            placeholder="Search all your tasks"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            maxLength={255}
            disabled={pending}
          />
          <button disabled={pending}>Search tasks</button>
        </div>
      </form>

      {searchError && (
        <p role="alert" className="error">
          {searchError}{" "}
          <button className="text-button" disabled={pending} onClick={() => setSearchVersion(value => value + 1)}>Retry</button>
        </p>
      )}
      {searching && <p role="status" className="hint" style={{ marginTop: "var(--space-3)" }}>Finding tasks…</p>}
      {!searching && !searchError && !available.length && (
        <p className="hint" style={{ marginTop: "var(--space-3)" }}>
          No other matching tasks in these results.
          {nextCursor !== null ? " More tasks are available below." : " Create a task in a goal plan, or try a different search."}
        </p>
      )}

      <ul className="dependency-list">
        {available.map(candidate => (
          <li key={candidate.id}>
            <div>
              <strong style={{ fontSize: "0.875rem" }}>{candidate.title || "Untitled draft"}</strong>
              <span className="badge" style={{ marginLeft: "var(--space-2)" }}>
                {candidate.workState.replace(/_/g, " ")}
              </span>
              <p className="hint" style={{ marginTop: "var(--space-1)" }}>
                {candidate.completionCriterion || "Completion criterion not yet defined"}
              </p>
            </div>
            <button
              disabled={pending || loading || !!readError || searching || !!searchError}
              onClick={() => void add(candidate)}
              aria-label={`Add prerequisite ${candidate.title || "untitled draft"}`}
            >
              Add prerequisite
            </button>
          </li>
        ))}
      </ul>

      {nextCursor !== null && (
        <button
          disabled={pending || searching || !!searchError}
          onClick={() => setPageCursor(nextCursor)}
          style={{ marginTop: "var(--space-4)" }}
        >
          More matching tasks
        </button>
      )}
    </section>
  );
}
