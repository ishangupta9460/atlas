import { useCallback, useEffect, useRef, useState } from "react";
import AuthScreen, { User } from "./AuthScreen";
import { ApiError, Client, messageOf, request } from "./api";
import GoalsScreen from "./GoalsScreen";
import TodayScreen from "./TodayScreen";
import CategoriesScreen from "./CategoriesScreen";

const SESSION_KEY = "atlas.session";
function savedToken() { try { return sessionStorage.getItem(SESSION_KEY); } catch { return null; } }
function persistToken(token: string | null) {
  try { if (token) sessionStorage.setItem(SESSION_KEY, token); else sessionStorage.removeItem(SESSION_KEY); } catch { /* In-memory sign-in still works when storage is unavailable. */ }
}

export default function App() {
  const [token, setToken] = useState(savedToken);
  const [user, setUser] = useState<User | null>(null);
  const [checking, setChecking] = useState(!!token);
  const [notice, setNotice] = useState("");
  const [verificationError, setVerificationError] = useState("");
  const [retry, setRetry] = useState(0);
  const [screen, setScreen] = useState<"goals" | "tasks" | "categories">("goals");
  const activeToken = useRef(token);
  activeToken.current = token;

  const signOut = useCallback((reason = "") => {
    activeToken.current = null;
    persistToken(null); setToken(null); setUser(null); setChecking(false); setNotice(reason); setScreen("goals");
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
        // Set both values before this effect's user dependency can clean it up.
        // Otherwise the cleanup aborts the request before `finally` clears loading.
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

  if (checking) return <main className="loading-page" role="status">Opening your space…</main>;
  if (token && !user) return <main className="loading-page"><p role="alert">{verificationError}</p><button onClick={() => setRetry(retry + 1)}>Try again</button><button onClick={() => signOut()}>Sign out</button></main>;
  if (!user) return <AuthScreen notice={notice} onSignIn={(nextToken, profile) => { persistToken(nextToken); setToken(nextToken); setUser(profile); setNotice(""); }} />;

  return <div className="app-shell">
    <header className="app-header"><a className="brand" href="#" onClick={e => { e.preventDefault(); setScreen("goals"); }}>atlas<span> / make room</span></a>
      <div className="account"><span>{user.email}</span><button className="text-button" onClick={() => signOut()}>Sign out</button></div>
    </header>
    <nav aria-label="Main navigation"><button aria-current={screen === "goals" ? "page" : undefined} onClick={() => setScreen("goals")}>Goals</button><button aria-current={screen === "tasks" ? "page" : undefined} onClick={() => setScreen("tasks")}>Tasks</button><button aria-current={screen === "categories" ? "page" : undefined} onClick={() => setScreen("categories")}>Categories</button></nav>
    <main className="workspace" key={user.id}>{screen === "goals" ? <GoalsScreen client={client} /> : screen === "categories" ? <CategoriesScreen client={client} /> : <TodayScreen client={client} />}</main>
  </div>;
}
