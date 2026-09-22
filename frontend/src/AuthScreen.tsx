import { FormEvent, useState } from "react";
import { jsonBody, messageOf, request } from "./api";

export type User = { id: number; email: string };

export default function AuthScreen({ onSignIn, notice }: {
  onSignIn: (token: string, user: User) => void; notice: string;
}) {
  const [register, setRegister] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    setPending(true); setError("");
    try {
      const credentials = { email: email.trim(), password };
      if (register) {
        await request<User>("/api/auth/register", null, { method: "POST", ...jsonBody(credentials) });
        setRegister(false); setCreated(true);
      }
      const { token } = await request<{ token: string }>("/api/auth/login", null, { method: "POST", ...jsonBody(credentials) });
      const user = await request<User>("/api/auth/me", token);
      onSignIn(token, user);
    } catch (failure) { setError(messageOf(failure)); }
    finally { setPending(false); }
  }

  return <main className="auth-layout">
    <section className="intro"><p className="eyebrow">ATLAS / A LITTLE MORE INTENTION</p>
      <h1>Make room for<br />what matters.</h1>
      <p>Start with something you want to achieve.<br />Keep your goals and your next steps in one place.</p>
    </section>
    <section className="card auth-card">
      <p className="eyebrow">YOUR SPACE TO MAKE PROGRESS</p>
      <h2>{register ? "Create your account" : "Welcome back"}</h2>
      {notice && <p role="status">{notice}</p>}
      {created && <p role="status">Your account is ready. Sign in to continue.</p>}
      <form onSubmit={submit}>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" autoComplete="username" maxLength={255} required value={email} onChange={e => setEmail(e.target.value)} disabled={pending} />
        <label htmlFor="password">Password</label>
        <input id="password" type="password" autoComplete={register ? "new-password" : "current-password"} minLength={register ? 8 : undefined} required value={password} onChange={e => setPassword(e.target.value)} disabled={pending} />
        {register && <p className="hint">Use at least 8 characters.</p>}
        {error && <p role="alert" className="error">{error}</p>}
        <button className="primary" disabled={pending}>{pending ? "Connecting…" : register ? "Create account" : "Sign in"}</button>
      </form>
      <button className="text-button" disabled={pending} onClick={() => { setRegister(!register); setError(""); setCreated(false); }}>{register ? "Already have an account? Sign in" : "New here? Create an account"}</button>
    </section>
  </main>;
}
