import { useState } from "react";
import { configure, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it } from "vitest";
import ExecutionWorkspace from "../src/ExecutionWorkspace";
import { Client, jsonBody, request } from "../src/api";
import { ExecutionView, Workspace } from "../src/execution";
import { addDays, dateKey } from "../src/executionTime";

// Run against a dedicated, disposable backend: mvn spring-boot:run -Plocal
// -Dspring-boot.run.arguments=--server.port=18080. No responses or clocks mocked.
const base = "http://localhost:18080";
configure({ asyncUtilTimeout: 15000 });
function Harness({ client }: { client: Client }) {
  const [view, navigate] = useState<ExecutionView>("today");
  return <><nav>{(["today", "focus", "schedule", "progress"] as const).map(v => <button key={v} onClick={() => navigate(v)}>{v}</button>)}</nav>
    <ExecutionWorkspace client={client} view={view} navigate={navigate} initialTask={null} openGoal={() => {}} /></>;
}

it("executes generated and manually moved work through real frontend/backend contracts", async () => {
  const credentials = { email: `execution-${crypto.randomUUID()}@example.test`, password: `Local-test-${crypto.randomUUID()}` };
  await request(`${base}/api/auth/register`, null, { method: "POST", ...jsonBody(credentials) });
  const auth = await request<{ token: string }>(`${base}/api/auth/login`, null, { method: "POST", ...jsonBody(credentials) });
  // jsdom AbortSignal is not the Node fetch realm's signal; only adapt that boundary.
  // HTTP methods, payloads, responses and authentication remain the production client contract.
  const client: Client = (path, options) => { const { signal: _signal, ...rest } = options ?? {}; return request(base + path, auth.token, rest); };
  const mutate = <T,>(path: string, body: unknown) => client<T>(path, { method: "POST", ...jsonBody(body), headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() } });
  await client("/users/me/working-hours", { method: "PUT", ...jsonBody({ timezone: "UTC", windows: Array.from({ length: 7 }, (_, i) => [
    { dayOfWeek: i + 1, startTime: "00:00", endTime: "12:00", kind: "working" },
    { dayOfWeek: i + 1, startTime: "12:00", endTime: "00:00", kind: "working" },
  ]).flat() }) });
  const task = await client<{ id: number }>("/commitments", { method: "POST", ...jsonBody({ title: "Live execution contract", completionCriterion: "Real UI and API complete the loop", importance: "medium", flexibilityTier: "flexible" }) });
  const start = new Date(Date.now() - 1000).toISOString(), end = new Date(Date.now() + 3600000).toISOString();
  const generate = { startTime: start, endTime: end, work: [{ commitmentId: task.id, workMinutes: 1 }] };
  await mutate("/schedule/generate", generate);
  const initial = await client<Workspace>("/execution");
  expect(initial.blocks).toHaveLength(1); expect(initial.blocks[0].userMovedFlag).toBe(false);
  const user = userEvent.setup(); const app = render(<Harness client={client} />);
  expect(await screen.findByText("Real UI and API complete the loop")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "schedule" }));
  const calendar = await screen.findByRole("region", { name: "Week calendar" });
  const article = within(calendar).getByRole("button", { name: "Live execution contract" }).closest("article")!;
  const today = dateKey(Date.now(), "UTC");
  // Pick a future visible day; on Sunday use the next week.
  if (new Date().getUTCDay() === 0) await user.click(screen.getByRole("button", { name: "Next week" }));
  const tomorrow = addDays(today, 1);
  const target = await screen.findByRole("button", { name: new RegExp(`Move here ${tomorrow} 10:00 AM`, "i") });
  const transfer = { getData: () => String(initial.blocks[0].id), setData: () => {} };
  fireEvent.dragStart(article, { dataTransfer: transfer }); fireEvent.dragOver(target, { dataTransfer: transfer }); fireEvent.drop(target, { dataTransfer: transfer });
  await waitFor(async () => expect((await client<Workspace>("/execution")).blocks.some(b => b.state === "scheduled" && b.userMovedFlag)).toBe(true));
  await user.click(screen.getByRole("button", { name: "Refresh" }));
  await screen.findByText("You chose this time · sticky", {}, { timeout: 10000 });
  const moved = (await client<Workspace>("/execution")).blocks.find(b => b.state === "scheduled")!;
  await mutate("/schedule/generate", generate);
  expect((await client<Workspace>("/execution")).blocks.find(b => b.id === moved.id)).toEqual(moved);

  // Exact move back to now through the same task brief form used by keyboard users.
  await user.click(within(screen.getByRole("region", { name: "Week calendar" })).getByRole("button", { name: "Live execution contract" }));
  await screen.findByRole("region", { name: "Task brief" });
  fireEvent.change(screen.getByLabelText("Minutes to set aside"), { target: { value: "1" } });
  await waitFor(() => expect(screen.getByRole("button", { name: "Move work window" })).toBeEnabled());
  await user.click(screen.getByRole("button", { name: "Move work window" }));
  await screen.findByText("Work window saved. It’s ready on Today and Schedule.", {}, {timeout:10000});
  await user.click(await screen.findByRole("button", { name: "Start" }));
  await screen.findByRole("heading", { name: "Focus", level: 1 });
  await waitFor(() => expect(screen.getByRole("button", {name:"Pause"})).toBeEnabled());
  await user.click(screen.getByRole("button", { name: "Pause" }));
  await screen.findByText("Paused · continue when ready");
  const paused = (await client<Workspace>("/execution")).blocks.find(b => b.state === "active")!;
  expect(paused.sessionState).toBe("paused");
  app.unmount(); render(<Harness client={client} />);
  await user.click(await screen.findByRole("button", { name: "Resume" }));
  await screen.findByRole("heading", { name: "Focus", level: 1 });
  // Wait for the REAL one-minute block plus five-minute grace period; no forged clock.
  await screen.findByText("Still working on this? Wrap up or keep going.", {}, { timeout: 380000 });
  await user.click(screen.getByRole("button", { name: "Keep going" }));
  await user.click(screen.getByRole("button", { name: "Refresh" }));
  await screen.findByRole("button", { name: "Pause" });
  expect(screen.queryByText("Still working on this? Wrap up or keep going.")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Finish" }));
  await user.type(screen.getByLabelText("What you accomplished"), "Verified the real execution loop");
  await user.selectOptions(screen.getByLabelText("Task outcome"), "partial");
  fireEvent.change(screen.getByLabelText("Your estimate of task completion (%)"), { target: { value: "40" } });
  await user.click(screen.getByRole("button", { name: "Save and finish" }));
  await screen.findByRole("region", { name: "Session saved" });
  const completed = await client<Workspace>("/execution");
  expect(completed.history).toHaveLength(1); expect(completed.tasks[0].completionPct).toBe(40);
  expect(completed.progress!.plannedMillis).toBe(60000);
  expect(completed.progress!.executedMillis).toBe(completed.history[0].activeMillis);
  expect(completed.history[0].activeMillis).toBeGreaterThan(300000);
  await user.click(screen.getByRole("button", { name: "progress" }));
  expect(await screen.findByText("40% reported complete")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "today" }));
  await waitFor(() => expect(screen.queryByRole("region", { name: "Current task" })).not.toBeInTheDocument());
  await user.click(screen.getByRole("button", { name: "schedule" }));
  await screen.findByRole("region", { name: "Week calendar" });
  expect((await client<Workspace>("/execution")).history).toEqual(completed.history);
});
