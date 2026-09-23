import { useCallback, useEffect, useRef, useState } from "react";
import AuthScreen, { User } from "./AuthScreen";
import { ApiError, Client, messageOf, request } from "./api";
import GoalsScreen from "./GoalsScreen";
import ExecutionWorkspace from "./ExecutionWorkspace";
import { ExecutionView } from "./execution";
import CategoriesScreen from "./CategoriesScreen";

const SESSION_KEY = "atlas.session";
function savedToken() { try { return sessionStorage.getItem(SESSION_KEY); } catch { return null; } }
function persistToken(token: string | null) {
  try { if (token) sessionStorage.setItem(SESSION_KEY, token); else sessionStorage.removeItem(SESSION_KEY); } catch { /* In-memory sign-in still works when storage is unavailable. */ }
}

type Screen = ExecutionView | "goals" | "categories";

export default function App() {
  const [token, setToken] = useState(savedToken);
  const [user, setUser] = useState<User | null>(null);
  const [checking, setChecking] = useState(!!token);
  const [notice, setNotice] = useState("");
  const [verificationError, setVerificationError] = useState("");
  const [retry, setRetry] = useState(0);
  const [screen, setScreen] = useState<Screen>("today");
  const [taskId, setTaskId] = useState<number | null>(null);
  const [goalId, setGoalId] = useState<number | null>(null);
  const activeToken = useRef(token);
  activeToken.current = token;

  const signOut = useCallback((reason = "") => {
    activeToken.current = null;
    persistToken(null); setToken(null); setUser(null); setChecking(false); setNotice(reason); setScreen("today"); setTaskId(null); setGoalId(null);
  }, []);

  useEffect(() => {
    // Remove credentials left by the old developer-only token-paste screen.
    try { localStorage.removeItem("atlas.walking-skeleton.jwt"); } catch { /* Storage may be disabled. */ }
  }, []);

  useEffect(() => {
    if (!token || user) return;
    const controller = new AbortController();
    setChecking(true); setVerificationError("");
    request<User>("/api/auth/me", token, { signal: controller.signal })
      .then(profile => {
        if (controller.signal.aborted) return;
        setUser(profile);
        setChecking(false);
      })
      .catch(error => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.status === 401) signOut("Your session ended. Sign in to continue.");
        else { setVerificationError(messageOf(error)); setChecking(false); }
      });
    return () => controller.abort();
  }, [token, user, retry, signOut]);

  const client: Client = useCallback(async <T,>(path: string, options?: RequestInit): Promise<T> => {
    try { return await request<T>(path, token, options); }
    catch (error) {
      if (error instanceof ApiError && error.status === 401 && activeToken.current === token) signOut("Your session ended. Sign in to continue.");
      throw error;
    }
  }, [token, signOut]);

  if (checking) return <main className="loading-page" role="status"><p>Opening your space…</p></main>;
  if (token && !user) return (
    <main className="loading-page">
      <p role="alert">{verificationError}</p>
      <button onClick={() => setRetry(retry + 1)}>Try again</button>
      <button onClick={() => signOut()}>Sign out</button>
    </main>
  );
  if (!user) return <AuthScreen notice={notice} onSignIn={(nextToken, profile) => { persistToken(nextToken); setToken(nextToken); setUser(profile); setNotice(""); }} />;

  const nav: { id: Screen; label: string }[] = [
    { id: "today",      label: "Today" },
    { id: "focus",      label: "Focus" },
    { id: "goals",      label: "Goals" },
    { id: "schedule", label: "Schedule" },
    { id: "progress", label: "Progress" },
  ];

  return (
    <div className="app-shell">
      <header className="app-header">
        {/* Brand wordmark */}
        <a className="brand" href="#" onClick={e => { e.preventDefault(); setScreen("today"); }}>
          atlas
        </a>

        {/* Inline nav links */}
        <nav className="app-nav" aria-label="Main navigation">
          {nav.map(({ id, label }) => (
            <button
              key={id}
              aria-current={screen === id ? "page" : undefined}
              onClick={() => { setScreen(id); setTaskId(null); setGoalId(null); }}
            >
              {label}
            </button>
          ))}
        </nav>

        {/* Right: account info + sign out */}
        <div className="account">
          <span title={user.email}>{user.email}</span>
          <button className="text-button" onClick={() => signOut()}>Sign out</button>
        </div>
      </header>

      <main className="workspace" key={user.id}>
        {screen === "goals"      ? <><div className="workspace-tools"><button className="text-button" onClick={() => setScreen("categories")}>Manage categories</button><button className="text-button" onClick={() => setScreen("today")}>Back to Today →</button></div><GoalsScreen client={client} initialGoalId={goalId} onWork={id => { setTaskId(id); setScreen("today"); }} /></> :
         screen === "categories" ? <CategoriesScreen client={client} /> :
                                   <ExecutionWorkspace client={client} view={screen} navigate={setScreen} initialTask={taskId} openGoal={id => { setGoalId(id); setScreen("goals"); }} />}
      </main>
    </div>
  );
}
