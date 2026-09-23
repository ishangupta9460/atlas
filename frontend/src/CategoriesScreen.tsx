import { FormEvent, useEffect, useState } from "react";
import { Client, jsonBody, messageOf } from "./api";

export type Category = { id: number; name: string; color: string; defaultImportance: string | null; defaultFlexibilityTier: string };
export const importanceOptions  = ["low", "medium", "high", "critical"];
export const flexibilityOptions = ["fixed", "protected", "flexible", "optional"];

export default function CategoriesScreen({ client }: { client: Client }) {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState("");
  const [notice,     setNotice]     = useState("");
  const [retry,      setRetry]      = useState(0);
  const [editor,     setEditor]     = useState<Category | "new" | null>(null);
  const [removing,   setRemoving]   = useState<number | null>(null);
  const [pending,    setPending]    = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError("");
    client<Category[]>("/categories", { signal: controller.signal })
      .then(items => { if (!controller.signal.aborted) setCategories(items); })
      .catch(failure => { if (!controller.signal.aborted) setError(messageOf(failure)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [client, retry]);

  async function remove(category: Category) {
    if (pending) return;
    setPending(true); setError(""); setNotice("");
    try {
      await client<void>(`/categories/${category.id}`, { method: "DELETE" });
      setCategories(items => items.filter(item => item.id !== category.id));
      setRemoving(null);
      setNotice("Category removed.");
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  return (
    <>
      {/* Page heading */}
      <div className="page-heading">
        <p className="eyebrow">Less repeating yourself</p>
        <h1>Your categories</h1>
        <p>Choose defaults once. Adjust individual tasks whenever you need to.</p>
      </div>

      {error && (
        <div role="alert" className="error">
          {error}{" "}
          <button
            className="text-button"
            disabled={pending || editor !== null || loading}
            onClick={() => { setRemoving(null); setRetry(value => value + 1); }}
          >
            Reload
          </button>
        </div>
      )}
      {notice && <p role="status" className="notice">{notice}</p>}

      {loading ? (
        <p role="status" className="hint">Loading categories…</p>
      ) : (
        <>
          <div className="section-heading">
            <h2>Your categories</h2>
            <button
              className="primary"
              disabled={editor !== null || pending || removing !== null}
              onClick={() => { setEditor("new"); setNotice(""); }}
            >
              New category
            </button>
          </div>

          {/* Category editor — ONE bordered panel */}
          {editor && (
            <CategoryEditor
              key={editor === "new" ? "new" : editor.id}
              category={editor === "new" ? null : editor}
              client={client}
              onClose={() => setEditor(null)}
              onSaved={saved => {
                setCategories(items =>
                  items.some(item => item.id === saved.id)
                    ? items.map(item => item.id === saved.id ? saved : item)
                    : [...items, saved]
                );
                setEditor(null);
                setNotice("Category saved. Existing tasks keep their importance and flexibility.");
              }}
            />
          )}

          {!categories.length && !error && !editor && (
            <p className="plan-empty">Create a category when you find yourself choosing the same defaults.</p>
          )}

          {/* Divider-separated category rows */}
          <ul className="plan-task-list">
            {categories.map(category => (
              <li key={category.id} className="plan-task">
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "var(--space-4)" }}>
                  <div style={{ minWidth: 0 }}>
                    <h3>
                      <CategorySwatch color={category.color} />
                      {category.name}
                    </h3>
                    <p className="hint" style={{ marginTop: "var(--space-1)" }}>
                      {category.defaultImportance ? `${category.defaultImportance} importance` : "Importance chosen per task"}
                      {" · "}
                      {category.defaultFlexibilityTier}
                    </p>
                  </div>

                  {removing !== category.id && (
                    <div className="actions" style={{ margin: 0, flexShrink: 0 }}>
                      <button
                        disabled={editor !== null || pending || removing !== null}
                        onClick={() => setEditor(category)}
                        aria-label={`Edit category ${category.name}`}
                      >
                        Edit
                      </button>
                      <button
                        className="destructive"
                        disabled={editor !== null || pending || removing !== null}
                        onClick={() => { setRemoving(category.id); setError(""); }}
                        aria-label={`Remove category ${category.name}`}
                      >
                        Remove
                      </button>
                    </div>
                  )}
                </div>

                {removing === category.id && (
                  <div style={{ marginTop: "var(--space-3)" }}>
                    <p style={{ marginBottom: "var(--space-3)", fontSize: "0.875rem", color: "var(--text-muted)" }}>
                      Remove this category? Categories used by tasks or recurring intentions must be unlinked first.
                    </p>
                    <div className="actions" style={{ marginTop: 0 }}>
                      <button
                        className="destructive"
                        disabled={pending}
                        onClick={() => void remove(category)}
                      >
                        Confirm remove
                      </button>
                      <button disabled={pending} onClick={() => setRemoving(null)}>Keep category</button>
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}

/* ── CategorySwatch ───────────────────────────────────────────────── */
export function CategorySwatch({ color }: { color: string }) {
  // Render only validated CSS color tokens; legacy free-text colors use a neutral swatch.
  const safe = /^(#[0-9a-f]{3,8}|[a-z]{1,30})$/i.test(color) ? color : "#6e6e62";
  return <span className="category-swatch" style={{ backgroundColor: safe }} aria-hidden="true" />;
}

/* ── CategoryEditor ───────────────────────────────────────────────── */
export function CategoryEditor({ category, client, onSaved, onClose }: {
  category: Category | null; client: Client; onSaved: (category: Category) => void; onClose: () => void;
}) {
  const [name,        setName]        = useState(category?.name ?? "");
  const [importance,  setImportance]  = useState(category?.defaultImportance ?? "");
  const [flexibility, setFlexibility] = useState(category?.defaultFlexibilityTier ?? "");
  const [color,       setColor]       = useState(category?.color ?? "#6e6e62");
  const [step,        setStep]        = useState(0);
  const [pending,     setPending]     = useState(false);
  const [error,       setError]       = useState("");

  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending || !name.trim()) return;
    if (step === 0) { setStep(1); return; }
    if (!flexibility) return;
    setPending(true); setError("");
    try {
      onSaved(await client<Category>(
        category ? `/categories/${category.id}` : "/categories",
        {
          method: category ? "PATCH" : "POST",
          ...jsonBody({ name: name.trim(), color, defaultImportance: importance || null, defaultFlexibilityTier: flexibility }),
        }
      ));
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  return (
    <form className="card commitment-editor" onSubmit={save}>
      <h3 style={{ marginBottom: "var(--space-4)" }}>
        {category ? "Edit category" : "A category that fits your life"}
      </h3>

      {step === 0 ? (
        <>
          <label htmlFor="category-name">What would you call this category?</label>
          <input
            id="category-name"
            value={name}
            onChange={e => setName(e.target.value)}
            required
            maxLength={255}
            disabled={pending}
            autoFocus
          />
          <label htmlFor="category-color">Color</label>
          <input
            id="category-color"
            type="color"
            value={/^#[0-9a-f]{6}$/i.test(color) ? color : "#6e6e62"}
            onChange={e => setColor(e.target.value)}
            disabled={pending}
          />
        </>
      ) : (
        <>
          <label htmlFor="category-flexibility">How flexible are tasks in this category?</label>
          <select
            id="category-flexibility"
            value={flexibility}
            onChange={e => setFlexibility(e.target.value)}
            required
            disabled={pending}
          >
            <option value="">Choose flexibility</option>
            {flexibilityOptions.map(value => <option key={value} value={value}>{value}</option>)}
          </select>
          <p className="hint">Fixed stays put; protected avoids disruption; flexible can move; optional yields first.</p>

          <label htmlFor="category-importance">
            Usual importance <span className="hint">Optional</span>
          </label>
          <select
            id="category-importance"
            value={importance}
            onChange={e => setImportance(e.target.value)}
            disabled={pending}
          >
            <option value="">Choose on each task</option>
            {importanceOptions.map(value => <option key={value} value={value}>{value}</option>)}
          </select>
          <p className="hint">These defaults apply to new tasks. Existing tasks keep their saved choices.</p>
        </>
      )}

      {error && <p role="alert" className="error">{error}</p>}

      <div className="actions">
        {step === 1 && (
          <button type="button" disabled={pending} onClick={() => setStep(0)}>Back</button>
        )}
        <button
          className="primary"
          disabled={pending || !name.trim() || (step === 1 && !flexibility)}
        >
          {step === 0 ? "Continue" : "Save category"}
        </button>
        <button type="button" disabled={pending} onClick={onClose}>Cancel</button>
      </div>
    </form>
  );
}
