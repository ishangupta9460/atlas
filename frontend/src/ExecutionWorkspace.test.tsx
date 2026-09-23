import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import App from "./App";
import { Workspace, Work } from "./execution";

const task: Work = { id: 1, title: "Build the sign-in flow", completionCriterion: "Sign in and return to Today", description: "Keep it simple", workState: "ready", completionPct: 0, importance: "high", flexibilityTier: "flexible", deadline: null, goalId: 10, goalTitle: "Ship Atlas", goalState: "active", milestoneTitle: "Foundation", categoryName: "Building", blockers: 0 };
let data: Workspace;
let failPause: boolean;
const calls: { path: string; options: RequestInit }[] = [];
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
beforeEach(() => {
  sessionStorage.clear(); sessionStorage.setItem("atlas.session", "test-token"); calls.length = 0; failPause = false;
  const now = Date.now();
  data = { serverTime: new Date(now).toISOString(), tasks: [task, { ...task, id: 2, title: "Write the next chapter" }], fixed: [], history: [], blocks: [
    { id: 1, commitmentId: 1, startTime: new Date(now - 1000).toISOString(), endTime: new Date(now + 1500000).toISOString(), state: "scheduled", placementReason: "You chose this work window.", sessionState: null, actualStart: null, runningSince: null, activeMillis: 0 },
  ] };
  vi.stubGlobal("fetch", async (path: string, options: RequestInit = {}) => {
    calls.push({ path, options });
    if (path === "/api/auth/me") return response({ id: 1, email: "person@example.com" });
    if (path === "/execution") return response(data);
    if (path.includes("/session/")) {
      const action = path.split("/").pop();
      if (action === "pause" && failPause) { failPause = false; return response({ message: "Connection interrupted. Try again." }, 503); }
      const b = data.blocks[0];
      if (action === "start") { b.state = "active"; b.sessionState = "running"; b.actualStart = new Date().toISOString(); b.runningSince = b.actualStart; data.tasks[0] = { ...task, workState: "in_progress" }; }
      if (action === "pause") { b.sessionState = "paused"; b.runningSince = null; b.activeMillis = 65000; }
      if (action === "resume") { b.sessionState = "running"; b.runningSince = new Date().toISOString(); }
      if (action === "finish") {
        const report = JSON.parse(String(options.body));
        b.state = "completed"; b.sessionState = "finished"; b.runningSince = null;
        data.tasks[0] = { ...task, workState: report.completionPct === 100 ? "completed" : "ready", completionPct: report.completionPct };
        data.history.push({ blockId: 1, commitmentId: 1, title: task.title!, startTime: b.actualStart!, endTime: new Date().toISOString(), activeMillis: 65000, report: report.report, completionPct: report.completionPct });
      }
      return response(b);
    }
    throw new Error(`Unexpected request: ${path}`);
  });
});

it("opens Today, runs focus pause/resume/finish, and connects saved progress and next work", async () => {
  const user = userEvent.setup(); render(<App />);
  await screen.findByRole("heading", { name: "Today" });
  expect(await screen.findByText(task.completionCriterion!)).toBeInTheDocument();
  await user.click(await screen.findByRole("button", { name: "Start" }));
  await screen.findByRole("heading", { name: "Focus" });
  await user.click(screen.getByRole("button", { name: "Pause" }));
  await screen.findByText("Paused · continue when ready");
  expect(screen.getByLabelText("Active time")).toHaveTextContent("00:01:05");
  await user.click(screen.getByRole("button", { name: "Today" }));
  expect(screen.getByRole("button", { name: "Resume" })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Resume" }));
  await user.click(screen.getByRole("button", { name: "Finish" }));
  await user.type(screen.getByLabelText("What you accomplished"), "The sign-in flow works end to end.");
  await user.click(screen.getByRole("button", { name: "Save and finish" }));
  await screen.findByText("Task complete. Your progress is saved.");
  await user.click(screen.getByRole("button", { name: "See what’s next →" }));
  expect(screen.getByText("Write the next chapter")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Progress" }));
  expect(screen.getByText("The sign-in flow works end to end.")).toBeInTheDocument();
  expect(screen.getByText(/50% · average reported task completion/)).toBeInTheDocument();
  expect(calls.filter(c => c.path.includes("/session/")).every(c => (c.options.headers as Record<string, string>)["Idempotency-Key"])).toBe(true);
});

it("retains the current session after a failed pause and retries with the same request key", async () => {
  const user = userEvent.setup(); render(<App />);
  await user.click(await screen.findByRole("button", { name: "Start" }));
  failPause = true;
  await user.click(screen.getByRole("button", { name: "Pause" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Connection interrupted");
  expect(screen.getByText("In progress")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Pause" }));
  await screen.findByText("Paused · continue when ready");
  const pauses = calls.filter(c => c.path.endsWith("/pause"));
  expect(pauses[0].options.headers).toEqual(pauses[1].options.headers);
});

it("keeps an unstarted window that crosses midnight available on Today", async () => {
  const midnight = new Date(); midnight.setHours(0, 5, 0, 0);
  data.serverTime = midnight.toISOString();
  data.blocks[0].startTime = new Date(midnight.getTime() - 15 * 60000).toISOString();
  data.blocks[0].endTime = new Date(midnight.getTime() + 25 * 60000).toISOString();
  render(<App />);
  expect(await screen.findByRole("button", { name: "Start" })).toBeEnabled();
  expect(screen.getByText(task.completionCriterion!)).toBeInTheDocument();
});

it("saves partial work without pretending the task is complete", async () => {
  const user = userEvent.setup(); render(<App />);
  await user.click(await screen.findByRole("button", { name: "Start" }));
  await user.click(screen.getByRole("button", { name: "Finish" }));
  await user.type(screen.getByLabelText("What you accomplished"), "Form is ready; validation remains.");
  await user.selectOptions(screen.getByLabelText("Task outcome"), "partial");
  await user.clear(screen.getByLabelText("Your estimate of task completion (%)"));
  await user.type(screen.getByLabelText("Your estimate of task completion (%)"), "40");
  await user.click(screen.getByRole("button", { name: "Save and finish" }));
  await screen.findByText("Session saved. The remaining work is ready for another window.");
  expect(data.tasks[0].workState).toBe("ready"); expect(data.tasks[0].completionPct).toBe(40);
});

it("keeps the queue short and exposes context and later work on demand", async () => {
  for (let n = 2; n <= 7; n++) {
    data.tasks.push({ ...task, id: n + 10, title: `Later task ${n}` });
    data.blocks.push({ ...data.blocks[0], id: n, commitmentId: n + 10, startTime: new Date(Date.now() + n * 60000).toISOString(), endTime: new Date(Date.now() + (n + 1) * 60000).toISOString() });
  }
  const user = userEvent.setup(); render(<App />);
  const next = await screen.findByRole("region", { name: "Next" });
  expect(within(next).getAllByRole("listitem")).toHaveLength(3);
  expect(screen.getByText("Later · 3 more today").closest("details")).not.toHaveAttribute("open");
  await user.click(screen.getByRole("button", { name: "Task context" }));
  const brief = screen.getByRole("region", { name: "Task brief" });
  expect(within(brief).getByText("Milestone · Foundation")).toBeInTheDocument();
});
