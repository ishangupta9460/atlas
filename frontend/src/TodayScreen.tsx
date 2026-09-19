import { FormEvent, useEffect, useState } from "react";

type TaskStatus = "ready" | "in_progress" | "completed";

type Task = {
  id: number;
  title: string;
  status: TaskStatus;
};

const TOKEN_STORAGE_KEY = "atlas.walking-skeleton.jwt";

function errorMessage(response: Response) {
  return response.json()
    .then((body: { message?: string }) => body.message ?? "Request failed")
    .catch(() => "Request failed");
}

/**
 * Deliberately bare FOUND-003 UI: it proves the task loop without taking on
 * the later Today/Now/Next/Later screen's scheduling or presentation rules.
 */
function TodayScreen() {
  const [tokenInput, setTokenInput] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY) ?? "");
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY) ?? "");
  const [tasks, setTasks] = useState<Task[]>([]);
  const [title, setTitle] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const response = await fetch(path, {
      ...options,
      headers: {
        Authorization: `Bearer ${token}`,
        ...options.headers,
      },
    });

    if (!response.ok) {
      throw new Error(await errorMessage(response));
    }

    return response.json() as Promise<T>;
  }

  async function refreshTasks() {
    if (!token) {
      setTasks([]);
      return;
    }

    setLoading(true);
    setError("");
    try {
      setTasks(await request<Task[]>("/api/tasks/today"));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Could not load Today");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refreshTasks();
  }, [token]);

  function saveToken(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextToken = tokenInput.trim();
    localStorage.setItem(TOKEN_STORAGE_KEY, nextToken);
    setToken(nextToken);
  }

  async function createTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!title.trim()) return;

    setError("");
    try {
      await request<Task>("/api/tasks", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title: title.trim() }),
      });
      setTitle("");
      await refreshTasks();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Could not create task");
    }
  }

  async function transitionTask(task: Task, action: "start" | "finish") {
    setError("");
    try {
      await request<Task>(`/api/tasks/${task.id}/${action}`, { method: "POST" });
      await refreshTasks();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : `Could not ${action} task`);
    }
  }

  return (
    <section>
      <h2>Today</h2>
      <p>Paste a JWT obtained from the existing login endpoint to use this placeholder task loop.</p>

      <form onSubmit={saveToken}>
        <label htmlFor="jwt-token">JWT token</label>
        <input
          id="jwt-token"
          type="password"
          value={tokenInput}
          onChange={(event) => setTokenInput(event.target.value)}
        />
        <button type="submit">Load tasks</button>
      </form>

      {token && (
        <>
          <form onSubmit={createTask}>
            <label htmlFor="task-title">New task</label>
            <input
              id="task-title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              required
            />
            <button type="submit">Create task</button>
          </form>

          <button type="button" onClick={() => void refreshTasks()} disabled={loading}>
            Refresh Today
          </button>
          {loading && <p>Loading…</p>}
          {error && <p role="alert">{error}</p>}

          <ul>
            {tasks.map((task) => (
              <li key={task.id}>
                {task.title} — {task.status}
                {task.status === "ready" && (
                  <button type="button" onClick={() => void transitionTask(task, "start")}>
                    Start
                  </button>
                )}
                {task.status === "in_progress" && (
                  <button type="button" onClick={() => void transitionTask(task, "finish")}>
                    Finish
                  </button>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

export default TodayScreen;
