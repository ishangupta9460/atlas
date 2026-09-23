import { FormEvent, useEffect, useState } from "react";
import { ApiError, Client, jsonBody, messageOf } from "./api";
import { Category, CategoryEditor, CategorySwatch } from "./CategoriesScreen";
import DependencyPanel from "./DependencyPanel";

type Milestone  = { id: number; title: string; order: number };
type Roadmap    = { id: number; milestones: Milestone[] };
type Commitment = {
  id: number; title: string | null; completionCriterion: string | null;
  milestoneId: number | null; importance: string; flexibilityTier: string; workState: string;
  categoryId: number | null;
};
type Page = { commitments: Commitment[]; nextCursor: number | null };

const label = (value: string) => value.replace(/_/g, " ");

function commitmentBadgeClass(state: string): string {
  if (state === "draft")      return "badge";
  if (state === "ready")      return "badge active";
  if (state === "in_progress" || state === "scheduled") return "badge now";
  if (state === "completed")  return "badge completed";
  return "badge";
}

export default function GoalPlanScreen({ goal, client, onBack }: {
  goal: { id: number; title: string }; client: Client; onBack: () => void;
}) {
  const [roadmap,          setRoadmap]          = useState<Roadmap | null>(null);
  const [tasks,            setTasks]            = useState<Commitment[]>([]);
  const [categories,       setCategories]       = useState<Category[]>([]);
  const [dependenciesFor,  setDependenciesFor]  = useState<number | null>(null);
  const [cursor,           setCursor]           = useState<number | null>(null);
  const [loading,          setLoading]          = useState(true);
  const [loaded,           setLoaded]           = useState(false);
  const [pending,          setPending]          = useState(false);
  const [error,            setError]            = useState("");
  const [notice,           setNotice]           = useState("");
  const [retry,            setRetry]            = useState(0);
  const [milestoneTitle,   setMilestoneTitle]   = useState("");
  const [editor,           setEditor]           = useState<Commitment | "new" | null>(null);
  const [editingMilestone, setEditingMilestone] = useState<number | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setLoaded(false); setError("");
    Promise.all([
      client<{ roadmap: Roadmap | null }>(`/goals/${goal.id}/roadmap`,     { signal: controller.signal }),
      client<Page>(`/goals/${goal.id}/commitments`,                          { signal: controller.signal }),
      client<Category[]>("/categories",                                       { signal: controller.signal }),
    ]).then(([plan, page, categoryList]) => {
      if (controller.signal.aborted) return;
      setRoadmap(plan.roadmap);
      setTasks(page.commitments);
      setCursor(page.nextCursor);
      setLoaded(true);
      setCategories(categoryList);
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
        setRoadmap(plan);
      }
      const milestone = await client<Milestone>(`/roadmaps/${plan.id}/milestones`, {
        method: "POST",
        ...jsonBody({ title: milestoneTitle.trim(), order: Math.max(0, ...plan.milestones.map(item => item.order)) + 1 }),
      });
      setRoadmap({ ...plan, milestones: [...plan.milestones, milestone] });
      setMilestoneTitle("");
      setNotice("Milestone saved.");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  async function loadMore() {
    if (pending || cursor === null) return;
    setPending(true); setError("");
    try {
      const page = await client<Page>(`/goals/${goal.id}/commitments?cursor=${cursor}`);
      setTasks(previous => [
        ...previous,
        ...page.commitments.filter(item => !previous.some(existing => existing.id === item.id)),
      ]);
      setCursor(page.nextCursor);
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  return (
    <>
      {/* Breadcrumb back navigation */}
      <nav className="breadcrumb" aria-label="Plan navigation">
        <button onClick={onBack}>← All goals</button>
        <span className="sep">/</span>
        <span style={{ color: "var(--text-primary)", fontWeight: 500 }}>Plan</span>
      </nav>

      {/* Page heading */}
      <div className="page-heading">
        <p className="eyebrow">Make the next step clear</p>
        <h1>{goal.title}</h1>
        <p>Shape a few milestones, or go straight to a task.</p>
      </div>

      {loading ? (
        <p role="status" className="hint">Loading your plan…</p>
      ) : (
        <>
          {error && (
            <div className="error" role="alert">
              {error}{" "}
              <button
                className="text-button"
                disabled={pending || editor !== null || editingMilestone !== null}
                onClick={() => setRetry(value => value + 1)}
              >
                Reload plan
              </button>
            </div>
          )}
          {notice && <p role="status" className="notice">{notice}</p>}

          {loaded && (
            <>
              {/* ── Milestones ───────────────────────────────────── */}
              <section aria-labelledby="milestone-heading" className="plan-section">
                <div className="section-heading">
                  <h2 id="milestone-heading">Milestones <span className="hint" style={{ fontWeight: 400, textTransform: "none", letterSpacing: 0 }}>Optional</span></h2>
                </div>
                <p className="hint" style={{ marginBottom: "var(--space-4)" }}>Useful steps along the way. Each can hold a few tasks.</p>

                <ol className="milestone-list">
                  {roadmap?.milestones.map(milestone => (
                    <li key={milestone.id}>
                      {editingMilestone === milestone.id ? (
                        <MilestoneEditor
                          milestone={milestone}
                          client={client}
                          onClose={() => setEditingMilestone(null)}
                          onSaved={updated => {
                            setRoadmap(previous => previous && ({
                              ...previous,
                              milestones: previous.milestones.map(item => item.id === updated.id ? updated : item),
                            }));
                            setEditingMilestone(null);
                            setNotice("Milestone updated.");
                          }}
                        />
                      ) : (
                        <div className="milestone-row">
                          <span>{milestone.title}</span>
                          <button
                            className="text-button"
                            disabled={pending || editor !== null || editingMilestone !== null}
                            aria-label={`Rename ${milestone.title}`}
                            onClick={() => setEditingMilestone(milestone.id)}
                          >
                            Rename
                          </button>
                        </div>
                      )}
                    </li>
                  ))}
                </ol>

                <form onSubmit={addMilestone} style={{ marginTop: "var(--space-5)" }}>
                  <label htmlFor="milestone-title">What is a useful milestone?</label>
                  <div className="input-row">
                    <input
                      id="milestone-title"
                      value={milestoneTitle}
                      onChange={e => setMilestoneTitle(e.target.value)}
                      placeholder="For example, play a first song"
                      maxLength={255}
                      required
                      disabled={pending || editor !== null || editingMilestone !== null}
                    />
                    <button
                      disabled={pending || editor !== null || editingMilestone !== null || !milestoneTitle.trim()}
                    >
                      Add milestone
                    </button>
                  </div>
                </form>
              </section>

              {/* ── Actionable tasks ─────────────────────────────── */}
              <section aria-labelledby="plan-tasks-heading" className="plan-section">
                <div className="section-heading">
                  <h2 id="plan-tasks-heading">Actionable tasks</h2>
                  <button
                    className="primary"
                    disabled={pending || editor !== null || editingMilestone !== null}
                    onClick={() => { setEditor("new"); setNotice(""); }}
                  >
                    Add a task
                  </button>
                </div>
                <p className="hint" style={{ marginBottom: "var(--space-4)" }}>
                  Define what done looks like to make a task ready. Scheduling comes next.
                </p>

                {/* Commitment editor — ONE bordered panel */}
                {editor !== null && (
                  <CommitmentEditor
                    key={editor === "new" ? "new" : editor.id}
                    task={editor === "new" ? null : editor}
                    goalId={goal.id}
                    milestones={roadmap?.milestones ?? []}
                    categories={categories}
                    onCategoryCreated={category => setCategories(items => [...items, category])}
                    client={client}
                    onClose={() => setEditor(null)}
                    onSaved={saved => {
                      setTasks(previous =>
                        previous.some(item => item.id === saved.id)
                          ? previous.map(item => item.id === saved.id ? saved : item)
                          : [saved, ...previous]
                      );
                      setEditor(null);
                      setNotice(saved.workState === "draft" ? "Draft saved. You can define done when you're ready." : "Task saved.");
                    }}
                  />
                )}

                {!tasks.length && !error && editor === null && (
                  <p className="plan-empty">What is one small action that would move this goal forward?</p>
                )}

                {/* Divider-separated task rows */}
                <ul className="plan-task-list">
                  {tasks.map(task => (
                    <li key={task.id} className="plan-task">
                      <div className="badges">
                        <span className={commitmentBadgeClass(task.workState)}>{label(task.workState)}</span>
                        <span className="hint">{label(task.importance)} importance · {label(task.flexibilityTier)}</span>
                      </div>
                      <h3>{task.title || "Untitled draft"}</h3>
                      {categories
                        .filter(category => category.id === task.categoryId)
                        .map(category => (
                          <p className="hint" key={category.id}>
                            <CategorySwatch color={category.color} />
                            {category.name}
                          </p>
                        ))
                      }
                      <p>{task.completionCriterion ? `Done when: ${task.completionCriterion}` : "Still defining what done looks like."}</p>
                      <p className="hint">
                        {roadmap?.milestones.find(item => item.id === task.milestoneId)?.title ?? "Directly supports this goal"}
                      </p>
                      <div className="actions">
                        <button
                          className="text-button"
                          disabled={pending || editor !== null || editingMilestone !== null}
                          aria-label={`Edit ${task.title || "untitled draft"}`}
                          onClick={() => { setEditor(task); setDependenciesFor(null); setNotice(""); }}
                        >
                          Edit task
                        </button>
                        <button
                          disabled={pending || editor !== null || editingMilestone !== null}
                          aria-expanded={dependenciesFor === task.id}
                          aria-label={`Prerequisites for ${task.title || "untitled draft"}`}
                          onClick={() => setDependenciesFor(current => current === task.id ? null : task.id)}
                        >
                          Prerequisites
                        </button>
                      </div>
                      {dependenciesFor === task.id && (
                        <DependencyPanel key={task.id} task={task} client={client} />
                      )}
                    </li>
                  ))}
                </ul>

                {cursor !== null && (
                  <button
                    disabled={pending || editor !== null || editingMilestone !== null}
                    onClick={() => void loadMore()}
                    style={{ marginTop: "var(--space-5)" }}
                  >
                    Show more tasks
                  </button>
                )}
              </section>
            </>
          )}
        </>
      )}
    </>
  );
}

/* ── MilestoneEditor ──────────────────────────────────────────────── */
function MilestoneEditor({ milestone, client, onSaved, onClose }: {
  milestone: Milestone; client: Client; onSaved: (value: Milestone) => void; onClose: () => void;
}) {
  const [title, setTitle] = useState(milestone.title);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending || !title.trim()) return;
    setPending(true); setError("");
    try {
      onSaved(await client<Milestone>(`/milestones/${milestone.id}`, {
        method: "PATCH", ...jsonBody({ title: title.trim() }),
      }));
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  return (
    <form onSubmit={save} style={{ padding: "var(--space-4) 0" }}>
      <label htmlFor="rename-milestone">Milestone name</label>
      <input
        id="rename-milestone"
        value={title}
        onChange={e => setTitle(e.target.value)}
        maxLength={255}
        required
        disabled={pending}
        autoFocus
      />
      {error && <p role="alert" className="error">{error}</p>}
      <div className="actions">
        <button disabled={pending || !title.trim()}>Save milestone</button>
        <button type="button" disabled={pending} onClick={onClose}>Cancel</button>
      </div>
    </form>
  );
}

/* ── CommitmentEditor ─────────────────────────────────────────────── */
function CommitmentEditor({ task, goalId, milestones, categories, onCategoryCreated, client, onSaved, onClose }: {
  task: Commitment | null; goalId: number; milestones: Milestone[]; categories: Category[];
  onCategoryCreated: (category: Category) => void; client: Client;
  onSaved: (value: Commitment) => void; onClose: () => void;
}) {
  const [title,           setTitle]           = useState(task?.title ?? "");
  const [criterion,       setCriterion]       = useState(task?.completionCriterion ?? "");
  const [importance,      setImportance]      = useState(task?.importance ?? "");
  const [flexibility,     setFlexibility]     = useState(task?.flexibilityTier ?? "");
  const [milestone,       setMilestone]       = useState(task?.milestoneId?.toString() ?? "");
  const [categoryId,      setCategoryId]      = useState(task?.categoryId?.toString() ?? "");
  const [categoryChanged, setCategoryChanged] = useState(false);
  const [creatingCategory, setCreatingCategory] = useState(false);
  const [step,            setStep]            = useState(0);
  const [pending,         setPending]         = useState(false);
  const [error,           setError]           = useState("");

  const criterionRequired = task !== null && task.workState !== "draft";
  const category = categories.find(item => String(item.id) === categoryId);
  const hasImportance  = !!importance  || (!task && !!category?.defaultImportance);
  const hasFlexibility = !!flexibility || (!task && !!category?.defaultFlexibilityTier);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending || !title.trim() || (criterionRequired && !criterion.trim())) return;
    if (step === 0) { setStep(1); return; }
    if (!hasImportance || !hasFlexibility) return;
    setPending(true); setError("");
    try {
      onSaved(await client<Commitment>(task ? `/commitments/${task.id}` : "/commitments", {
        method: task ? "PATCH" : "POST",
        ...jsonBody({
          title: title.trim(),
          completionCriterion: criterion.trim() || null,
          goalId,
          milestoneId: milestone ? Number(milestone) : null,
          ...(importance   ? { importance } : {}),
          ...(flexibility  ? { flexibilityTier: flexibility } : {}),
          ...(categoryChanged ? { categoryId: categoryId ? Number(categoryId) : null } : {}),
        }),
      }));
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  if (creatingCategory) {
    return (
      <CategoryEditor
        category={null}
        client={client}
        onClose={() => setCreatingCategory(false)}
        onSaved={saved => {
          onCategoryCreated(saved);
          setCategoryId(String(saved.id));
          setCategoryChanged(true);
          setCreatingCategory(false);
        }}
      />
    );
  }

  return (
    <form className="card commitment-editor" onSubmit={save}>
      <h3 style={{ marginBottom: "var(--space-4)" }}>
        {task ? "Shape this task" : "One concrete next step"}
      </h3>

      {step === 0 ? (
        <>
          <label htmlFor="plan-task-title">What will you do?</label>
          <input
            id="plan-task-title"
            value={title}
            onChange={e => setTitle(e.target.value)}
            maxLength={255}
            required
            disabled={pending}
            autoFocus
          />
          <label htmlFor="criterion">
            What will done look like?{" "}
            <span className="hint">
              {criterionRequired ? "Required for a ready task" : "Leave empty to save a draft"}
            </span>
          </label>
          <textarea
            id="criterion"
            value={criterion}
            onChange={e => setCriterion(e.target.value)}
            required={criterionRequired}
            disabled={pending}
            rows={3}
          />
        </>
      ) : (
        <>
          <details open={!!categoryId}>
            <summary>Category and defaults <span className="hint">Optional</span></summary>
            <label htmlFor="task-category">Category</label>
            <select
              id="task-category"
              value={categoryId}
              onChange={e => { setCategoryId(e.target.value); setCategoryChanged(true); }}
              disabled={pending}
            >
              <option value="">No category</option>
              {categories.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
            <button type="button" className="text-button" disabled={pending} onClick={() => setCreatingCategory(true)}>
              Create a category
            </button>
            <p className="hint">
              {task
                ? "Changing category keeps this task's saved importance and flexibility."
                : "Use category defaults below, or choose an override for this task."}
            </p>
          </details>

          <label htmlFor="importance">How important is this task?</label>
          <select
            id="importance"
            required={!!task || !category?.defaultImportance}
            value={importance}
            onChange={e => setImportance(e.target.value)}
            disabled={pending}
          >
            <option value="">
              {!task && category?.defaultImportance
                ? `Use category default (${category.defaultImportance})`
                : "Choose importance"}
            </option>
            {["low", "medium", "high", "critical"].map(value => (
              <option key={value} value={value}>{label(value)}</option>
            ))}
          </select>

          <label htmlFor="flexibility">How flexible is its placement?</label>
          <select
            id="flexibility"
            required={!!task || !category?.defaultFlexibilityTier}
            value={flexibility}
            onChange={e => setFlexibility(e.target.value)}
            disabled={pending}
          >
            <option value="">
              {!task && category?.defaultFlexibilityTier
                ? `Use category default (${category.defaultFlexibilityTier})`
                : "Choose flexibility"}
            </option>
            {["fixed", "protected", "flexible", "optional"].map(value => (
              <option key={value} value={value}>{label(value)}</option>
            ))}
          </select>
          <p className="hint">Fixed stays put; protected avoids disruption; flexible can move; optional yields first.</p>

          {milestones.length > 0 && (
            <details>
              <summary>Place under a milestone <span className="hint">Optional</span></summary>
              <label htmlFor="task-milestone">Milestone</label>
              <select
                id="task-milestone"
                value={milestone}
                onChange={e => setMilestone(e.target.value)}
                disabled={pending}
              >
                <option value="">Directly under this goal</option>
                {milestones.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}
              </select>
            </details>
          )}
        </>
      )}

      {error && <p role="alert" className="error">{error}</p>}

      <div className="actions">
        {step === 1 && (
          <button type="button" disabled={pending} onClick={() => setStep(0)}>Back</button>
        )}
        <button
          className="primary"
          disabled={pending || !title.trim() || (step === 1 && (!hasImportance || !hasFlexibility))}
        >
          {pending ? "Saving…" : step === 0 ? "Continue" : criterion.trim() ? "Save task" : "Save draft"}
        </button>
        <button type="button" disabled={pending} onClick={onClose}>Cancel</button>
      </div>
    </form>
  );
}
